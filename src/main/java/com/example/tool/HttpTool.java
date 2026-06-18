package com.example.tool;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class HttpTool {

    private final RestTemplate restTemplate;

    private static final Map<String, String> API_MAP = new HashMap<>();

    static {
        API_MAP.put("登录", "POST /api/login");
        API_MAP.put("用户信息", "GET /api/user/info");
        API_MAP.put("帖子列表", "GET /api/post/list");
    }

    public HttpTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Tool("根据接口中文名称查询对应的HTTP方法和URL路径，例如：登录 -> POST /api/login")
    public String getApiInfo(String apiName) {
        return apiName + " -> " + API_MAP.getOrDefault(apiName, "未找到该接口");
    }

    @Tool("发送HTTP请求并返回响应内容，支持GET和POST等请求方法，参数method为请求方法，url为完整请求地址")
    public String sendRequest(String method, String url) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.valueOf(method.toUpperCase()),
                    null,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            return "请求失败：" + e.getMessage();
        }
    }
}
