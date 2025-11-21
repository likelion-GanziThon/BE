package com.ganzithon.homemate.dto.Post;

import com.ganzithon.homemate.dto.Comment.CommentResponse;
import com.ganzithon.homemate.entity.Post.RoommatePost;
import com.ganzithon.homemate.entity.Post.RoommatePostImage;
import com.ganzithon.homemate.entity.Post.FreePost;
import com.ganzithon.homemate.entity.Post.FreePostImage;
import com.ganzithon.homemate.entity.Post.PolicyPost;
import com.ganzithon.homemate.entity.Post.PolicyPostImage;

import java.time.Instant;
import java.util.List;

public class PostDetailResponse {

    private Long id;
    private String title;
    private String content;
    private Long userId;
    private Long viewCount;
    private Instant createdAt;
    private PostCategory category;
    private List<String> imageUrls;
    // ROOMMATE 전용
    private String openchatUrl;
    // 좋아요 개수
    private long likeCount;
    // 내가 좋아요 눌렀는지 여부 (로그인 안 했으면 null)
    private Boolean likedByMe;
    // 댓글 개수
    private long commentCount;
    // 댓글 목록
    private List<CommentResponse> comments;


    public PostDetailResponse() {
    }

    // ===== 정적 팩터리 (ROOMMATE 전용) =====
    public static PostDetailResponse fromRoommate(RoommatePost post) {
        PostDetailResponse dto = new PostDetailResponse();
        dto.id = post.getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.userId = post.getUserId();
        dto.viewCount = post.getViewCount();
        dto.createdAt = post.getCreatedAt();
        dto.category = PostCategory.ROOMMATE;

        // 이미지 URL만 추출
        List<String> urls = post.getImages().stream()
                .map(RoommatePostImage::getUrl)
                .toList();
        dto.imageUrls = urls;

        dto.openchatUrl = post.getOpenchatUrl();

        return dto;
    }

    // FREE, POLICY용은 나중에 엔티티 만들면 이렇게 추가하면 됨

    public static PostDetailResponse fromFree(FreePost post) {
        PostDetailResponse dto = new PostDetailResponse();
        dto.id = post.getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.userId = post.getUserId();
        dto.viewCount = post.getViewCount();
        dto.createdAt = post.getCreatedAt();
        dto.category = PostCategory.FREE;

        List<String> urls = post.getImages().stream()
                .map(FreePostImage::getUrl)
                .toList();
        dto.imageUrls = urls;

        // openchatUrl 없음
        return dto;
    }

    public static PostDetailResponse fromPolicy(PolicyPost post) {
        PostDetailResponse dto = new PostDetailResponse();
        dto.id = post.getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.userId = post.getUserId();
        dto.viewCount = post.getViewCount();
        dto.createdAt = post.getCreatedAt();
        dto.category = PostCategory.POLICY;

        List<String> urls = post.getImages().stream()
                .map(PolicyPostImage::getUrl)
                .toList();
        dto.imageUrls = urls;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public PostCategory getCategory() {
        return category;
    }

    public void setCategory(PostCategory category) {
        this.category = category;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getOpenchatUrl() {
        return openchatUrl;
    }

    public void setOpenchatUrl(String openchatUrl) {
        this.openchatUrl = openchatUrl;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public Boolean getLikedByMe() {
        return likedByMe;
    }

    public void setLikedByMe(Boolean likedByMe) {
        this.likedByMe = likedByMe;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public List<CommentResponse> getComments() {
        return comments;
    }

    public void setComments(List<CommentResponse> comments) {
        this.comments = comments;
    }

}
