package com.testgen.model.testcase;

import java.util.Map;

/**
 * 后端操作步骤
 */
public class BackEndStep {
    /**
     * 步骤编号
     */
    private int stepNumber;
    
    /**
     * 操作类型: api_call, db_query等
     */
    private String action;
    
    /**
     * API路径
     */
    private String apiPath;
    
    /**
     * 请求方法
     */
    private String method;
    
    /**
     * 请求参数
     */
    private Map<String, Object> params;
    
    /**
     * 期望的HTTP状态码
     */
    private Integer expectedCode;
    
    public BackEndStep() {}
    
    public BackEndStep(int stepNumber, String action, String apiPath, String method, 
                       Map<String, Object> params, Integer expectedCode) {
        this.stepNumber = stepNumber;
        this.action = action;
        this.apiPath = apiPath;
        this.method = method;
        this.params = params;
        this.expectedCode = expectedCode;
    }
    
    public int getStepNumber() {
        return stepNumber;
    }
    
    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getApiPath() {
        return apiPath;
    }
    
    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public Map<String, Object> getParams() {
        return params;
    }
    
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
    
    public Integer getExpectedCode() {
        return expectedCode;
    }
    
    public void setExpectedCode(Integer expectedCode) {
        this.expectedCode = expectedCode;
    }
}
