package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.RoommatePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;



public interface RoommatePostRepository extends JpaRepository<RoommatePost, Long> {

    Page<RoommatePost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 제목 검색
    Page<RoommatePost> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            String keyword,
            Pageable pageable
    );

    Page<RoommatePost> findByTitleContainingIgnoreCaseAndSidoCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            Pageable pageable
    );

    Page<RoommatePost> findByTitleContainingIgnoreCaseAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            String sigunguCode,
            Pageable pageable
    );

    // 내용 검색
    Page<RoommatePost> findByContentContainingOrderByCreatedAtDesc(
            String keyword,
            Pageable pageable
    );

    Page<RoommatePost> findByContentContainingAndSidoCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            Pageable pageable
    );

    Page<RoommatePost> findByContentContainingAndSidoCodeAndSigunguCodeOrderByCreatedAtDesc(
            String keyword,
            String sidoCode,
            String sigunguCode,
            Pageable pageable
    );

}
