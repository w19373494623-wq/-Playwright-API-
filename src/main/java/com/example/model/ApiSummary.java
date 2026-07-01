package com.example.model;

public class ApiSummary {
    private String method;
    private String resource;
    private String url;             // 完整 URL（含 query 参数）
    private String category;
    private int callCount;
    private String apiName;
    private String requestBody;     // 请求体（JSON 字符串）
    private java.util.Map<String, String> headers;  // 请求头

    public ApiSummary() {}

    public ApiSummary(String method, String resource, String category, int callCount) {
        this.method = method;
        this.resource = resource;
        this.category = category;
        this.callCount = callCount;
        this.apiName = method + " " + resource;
    }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getCallCount() { return callCount; }
    public void setCallCount(int callCount) { this.callCount = callCount; }

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public java.util.Map<String, String> getHeaders() { return headers; }
    public void setHeaders(java.util.Map<String, String> headers) { this.headers = headers; }
}
