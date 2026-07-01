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
     *  2. 读取 AutomationConfig，按需登录获取 Token
     *  3. 将 Token 注入 Collection 变量数组（保持 {{token}} 占位符供 Executor 解析）
     *  4. 调用 SmokeTestService 逐条执行接口
     *  5. 每条接口执行后，根据 VariableRule 从响应提取变量 → 存入 VariableResolver
     *  6. 新提取的变量自动加入 Executor 变量列表，供后续接口使用
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

        // 2. 读取配置并按需登录
        AutomationConfig config = automationConfigService.getConfig();
        if ("LOGIN".equals(config.getAuthType())) {
            String token = loginService.login(config);
            variableResolver.put("token", token);
            log.info("登录成功，token 已写入 VariableResolver");

            // 3. 将 Token 注入 Collection 变量数组
            collectionJson = injectTokenToVariables(collectionJson, token);
        }

        // 4. 执行烟雾测试，每条接口完成后提取变量
        return smokeTestService.runSmokeTest(collectionJson, this::extractVariables);
    }

    /**
     * 将 Token 注入 Collection JSON 的 variable 数组。
     * 保持 {{token}} 占位符，由 Executor 在逐条执行时解析。
     */
    private String injectTokenToVariables(String collectionJson, String token) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(collectionJson);
            ArrayNode variables = (ArrayNode) root.get("variable");

            boolean found = false;
            if (variables != null) {
                for (int i = 0; i < variables.size(); i++) {
                    ObjectNode var = (ObjectNode) variables.get(i);
                    if ("token".equals(var.get("key").asText())) {
                        var.put("value", token);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                if (variables == null) {
                    variables = MAPPER.createArrayNode();
                    root.set("variable", variables);
                }
                ObjectNode tokenVar = variables.addObject();
                tokenVar.put("key", "token");
                tokenVar.put("value", token);
            }

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Token 注入失败: " + e.getMessage(), e);
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
