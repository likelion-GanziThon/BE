package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.CommentResponse;
import com.ganzithon.homemate.dto.CreateCommentRequest;
import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.dto.UpdateCommentRequest;
import com.ganzithon.homemate.entity.Comment;
import com.ganzithon.homemate.repository.CommentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Transactional
    public void create(Long userId, PostCategory category, Long postId, CreateCommentRequest req) {
        Comment comment = Comment.create(category, postId, userId, req.getContent());
        commentRepository.save(comment);
    }

    @Transactional
    public void update(Long userId, Long commentId, UpdateCommentRequest req) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));

        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인 댓글만 수정할 수 있습니다.");
        }

        comment.updateContent(req.getContent());
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));

        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인 댓글만 삭제할 수 있습니다.");
        }

        commentRepository.delete(comment);
    }

    public List<CommentResponse> getComments(PostCategory category, Long postId) {
        return commentRepository.findByCategoryAndPostIdOrderByCreatedAtAsc(category, postId)
                .stream()
                .map(c -> new CommentResponse(
                        c.getId(),
                        c.getUserId(),
                        c.getContent(),
                        c.getCreatedAt(),
                        c.getUpdatedAt()
                ))
                .toList();
    }

    public long getCommentCount(PostCategory category, Long postId) {
        return commentRepository.countByCategoryAndPostId(category, postId);
    }
}
