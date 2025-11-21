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
@Table(name = "free_post_image")
public class FreePostImage {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private FreePost post;


    @Column(length = 512, nullable = false)
    private String url;


    @Column(nullable = false)
    private short orderNo;


    protected FreePostImage() {
    }


    private FreePostImage(FreePost post, String url, short orderNo) {
        this.post = post;
        this.url = url;
        this.orderNo = orderNo;
    }


    public static FreePostImage of(FreePost post, String url, int orderNo) {
        return new FreePostImage(post, url, (short) orderNo);
    }

    public String getUrl() {
        return url;
    }

    public short getOrderNo() {
        return orderNo;
    }

}
