package com.ganzithon.homemate.dto.Post;

import java.util.List;

public class HomePostsResponse {

    private List<PostListItemResponse> roommate;
    private List<PostListItemResponse> free;
    private List<PostListItemResponse> policy;

    public HomePostsResponse() {
    }

    public HomePostsResponse(
            List<PostListItemResponse> roommate,
            List<PostListItemResponse> free,
            List<PostListItemResponse> policy
    ) {
        this.roommate = roommate;
        this.free = free;
        this.policy = policy;
    }

    public List<PostListItemResponse> getRoommate() {
        return roommate;
    }

    public List<PostListItemResponse> getFree() {
        return free;
    }

    public List<PostListItemResponse> getPolicy() {
        return policy;
    }

    public void setRoommate(List<PostListItemResponse> roommate) {
        this.roommate = roommate;
    }

    public void setFree(List<PostListItemResponse> free) {
        this.free = free;
    }

    public void setPolicy(List<PostListItemResponse> policy) {
        this.policy = policy;
    }
}
