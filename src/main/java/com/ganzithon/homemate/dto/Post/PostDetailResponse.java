package com.ganzithon.homemate.dto.Post;

import com.ganzithon.homemate.dto.Comment.CommentResponse;
import com.ganzithon.homemate.entity.Post.RoommatePost;
import com.ganzithon.homemate.entity.Post.RoommatePostImage;
import com.ganzithon.homemate.entity.Post.FreePost;
import com.ganzithon.homemate.entity.Post.FreePostImage;
import com.ganzithon.homemate.entity.Post.PolicyPost;
import com.ganzithon.homemate.entity.Post.PolicyPostImage;
import com.ganzithon.homemate.entity.User;

import java.time.Instant;
import java.util.List;

public class PostDetailResponse {

    private Long id;
    private String title;
    private String content;
    private Long userId;
    private String writerLoginId;
    private String writerProfileImagePath;

    private Long viewCount;
    private Instant createdAt;
    private PostCategory category;
    private List<String> imageUrls;

    // ROOMMATE 전용
    private String openchatUrl;

    // 좋아요
    private long likeCount;
    private Boolean likedByMe;

    // 댓글
    private long commentCount;
    private List<CommentResponse> comments;

    public PostDetailResponse() {
    }

    // ===== Roommate =====
    public static PostDetailResponse fromRoommate(RoommatePost post, User writer) {
        PostDetailResponse dto = new PostDetailResponse();
        dto.id = post.getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.userId = post.getUserId();

        if (writer != null) {
            dto.writerLoginId = writer.getLoginId();
            dto.writerProfileImagePath = "/api/profile/image/" + writer.getId();
        }

        dto.viewCount = post.getViewCount();
        dto.createdAt = post.getCreatedAt();
        dto.category = PostCategory.ROOMMATE;

        dto.imageUrls = post.getImages().stream()
                .map(RoommatePostImage::getUrl)
                .toList();

        dto.openchatUrl = post.getOpenchatUrl();
        return dto;
    }

    // ===== Free =====
    public static PostDetailResponse fromFree(FreePost post, User writer) {
        PostDetailResponse dto = new PostDetailResponse();
        dto.id = post.getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.userId = post.getUserId();

        if (writer != null) {
            dto.writerLoginId = writer.getLoginId();
            dto.writerProfileImagePath = "/api/profile/image/" + writer.getId();
        }

        dto.viewCount = post.getViewCount();
        dto.createdAt = post.getCreatedAt();
        dto.category = PostCategory.FREE;

        dto.imageUrls = post.getImages().stream()
                .map(FreePostImage::getUrl)
                .toList();

        return dto;
    }

    // ===== Policy =====
    public static PostDetailResponse fromPolicy(PolicyPost post, User writer) {
        PostDetailResponse dto = new PostDetailResponse();
        dto.id = post.getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.userId = post.getUserId();

        if (writer != null) {
            dto.writerLoginId = writer.getLoginId();
            dto.writerProfileImagePath = "/api/profile/image/" + writer.getId();
        }

        dto.viewCount = post.getViewCount();
        dto.createdAt = post.getCreatedAt();
        dto.category = PostCategory.POLICY;

        dto.imageUrls = post.getImages().stream()
                .map(PolicyPostImage::getUrl)
                .toList();

        return dto;
    }

    // ====================
    // getters/setters
    // ====================

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Long getUserId() { return userId; }

    public String getWriterLoginId() { return writerLoginId; }
    public String getWriterProfileImagePath() { return writerProfileImagePath; }

    public void setWriterLoginId(String writerLoginId) { this.writerLoginId = writerLoginId; }
    public void setWriterProfileImagePath(String writerProfileImagePath) { this.writerProfileImagePath = writerProfileImagePath; }

    public Long getViewCount() { return viewCount; }
    public Instant getCreatedAt() { return createdAt; }
    public PostCategory getCategory() { return category; }
    public List<String> getImageUrls() { return imageUrls; }
    public String getOpenchatUrl() { return openchatUrl; }
    public long getLikeCount() { return likeCount; }
    public Boolean getLikedByMe() { return likedByMe; }
    public long getCommentCount() { return commentCount; }
    public List<CommentResponse> getComments() { return comments; }

    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }
    public void setLikedByMe(Boolean likedByMe) { this.likedByMe = likedByMe; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }
    public void setComments(List<CommentResponse> comments) { this.comments = comments; }
}
