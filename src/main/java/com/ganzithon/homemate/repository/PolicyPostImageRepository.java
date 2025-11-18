package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.PolicyPost;
import com.ganzithon.homemate.entity.PolicyPostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyPostImageRepository extends JpaRepository<PolicyPostImage, Long> {
    List<PolicyPostImage> findByPost(PolicyPost post);
}