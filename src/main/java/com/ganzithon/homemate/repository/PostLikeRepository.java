package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByCategoryAndPostIdAndUserId(PostCategory category, Long postId, Long userId);

    Optional<PostLike> findByCategoryAndPostIdAndUserId(PostCategory category, Long postId, Long userId);

    long countByCategoryAndPostId(PostCategory category, Long postId);
}