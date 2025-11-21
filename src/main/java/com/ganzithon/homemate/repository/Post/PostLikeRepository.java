package com.ganzithon.homemate.repository.Post;

import com.ganzithon.homemate.dto.Post.PostCategory;
import com.ganzithon.homemate.entity.Post.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByCategoryAndPostIdAndUserId(PostCategory category, Long postId, Long userId);

    Optional<PostLike> findByCategoryAndPostIdAndUserId(PostCategory category, Long postId, Long userId);

    long countByCategoryAndPostId(PostCategory category, Long postId);

    List<PostLike> findByCategoryAndPostId(PostCategory category, Long postId);
}