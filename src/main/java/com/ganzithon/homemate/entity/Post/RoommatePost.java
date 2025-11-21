package com.ganzithon.homemate.entity.Post;

import com.ganzithon.homemate.dto.Post.CreatePostRequest;
import com.ganzithon.homemate.dto.Post.UpdatePostRequest;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "roommate_post")
public class RoommatePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 작성자 (User.id)

    @Column(length = 100, nullable = false)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(length = 10, nullable = false)
    private String sidoCode;

    @Column(length = 10, nullable = false)
    private String sigunguCode;

    @Column(nullable = false)
    private Long viewCount = 0L;

    @Column(length = 255, nullable = false)
    private String openchatUrl;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoommatePostImage> images = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected RoommatePost() {
    }

    private RoommatePost(Long userId, String title, String content,
                         String sidoCode, String sigunguCode, String openchatUrl) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.sidoCode = sidoCode;
        this.sigunguCode = sigunguCode;
        this.openchatUrl = openchatUrl;
    }

    public static RoommatePost create(Long userId, CreatePostRequest req) {
        return new RoommatePost(
                userId,
                req.getTitle(),
                req.getContent(),
                req.getSidoCode(),
                req.getSigunguCode(),
                req.getOpenchatUrl()
        );
    }


    public void updateAll(UpdatePostRequest req) {
        this.title = req.getTitle();
        this.content = req.getContent();
        this.sidoCode = req.getSidoCode();
        this.sigunguCode = req.getSigunguCode();
        this.openchatUrl = req.getOpenchatUrl();
    }

    // ===== Getter & 기타 메서드 =====

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getSidoCode() {
        return sidoCode;
    }

    public String getSigunguCode() {
        return sigunguCode;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<RoommatePostImage> getImages() {
        return images;
    }

    public String getOpenchatUrl() {
        return openchatUrl;
    }

    public void increaseViewCount() {
        this.viewCount = this.viewCount + 1;
    }

    public boolean isOwner(Long targetUserId) {
        return this.userId != null && this.userId.equals(targetUserId);
    }
}
