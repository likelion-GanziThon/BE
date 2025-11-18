package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.FreePost;
import com.ganzithon.homemate.entity.FreePostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FreePostImageRepository extends JpaRepository<FreePostImage, Long> {
    List<FreePostImage> findByPost(FreePost post);
}
