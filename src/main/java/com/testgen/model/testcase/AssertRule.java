package com.testgen.model.testcase;

/**
 * 断言规则
 */
public class AssertRule {
    /**
     * 断言类型: equals, contains, exists, status_code等
     */
    private String assertType;
    
    /**
     * 断言目标
     */
    private String target;
    
    /**
     * 期望值
     */
    private Object expectedValue;
    
    /**
     * 断言描述
     */
    private String description;
    
    public AssertRule() {}
    
    public AssertRule(String assertType, String target, Object expectedValue, String description) {
        this.assertType = assertType;
        this.target = target;
        this.expectedValue = expectedValue;
        this.description = description;
    }
    
    public String getAssertType() {
        return assertType;
    }
    
    public void setAssertType(String assertType) {
        this.assertType = assertType;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public Object getExpectedValue() {
        return expectedValue;
    }
    
    public void setExpectedValue(Object expectedValue) {
        this.expectedValue = expectedValue;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}
