package lab5;

public class CachedResponse {
    private String content;
    private long expirationTime;

    CachedResponse(String content, long expirationTime) {
        this.content = content;
        this.expirationTime = expirationTime;
    }

    boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    String getContent() {
        return content;
    }
}
