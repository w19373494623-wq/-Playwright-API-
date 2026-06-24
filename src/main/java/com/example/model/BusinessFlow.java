package com.example.model;

import java.util.List;
import java.util.Map;

public class BusinessFlow {

    private String scenario;                 // "创作者社交与内容互动"
    private String description;              // 场景说明
    private List<BusinessAction> actions;    // 操作序列（已排序）
    private Map<String, Object> metadata;    // 额外信息

    public BusinessFlow() {}

    public BusinessFlow(String scenario, String description, List<BusinessAction> actions) {
        this.scenario = scenario;
        this.description = description;
        this.actions = actions;
    }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<BusinessAction> getActions() { return actions; }
    public void setActions(List<BusinessAction> actions) { this.actions = actions; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
