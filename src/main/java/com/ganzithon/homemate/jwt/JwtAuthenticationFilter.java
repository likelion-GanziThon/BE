package com.ganzithon.homemate.jwt;

import com.ganzithon.homemate.entity.User;
import com.ganzithon.homemate.repository.UserRepository;
import com.ganzithon.homemate.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String bearerToken = resolveToken(request);
        if (bearerToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateIfPossible(bearerToken, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateIfPossible(String token, HttpServletRequest request) {
        try {
            Claims claims = tokenProvider.parseAccess(token);
            Long userId = Long.valueOf(claims.getSubject());
            Optional<User> userOptional = userRepository.findById(userId);
            userOptional.ifPresent(user -> setAuthentication(user, request));
        } catch (Exception ignored) {
            // 유효하지 않은 토큰이면 인증을 건너뛴다.
        }
    }

    private void setAuthentication(User user, HttpServletRequest request) {
        UserPrincipal principal = user.toPrincipal();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

