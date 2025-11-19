package com.ganzithon.homemate.entity;

import com.ganzithon.homemate.dto.PostCategory;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Entity
@Table(name = "comment")
public class Comment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PostCategory category;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected Comment() {}

    private Comment(PostCategory category, Long postId, Long userId, String content) {
        this.category = category;
        this.postId = postId;
        this.userId = userId;
        this.content = content;
    }

    public static Comment create(PostCategory category, Long postId, Long userId, String content) {
        return new Comment(category, postId, userId, content);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void moveTo(PostCategory category, Long postId) {
        this.category = category;
        this.postId = postId;
    }
}
