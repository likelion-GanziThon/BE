package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.Comment.CommentResponse;
import com.ganzithon.homemate.dto.Comment.CreateCommentRequest;
import com.ganzithon.homemate.dto.Post.PostCategory;
import com.ganzithon.homemate.dto.Comment.UpdateCommentRequest;
import com.ganzithon.homemate.entity.Comment;
import com.ganzithon.homemate.entity.User;
import com.ganzithon.homemate.repository.CommentRepository;
import com.ganzithon.homemate.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
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

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(PostCategory category, Long postId) {
        List<Comment> comments = commentRepository
                .findByCategoryAndPostIdOrderByCreatedAtAsc(category, postId);

        // 작성자 ID 모아서 한 번에 조회
        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return comments.stream()
                .map(c -> {
                    User writer = userMap.get(c.getUserId());
                    return CommentResponse.from(c, writer);
                })
                .toList();
    }

    public long getCommentCount(PostCategory category, Long postId) {
        return commentRepository.countByCategoryAndPostId(category, postId);
    }

    // 게시판 이동 시 댓글 모두 이동
    @Transactional
    public void moveAll(PostCategory fromCategory,
                        Long fromPostId,
                        PostCategory toCategory,
                        Long toPostId) {

        var comments = commentRepository.findByCategoryAndPostId(fromCategory, fromPostId);
        for (Comment comment : comments) {
            comment.moveTo(toCategory, toPostId);
        }
    }
}
