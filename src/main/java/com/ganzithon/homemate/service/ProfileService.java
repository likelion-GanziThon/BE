package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.ProfileResponse;
import com.ganzithon.homemate.dto.ProfileUpdateRequest;
import com.ganzithon.homemate.entity.User;
import com.ganzithon.homemate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;

    @Value("${uploadPath}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 프로필 이미지 URL 생성 (이미지가 있으면 URL 반환, 없으면 빈 문자열)
        String profileImageUrl = "";
        if (user.getProfileImagePath() != null && !user.getProfileImagePath().isEmpty()) {
            // 실제로 파일이 존재하는지 확인
            Path filePath = Paths.get(uploadPath).resolve(user.getProfileImagePath()).normalize();
            if (Files.exists(filePath)) {
                profileImageUrl = "/api/profile/image/" + userId;
            }
        }

        return ProfileResponse.of(
                user.getId(),
                user.getLoginId(),
                user.getDesiredArea(),
                user.getDesiredMoveInDate(),
                user.getIntroduction(),
                profileImageUrl
        );
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request, MultipartFile profileImage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 프로필 이미지 처리: 새 이미지가 있으면 저장, 없으면 기존 이미지 유지
        // 프로필 정보만 업데이트할 때는 이미지가 포함되지 않으면 기존 이미지가 그대로 유지됨
        String profileImagePath = user.getProfileImagePath();
        if (profileImage != null && !profileImage.isEmpty()) {
            // 새 이미지 업로드 시: 이미지가 없는 상태에서 추가 가능, 기존 이미지가 있으면 자동으로 교체
            profileImagePath = saveProfileImage(userId, profileImage, user);
        }
        // profileImage가 없으면 profileImagePath는 기존 값(user.getProfileImagePath()) 유지

        // 프로필 정보 업데이트 (request가 null이 아닌 경우)
        String desiredArea = request != null && request.desiredArea() != null 
                ? request.desiredArea() 
                : user.getDesiredArea();
        LocalDate desiredMoveInDate = request != null && request.desiredMoveInDate() != null 
                ? request.desiredMoveInDate() 
                : user.getDesiredMoveInDate();
        String introduction = request != null && request.introduction() != null 
                ? request.introduction() 
                : user.getIntroduction();

        user.updateProfile(
                desiredArea,
                desiredMoveInDate,
                introduction,
                profileImagePath
        );

        userRepository.save(user);

        // 프로필 이미지 URL 생성
        String profileImageUrl = "";
        if (profileImagePath != null && !profileImagePath.isEmpty()) {
            // 실제로 파일이 존재하는지 확인
            Path filePath = Paths.get(uploadPath).resolve(profileImagePath).normalize();
            if (Files.exists(filePath)) {
                profileImageUrl = "/api/profile/image/" + userId;
            }
        }

        return ProfileResponse.of(
                user.getId(),
                user.getLoginId(),
                user.getDesiredArea(),
                user.getDesiredMoveInDate(),
                user.getIntroduction(),
                profileImageUrl
        );
    }

    @Transactional
    public ProfileResponse deleteProfileImage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 기존 이미지 파일 삭제
        if (user.getProfileImagePath() != null && !user.getProfileImagePath().isEmpty()) {
            deleteProfileImageFile(user.getProfileImagePath());
        }

        // 프로필 이미지 경로를 null로 업데이트
        user.updateProfile(
                user.getDesiredArea(),
                user.getDesiredMoveInDate(),
                user.getIntroduction(),
                null
        );

        userRepository.save(user);

        return ProfileResponse.of(
                user.getId(),
                user.getLoginId(),
                user.getDesiredArea(),
                user.getDesiredMoveInDate(),
                user.getIntroduction(),
                ""
        );
    }

    private String saveProfileImage(Long userId, MultipartFile file, User user) {
        try {
            // 기존 이미지가 있으면 삭제 (이미지 변경 시)
            // 이미지가 없는 상태에서 추가하는 경우에는 이 부분이 실행되지 않음
            if (user.getProfileImagePath() != null && !user.getProfileImagePath().isEmpty()) {
                deleteProfileImageFile(user.getProfileImagePath());
            }

            // 업로드 디렉토리 확인 및 생성
            Path uploadDir = Paths.get(uploadPath, "profiles");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 파일명 생성 (userId_originalFileName)
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = userId + "_" + UUID.randomUUID().toString() + extension;
            
            // 파일 저장
            Path filePath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 상대 경로 반환
            return "profiles/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("프로필 이미지 저장 중 오류가 발생했습니다.", e);
        }
    }

    private void deleteProfileImageFile(String imagePath) {
        try {
            Path filePath = Paths.get(uploadPath).resolve(imagePath).normalize();
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            // 파일 삭제 실패해도 계속 진행 (이미 DB에서 제거됨)
            // 로그만 남기고 예외는 던지지 않음
        }
    }

    public Resource loadProfileImage(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            if (user.getProfileImagePath() == null || user.getProfileImagePath().isEmpty()) {
                throw new IllegalArgumentException("프로필 이미지가 없습니다.");
            }

            Path filePath = Paths.get(uploadPath).resolve(user.getProfileImagePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("프로필 이미지를 읽을 수 없습니다.");
            }
        } catch (Exception e) {
            throw new RuntimeException("프로필 이미지 로드 중 오류가 발생했습니다.", e);
        }
    }
}

