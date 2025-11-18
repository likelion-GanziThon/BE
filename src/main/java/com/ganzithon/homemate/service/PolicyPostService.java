package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.CreatePostRequest;
import com.ganzithon.homemate.dto.UpdatePostRequest;
import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.dto.PostListItemResponse;
import com.ganzithon.homemate.dto.PostDetailResponse;

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
            FreePostRepository freePostRepository,
            FreePostImageRepository freePostImageRepository,
            RoommatePostRepository roommatePostRepository,
            RoommatePostImageRepository roommatePostImageRepository
    ) {
        this.policyPostRepository = policyPostRepository;
        this.policyPostImageRepository = policyPostImageRepository;
        this.imageStorage = imageStorage;
        this.commentService = commentService;
        this.freePostRepository = freePostRepository;
        this.freePostImageRepository = freePostImageRepository;
        this.roommatePostRepository = roommatePostRepository;
        this.roommatePostImageRepository = roommatePostImageRepository;
    }

    // =======================
    // CREATE
    // =======================
    @Transactional
    public void create(Long userId, CreatePostRequest req, List<MultipartFile> images) {
        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());

        PolicyPost post = PolicyPost.create(userId, req);
        policyPostRepository.save(post);

        if (images != null) {
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
    // =======================
    @Transactional
    public void update(Long userId, Long postId, UpdatePostRequest req, List<MultipartFile> images) {
        PolicyPost post = policyPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());

        post.updateAll(req);

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

        if (targetCategory == null || targetCategory == PostCategory.POLICY) {
            update(userId, postId, req, images);
            return;
        }

        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());

        List<PolicyPostImage> oldImages = policyPostImageRepository.findByPost(post);

        switch (targetCategory) {
            case FREE      -> movePolicyToFree(userId, post, req, images, oldImages);
            case ROOMMATE  -> movePolicyToRoommate(userId, post, req, images, oldImages);
            default        -> throw new IllegalArgumentException("지원하지 않는 카테고리입니다: " + targetCategory);
        }

        deletePolicyPostWithImages(post, oldImages);
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

    // POLICY → FREE
    private void movePolicyToFree(
            Long userId,
            PolicyPost original,
            UpdatePostRequest req,
            List<MultipartFile> newImages,
            List<PolicyPostImage> oldImages
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
            for (PolicyPostImage oldImg : oldImages) {
                String url = oldImg.getUrl();
                if (!StringUtils.hasText(url)) continue;

                FreePostImage copy = FreePostImage.of(freePost, url, order++);
                freePostImageRepository.save(copy);
            }
        }
    }

    // POLICY → ROOMMATE (여기서도 openchatUrl 필수)
    private void movePolicyToRoommate(
            Long userId,
            PolicyPost original,
            UpdatePostRequest req,
            List<MultipartFile> newImages,
            List<PolicyPostImage> oldImages
    ) {
        CreatePostRequest createReq = toCreatePostRequest(req, PostCategory.ROOMMATE);

        RoommatePost roommatePost = RoommatePost.create(userId, createReq);
        roommatePostRepository.save(roommatePost);

        if (newImages != null && !newImages.isEmpty()) {
            int order = 0;
            for (MultipartFile file : newImages) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadFreeImage(file);
                RoommatePostImage image = RoommatePostImage.of(roommatePost, url, order++);
                roommatePostImageRepository.save(image);
            }
        } else {
            int order = 0;
            for (PolicyPostImage oldImg : oldImages) {
                String url = oldImg.getUrl();
                if (!StringUtils.hasText(url)) continue;

                RoommatePostImage copy = RoommatePostImage.of(roommatePost, url, order++);
                roommatePostImageRepository.save(copy);
            }
        }
    }

    private void deletePolicyPostWithImages(PolicyPost post, List<PolicyPostImage> images) {
        for (PolicyPostImage img : images) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deleteFreeImage(url);
            }
        }
        policyPostImageRepository.deleteAllInBatch(images);
        policyPostRepository.delete(post);
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

        policyPostRepository.delete(post);
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
