package com.ganzithon.homemate.controller;

import com.ganzithon.homemate.dto.Profile.ProfileResponse;
import com.ganzithon.homemate.dto.Profile.ProfileUpdateRequest;
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

import java.time.LocalDate;

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

    // 본인 프로필 수정 (PUT) - JSON 방식
    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid ProfileUpdateRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileResponse response = profileService.updateProfile(principal.id(), request, null);
        return ResponseEntity.ok(response);
    }

    // 본인 프로필 수정 (PUT) - Multipart 방식 (이미지 포함)
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileResponse> updateMyProfileWithImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "desiredArea", required = false) String desiredArea,
            @RequestParam(name = "desiredMoveInDate", required = false) String desiredMoveInDate,
            @RequestParam(name = "introduction", required = false) String introduction,
            @RequestPart(name = "profileImage", required = false) MultipartFile profileImage) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 문자열을 LocalDate로 변환
        LocalDate moveInDate = null;
        if (desiredMoveInDate != null && !desiredMoveInDate.isEmpty()) {
            try {
                moveInDate = LocalDate.parse(desiredMoveInDate);
            } catch (Exception e) {
                // 파싱 실패 시 null 유지
            }
        }
        
        ProfileUpdateRequest request = new ProfileUpdateRequest(desiredArea, moveInDate, introduction);
        ProfileResponse response = profileService.updateProfile(principal.id(), request, profileImage);
        return ResponseEntity.ok(response);
    }

    // 본인 프로필 삭제 (DELETE)
    // type=image: 이미지만 삭제
    // type=content: 내용만 삭제 (desiredArea, desiredMoveInDate, introduction)
    // type 없음: 이미지와 내용 모두 삭제
    @DeleteMapping("/me")
    public ResponseEntity<ProfileResponse> deleteMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "type", required = false) String type) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        ProfileResponse response;
        if ("image".equalsIgnoreCase(type)) {
            // 이미지만 삭제
            response = profileService.deleteProfileImage(principal.id());
        } else if ("content".equalsIgnoreCase(type)) {
            // 내용만 삭제
            response = profileService.deleteProfileContent(principal.id());
        } else {
            // 전체 삭제 (이미지 + 내용)
            response = profileService.deleteProfileAll(principal.id());
        }
        
        return ResponseEntity.ok(response);
    }

    // 타인 프로필 조회
    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponse> getUserProfile(@PathVariable("userId") Long userId) {
        ProfileResponse response = profileService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/image/{userId}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable("userId") Long userId) {
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

