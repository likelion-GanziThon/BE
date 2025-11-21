package com.ganzithon.homemate.repository.Post;

import com.ganzithon.homemate.entity.Post.FreePost;
import com.ganzithon.homemate.entity.Post.FreePostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FreePostImageRepository extends JpaRepository<FreePostImage, Long> {
    List<FreePostImage> findByPost(FreePost post);
}
