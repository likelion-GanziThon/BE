package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.FreePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface FreePostRepository extends JpaRepository<FreePost, Long> {
    Page<FreePost> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
