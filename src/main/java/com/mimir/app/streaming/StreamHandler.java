package com.mimir.app.streaming;

public interface StreamHandler {
    void onToken(String token);

    void onComplete();

    void onError(Throwable t);
}
