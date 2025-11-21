package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.Post.PostCategory;
import com.ganzithon.homemate.entity.Post.PostLike;
import com.ganzithon.homemate.repository.Post.PostLikeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;

    public PostLikeService(PostLikeRepository postLikeRepository) {
        this.postLikeRepository = postLikeRepository;
    }

    @Transactional
    public void like(PostCategory category, Long postId, Long userId) {
        // 이미 눌렀으면 그냥 무시 (idempotent)
        if (postLikeRepository.existsByCategoryAndPostIdAndUserId(category, postId, userId)) {
            return;
        }
        postLikeRepository.save(PostLike.create(category, postId, userId));
    }

    @Transactional
    public void unlike(PostCategory category, Long postId, Long userId) {
        postLikeRepository.findByCategoryAndPostIdAndUserId(category, postId, userId)
                .ifPresent(postLikeRepository::delete);
    }

    public long getLikeCount(PostCategory category, Long postId) {
        return postLikeRepository.countByCategoryAndPostId(category, postId);
    }

    public boolean likedByUser(PostCategory category, Long postId, Long userId) {
        return postLikeRepository.existsByCategoryAndPostIdAndUserId(category, postId, userId);
    }

    // ★ 게시판 이동 시 좋아요 모두 이동
    @Transactional
    public void moveAll(PostCategory fromCategory,
                        Long fromPostId,
                        PostCategory toCategory,
                        Long toPostId) {

        var likes = postLikeRepository.findByCategoryAndPostId(fromCategory, fromPostId);
        for (PostLike like : likes) {
            like.moveTo(toCategory, toPostId); // 엔티티 수정 → JPA 더티체킹
        }
    }
}