package com.ganzithon.homemate.controller;

import com.ganzithon.homemate.dto.Post.CreatePostRequest;
import com.ganzithon.homemate.dto.Post.UpdatePostRequest;
import com.ganzithon.homemate.dto.Post.PostCategory;
import com.ganzithon.homemate.dto.Post.PostDetailResponse;
import com.ganzithon.homemate.dto.Comment.CreateCommentRequest;
import com.ganzithon.homemate.dto.Comment.UpdateCommentRequest;
import com.ganzithon.homemate.dto.Comment.CommentResponse;
import com.ganzithon.homemate.security.UserPrincipal;
import com.ganzithon.homemate.service.RoommatePostService;
import com.ganzithon.homemate.service.FreePostService;
import com.ganzithon.homemate.service.PolicyPostService;
import com.ganzithon.homemate.service.PostLikeService;
import com.ganzithon.homemate.service.CommentService;
import com.ganzithon.homemate.dto.PageResponse;
import com.ganzithon.homemate.dto.Post.PostListItemResponse;
import com.ganzithon.homemate.dto.Post.SearchType;
import com.ganzithon.homemate.dto.ApiResponse;
import com.ganzithon.homemate.dto.Post.HomePostsResponse;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;

import java.util.List;



@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final RoommatePostService roommatePostService;
    private final FreePostService freePostService;
    private final PolicyPostService policyPostService;
    private final PostLikeService postLikeService;
    private final CommentService commentService;

    public PostController(RoommatePostService roommatePostService,
                          FreePostService freePostService,
                          PolicyPostService policyPostService,
                          PostLikeService postLikeService,
                          CommentService commentService) {
        this.roommatePostService = roommatePostService;
        this.freePostService = freePostService;
        this.policyPostService = policyPostService;
        this.postLikeService = postLikeService;
        this.commentService = commentService;
    }

    // =============================================================
    // 글 작성
    // POST /api/posts
    // - multipart/form-data 요청
    // - 일반 필드는 form-data로 전달받아 DTO(CreatePostRequest)로 바인딩
    // - 이미지(images)는 MultipartFile로 업로드
    // =============================================================
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @ModelAttribute @Valid CreatePostRequest req,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        Long userId = principal.id();

        switch (req.getCategory()) {
            case ROOMMATE -> roommatePostService.create(userId, req, images);
            case FREE -> freePostService.create(userId, req, images);
            case POLICY -> policyPostService.create(userId, req, images);
        }

        ApiResponse<Void> body = new ApiResponse<>("게시글이 작성되었습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // =================================================
    // 글 수정 (작성자만)
    // PUT /api/posts/{id}
    // - category는 쿼리 파라미터
    // =================================================
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam PostCategory category,
            @ModelAttribute UpdatePostRequest req,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        Long userId = principal.id();

        PostCategory targetCategory = req.getNewCategory();
        if (targetCategory == null) {
            targetCategory = category;
        }

        if (targetCategory == category) {
            switch (category) {
                case ROOMMATE -> roommatePostService.update(userId, id, req, images);
                case FREE -> freePostService.update(userId, id, req, images);
                case POLICY -> policyPostService.update(userId, id, req, images);
            }
        } else {
            switch (category) {
                case ROOMMATE -> roommatePostService.moveToAnotherCategory(
                        userId, id, req, images, targetCategory
                );
                case FREE -> freePostService.moveToAnotherCategory(
                        userId, id, req, images, targetCategory
                );
                case POLICY -> policyPostService.moveToAnotherCategory(
                        userId, id, req, images, targetCategory
                );
            }
        }

        ApiResponse<Void> body = new ApiResponse<>("게시글이 수정되었습니다.");
        return ResponseEntity.ok(body);
    }

    // =====================================
    // 글 삭제 (작성자만)
    // DELETE /api/posts/{category}/{id}
    // =====================================
    @DeleteMapping("/{category}/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        Long userId = principal.id();

        switch (category) {
            case ROOMMATE -> roommatePostService.delete(userId, id);
            case FREE -> freePostService.delete(userId, id);
            case POLICY -> policyPostService.delete(userId, id);
        }

        ApiResponse<Void> body = new ApiResponse<>("게시글이 삭제되었습니다.");
        return ResponseEntity.ok(body);
    }

    // ========================================
    // 목록 조회
    // GET /api/posts/{category}?page=&size=
    // ========================================
    @GetMapping("/{category}")
    public ResponseEntity<PageResponse<PostListItemResponse>> list(
            @PathVariable PostCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SearchType searchType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu
    ) {
        Page<PostListItemResponse> resultPage = null;

        boolean hasKeyword = (keyword != null && !keyword.isBlank());
        boolean hasSearchType = (searchType != null);
        boolean hasSido = (sido != null && !sido.isBlank());
        boolean hasSigungu = (sigungu != null && !sigungu.isBlank());

        switch (category) {
            case ROOMMATE -> {
                if (hasKeyword && hasSearchType) {
                    resultPage = roommatePostService.searchList(
                            page, size, searchType, keyword,
                            hasSido ? sido : null,
                            hasSigungu ? sigungu : null
                    );
                } else {
                    resultPage = roommatePostService.getList(page, size);
                }
            }
            case FREE -> {
                if (hasKeyword && hasSearchType) {
                    resultPage = freePostService.searchList(
                            page, size, searchType, keyword,
                            hasSido ? sido : null,
                            hasSigungu ? sigungu : null
                    );
                } else {
                    resultPage = freePostService.getList(page, size);
                }
            }
            case POLICY -> {
                if (hasKeyword && hasSearchType) {
                    resultPage = policyPostService.searchList(
                            page, size, searchType, keyword
                    );
                } else {
                    resultPage = policyPostService.getList(page, size);
                }
            }
        }

        return ResponseEntity.ok(new PageResponse<>(resultPage));
    }


    // ========================================
    // 상세 조회 (+ 좋아요/댓글 정보 포함)
    // GET /api/posts/{category}/{id}
    // ========================================
    @GetMapping("/{category}/{id}")
    public ResponseEntity<PostDetailResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        PostDetailResponse response = switch (category) {
            case ROOMMATE -> roommatePostService.getDetailAndIncreaseView(id);
            case FREE -> freePostService.getDetailAndIncreaseView(id);
            case POLICY -> policyPostService.getDetailAndIncreaseView(id);
        };

        // 좋아요 정보
        long likeCount = postLikeService.getLikeCount(category, id);
        response.setLikeCount(likeCount);

        Long userId = (principal != null) ? principal.id() : null;
        if (userId != null) {
            boolean likedByMe = postLikeService.likedByUser(category, id, userId);
            response.setLikedByMe(likedByMe);
        }

        // 댓글 정보
        long commentCount = commentService.getCommentCount(category, id);
        response.setCommentCount(commentCount);

        List<CommentResponse> comments = commentService.getComments(category, id);
        response.setComments(comments);

        return ResponseEntity.ok(response);
    }

    // ========================================
    // 좋아요
    // POST   /api/posts/{category}/{id}/likes
    // DELETE /api/posts/{category}/{id}/likes
    // ========================================
    @PostMapping("/{category}/{id}/likes")
    public ResponseEntity<ApiResponse<Void>> like(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        Long userId = principal.id();
        postLikeService.like(category, id, userId);
        ApiResponse<Void> body = new ApiResponse<>("좋아요가 추가되었습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{category}/{id}/likes")
    public ResponseEntity<ApiResponse<Void>> unlike(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        Long userId = principal.id();
        postLikeService.unlike(category, id, userId);
        ApiResponse<Void> body = new ApiResponse<>("좋아요가 취소되었습니다.");
        return ResponseEntity.ok(body);
    }

    // ========================================
    // 댓글
    // 생성: POST /api/posts/{category}/{id}/comments
    // 조회: GET  /api/posts/{category}/{id}/comments
    // 수정: PUT    /api/posts/comments/{commentId}
    // 삭제: DELETE /api/posts/comments/{commentId}
    // ========================================
    @PostMapping("/{category}/{id}/comments")
    public ResponseEntity<ApiResponse<Void>> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id,
            @RequestBody @Valid CreateCommentRequest req
    ) {
        Long userId = principal.id();
        commentService.create(userId, category, id, req);
        ApiResponse<Void> body = new ApiResponse<>("댓글이 작성되었습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> updateComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long commentId,
            @RequestBody @Valid UpdateCommentRequest req
    ) {
        Long userId = principal.id();
        commentService.update(userId, commentId, req);
        ApiResponse<Void> body = new ApiResponse<>("댓글이 수정되었습니다.");
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long commentId
    ) {
        Long userId = principal.id();
        commentService.delete(userId, commentId);
        ApiResponse<Void> body = new ApiResponse<>("댓글이 삭제되었습니다.");
        return ResponseEntity.ok(body);
    }

    // ========================================
    // 메인 페이지용 최신 글 2개씩
    // GET /api/posts/main
    // ========================================
    @GetMapping("/main")
    public ResponseEntity<HomePostsResponse> getMainPosts() {

        // 각 게시판마다 최신순 2개만
        Page<PostListItemResponse> roommatePage = roommatePostService.getList(0, 2);
        Page<PostListItemResponse> freePage      = freePostService.getList(0, 2);
        Page<PostListItemResponse> policyPage    = policyPostService.getList(0, 2);

        HomePostsResponse body = new HomePostsResponse(
                roommatePage.getContent(),
                freePage.getContent(),
                policyPage.getContent()
        );

        return ResponseEntity.ok(body);
    }

}

