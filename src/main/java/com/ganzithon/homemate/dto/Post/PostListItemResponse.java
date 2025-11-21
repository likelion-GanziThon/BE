package com.ganzithon.homemate.dto.Post;

import com.ganzithon.homemate.entity.Post.RoommatePost;
import com.ganzithon.homemate.entity.Post.RoommatePostImage;
import com.ganzithon.homemate.entity.Post.FreePost;
import com.ganzithon.homemate.entity.Post.FreePostImage;
import com.ganzithon.homemate.entity.Post.PolicyPost;
import com.ganzithon.homemate.entity.Post.PolicyPostImage;
import com.ganzithon.homemate.entity.User;

import java.time.Instant;
import java.util.Comparator;

public class PostListItemResponse {

    private Long id;
    private String title;
    private Long userId;

    // ✅ 목록에서는 작성자 loginId만 필요
    private String writerLoginId;

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
    public static PostListItemResponse fromRoommate(RoommatePost post, User writer) {

        PostListItemResponse dto = new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getUserId(),
                post.getViewCount(),
                post.getCreatedAt()
        );

        // 작성자 loginId만 세팅
        dto.writerLoginId = (writer != null) ? writer.getLoginId() : null;

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
    public static PostListItemResponse fromFree(FreePost post, User writer) {

        PostListItemResponse dto = new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getUserId(),
                post.getViewCount(),
                post.getCreatedAt()
        );

        dto.writerLoginId = (writer != null) ? writer.getLoginId() : null;

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
    public static PostListItemResponse fromPolicy(PolicyPost post, User writer) {

        PostListItemResponse dto = new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getUserId(),
                post.getViewCount(),
                post.getCreatedAt()
        );

        dto.writerLoginId = (writer != null) ? writer.getLoginId() : null;

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

    public String getWriterLoginId() {
        return writerLoginId;
    }

    public void setWriterLoginId(String writerLoginId) {
        this.writerLoginId = writerLoginId;
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
