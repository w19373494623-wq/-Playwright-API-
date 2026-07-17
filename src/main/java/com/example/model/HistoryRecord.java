package com.example.model;

import java.util.List;
import java.util.Map;

/**
 * 历史录制记录 — 保存录制项目快照。
 *
 * 从"录制记录"升级为"测试项目快照"，包含：
 * - 接口列表（精简 + 完整两份）
 * - 业务链路分析结果
 * - 环境变量
 * - AI 分析结果
 *
 * 版本记录：
 * - v1: 初始版本，仅 apis + summary + businessFlow
 * - v2: 新增 assets/updateTime/version/scenarios/aiAnalysis
 */
public class HistoryRecord {

    // ===== 基础信息 =====
    private String id;
    private String title;
    private String url;
    private String mainDomain;
    private long createdAt;
    private long updateTime;
    private long duration;       // 录制耗时（秒）
    private int version = 2;     // 数据结构版本，后续兼容用

    // ===== 统计 =====
    private int totalRaw;        // 原始捕获数
    private int totalFiltered;   // 过滤后
    private int apiCount;        // 去重后唯一接口数

    // ===== 接口资产 =====
    private List<ApiSummary> apis;    // 精简列表（前端展示用）
    private List<ApiAsset> assets;    // 完整数据（测试执行用）

    // ===== AI 分析 =====
    private String summary;           // AI 总结（兼容旧字段）
    private BusinessFlow businessFlow; // 业务链路
    private List<Map<String, Object>> scenarios;  // 多场景识别结果
    private DedupResult dedupResult;  // AI 去重缓存

    // ===== 环境变量 =====
    private Map<String, String> envVars;

    // ===== 扩展预留 =====
    private Map<String, Object> aiAnalysis;    // 后续可替换为 AiAnalysisResult
    private List<Object> smokeTestResults;     // 烟雾测试结果（预留）

    // ===== 烟雾测试 =====
    private SmokeTestSummary smokeTestResult;

    public HistoryRecord() {}

    // ===== getters & setters =====

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

    public long getUpdateTime() { return updateTime; }
    public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public int getTotalRaw() { return totalRaw; }
    public void setTotalRaw(int totalRaw) { this.totalRaw = totalRaw; }

    public int getTotalFiltered() { return totalFiltered; }
    public void setTotalFiltered(int totalFiltered) { this.totalFiltered = totalFiltered; }

    public int getApiCount() { return apiCount; }
    public void setApiCount(int apiCount) { this.apiCount = apiCount; }

    public List<ApiSummary> getApis() { return apis; }
    public void setApis(List<ApiSummary> apis) { this.apis = apis; }

    public List<ApiAsset> getAssets() { return assets; }
    public void setAssets(List<ApiAsset> assets) { this.assets = assets; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public BusinessFlow getBusinessFlow() { return businessFlow; }
    public void setBusinessFlow(BusinessFlow businessFlow) { this.businessFlow = businessFlow; }

    public List<Map<String, Object>> getScenarios() { return scenarios; }
    public void setScenarios(List<Map<String, Object>> scenarios) { this.scenarios = scenarios; }

    public DedupResult getDedupResult() { return dedupResult; }
    public void setDedupResult(DedupResult dedupResult) { this.dedupResult = dedupResult; }

    public Map<String, String> getEnvVars() { return envVars; }
    public void setEnvVars(Map<String, String> envVars) { this.envVars = envVars; }

    public Map<String, Object> getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(Map<String, Object> aiAnalysis) { this.aiAnalysis = aiAnalysis; }

    public List<Object> getSmokeTestResults() { return smokeTestResults; }
    public void setSmokeTestResults(List<Object> smokeTestResults) { this.smokeTestResults = smokeTestResults; }

    public SmokeTestSummary getSmokeTestResult() { return smokeTestResult; }
    public void setSmokeTestResult(SmokeTestSummary smokeTestResult) { this.smokeTestResult = smokeTestResult; }
}
