package com.ganzithon.homemate.dto.Post;

public class UpdatePostRequest {

    private PostCategory newCategory;
    private String title;
    private String content;
    private String sidoCode;
    private String sigunguCode;

    // ROOMMATE 전용 (null 가능)
    private String openchatUrl;

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

    public PostCategory getNewCategory() {
        return newCategory;
    }

    public void setNewCategory(PostCategory newCategory) {
        this.newCategory = newCategory;
    }

}
