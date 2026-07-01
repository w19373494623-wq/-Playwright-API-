package com.example.smoke.model;

import java.util.List;
import java.util.Map;

/**
 * Postman Collection v2.1 顶层模型
 */
public class PostmanCollection {

    private Info info;
    private List<Map<String, String>> variable;
    private List<PostmanItem> item;

    public Info getInfo() { return info; }
    public void setInfo(Info info) { this.info = info; }

    public List<Map<String, String>> getVariable() { return variable; }
    public void setVariable(List<Map<String, String>> variable) { this.variable = variable; }

    public List<PostmanItem> getItem() { return item; }
    public void setItem(List<PostmanItem> item) { this.item = item; }

    public static class Info {
        private String name;
        private String schema;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
    }
}
