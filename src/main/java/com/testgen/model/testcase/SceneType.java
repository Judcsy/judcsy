package com.testgen.model.testcase;

/**
 * 测试场景类型
 */
public enum SceneType {
    /**
     * 前端场景
     */
    FRONTEND("frontend"),
    
    /**
     * 后端场景
     */
    BACKEND("backend"),
    
    /**
     * 前后端协同场景
     */
    INTEGRATION("integration"),
    
    /**
     * 异常场景
     */
    EXCEPTION("exception");
    
    private final String value;
    
    SceneType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}
