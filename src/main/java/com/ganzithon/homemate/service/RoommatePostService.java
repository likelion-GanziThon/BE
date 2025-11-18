package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.CreatePostRequest;
import com.ganzithon.homemate.dto.UpdatePostRequest;
import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.dto.PostListItemResponse;
import com.ganzithon.homemate.dto.PostDetailResponse;

import com.ganzithon.homemate.entity.RoommatePost;
import com.ganzithon.homemate.entity.RoommatePostImage;
import com.ganzithon.homemate.entity.FreePost;
import com.ganzithon.homemate.entity.FreePostImage;
import com.ganzithon.homemate.entity.PolicyPost;
import com.ganzithon.homemate.entity.PolicyPostImage;

import com.ganzithon.homemate.repository.RoommatePostRepository;
import com.ganzithon.homemate.repository.RoommatePostImageRepository;
import com.ganzithon.homemate.repository.FreePostRepository;
import com.ganzithon.homemate.repository.FreePostImageRepository;
import com.ganzithon.homemate.repository.PolicyPostRepository;
import com.ganzithon.homemate.repository.PolicyPostImageRepository;

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
public class RoommatePostService {

    private final RoommatePostRepository roommatePostRepository;
    private final RoommatePostImageRepository roommatePostImageRepository;
    private final ImageStorage imageStorage;
    private final CommentService commentService;

    // 이동용(다른 게시판)
    private final FreePostRepository freePostRepository;
    private final FreePostImageRepository freePostImageRepository;
    private final PolicyPostRepository policyPostRepository;
    private final PolicyPostImageRepository policyPostImageRepository;

    public RoommatePostService(
            RoommatePostRepository roommatePostRepository,
            RoommatePostImageRepository roommatePostImageRepository,
            ImageStorage imageStorage,
            CommentService commentService,
            FreePostRepository freePostRepository,
            FreePostImageRepository freePostImageRepository,
            PolicyPostRepository policyPostRepository,
            PolicyPostImageRepository policyPostImageRepository
    ) {
        this.roommatePostRepository = roommatePostRepository;
        this.roommatePostImageRepository = roommatePostImageRepository;
        this.imageStorage = imageStorage;
        this.commentService = commentService;
        this.freePostRepository = freePostRepository;
        this.freePostImageRepository = freePostImageRepository;
        this.policyPostRepository = policyPostRepository;
        this.policyPostImageRepository = policyPostImageRepository;
    }

    // =======================
    // CREATE
    // =======================
    @Transactional
    public void create(Long userId, CreatePostRequest req, List<MultipartFile> images) {

        // ROOMMATE 전용 필수 필드 체크
        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());
        if (!StringUtils.hasText(req.getOpenchatUrl())) {
            throw new IllegalArgumentException("ROOMMATE 게시판은 openchatUrl이 필수입니다.");
        }

        RoommatePost post = RoommatePost.create(userId, req);
        roommatePostRepository.save(post);

        if (images != null) {
            int order = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadRoommateImage(file);
                RoommatePostImage image = RoommatePostImage.of(post, url, order++);
                roommatePostImageRepository.save(image);
            }
        }
    }

    // =======================
    // UPDATE (ROOMMATE 내에서만)
    // =======================
    @Transactional
    public void update(Long userId, Long postId, UpdatePostRequest req, List<MultipartFile> images) {
        RoommatePost post = roommatePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());
        if (!StringUtils.hasText(req.getOpenchatUrl())) {
            throw new IllegalArgumentException("ROOMMATE 게시판은 openchatUrl이 필수입니다.");
        }

        post.updateAll(req);

        // 이미지 전체 교체
        if (images != null) {
            List<RoommatePostImage> oldImages = roommatePostImageRepository.findByPost(post);

            // 스토리지에서 삭제
            for (RoommatePostImage img : oldImages) {
                String url = img.getUrl();
                if (StringUtils.hasText(url)) {
                    imageStorage.deleteRoommateImage(url);
                }
            }
            roommatePostImageRepository.deleteAllInBatch(oldImages);

            if (!images.isEmpty()) {
                int order = 0;
                for (MultipartFile file : images) {
                    if (file == null || file.isEmpty()) continue;

                    String url = imageStorage.uploadRoommateImage(post.getId(), order, file);
                    RoommatePostImage image = RoommatePostImage.of(post, url, order++);
                    roommatePostImageRepository.save(image);
                }
            }
        }
    }

    // =======================
    // UPDATE + CATEGORY MOVE
    // (ROOMMATE → FREE / POLICY)
    // =======================
    @Transactional
    public void moveToAnotherCategory(
            Long userId,
            Long postId,
            UpdatePostRequest req,
            List<MultipartFile> images,
            PostCategory targetCategory
    ) {
        RoommatePost post = roommatePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        if (targetCategory == null || targetCategory == PostCategory.ROOMMATE) {
            // 그대로면 그냥 update
            update(userId, postId, req, images);
            return;
        }

        // 텍스트는 항상 검증
        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());

        // 기존 이미지들
        List<RoommatePostImage> oldImages = roommatePostImageRepository.findByPost(post);

        switch (targetCategory) {
            case FREE   -> moveRoommateToFree(userId, post, req, images, oldImages);
            case POLICY -> moveRoommateToPolicy(userId, post, req, images, oldImages);
            default     -> throw new IllegalArgumentException("지원하지 않는 카테고리입니다: " + targetCategory);
        }

        // 원본 삭제
        deleteRoommatePostWithImages(post, oldImages);
    }

    private void validateTextFields(String title, String content, String sidoCode, String sigunguCode) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("내용은 필수입니다.");
        }
        if (!StringUtils.hasText(sidoCode) || !StringUtils.hasText(sigunguCode)) {
            throw new IllegalArgumentException("지역 정보는 필수입니다.");
        }
    }

    private CreatePostRequest toCreatePostRequest(UpdatePostRequest req, PostCategory targetCategory) {
        // ROOMMATE로 이동할 땐 openchatUrl 필수
        if (targetCategory == PostCategory.ROOMMATE &&
                !StringUtils.hasText(req.getOpenchatUrl())) {
            throw new IllegalArgumentException("ROOMMATE 게시판으로 이동할 때는 openchatUrl이 필수입니다.");
        }

        CreatePostRequest createReq = new CreatePostRequest();
        createReq.setCategory(targetCategory);
        createReq.setTitle(req.getTitle());
        createReq.setContent(req.getContent());
        createReq.setSidoCode(req.getSidoCode());
        createReq.setSigunguCode(req.getSigunguCode());
        createReq.setOpenchatUrl(req.getOpenchatUrl());
        return createReq;
    }

    // ROOMMATE → FREE
    private void moveRoommateToFree(
            Long userId,
            RoommatePost original,
            UpdatePostRequest req,
            List<MultipartFile> newImages,
            List<RoommatePostImage> oldImages
    ) {
        CreatePostRequest createReq = toCreatePostRequest(req, PostCategory.FREE);

        FreePost freePost = FreePost.create(userId, createReq);
        freePostRepository.save(freePost);

        if (newImages != null && !newImages.isEmpty()) {
            int order = 0;
            for (MultipartFile file : newImages) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadFreeImage(file);
                FreePostImage image = FreePostImage.of(freePost, url, order++);
                freePostImageRepository.save(image);
            }
        } else {
            int order = 0;
            for (RoommatePostImage oldImg : oldImages) {
                String url = oldImg.getUrl();
                if (!StringUtils.hasText(url)) continue;

                FreePostImage copy = FreePostImage.of(freePost, url, order++);
                freePostImageRepository.save(copy);
            }
        }
    }

    // ROOMMATE → POLICY
    private void moveRoommateToPolicy(
            Long userId,
            RoommatePost original,
            UpdatePostRequest req,
            List<MultipartFile> newImages,
            List<RoommatePostImage> oldImages
    ) {
        CreatePostRequest createReq = toCreatePostRequest(req, PostCategory.POLICY);

        PolicyPost policyPost = PolicyPost.create(userId, createReq);
        policyPostRepository.save(policyPost);

        if (newImages != null && !newImages.isEmpty()) {
            int order = 0;
            for (MultipartFile file : newImages) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadFreeImage(file);
                PolicyPostImage image = PolicyPostImage.of(policyPost, url, order++);
                policyPostImageRepository.save(image);
            }
        } else {
            int order = 0;
            for (RoommatePostImage oldImg : oldImages) {
                String url = oldImg.getUrl();
                if (!StringUtils.hasText(url)) continue;

                PolicyPostImage copy = PolicyPostImage.of(policyPost, url, order++);
                policyPostImageRepository.save(copy);
            }
        }
    }

    private void deleteRoommatePostWithImages(RoommatePost post, List<RoommatePostImage> images) {
        for (RoommatePostImage img : images) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deleteFreeImage(url);
            }
        }
        roommatePostImageRepository.deleteAllInBatch(images);
        roommatePostRepository.delete(post);
    }

    // =======================
    // DELETE
    // =======================
    @Transactional
    public void delete(Long userId, Long postId) {
        RoommatePost post = roommatePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 삭제할 수 있습니다.");
        }

        List<RoommatePostImage> images = roommatePostImageRepository.findByPost(post);

        for (RoommatePostImage img : images) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deleteRoommateImage(url);
            }
        }

        roommatePostRepository.delete(post);
    }

    // =======================
    // LIST
    // =======================
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> getList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<RoommatePost> posts =
                roommatePostRepository.findAllByOrderByCreatedAtDesc(pageable);

        return posts.map(post -> {
            PostListItemResponse dto = PostListItemResponse.fromRoommate(post);

            long commentCount = commentService.getCommentCount(
                    PostCategory.ROOMMATE,
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
        RoommatePost post = roommatePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        post.increaseViewCount();

        return PostDetailResponse.fromRoommate(post);
    }
}