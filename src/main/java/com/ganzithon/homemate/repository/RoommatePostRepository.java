package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.RoommatePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;



public interface RoommatePostRepository extends JpaRepository<RoommatePost, Long> {

    Page<RoommatePost> findAllByOrderByCreatedAtDesc(Pageable pageable);
}