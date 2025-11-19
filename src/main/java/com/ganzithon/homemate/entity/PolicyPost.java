package com.ganzithon.homemate.entity;


import com.ganzithon.homemate.dto.CreatePostRequest;
import com.ganzithon.homemate.dto.UpdatePostRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "policy_post")
public class PolicyPost {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false)
    private Long userId;


    @Column(length = 100, nullable = false)
    private String title;


    @Lob
    @Column(nullable = false)
    private String content;


    @Column(length = 10)
    private String sidoCode;


    @Column(length = 10)
    private String sigunguCode;


    @Column(nullable = false)
    private Long viewCount = 0L;


    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PolicyPostImage> images = new ArrayList<>();


    @CreationTimestamp
    private Instant createdAt;


    @UpdateTimestamp
    private Instant updatedAt;


    protected PolicyPost() {
    }


    private PolicyPost(Long userId, String title, String content,
                       String sidoCode, String sigunguCode) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.sidoCode = sidoCode;
        this.sigunguCode = sigunguCode;
    }


    public static PolicyPost create(Long userId, CreatePostRequest req) {
        return new PolicyPost(
                userId,
                req.getTitle(),
                req.getContent(),
                req.getSidoCode(),
                req.getSigunguCode()
        );
    }


    public void updateAll(UpdatePostRequest req) {
        this.title = req.getTitle();
        this.content = req.getContent();
        this.sidoCode = req.getSidoCode();
        this.sigunguCode = req.getSigunguCode();
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

    public List<PolicyPostImage> getImages() {
        return images;
    }

    // 조회수 올릴 때 쓸 예정이면 이렇게 하나 만들어 두면 좋고
    public void increaseViewCount() {
        this.viewCount = this.viewCount + 1;
    }


    public boolean isOwner(Long targetUserId) {
        return this.userId != null && this.userId.equals(targetUserId);
    }


    public Long getId() {
        return id;
    }
}