package com.ganzithon.homemate.repository.Post;

import com.ganzithon.homemate.entity.Post.RoommatePost;
import com.ganzithon.homemate.entity.Post.RoommatePostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoommatePostImageRepository extends JpaRepository<RoommatePostImage, Long> {

    List<RoommatePostImage> findByPost(RoommatePost post);
}