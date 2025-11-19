package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.RoommatePost;
import com.ganzithon.homemate.entity.RoommatePostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoommatePostImageRepository extends JpaRepository<RoommatePostImage, Long> {

    List<RoommatePostImage> findByPost(RoommatePost post);
}