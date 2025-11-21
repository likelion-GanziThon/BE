package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.Post.CreatePostRequest;
import com.ganzithon.homemate.dto.Post.UpdatePostRequest;
import com.ganzithon.homemate.dto.Post.PostCategory;
import com.ganzithon.homemate.dto.Post.PostListItemResponse;
import com.ganzithon.homemate.dto.Post.PostDetailResponse;
import com.ganzithon.homemate.dto.Post.SearchType;


import com.ganzithon.homemate.entity.Post.FreePost;
import com.ganzithon.homemate.entity.Post.FreePostImage;
import com.ganzithon.homemate.entity.Post.RoommatePost;
import com.ganzithon.homemate.entity.Post.RoommatePostImage;
import com.ganzithon.homemate.entity.Post.PolicyPost;
import com.ganzithon.homemate.entity.Post.PolicyPostImage;
import com.ganzithon.homemate.entity.User;

import com.ganzithon.homemate.repository.Post.FreePostRepository;
import com.ganzithon.homemate.repository.Post.FreePostImageRepository;
import com.ganzithon.homemate.repository.Post.RoommatePostRepository;
import com.ganzithon.homemate.repository.Post.RoommatePostImageRepository;
import com.ganzithon.homemate.repository.Post.PolicyPostRepository;
import com.ganzithon.homemate.repository.Post.PolicyPostImageRepository;
import com.ganzithon.homemate.repository.UserRepository;

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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FreePostService {

    private final FreePostRepository freePostRepository;
    private final FreePostImageRepository freePostImageRepository;
    private final ImageStorage imageStorage;
    private final CommentService commentService;
    private final PostLikeService postLikeService;
    private final UserRepository userRepository;

    private final RoommatePostRepository roommatePostRepository;
    private final RoommatePostImageRepository roommatePostImageRepository;
    private final PolicyPostRepository policyPostRepository;
    private final PolicyPostImageRepository policyPostImageRepository;

    public FreePostService(
            FreePostRepository freePostRepository,
            FreePostImageRepository freePostImageRepository,
            ImageStorage imageStorage,
            CommentService commentService,
            PostLikeService postLikeService,
            RoommatePostRepository roommatePostRepository,
            RoommatePostImageRepository roommatePostImageRepository,
            PolicyPostRepository policyPostRepository,
            PolicyPostImageRepository policyPostImageRepository,
            UserRepository userRepository
    ) {
        this.freePostRepository = freePostRepository;
        this.freePostImageRepository = freePostImageRepository;
        this.imageStorage = imageStorage;
        this.commentService = commentService;
        this.postLikeService = postLikeService;
        this.roommatePostRepository = roommatePostRepository;
        this.roommatePostImageRepository = roommatePostImageRepository;
        this.policyPostRepository = policyPostRepository;
        this.policyPostImageRepository = policyPostImageRepository;
        this.userRepository = userRepository;
    }

    // ========================================
    // CREATE
    // FREE: 제목/내용/지역 필수
    // ========================================
    @Transactional
    public void create(Long userId, CreatePostRequest req, List<MultipartFile> images) {

        validateTextFieldsForCreate(req.getTitle(), req.getContent(), req.getSidoCode(), req.getSigunguCode());

        FreePost post = FreePost.create(userId, req);
        freePostRepository.save(post);  // ★ 먼저 저장

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

        // FREE 안에서 수정: 제목/내용/지역 필수
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
    // 좋아요/댓글 동반 이동
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

        // 카테고리 안 바뀌는 경우 → 기존 update 로직 사용
        if (targetCategory == null || targetCategory == PostCategory.FREE) {
            update(userId, postId, req, images);
            return;
        }

        // 1) 텍스트 검증
        validateTextFields(req); // 제목/내용/지역 필수 체크

        // 2) 기존 FREE 이미지들 파일만 삭제 (DB row는 cascade로 삭제하게 놔둠)
        List<FreePostImage> oldImages = freePostImageRepository.findByPost(post);
        for (FreePostImage img : oldImages) {
            String url = img.getUrl();
            if (StringUtils.hasText(url)) {
                imageStorage.deleteFreeImage(url); // 스토리지 파일 삭제
            }
        }
        // freePostImageRepository.deleteAllInBatch(oldImages);  // ← 굳이 안 지워도 됨 (cascade)

        // 3) 타겟 게시판용 CreatePostRequest 만들기
        CreatePostRequest createReq = toCreatePostRequest(req, targetCategory);

        // 4) 타겟 게시판에 새 글 + 새 이미지 생성
        Long newPostId;
        switch (targetCategory) {
            case ROOMMATE -> {
                RoommatePost newPost = moveFreeToRoommate(userId, createReq, images);
                newPostId = newPost.getId();
            }
            case POLICY -> {
                PolicyPost newPost = moveFreeToPolicy(userId, createReq, images);
                newPostId = newPost.getId();
            }
            default -> throw new IllegalArgumentException("지원하지 않는 카테고리입니다: " + targetCategory);
        }

        // 5) 댓글/좋아요 이동
        commentService.moveAll(PostCategory.FREE, postId, targetCategory, newPostId);
        postLikeService.moveAll(PostCategory.FREE, postId, targetCategory, newPostId);

        // 6) 원본 FREE 게시글 row 삭제 (images는 cascade로 같이 삭제)
        freePostRepository.delete(post);
    }

    private void validateTextFieldsForCreate(String title, String content, String sidoCode, String sigunguCode) {
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
        createReq.setOpenchatUrl(req.getOpenchatUrl()); // ROOMMATE 전용 (FREE/POLICY는 무시)
        return createReq;
    }

    // FREE → ROOMMATE 이동 시
    private RoommatePost moveFreeToRoommate(
            Long userId,
            CreatePostRequest createReq,
            List<MultipartFile> images
    ) {
        // ROOMMATE 새 글 생성
        RoommatePost roommatePost = RoommatePost.create(userId, createReq);
        roommatePostRepository.save(roommatePost);  // ★ 먼저 저장

        // 새 이미지 업로드 (있을 때만)
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

    // FREE → POLICY 이동 시
    private PolicyPost moveFreeToPolicy(
            Long userId,
            CreatePostRequest createReq,
            List<MultipartFile> images
    ) {
        // POLICY 새 글 생성
        PolicyPost policyPost = PolicyPost.create(userId, createReq);
        policyPostRepository.save(policyPost);  // ★ 먼저 저장

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

    // LIST
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> getList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FreePost> posts =
                freePostRepository.findAllByOrderByCreatedAtDesc(pageable);

        Set<Long> userIds = posts.stream()
                .map(FreePost::getUserId)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return posts.map(post -> {
            User writer = userMap.get(post.getUserId());
            PostListItemResponse dto = PostListItemResponse.fromFree(post, writer);

            long commentCount = commentService.getCommentCount(
                    PostCategory.FREE,
                    post.getId()
            );
            dto.setCommentCount(commentCount);

            return dto;
        });
    }

    // SEARCH LIST
    @Transactional(readOnly = true)
    public Page<PostListItemResponse> searchList(
            int page,
            int size,
            SearchType searchType,
            String keyword,
            String sidoCode,
            String sigunguCode
    ) {
        Pageable pageable = PageRequest.of(page, size);

        boolean hasSido = StringUtils.hasText(sidoCode);
        boolean hasSigungu = StringUtils.hasText(sigunguCode);

        Page<FreePost> posts;

        switch (searchType) {
            case TITLE -> {
                if (!hasSido) {
                    posts = freePostRepository
                            .findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword, pageable);
                } else if (!hasSigungu) {
                    posts = freePostRepository
                            .findByTitleContainingIgnoreCaseAndSidoCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, pageable
                            );
                } else {
                    posts = freePostRepository
                            .findByTitleContainingIgnoreCaseAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, sigunguCode, pageable
                            );
                }
            }
            case CONTENT -> {
                if (!hasSido) {
                    posts = freePostRepository
                            .findByContentContainingOrderByCreatedAtDesc(keyword, pageable);
                } else if (!hasSigungu) {
                    posts = freePostRepository
                            .findByContentContainingAndSidoCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, pageable
                            );
                } else {
                    posts = freePostRepository
                            .findByContentContainingAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
                                    keyword, sidoCode, sigunguCode, pageable
                            );
                }
            }
            default -> throw new IllegalArgumentException("지원하지 않는 검색 타입입니다: " + searchType);
        }

        Set<Long> userIds = posts.stream()
                .map(FreePost::getUserId)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return posts.map(post -> {
            User writer = userMap.get(post.getUserId());
            PostListItemResponse dto = PostListItemResponse.fromFree(post, writer);

            long commentCount = commentService.getCommentCount(
                    PostCategory.FREE,
                    post.getId()
            );
            dto.setCommentCount(commentCount);

            return dto;
        });
    }

    // DETAIL
    @Transactional
    public PostDetailResponse getDetailAndIncreaseView(Long postId) {
        FreePost post = freePostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        post.increaseViewCount();

        User writer = userRepository.findById(post.getUserId())
                .orElseThrow(() -> new IllegalStateException("작성자 정보를 찾을 수 없습니다."));

        return PostDetailResponse.fromFree(post, writer);
    }
}
