package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByCategoryAndPostIdOrderByCreatedAtAsc(PostCategory category, Long postId);

    long countByCategoryAndPostId(PostCategory category, Long postId);

    Optional<Comment> findByIdAndUserId(Long id, Long userId);

    List<Comment> findByCategoryAndPostId(PostCategory category, Long postId);
}