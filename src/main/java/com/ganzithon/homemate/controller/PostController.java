package com.ganzithon.homemate.controller;

import com.ganzithon.homemate.dto.CreatePostRequest;
import com.ganzithon.homemate.dto.UpdatePostRequest;
import com.ganzithon.homemate.dto.PostCategory;
import com.ganzithon.homemate.dto.PostDetailResponse;
import com.ganzithon.homemate.dto.CreateCommentRequest;
import com.ganzithon.homemate.dto.UpdateCommentRequest;
import com.ganzithon.homemate.dto.CommentResponse;
import com.ganzithon.homemate.security.UserPrincipal;
import com.ganzithon.homemate.service.RoommatePostService;
import com.ganzithon.homemate.service.FreePostService;
import com.ganzithon.homemate.service.PolicyPostService;
import com.ganzithon.homemate.service.PostLikeService;
import com.ganzithon.homemate.service.CommentService;
import com.ganzithon.homemate.dto.PageResponse;
import com.ganzithon.homemate.dto.PostListItemResponse;

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
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @ModelAttribute @Valid CreatePostRequest req,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        Long userId = principal.id();

        switch (req.getCategory()) {
            case ROOMMATE -> roommatePostService.create(userId, req, images);
            case FREE     -> freePostService.create(userId, req, images);
            case POLICY   -> policyPostService.create(userId, req, images);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // =================================================
    // 글 수정 (작성자만)
    // PUT /api/posts/{id}
    // - category는 쿼리 파라미터 (게시판 이동은 별도 설계 필요)
    // =================================================
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam PostCategory category,
            @ModelAttribute UpdatePostRequest req,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        Long userId = principal.id();

        // 수정 후 최종 카테고리
        PostCategory targetCategory = req.getNewCategory();
        if (targetCategory == null) {
            // 혹시라도 안 들어오면 현재 게시판 유지
            targetCategory = category;
        }

        // 1) 게시판이 그대로면 → 기존 update만 호출
        if (targetCategory == category) {
            switch (category) {
                case ROOMMATE -> roommatePostService.update(userId, id, req, images);
                case FREE     -> freePostService.update(userId, id, req, images);
                case POLICY   -> policyPostService.update(userId, id, req, images);
            }
        }
        // 2) 게시판이 바뀌면 → "이동 + 수정" 로직 별도 호출
        else {
            switch (category) { // 현재 게시판 기준 분기
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

        return ResponseEntity.noContent().build();
    }

    // =====================================
    // 글 삭제 (작성자만)
    // DELETE /api/posts/{category}/{id}
    // =====================================
    @DeleteMapping("/{category}/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        Long userId = principal.id();

        switch (category) {
            case ROOMMATE -> roommatePostService.delete(userId, id);
            case FREE     -> freePostService.delete(userId, id);
            case POLICY   -> policyPostService.delete(userId, id);
        }

        return ResponseEntity.noContent().build();
    }

    // ========================================
    // 목록 조회
    // GET /api/posts/{category}?page=&size=
    // ========================================
    @GetMapping("/{category}")
    public ResponseEntity<PageResponse<PostListItemResponse>> list(
            @PathVariable PostCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<PostListItemResponse> resultPage = switch (category) {
            case ROOMMATE -> roommatePostService.getList(page, size);
            case FREE     -> freePostService.getList(page, size);
            case POLICY   -> policyPostService.getList(page, size);
        };

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
            case FREE     -> freePostService.getDetailAndIncreaseView(id);
            case POLICY   -> policyPostService.getDetailAndIncreaseView(id);
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
    public ResponseEntity<Void> like(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        Long userId = principal.id();
        postLikeService.like(category, id, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{category}/{id}/likes")
    public ResponseEntity<Void> unlike(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        Long userId = principal.id();
        postLikeService.unlike(category, id, userId);
        return ResponseEntity.noContent().build();
    }

    // ========================================
    // 댓글
    // 생성: POST /api/posts/{category}/{id}/comments
    // 조회: GET  /api/posts/{category}/{id}/comments
    // 수정: PUT    /api/posts/comments/{commentId}
    // 삭제: DELETE /api/posts/comments/{commentId}
    // ========================================
    @PostMapping("/{category}/{id}/comments")
    public ResponseEntity<Void> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PostCategory category,
            @PathVariable Long id,
            @RequestBody @Valid CreateCommentRequest req
    ) {
        Long userId = principal.id();
        commentService.create(userId, category, id, req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{category}/{id}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable PostCategory category,
            @PathVariable Long id
    ) {
        List<CommentResponse> comments = commentService.getComments(category, id);
        return ResponseEntity.ok(comments);
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<Void> updateComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long commentId,
            @RequestBody @Valid UpdateCommentRequest req
    ) {
        Long userId = principal.id();
        commentService.update(userId, commentId, req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long commentId
    ) {
        Long userId = principal.id();
        commentService.delete(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
