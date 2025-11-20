package com.ganzithon.homemate.dto;

public record ResetResponse(
    String message,
    boolean reset,
    ResetState state
) {
    public record ResetState(
        boolean promptReset,
        boolean regionReset,
        boolean recommendationsReset
    ) {}
    
    public static ResetResponse success() {
        return new ResetResponse(
            "리셋이 완료되었습니다. 새로운 추천을 받을 수 있습니다.",
            true,
            new ResetState(true, true, true)
        );
    }
}

