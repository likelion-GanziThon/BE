package com.ganzithon.homemate.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

    // 기본 업로드 메서드 (지금 Local에서 쓰는 방식)
    String uploadRoommateImage(MultipartFile file);
    String uploadFreeImage(MultipartFile file);
    String uploadPolicyImage(MultipartFile file);

    // postId + order 기반 업로드
    // Local에서는 굳이 안 써도 되니까 default로 옛날 방식 위임
    default String uploadRoommateImage(Long postId, int order, MultipartFile file) {
        return uploadRoommateImage(file);
    }

    default String uploadFreeImage(Long postId, int order, MultipartFile file) {
        return uploadFreeImage(file);
    }

    default String uploadPolicyImage(Long postId, int order, MultipartFile file) {
        return uploadPolicyImage(file);
    }

    // 삭제용
    void deleteRoommateImage(String url);
    void deleteFreeImage(String url);
    void deletePolicyImage(String url);
}
