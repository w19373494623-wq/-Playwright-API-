package com.example.model;

/**
 * 捕获到的单个网络请求数据
 */
public class CapturedRequest {

    private String url;
    private String method;
    private String requestBody;
    private int statusCode;
    private String responseBody;

    public CapturedRequest(String url, String method, String requestBody,
                           int statusCode, String responseBody) {
        this.url = url;
        this.method = method;
        this.requestBody = requestBody;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public String getRequestBody() { return requestBody; }
    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }

    @Override
    public String toString() {
        return method + " " + url + " -> " + statusCode;
    }
}
