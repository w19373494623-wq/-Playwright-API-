package com.example.model;

/**
 * 变量提取规则。
 * 表示从某个接口的响应中提取指定字段并保存为变量。
 */
public class VariableRule {

    private String variableName;   // 变量名，如 ugcId
    private String sourceApi;      // 来源接口，用于匹配 PostmanItem.name
    private String jsonPath;       // 提取路径，如 data.ugcId

    public VariableRule() {}

    public VariableRule(String variableName, String sourceApi, String jsonPath) {
        this.variableName = variableName;
        this.sourceApi = sourceApi;
        this.jsonPath = jsonPath;
    }

    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }

    public String getSourceApi() { return sourceApi; }
    public void setSourceApi(String sourceApi) { this.sourceApi = sourceApi; }

    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }
}
