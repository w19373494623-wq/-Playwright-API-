package com.example.service;

import com.example.model.VariableRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 变量提取规则管理服务。
 * 管理从接口响应中提取变量的规则。
 *
 * 当前使用内存存储，后续可扩展为持久化。
 */
@Service
public class VariableRuleService {

    private final List<VariableRule> rules = new ArrayList<>();

    /**
     * 添加提取规则。
     */
    public void addRule(VariableRule rule) {
        if (rule != null && rule.getVariableName() != null && rule.getSourceApi() != null) {
            rules.add(rule);
        }
    }

    /**
     * 批量添加提取规则。
     */
    public void addRules(List<VariableRule> newRules) {
        if (newRules != null) {
            newRules.forEach(this::addRule);
        }
    }

    /**
     * 获取所有提取规则。
     */
    public List<VariableRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * 根据接口名称查找匹配的提取规则。
     */
    public List<VariableRule> findByApi(String apiName) {
        if (apiName == null) return List.of();
        return rules.stream()
                .filter(r -> apiName.contains(r.getSourceApi())
                        || r.getSourceApi().contains(apiName)
                        || apiName.equalsIgnoreCase(r.getSourceApi()))
                .collect(Collectors.toList());
    }

    /**
     * 清空所有规则。
     */
    public void clear() {
        rules.clear();
    }
}
