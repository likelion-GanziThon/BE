package com.ganzithon.homemate.entity.Post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


@Entity
@Table(name = "roommate_post_image")
public class RoommatePostImage {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private RoommatePost post;


    @Column(length = 512, nullable = false)
    private String url;


    @Column(nullable = false)
    private short orderNo;


    protected RoommatePostImage() {
    }


    private RoommatePostImage(RoommatePost post, String url, short orderNo) {
        this.post = post;
        this.url = url;
        this.orderNo = orderNo;
    }


    public String getUrl() {
        return url;
    }

    public short getOrderNo() {
        return orderNo;
    }


    public static RoommatePostImage of(RoommatePost post, String url, int orderNo) {
        return new RoommatePostImage(post, url, (short) orderNo);
    }
}
