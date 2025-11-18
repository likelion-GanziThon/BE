package com.ganzithon.homemate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public class CreatePostRequest {


    @NotNull
    private PostCategory category; // ROOMMATE / FREE / POLICY


    @NotBlank
    @Size(max = 100)
    private String title;


    @NotBlank
    private String content;


    @NotBlank
    private String sidoCode; //추후 프론트 입력이 확정되면 코드 수정


    @NotBlank
    private String sigunguCode; //추후 프론트 입력이 확정되면 코드 수정


    // ROOMMATE일 때만 필수, FREE/POLICY는 null 허용
    private String openchatUrl;


    public PostCategory getCategory() {
        return category;
    }


    public void setCategory(PostCategory category) {
        this.category = category;
    }


    public String getTitle() {
        return title;
    }


    public void setTitle(String title) {
        this.title = title;
    }


    public String getContent() {
        return content;
    }


    public void setContent(String content) {
        this.content = content;
    }


    public String getSidoCode() {
        return sidoCode;
    }


    public void setSidoCode(String sidoCode) {
        this.sidoCode = sidoCode;
    }


    public String getSigunguCode() {
        return sigunguCode;
    }


    public void setSigunguCode(String sigunguCode) {
        this.sigunguCode = sigunguCode;
    }


    public String getOpenchatUrl() {
        return openchatUrl;
    }


    public void setOpenchatUrl(String openchatUrl) {
        this.openchatUrl = openchatUrl;
    }
}