package com.example.model;

/**
 * 自动化配置项。
 *
 * 认证方式：
 *  - NONE: 无需认证
 *  - LOGIN: 先登录获取 Token，再执行测试
 */
public class AutomationConfig {

    private String authType = "NONE";          // NONE | LOGIN
    private String loginUrl = "";              // 登录接口地址
    private String username = "";              // 登录用户名
    private String password = "";              // 登录密码
    private String tokenField = "token";       // Token 在响应中的字段路径（如 token / data.token / accessToken）

    public AutomationConfig() {}

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getLoginUrl() { return loginUrl; }
    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getTokenField() { return tokenField; }
    public void setTokenField(String tokenField) { this.tokenField = tokenField; }
}
