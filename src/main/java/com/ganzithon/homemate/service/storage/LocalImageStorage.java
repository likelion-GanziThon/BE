package com.ganzithon.homemate.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalImageStorage implements ImageStorage {

    @Value("${homemate.upload-dir}")
    private String uploadDir; // 예: C:/homemate-uploads


    // ======================
    // 이미지 저장 공통 메서드
    // ======================
    private String save(MultipartFile file, String subDir) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일입니다.");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }

        String filename = UUID.randomUUID() + ext;

        Path dir = Paths.get(uploadDir, subDir);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new RuntimeException("이미지 저장 실패", e);
        }

        return "/uploads/" + subDir + "/" + filename;
    }


    @Override
    public String uploadRoommateImage(MultipartFile file) {
        return save(file, "roommate");
    }

    @Override
    public String uploadFreeImage(MultipartFile file) {
        return save(file, "free");
    }

    @Override
    public String uploadPolicyImage(MultipartFile file) {
        return save(file, "policy");
    }


    // ======================
    //  삭제 공통 처리
    // ======================
    private void deleteByUrl(String url) {
        if (!StringUtils.hasText(url)) return;

        try {
            Path urlPath = Paths.get(url);     // /uploads/roommate/a.png
            String fileName = urlPath.getFileName().toString();  // a.png
            String subDir = urlPath.getParent().getFileName().toString(); // roommate

            // 실제 저장된 파일 경로
            Path filePath = Paths.get(uploadDir, subDir, fileName);

            Files.deleteIfExists(filePath);

        } catch (Exception e) {
            // 삭제 실패시 로그만 남기기
            // log.warn("이미지 삭제 실패: {}", url, e);
        }
    }


    @Override
    public void deleteRoommateImage(String url) {
        deleteByUrl(url);
    }

    @Override
    public void deleteFreeImage(String url) {
        deleteByUrl(url);
    }

    @Override
    public void deletePolicyImage(String url) {
        deleteByUrl(url);
    }
}
