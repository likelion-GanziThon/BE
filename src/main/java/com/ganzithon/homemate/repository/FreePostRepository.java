package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.FreePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface FreePostRepository extends JpaRepository<FreePost, Long> {

    Page<FreePost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 제목 검색
    Page<FreePost> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            String keyword,
            Pageable pageable
    );

    Page<FreePost> findByTitleContainingIgnoreCaseAndSidoCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            Pageable pageable
    );

    Page<FreePost> findByTitleContainingIgnoreCaseAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            String sigunguCode,
            Pageable pageable
    );

    // 내용 검색
    Page<FreePost> findByContentContainingOrderByCreatedAtDesc(
            String keyword,
            Pageable pageable
    );

    Page<FreePost> findByContentContainingAndSidoCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            Pageable pageable
    );

    Page<FreePost> findByContentContainingAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            String sigunguCode,
            Pageable pageable
    );

}

