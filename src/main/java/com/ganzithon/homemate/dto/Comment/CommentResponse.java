package com.ganzithon.homemate.dto.Comment;

import com.ganzithon.homemate.entity.Comment;
import com.ganzithon.homemate.entity.User;

import java.time.Instant;

public class CommentResponse {

    private Long id;
    private Long userId;
    private String writerLoginId;            // ✅ 추가
    private String writerProfileImagePath;   // ✅ 추가
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    public CommentResponse() {
    }

    // 정적 팩토리
    public static CommentResponse from(Comment comment, User writer) {
        CommentResponse dto = new CommentResponse();
        dto.id = comment.getId();
        dto.userId = comment.getUserId();
        dto.content = comment.getContent();
        dto.createdAt = comment.getCreatedAt();
        dto.updatedAt = comment.getUpdatedAt();

        dto.writerLoginId = writer != null ? writer.getLoginId() : null;
        dto.writerProfileImagePath = writer != null ? writer.getProfileImagePath() : null;

        return dto;
    }

    // ===== getter =====

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getWriterLoginId() { return writerLoginId; }
    public String getWriterProfileImagePath() { return writerProfileImagePath; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
