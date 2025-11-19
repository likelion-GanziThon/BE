package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.CreatePostRequest;
import com.ganzithon.homemate.dto.UpdatePostRequest;
import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.dto.PostListItemResponse;
import com.ganzithon.homemate.dto.PostDetailResponse;
import com.ganzithon.homemate.dto.SearchType;


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
    private final PostLikeService postLikeService;

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
            PostLikeService postLikeService,
            FreePostRepository freePostRepository,
            FreePostImageRepository freePostImageRepository,
            PolicyPostRepository policyPostRepository,
            PolicyPostImageRepository policyPostImageRepository
    ) {
        this.roommatePostRepository = roommatePostRepository;
        this.roommatePostImageRepository = roommatePostImageRepository;
        this.imageStorage = imageStorage;
        this.commentService = commentService;
        this.postLikeService = postLikeService;
        this.freePostRepository = freePostRepository;
        this.freePostImageRepository = freePostImageRepository;
        this.policyPostRepository = policyPostRepository;
        this.policyPostImageRepository = policyPostImageRepository;
    }

    // =======================
    // CREATE
    // ROOMMATE: 제목/내용/지역 + openchatUrl 필수
    // =======================
    @Transactional
    public void create(Long userId, CreatePostRequest req, List<MultipartFile> images) {
        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());

        if (!StringUtils.hasText(req.getOpenchatUrl())) {
            throw new IllegalArgumentException("ROOMMATE 게시판은 openchatUrl이 필수입니다.");
        }

        RoommatePost post = RoommatePost.create(userId, req);
        roommatePostRepository.save(post); // ★ 먼저 저장

        if (images != null && !images.isEmpty()) {
            int order = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                // 단순 업로드 버전 사용
                String url = imageStorage.uploadRoommateImage(file);
                RoommatePostImage image = RoommatePostImage.of(post, url, order++);
                roommatePostImageRepository.save(image);
            }
        }
    }

    // =======================
    // UPDATE (ROOMMATE 내에서만)
    // 제목/내용/지역 + openchatUrl 필수
    // =======================
    @Transactional
    public void update(Long userId, Long postId, UpdatePostRequest req, List<MultipartFile> images) {
        RoommatePost post = roommatePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.isOwner(userId)) {
            throw new AccessDeniedException("본인 게시글만 수정할 수 있습니다.");
        }

        // ROOMMATE 안에서 수정: 지역 + openchatUrl 필수
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
    // 좋아요/댓글 동반 이동
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

        // 카테고리 그대로면 그냥 ROOMMATE 안에서 update
        if (targetCategory == null || targetCategory == PostCategory.ROOMMATE) {
            update(userId, postId, req, images);
            return;
        }

        // 다른 게시판으로 나갈 거라서 openchatUrl은 필수 X, 대신 공통 필드(제목/내용/지역)는 필수
        validateTextFields(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());

        // 1) 기존 ROOMMATE 이미지 파일 삭제
        List<RoommatePostImage> oldImages = roommatePostImageRepository.findByPost(post);
        for (RoommatePostImage img : oldImages) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deleteRoommateImage(url);
            }
        }
        // roommatePostImageRepository.deleteAllInBatch(oldImages); // 게시글 삭제 시 cascade 로 정리해도 됨

        // 2) 타겟 게시판용 CreatePostRequest 생성
        CreatePostRequest createReq = toCreatePostRequest(req, targetCategory);

        // 3) 타겟 게시판에 새 글 + 새 이미지 생성
        Long newPostId;
        switch (targetCategory) {
            case FREE -> {
                FreePost newPost = moveRoommateToFree(userId, createReq, images);
                newPostId = newPost.getId();
            }
            case POLICY -> {
                PolicyPost newPost = moveRoommateToPolicy(userId, createReq, images);
                newPostId = newPost.getId();
            }
            default -> throw new IllegalArgumentException("지원하지 않는 카테고리입니다: " + targetCategory);
        }

        // 4) 댓글/좋아요 이동
        commentService.moveAll(PostCategory.ROOMMATE, postId, targetCategory, newPostId);
        postLikeService.moveAll(PostCategory.ROOMMATE, postId, targetCategory, newPostId);

        // 5) 원본 ROOMMATE 글 삭제
        roommatePostRepository.delete(post);
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
        // 혹시 나중에 ROOMMATE → ROOMMATE 같은 이동 지원할 때 대비
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
        createReq.setOpenchatUrl(req.getOpenchatUrl()); // FREE/POLICY 에선 무시
        return createReq;
    }

    // ROOMMATE → FREE
    private FreePost moveRoommateToFree(
            Long userId,
            CreatePostRequest createReq,
            List<MultipartFile> images
    ) {
        FreePost freePost = FreePost.create(userId, createReq);
        freePostRepository.save(freePost);

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

    // ROOMMATE → POLICY
    private PolicyPost moveRoommateToPolicy(
            Long userId,
            CreatePostRequest createReq,
            List<MultipartFile> images
    ) {
        PolicyPost policyPost = PolicyPost.create(userId, createReq);
        policyPostRepository.save(policyPost);

        if (images != null && !images.isEmpty()) {
            int order = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                String url = imageStorage.uploadPolicyImage(policyPost.getId(), order, file);
                PolicyPostImage image = PolicyPostImage.of(policyPost, url, order++);
                policyPostImageRepository.save(image);
            }
        }
        return policyPost;
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

        roommatePostRepository.delete(post); // cascade 로 image row 삭제
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
    // SEARCH LIST (검색 + 지역 필터)
    // =======================
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> searchList(
            int page,
            int size,
            SearchType searchType,
            String keyword,
            String sidoCode,    // null 가능
            String sigunguCode  // null 가능
    ) {
        Pageable pageable = PageRequest.of(page, size);

        boolean hasSido = StringUtils.hasText(sidoCode);
        boolean hasSigungu = StringUtils.hasText(sigunguCode);

        Page<RoommatePost> posts;

        switch (searchType) {
            case TITLE -> {
                if (!hasSido) {
                    // 제목 + 지역 X
                    posts = roommatePostRepository
                            .findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword, pageable);
                } else if (!hasSigungu) {
                    // 제목 + 시/도만
                    posts = roommatePostRepository
                            .findByTitleContainingIgnoreCaseAndSidoCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, pageable
                            );
                } else {
                    // 제목 + 시/도 + 시/군/구
                    posts = roommatePostRepository
                            .findByTitleContainingIgnoreCaseAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, sigunguCode, pageable
                            );
                }
            }
            case CONTENT -> {
                if (!hasSido) {
                    posts = roommatePostRepository
                            .findByContentContainingOrderByCreatedAtDesc(keyword, pageable);
                } else if (!hasSigungu) {
                    posts = roommatePostRepository
                            .findByContentContainingAndSidoCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, pageable
                            );
                } else {
                    posts = roommatePostRepository
                            .findByContentContainingAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, sigunguCode, pageable
                            );
                }
            }

            default -> throw new IllegalArgumentException("지원하지 않는 검색 타입입니다: " + searchType);
        }

        // 목록 DTO + 댓글 수 세팅
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
