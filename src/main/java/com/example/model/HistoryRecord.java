package com.example.model;

import java.util.List;

/**
 * 历史录制记录 — 保存每次录制的成果（精简版，不含 headers/body/cookies）
 */
public class HistoryRecord {
    private String id;
    private String title;
    private String url;
    private String mainDomain;
    private long createdAt;
    private long duration;       // 录制耗时（秒）
    private int totalRaw;        // 原始捕获数
    private int totalFiltered;   // 过滤后
    private int apiCount;        // 去重后唯一接口数
    private String summary;      // AI 总结（可为空）
    private Object businessFlow; // AI 业务链路（可为空）
    private List<ApiSummary> apis;
    private java.util.Map<String, String> envVars; // 环境变量（token、userId 等）

    public HistoryRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMainDomain() { return mainDomain; }
    public void setMainDomain(String mainDomain) { this.mainDomain = mainDomain; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public int getTotalRaw() { return totalRaw; }
    public void setTotalRaw(int totalRaw) { this.totalRaw = totalRaw; }

    public int getTotalFiltered() { return totalFiltered; }
    public void setTotalFiltered(int totalFiltered) { this.totalFiltered = totalFiltered; }

    public int getApiCount() { return apiCount; }
    public void setApiCount(int apiCount) { this.apiCount = apiCount; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Object getBusinessFlow() { return businessFlow; }
    public void setBusinessFlow(Object businessFlow) { this.businessFlow = businessFlow; }

    public List<ApiSummary> getApis() { return apis; }
    public void setApis(List<ApiSummary> apis) { this.apis = apis; }

    public java.util.Map<String, String> getEnvVars() { return envVars; }
    public void setEnvVars(java.util.Map<String, String> envVars) { this.envVars = envVars; }
}
