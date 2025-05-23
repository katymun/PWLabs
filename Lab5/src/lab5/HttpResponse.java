package lab5;

import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    int statusCode;
    Map<String, String> headers = new HashMap<>();
    String body;

    public HttpResponse() {
        this.statusCode = 200;
        this.headers = new HashMap<>();
        this.body = "";
    }

    public HttpResponse(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
