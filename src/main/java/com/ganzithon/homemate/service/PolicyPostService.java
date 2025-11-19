package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.CreatePostRequest;
import com.ganzithon.homemate.dto.UpdatePostRequest;
import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.dto.PostListItemResponse;
import com.ganzithon.homemate.dto.PostDetailResponse;
import com.ganzithon.homemate.dto.SearchType;


import com.ganzithon.homemate.entity.PolicyPost;
import com.ganzithon.homemate.entity.PolicyPostImage;
import com.ganzithon.homemate.entity.FreePost;
import com.ganzithon.homemate.entity.FreePostImage;
import com.ganzithon.homemate.entity.RoommatePost;
import com.ganzithon.homemate.entity.RoommatePostImage;

import com.ganzithon.homemate.repository.PolicyPostRepository;
import com.ganzithon.homemate.repository.PolicyPostImageRepository;
import com.ganzithon.homemate.repository.FreePostRepository;
import com.ganzithon.homemate.repository.FreePostImageRepository;
import com.ganzithon.homemate.repository.RoommatePostRepository;
import com.ganzithon.homemate.repository.RoommatePostImageRepository;

import com.ganzithon.homemate.service.storage.ImageStorage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class PolicyPostService {

    private final PolicyPostRepository policyPostRepository;
    private final PolicyPostImageRepository policyPostImageRepository;
    private final ImageStorage imageStorage;
    private final CommentService commentService;
    private final PostLikeService postLikeService;

    // 이동용(다른 게시판)
    private final FreePostRepository freePostRepository;
    private final FreePostImageRepository freePostImageRepository;
    private final RoommatePostRepository roommatePostRepository;
    private final RoommatePostImageRepository roommatePostImageRepository;

    public PolicyPostService(
            PolicyPostRepository policyPostRepository,
            PolicyPostImageRepository policyPostImageRepository,
            ImageStorage imageStorage,
            CommentService commentService,
            PostLikeService postLikeService,
            FreePostRepository freePostRepository,
            FreePostImageRepository freePostImageRepository,
            RoommatePostRepository roommatePostRepository,
            RoommatePostImageRepository roommatePostImageRepository
    ) {
        this.policyPostRepository = policyPostRepository;
        this.policyPostImageRepository = policyPostImageRepository;
        this.imageStorage = imageStorage;
        this.commentService = commentService;
        this.postLikeService = postLikeService;
        this.freePostRepository = freePostRepository;
        this.freePostImageRepository = freePostImageRepository;
        this.roommatePostRepository = roommatePostRepository;
        this.roommatePostImageRepository = roommatePostImageRepository;
    }

    // =======================
    // CREATE (POLICY 전용)
    // - 제목/내용만 필수
    // - 지역은 선택 (null 허용)
    // =======================
    @Transactional
    public void create(Long userId, CreatePostRequest req, List<MultipartFile> images) {
        // POLICY 안에서는 지역 필수 아님
        validateTextFieldsForCreate(req.getTitle(), req.getContent());

        PolicyPost post = PolicyPost.create(userId, req);
        policyPostRepository.save(post); // ★ 먼저 저장

        if (images != null && !images.isEmpty()) {
            int order = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadPolicyImage(file);
                PolicyPostImage image = PolicyPostImage.of(post, url, order++);
                policyPostImageRepository.save(image);
            }
        }
    }

    // =======================
    // UPDATE (POLICY 내에서만)
    // - 제목/내용만 필수
    // - 지역은 여전히 선택
    // =======================
    @Transactional
    public void update(Long userId, Long postId, UpdatePostRequest req, List<MultipartFile> images) {
        PolicyPost post = policyPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        // POLICY 안에서 수정 → 제목/내용만 필수
        validateTextFieldsForUpdateWithinPolicy(req);

        post.updateAll(req); // sidoCode/sigunguCode는 null 가능

        if (images != null) {
            List<PolicyPostImage> oldImages = policyPostImageRepository.findByPost(post);

            for (PolicyPostImage img : oldImages) {
                String url = img.getUrl();
                if (StringUtils.hasText(url)) {
                    imageStorage.deletePolicyImage(url);
                }
            }
            policyPostImageRepository.deleteAllInBatch(oldImages);

            if (!images.isEmpty()) {
                int order = 0;
                for (MultipartFile file : images) {
                    if (file == null || file.isEmpty()) continue;

                    String url = imageStorage.uploadPolicyImage(post.getId(), order, file);
                    PolicyPostImage image = PolicyPostImage.of(post, url, order++);
                    policyPostImageRepository.save(image);
                }
            }
        }
    }

    // =======================
    // UPDATE + CATEGORY MOVE
    // (POLICY → FREE / ROOMMATE)
    // - 여기서는 타겟 게시판 요구사항을 모두 만족해야 함
    //   · FREE: 제목/내용/지역 필수
    //   · ROOMMATE: 제목/내용/지역 + openchatUrl 필수
    //   + 좋아요/댓글 이동
    // =======================
    @Transactional
    public void moveToAnotherCategory(
            Long userId,
            Long postId,
            UpdatePostRequest req,
            List<MultipartFile> images,
            PostCategory targetCategory
    ) {
        PolicyPost post = policyPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        // 카테고리 그대로면 그냥 POLICY 안에서 update
        if (targetCategory == null || targetCategory == PostCategory.POLICY) {
            update(userId, postId, req, images);
            return;
        }

        // 여기부터는 FREE / ROOMMATE 같은 "다른 게시판"으로 이동
        validateTextFieldsForMoveToOtherBoard(req, targetCategory);

        // 1) 기존 POLICY 이미지 파일 삭제 (DB row는 cascade 로 정리)
        List<PolicyPostImage> oldImages = policyPostImageRepository.findByPost(post);
        for (PolicyPostImage img : oldImages) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deletePolicyImage(url);
            }
        }
        // policyPostImageRepository.deleteAllInBatch(oldImages); // 게시글 삭제 시 cascade 처리 가능

        // 2) 타겟 게시판용 CreatePostRequest 생성
        CreatePostRequest createReq = toCreatePostRequest(req, targetCategory);

        // 3) 타겟 게시판에 새 글 + 새 이미지 생성
        Long newPostId;
        switch (targetCategory) {
            case FREE -> {
                FreePost newPost = movePolicyToFree(userId, createReq, images);
                newPostId = newPost.getId();
            }
            case ROOMMATE -> {
                RoommatePost newPost = movePolicyToRoommate(userId, createReq, images);
                newPostId = newPost.getId();
            }
            default -> throw new IllegalArgumentException("지원하지 않는 카테고리입니다: " + targetCategory);
        }

        // 4) 댓글/좋아요 이동
        commentService.moveAll(PostCategory.POLICY, postId, targetCategory, newPostId);
        postLikeService.moveAll(PostCategory.POLICY, postId, targetCategory, newPostId);

        // 5) 원본 POLICY 글 삭제 (이미지는 cascade)
        policyPostRepository.delete(post);
    }

    // =======================
    // 텍스트 필드 검증 메서드들
    // =======================

    // POLICY 생성 시: 제목/내용만 필수
    private void validateTextFieldsForCreate(String title, String content) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("내용은 필수입니다.");
        }
        // 지역은 POLICY에서 필수가 아님
    }

    // POLICY 안에서 수정: 제목/내용만 필수
    private void validateTextFieldsForUpdateWithinPolicy(UpdatePostRequest req) {
        if (!StringUtils.hasText(req.getTitle())) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        if (!StringUtils.hasText(req.getContent())) {
            throw new IllegalArgumentException("내용은 필수입니다.");
        }
        // 지역은 여전히 선택 값 (null/빈값 허용)
    }

    // 다른 게시판(FREE/ROOMMATE)으로 이동할 때:
    // 그 게시판의 요구사항을 강제
    private void validateTextFieldsForMoveToOtherBoard(UpdatePostRequest req, PostCategory targetCategory) {
        if (!StringUtils.hasText(req.getTitle())) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        if (!StringUtils.hasText(req.getContent())) {
            throw new IllegalArgumentException("내용은 필수입니다.");
        }
        if (!StringUtils.hasText(req.getSidoCode()) || !StringUtils.hasText(req.getSigunguCode())) {
            throw new IllegalArgumentException("지역 정보는 필수입니다.");
        }

        if (targetCategory == PostCategory.ROOMMATE &&
                !StringUtils.hasText(req.getOpenchatUrl())) {
            throw new IllegalArgumentException("ROOMMATE 게시판으로 이동할 때는 openchatUrl이 필수입니다.");
        }
    }

    // UpdatePostRequest → CreatePostRequest 변환
    // (실제 필수 검증은 위 메서드에서 이미 끝내놓고, 여기선 값만 옮김)
    private CreatePostRequest toCreatePostRequest(UpdatePostRequest req, PostCategory targetCategory) {
        CreatePostRequest createReq = new CreatePostRequest();
        createReq.setCategory(targetCategory);
        createReq.setTitle(req.getTitle());
        createReq.setContent(req.getContent());
        createReq.setSidoCode(req.getSidoCode());
        createReq.setSigunguCode(req.getSigunguCode());
        createReq.setOpenchatUrl(req.getOpenchatUrl());
        return createReq;
    }

    // POLICY → FREE
    private FreePost movePolicyToFree(
            Long userId,
            CreatePostRequest createReq,
            List<MultipartFile> images
    ) {
        FreePost freePost = FreePost.create(userId, createReq);
        freePostRepository.save(freePost); // ★ 먼저 저장

        if (images != null && !images.isEmpty()) {
            int order = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadFreeImage(freePost.getId(), order, file);
                FreePostImage image = FreePostImage.of(freePost, url, order++);
                freePostImageRepository.save(image);
            }
        }
        return freePost;
    }

    // POLICY → ROOMMATE
    private RoommatePost movePolicyToRoommate(
            Long userId,
            CreatePostRequest createReq,
            List<MultipartFile> images
    ) {
        // 여기 올 때는 이미 validateTextFieldsForMoveToOtherBoard 에서
        // openchatUrl / 지역 검증 끝난 상태
        RoommatePost roommatePost = RoommatePost.create(userId, createReq);
        roommatePostRepository.save(roommatePost); // ★ 먼저 저장

        if (images != null && !images.isEmpty()) {
            int order = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadRoommateImage(roommatePost.getId(), order, file);
                RoommatePostImage image = RoommatePostImage.of(roommatePost, url, order++);
                roommatePostImageRepository.save(image);
            }
        }
        return roommatePost;
    }

    // =======================
    // DELETE
    // =======================
    @Transactional
    public void delete(Long userId, Long postId) {
        PolicyPost post = policyPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 삭제할 수 있습니다.");
        }

        List<PolicyPostImage> images = policyPostImageRepository.findByPost(post);

        for (PolicyPostImage img : images) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deletePolicyImage(url);
            }
        }

        policyPostRepository.delete(post); // cascade 로 image row 삭제
    }

    // =======================
    // LIST
    // =======================
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> getList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PolicyPost> posts =
                policyPostRepository.findAllByOrderByCreatedAtDesc(pageable);

        return posts.map(post -> {
            PostListItemResponse dto = PostListItemResponse.fromPolicy(post);

            long commentCount = commentService.getCommentCount(
                    PostCategory.POLICY,
                    post.getId()
            );
            dto.setCommentCount(commentCount);

            return dto;
        });
    }

    // =======================
    // SEARCH LIST (검색만, 지역 필터 없음)
    // =======================
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> searchList(
            int page,
            int size,
            SearchType searchType,
            String keyword
    ) {
        Pageable pageable = PageRequest.of(page, size);

        Page<PolicyPost> posts;

        switch (searchType) {
            case TITLE ->
                    posts = policyPostRepository
                            .findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword, pageable);
            case CONTENT ->
                    posts = policyPostRepository
                            .findByContentContainingOrderByCreatedAtDesc(keyword, pageable);
            default ->
                    throw new IllegalArgumentException("지원하지 않는 검색 타입입니다: " + searchType);
        }

        return posts.map(post -> {
            PostListItemResponse dto = PostListItemResponse.fromPolicy(post);

            long commentCount = commentService.getCommentCount(
                    PostCategory.POLICY,
                    post.getId()
            );
            dto.setCommentCount(commentCount);

            return dto;
        });
    }


    // =======================
    // DETAIL (+ 조회수 증가)
    // =======================
    @Transactional
    public PostDetailResponse getDetailAndIncreaseView(Long postId) {
        PolicyPost post = policyPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        post.increaseViewCount();

        return PostDetailResponse.fromPolicy(post);
    }
}
