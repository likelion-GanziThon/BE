package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.CreatePostRequest;
import com.ganzithon.homemate.dto.UpdatePostRequest;
import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.dto.PostListItemResponse;
import com.ganzithon.homemate.dto.PostDetailResponse;

import com.ganzithon.homemate.entity.FreePost;
import com.ganzithon.homemate.entity.FreePostImage;
import com.ganzithon.homemate.entity.RoommatePost;
import com.ganzithon.homemate.entity.RoommatePostImage;
import com.ganzithon.homemate.entity.PolicyPost;
import com.ganzithon.homemate.entity.PolicyPostImage;

import com.ganzithon.homemate.repository.FreePostRepository;
import com.ganzithon.homemate.repository.FreePostImageRepository;
import com.ganzithon.homemate.repository.RoommatePostRepository;
import com.ganzithon.homemate.repository.RoommatePostImageRepository;
import com.ganzithon.homemate.repository.PolicyPostRepository;
import com.ganzithon.homemate.repository.PolicyPostImageRepository;

import com.ganzithon.homemate.service.storage.ImageStorage;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Service
public class FreePostService {

    private final FreePostRepository freePostRepository;
    private final FreePostImageRepository freePostImageRepository;
    private final ImageStorage imageStorage;
    private final CommentService commentService;

    // 다른 게시판들
    private final RoommatePostRepository roommatePostRepository;
    private final RoommatePostImageRepository roommatePostImageRepository;
    private final PolicyPostRepository policyPostRepository;
    private final PolicyPostImageRepository policyPostImageRepository;

    public FreePostService(
            FreePostRepository freePostRepository,
            FreePostImageRepository freePostImageRepository,
            ImageStorage imageStorage,
            CommentService commentService,
            RoommatePostRepository roommatePostRepository,
            RoommatePostImageRepository roommatePostImageRepository,
            PolicyPostRepository policyPostRepository,
            PolicyPostImageRepository policyPostImageRepository
    ) {
        this.freePostRepository = freePostRepository;
        this.freePostImageRepository = freePostImageRepository;
        this.imageStorage = imageStorage;
        this.commentService = commentService;
        this.roommatePostRepository = roommatePostRepository;
        this.roommatePostImageRepository = roommatePostImageRepository;
        this.policyPostRepository = policyPostRepository;
        this.policyPostImageRepository = policyPostImageRepository;
    }

    // ========================================
    // CREATE
    // ========================================
    @Transactional
    public void create(Long userId, CreatePostRequest req, List<MultipartFile> images) {

        FreePost post = FreePost.create(userId, req);
        freePostRepository.save(post);

        if (images != null) {
            int order = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadFreeImage(file);
                FreePostImage image = FreePostImage.of(post, url, order++);
                freePostImageRepository.save(image);
            }
        }
    }

    // ========================================
    // UPDATE (FREE 안에서만)
    // ========================================
    @Transactional
    public void update(Long userId, Long postId, UpdatePostRequest req, List<MultipartFile> images) {
        FreePost post = freePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        // 텍스트 검증 (openchatUrl 없음)
        validateTextFields(req);

        // 텍스트 전체 덮어쓰기
        post.updateAll(req);

        // 이미지 전체 교체
        if (images != null) {
            List<FreePostImage> oldImages = freePostImageRepository.findByPost(post);

            // 기존 파일 삭제
            for (FreePostImage img : oldImages) {
                String url = img.getUrl();
                if (StringUtils.hasText(url)) {
                    imageStorage.deleteFreeImage(url);
                }
            }
            freePostImageRepository.deleteAllInBatch(oldImages);

            // 새 이미지 저장
            if (!images.isEmpty()) {
                int order = 0;
                for (MultipartFile file : images) {
                    if (file == null || file.isEmpty()) continue;

                    String url = imageStorage.uploadFreeImage(post.getId(), order, file);
                    FreePostImage image = FreePostImage.of(post, url, order++);
                    freePostImageRepository.save(image);
                }
            }
        }
    }

    // ========================================
    // UPDATE + CATEGORY MOVE (FREE → 다른 게시판)
    // ========================================
    @Transactional
    public void moveToAnotherCategory(
            Long userId,
            Long postId,
            UpdatePostRequest req,
            List<MultipartFile> images,
            PostCategory targetCategory
    ) {
        FreePost post = freePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        if (targetCategory == null || targetCategory == PostCategory.FREE) {
            // 목표 카테고리가 FREE이면 그냥 기존 update 로직 사용
            update(userId, postId, req, images);
            return;
        }

        // 텍스트 검증
        validateTextFields(req);

        // 기존 FREE 이미지 목록 (나중에 복사에 사용할 수 있음)
        List<FreePostImage> oldImages = freePostImageRepository.findByPost(post);

        switch (targetCategory) {
            case ROOMMATE -> moveFreeToRoommate(userId, post, req, images, oldImages);
            case POLICY   -> moveFreeToPolicy(userId, post, req, images, oldImages);
            default       -> throw new IllegalArgumentException("지원하지 않는 카테고리입니다: " + targetCategory);
        }

        // FREE 게시글 삭제 (cascade 로 이미지 row도 같이 삭제됨)
        // 여기서는 스토리지 파일은 지우지 않는다. (새 글에서 같은 URL을 쓸 수 있으니까)
        freePostRepository.delete(post);
    }

    private void validateTextFields(UpdatePostRequest req) {
        if (!StringUtils.hasText(req.getTitle())) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        if (!StringUtils.hasText(req.getContent())) {
            throw new IllegalArgumentException("내용은 필수입니다.");
        }
        if (!StringUtils.hasText(req.getSidoCode()) || !StringUtils.hasText(req.getSigunguCode())) {
            throw new IllegalArgumentException("지역 정보는 필수입니다.");
        }
    }

    // UpdatePostRequest → CreatePostRequest 변환
    private CreatePostRequest toCreatePostRequest(UpdatePostRequest req, PostCategory targetCategory) {
        // ROOMMATE로 갈 땐 openchatUrl 필수
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
        createReq.setOpenchatUrl(req.getOpenchatUrl()); // ROOMMATE 전용
        return createReq;
    }


    // FREE → ROOMMATE 이동
    private void moveFreeToRoommate(
            Long userId,
            FreePost original,
            UpdatePostRequest req,
            List<MultipartFile> newImages,
            List<FreePostImage> oldImages
    ) {
        CreatePostRequest createReq = toCreatePostRequest(req, PostCategory.ROOMMATE);

        RoommatePost roommatePost = RoommatePost.create(userId, createReq);
        roommatePostRepository.save(roommatePost);

        // 이미지
        if (newImages != null && !newImages.isEmpty()) {
            int order = 0;
            for (MultipartFile file : newImages) {
                if (file == null || file.isEmpty()) continue;

                // 지금은 일단 FREE와 같은 저장 메서드 재사용
                String url = imageStorage.uploadFreeImage(file);
                RoommatePostImage image = RoommatePostImage.of(roommatePost, url, order++);
                roommatePostImageRepository.save(image);
            }
        } else {
            // 새 이미지가 없으면 기존 FREE 이미지 URL 복사
            int order = 0;
            for (FreePostImage oldImg : oldImages) {
                String url = oldImg.getUrl();
                if (!StringUtils.hasText(url)) continue;

                RoommatePostImage copy = RoommatePostImage.of(roommatePost, url, order++);
                roommatePostImageRepository.save(copy);
            }
        }
    }

    // FREE → POLICY 이동
    private void moveFreeToPolicy(
            Long userId,
            FreePost original,
            UpdatePostRequest req,
            List<MultipartFile> newImages,
            List<FreePostImage> oldImages
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
            for (FreePostImage oldImg : oldImages) {
                String url = oldImg.getUrl();
                if (!StringUtils.hasText(url)) continue;

                PolicyPostImage copy = PolicyPostImage.of(policyPost, url, order++);
                policyPostImageRepository.save(copy);
            }
        }
    }

    // ========================================
    // DELETE
    // ========================================
    @Transactional
    public void delete(Long userId, Long postId) {
        FreePost post = freePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 삭제할 수 있습니다.");
        }

        // 1) 이미지 목록 조회
        List<FreePostImage> images = freePostImageRepository.findByPost(post);

        // 2) 스토리지에서 파일 삭제
        for (FreePostImage img : images) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deleteFreeImage(url);
            }
        }

        // 3) 게시글 삭제 (이미지 row는 cascade로 같이 삭제)
        freePostRepository.delete(post);
    }

    // ========================================
    // LIST (최신순, 페이징)
    // ========================================
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> getList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FreePost> posts =
                freePostRepository.findAllByOrderByCreatedAtDesc(pageable);

        return posts.map(post -> {
            PostListItemResponse dto = PostListItemResponse.fromFree(post);

            long commentCount = commentService.getCommentCount(
                    PostCategory.FREE,
                    post.getId()
            );
            dto.setCommentCount(commentCount);

            return dto;
        });
    }

    // ========================================
    // DETAIL (+ 조회수 증가)
    // ========================================
    @Transactional
    public PostDetailResponse getDetailAndIncreaseView(Long postId) {
        FreePost post = freePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        post.increaseViewCount();

        return PostDetailResponse.fromFree(post);
    }
}
