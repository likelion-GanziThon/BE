package com.ganzithon.homemate.controller;

import com.ganzithon.homemate.dto.ProfileResponse;
import com.ganzithon.homemate.dto.ProfileUpdateRequest;
import com.ganzithon.homemate.security.UserPrincipal;
import com.ganzithon.homemate.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // 본인 프로필 조회 (GET)
    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileResponse response = profileService.getProfile(principal.id());
        return ResponseEntity.ok(response);
    }

    // 본인 프로필 수정 (PUT) - JSON 또는 multipart 모두 지원
    @PutMapping(value = "/me", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) @Valid ProfileUpdateRequest requestBody,
            @RequestPart(value = "profile", required = false) @Valid ProfileUpdateRequest requestPart,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // JSON 또는 multipart 중 하나 사용
        ProfileUpdateRequest request = requestPart != null ? requestPart : requestBody;
        ProfileResponse response = profileService.updateProfile(principal.id(), request, profileImage);
        return ResponseEntity.ok(response);
    }

    // 본인 프로필 이미지 삭제 (DELETE)
    @DeleteMapping("/me")
    public ResponseEntity<ProfileResponse> deleteMyProfileImage(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        ProfileResponse response = profileService.deleteProfileImage(principal.id());
        return ResponseEntity.ok(response);
    }

    // 타인 프로필 조회
    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponse> getUserProfile(@PathVariable Long userId) {
        ProfileResponse response = profileService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/image/{userId}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable Long userId) {
        try {
            Resource resource = profileService.loadProfileImage(userId);
            String contentType = "application/octet-stream";
            
            try {
                String filename = resource.getFilename();
                if (filename != null) {
                    if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                        contentType = MediaType.IMAGE_JPEG_VALUE;
                    } else if (filename.toLowerCase().endsWith(".png")) {
                        contentType = MediaType.IMAGE_PNG_VALUE;
                    } else if (filename.toLowerCase().endsWith(".gif")) {
                        contentType = MediaType.IMAGE_GIF_VALUE;
                    }
                }
            } catch (Exception ignored) {
                // 기본값 사용
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

