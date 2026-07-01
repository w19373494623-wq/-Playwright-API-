package com.example.smoke.executor;

import com.example.smoke.model.PostmanItem;
import com.example.smoke.model.SmokeTestResult;

import java.util.List;
import java.util.Map;

/**
 * HTTP 请求执行器接口。
 * 扩展点：可实现 Token 自动刷新、重试、代理等。
 */
public interface HttpExecutor {

    /**
     * 执行单个接口的 HTTP 请求。
     *
     * @param item      Postman 接口条目
     * @param variables 环境变量列表（用于替换 {{var}}）
     * @return 测试结果
     */
    SmokeTestResult execute(PostmanItem item, List<Map<String, String>> variables);
}
