package com.ganzithon.homemate.dto;

import com.ganzithon.homemate.entity.RoommatePost;
import com.ganzithon.homemate.entity.RoommatePostImage;
import com.ganzithon.homemate.entity.FreePost;
import com.ganzithon.homemate.entity.FreePostImage;
import com.ganzithon.homemate.entity.PolicyPost;
import com.ganzithon.homemate.entity.PolicyPostImage;

import java.time.Instant;
import java.util.Comparator;

public class PostListItemResponse {

    private Long id;
    private String title;
    private Long userId;
    private Long viewCount;
    private Instant createdAt;
    private long commentCount;
    private String thumbnailUrl;

    public PostListItemResponse() {
    }

    public PostListItemResponse(Long id, String title, Long userId, Long viewCount, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.userId = userId;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
    }

    // ===============================================
    // ROOMMATE
    // ===============================================
    public static PostListItemResponse fromRoommate(RoommatePost post) {

        PostListItemResponse dto = new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getUserId(),
                post.getViewCount(),
                post.getCreatedAt()
        );

        // 썸네일 (첫 번째 이미지)
        String thumbnail = post.getImages().stream()
                .sorted(Comparator.comparingInt(RoommatePostImage::getOrderNo))
                .map(RoommatePostImage::getUrl)
                .findFirst()
                .orElse(null);

        dto.setThumbnailUrl(thumbnail);
        return dto;
    }

    // ===============================================
    // FREE
    // ===============================================
    public static PostListItemResponse fromFree(FreePost post) {

        PostListItemResponse dto = new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getUserId(),
                post.getViewCount(),
                post.getCreatedAt()
        );

        String thumbnail = post.getImages().stream()
                .sorted(Comparator.comparingInt(FreePostImage::getOrderNo))
                .map(FreePostImage::getUrl)
                .findFirst()
                .orElse(null);

        dto.setThumbnailUrl(thumbnail);
        return dto;
    }

    // ===============================================
    // POLICY
    // ===============================================
    public static PostListItemResponse fromPolicy(PolicyPost post) {

        PostListItemResponse dto = new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getUserId(),
                post.getViewCount(),
                post.getCreatedAt()
        );

        String thumbnail = post.getImages().stream()
                .sorted(Comparator.comparingInt(PolicyPostImage::getOrderNo))
                .map(PolicyPostImage::getUrl)
                .findFirst()
                .orElse(null);

        dto.setThumbnailUrl(thumbnail);
        return dto;
    }

    // ===== getter / setter =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
