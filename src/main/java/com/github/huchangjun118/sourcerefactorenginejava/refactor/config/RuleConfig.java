package com.github.huchangjun118.sourcerefactorenginejava.refactor.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration of a single rewrite rule.
 * Supported types: rename-method, rename-field, rename-type.
 */
public class RuleConfig {

    private String id;
    private String type;
    private int order;
    private Map<String, String> params = new LinkedHashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }
}
