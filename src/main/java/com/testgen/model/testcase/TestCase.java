package com.testgen.model.testcase;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用例
 */
public class TestCase {
    /**
     * 用例ID
     */
    private String caseId;
    
    /**
     * 所属模块
     */
    private String module;
    
    /**
     * 场景类型
     */
    private SceneType sceneType;
    
    /**
     * 用例标题
     */
    private String title;
    
    /**
     * 前置条件
     */
    private List<String> preCondition = new ArrayList<>();
    
    /**
     * 前端操作步骤
     */
    private List<FrontEndStep> frontEndSteps = new ArrayList<>();
    
    /**
     * 后端操作步骤
     */
    private List<BackEndStep> backEndSteps = new ArrayList<>();
    
    /**
     * 前端预期结果
     */
    private List<String> frontEndExpected = new ArrayList<>();
    
    /**
     * 后端预期结果
     */
    private List<String> backEndExpected = new ArrayList<>();
    
    /**
     * 断言规则
     */
    private List<AssertRule> assertRules = new ArrayList<>();
    
    /**
     * 优先级: P0, P1, P2
     */
    private String priority = "P1";
    
    /**
     * 标签
     */
    private List<String> tags = new ArrayList<>();
    
    public TestCase() {}
    
    // Getters and Setters
    public String getCaseId() {
        return caseId;
    }
    
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }
    
    public String getModule() {
        return module;
    }
    
    public void setModule(String module) {
        this.module = module;
    }
    
    public SceneType getSceneType() {
        return sceneType;
    }
    
    public void setSceneType(SceneType sceneType) {
        this.sceneType = sceneType;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public List<String> getPreCondition() {
        return preCondition;
    }
    
    public void setPreCondition(List<String> preCondition) {
        this.preCondition = preCondition;
    }
    
    public List<FrontEndStep> getFrontEndSteps() {
        return frontEndSteps;
    }
    
    public void setFrontEndSteps(List<FrontEndStep> frontEndSteps) {
        this.frontEndSteps = frontEndSteps;
    }
    
    public List<BackEndStep> getBackEndSteps() {
        return backEndSteps;
    }
    
    public void setBackEndSteps(List<BackEndStep> backEndSteps) {
        this.backEndSteps = backEndSteps;
    }
    
    public List<String> getFrontEndExpected() {
        return frontEndExpected;
    }
    
    public void setFrontEndExpected(List<String> frontEndExpected) {
        this.frontEndExpected = frontEndExpected;
    }
    
    public List<String> getBackEndExpected() {
        return backEndExpected;
    }
    
    public void setBackEndExpected(List<String> backEndExpected) {
        this.backEndExpected = backEndExpected;
    }
    
    public List<AssertRule> getAssertRules() {
        return assertRules;
    }
    
    public void setAssertRules(List<AssertRule> assertRules) {
        this.assertRules = assertRules;
    }
    
    public String getPriority() {
        return priority;
    }
    
    public void setPriority(String priority) {
        this.priority = priority;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
