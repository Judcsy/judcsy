package com.testgen.model.testcase;

import java.util.Map;

/**
 * 前端操作步骤
 */
public class FrontEndStep {
    /**
     * 步骤编号
     */
    private int stepNumber;
    
    /**
     * 操作类型: click, input, select, submit等
     */
    private String action;
    
    /**
     * 元素标识
     */
    private String element;
    
    /**
     * 输入值（如果有）
     */
    private String value;
    
    /**
     * 元素定位符 {type: "id", value: "xxx"}
     */
    private Map<String, String> locator;
    
    public FrontEndStep() {}
    
    public FrontEndStep(int stepNumber, String action, String element, String value, Map<String, String> locator) {
        this.stepNumber = stepNumber;
        this.action = action;
        this.element = element;
        this.value = value;
        this.locator = locator;
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
    
    public String getElement() {
        return element;
    }
    
    public void setElement(String element) {
        this.element = element;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public Map<String, String> getLocator() {
        return locator;
    }
    
    public void setLocator(Map<String, String> locator) {
        this.locator = locator;
    }
}
