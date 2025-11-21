package com.ganzithon.homemate.repository.Post;

import com.ganzithon.homemate.entity.Post.PolicyPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PolicyPostRepository extends JpaRepository<PolicyPost, Long> {

    Page<PolicyPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PolicyPost> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            String keyword,
            Pageable pageable
    );

    Page<PolicyPost> findByContentContainingOrderByCreatedAtDesc(
            String keyword,
            Pageable pageable
    );

}
