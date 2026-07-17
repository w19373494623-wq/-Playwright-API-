package com.example.service;

import com.example.model.AutomationConfig;
import com.example.model.HistoryRecord;
import com.example.model.VariableRule;
import com.example.smoke.model.PostmanItem;
import com.example.smoke.model.SmokeTestReport;
import com.example.smoke.model.SmokeTestResult;
import com.example.smoke.service.SmokeTestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 自动化执行服务。
 *
 * 职责：
 *  - 根据历史记录 ID 加载接口数据
 *  - 读取自动化配置，按需自动登录获取 Token
 *  - 将 Token 注入 Collection 变量数组，由 Executor 逐条解析 {{token}}
 *  - 执行过程中根据 VariableRule 从响应中提取变量，供后续接口使用
 *  - 复用 SmokeTestService 执行烟雾测试
 */
@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HistoryService historyService;
    private final SmokeTestService smokeTestService;
    private final AutomationConfigService automationConfigService;
    private final LoginService loginService;
    private final VariableResolver variableResolver;
    private final VariableRuleService variableRuleService;

    public AutomationService(HistoryService historyService,
                             SmokeTestService smokeTestService,
                             AutomationConfigService automationConfigService,
                             LoginService loginService,
                             VariableResolver variableResolver,
                             VariableRuleService variableRuleService) {
        this.historyService = historyService;
        this.smokeTestService = smokeTestService;
        this.automationConfigService = automationConfigService;
        this.loginService = loginService;
        this.variableResolver = variableResolver;
        this.variableRuleService = variableRuleService;
    }

    /**
     * 根据历史记录执行自动化烟雾测试。
     *
     * 执行流程：
     *  1. 加载 HistoryRecord → 生成 Postman Collection JSON
     *  2. 检查 envVars 是否已有有效 token，没有则自动调用 LoginService 获取并持久化
     *  3. 将 HistoryRecord.envVars 注入 Collection 变量数组（含 {{token}} 等占位符）
     *  4. 调用 SmokeTestService 逐条执行接口，Executor 自动替换 {{var}} 为实际值
     *  5. 每条接口执行后，根据 VariableRule 从响应提取变量
     *
     * @param historyId 历史记录 ID
     * @return 烟雾测试报告
     */
    public SmokeTestReport run(String historyId) {
        HistoryRecord record = historyService.findById(historyId);
        if (record == null) {
            throw new IllegalArgumentException("历史记录不存在: " + historyId);
        }

        log.info("自动化测试开始: historyId={}, title={}, apis={}",
                historyId, record.getTitle(),
                record.getApis() != null ? record.getApis().size() : 0);

        // 1. 转 Collection JSON
        String collectionJson = historyService.toApifoxCollectionJson(record);

        // 2. 检查 envVars 是否已有 token，没有则自动登录获取
        Map<String, String> envVars = record.getEnvVars();
        boolean hasToken = envVars != null && envVars.containsKey("token")
                && envVars.get("token") != null && !envVars.get("token").isEmpty();

        if (!hasToken) {
            AutomationConfig config = automationConfigService.getConfig();
            if ("LOGIN".equals(config.getAuthType())) {
                log.info("envVars 中无有效 token，触发自动登录: loginUrl={}", config.getLoginUrl());
                try {
                    String token = loginService.login(config);
                    log.info("自动登录成功，token 长度: {}", token.length());

                    // 保存 token 到 HistoryRecord.envVars（持久化）
                    if (envVars == null) {
                        envVars = new java.util.LinkedHashMap<>();
                    }
                    envVars.put("token", token);
                    historyService.updateEnvVars(historyId, envVars);
                    record.setEnvVars(envVars);
                    log.info("token 已保存到 HistoryRecord.envVars, historyId={}", historyId);

                    variableResolver.put("token", token);
                } catch (Exception e) {
                    log.warn("自动登录失败，将使用已有环境变量继续: {}", e.getMessage());
                }
            } else {
                log.info("无有效 token 且未配置 LOGIN 模式，将使用环境变量中的现有值");
            }
        } else {
            log.info("envVars 中已有 token，跳过自动登录");
        }

        // 3. 注入 HistoryRecord.envVars（含新获取或已有的 token）
        envVars = record.getEnvVars();
        if (envVars != null && !envVars.isEmpty()) {
            collectionJson = injectVariables(collectionJson, envVars);
            log.info("已注入 {} 个环境变量: {}", envVars.size(), envVars.keySet());
        }

        // 4. 执行烟雾测试，每条接口完成后提取变量
        return smokeTestService.runSmokeTest(collectionJson, this::extractVariables);
    }

    /**
     * 将 Token 注入 Collection JSON 的 variable 数组。
     * 保持 {{token}} 占位符，由 Executor 在逐条执行时解析。
     */
    private String injectTokenToVariables(String collectionJson, String token) {
        return injectVariables(collectionJson, Map.of("token", token));
    }

    /**
     * 批量注入环境变量到 Collection JSON 的 variable 数组。
     * 已存在的变量覆盖值，不存在的追加。
     * 由 Executor 在逐条执行时通过 replaceVariables() 解析 {{key}}。
     */
    private String injectVariables(String collectionJson, Map<String, String> variables) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(collectionJson);
            ArrayNode vars = (ArrayNode) root.get("variable");
            if (vars == null) {
                vars = MAPPER.createArrayNode();
                root.set("variable", vars);
            }

            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null || value.isEmpty()) continue;

                boolean found = false;
                for (int i = 0; i < vars.size(); i++) {
                    ObjectNode var = (ObjectNode) vars.get(i);
                    if (key.equals(var.get("key").asText())) {
                        var.put("value", value);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ObjectNode newVar = vars.addObject();
                    newVar.put("key", key);
                    newVar.put("value", value);
                }
            }

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("环境变量注入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 每条接口执行完成后的回调。
     * 根据 VariableRule 从响应中提取变量，存入 VariableResolver 和 Executor 变量列表。
     */
    private void extractVariables(PostmanItem item,
                                  SmokeTestResult result,
                                  List<Map<String, String>> variables) {
        if (result.getResponseBody() == null || !result.isPassed()) return;

        List<VariableRule> rules = variableRuleService.findByApi(item.getName());
        if (rules.isEmpty()) return;

        try {
            JsonNode root = MAPPER.readTree(result.getResponseBody());
            for (VariableRule rule : rules) {
                String value = variableResolver.extract(root, rule.getJsonPath());
                if (value != null) {
                    // 加入 Executor 变量列表，后续接口可引用 {{variableName}}
                    variables.add(Map.of("key", rule.getVariableName(), "value", value));
                    log.info("变量提取: {}={} (来自: {})", rule.getVariableName(), value, item.getName());
                }
            }
        } catch (Exception e) {
            log.warn("变量提取失败 (接口: {}): {}", item.getName(), e.getMessage());
        }
    }
}
