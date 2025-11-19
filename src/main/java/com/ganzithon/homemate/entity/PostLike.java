package com.ganzithon.homemate.entity;

import com.ganzithon.homemate.dto.PostCategory;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "post_like",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"category", "post_id", "user_id"})
        }
)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PostCategory category;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected PostLike() {}

    private PostLike(PostCategory category, Long postId, Long userId) {
        this.category = category;
        this.postId = postId;
        this.userId = userId;
    }

    public static PostLike create(PostCategory category, Long postId, Long userId) {
        return new PostLike(category, postId, userId);
    }

    public void moveTo(PostCategory category, Long postId) {
        this.category = category;
        this.postId = postId;
    }
}

