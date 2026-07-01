package com.example.model;

public class ApiSummary {
    private String method;
    private String resource;
    private String category;
    private int callCount;
    private String apiName;

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

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getCallCount() { return callCount; }
    public void setCallCount(int callCount) { this.callCount = callCount; }

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }
}
