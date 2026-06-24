package com.example.model;

public class BusinessAction {

    private int sequence;
    private String action;       // "关注用户", "点赞内容"
    private String category;     // "社交互动", "内容互动", "用户认证"
    private String method;       // POST, GET
    private String url;          // 原始 URL
    private String resource;     // /api/ugc/followUser
    private int apiIndex;        // 对应的 ApiAsset 索引

    public BusinessAction() {}

    public BusinessAction(int sequence, String action, String category,
                          String method, String url, String resource, int apiIndex) {
        this.sequence = sequence;
        this.action = action;
        this.category = category;
        this.method = method;
        this.url = url;
        this.resource = resource;
        this.apiIndex = apiIndex;
    }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public int getApiIndex() { return apiIndex; }
    public void setApiIndex(int apiIndex) { this.apiIndex = apiIndex; }
}
