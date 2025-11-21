package com.ganzithon.homemate.repository.Post;

import com.ganzithon.homemate.entity.Post.PolicyPost;
import com.ganzithon.homemate.entity.Post.PolicyPostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyPostImageRepository extends JpaRepository<PolicyPostImage, Long> {
    List<PolicyPostImage> findByPost(PolicyPost post);
}