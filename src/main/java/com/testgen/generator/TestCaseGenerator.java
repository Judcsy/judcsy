package com.testgen.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testgen.model.testcase.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * 测试用例生成引擎
 * 基于PRD解析结果自动生成结构化测试用例，支持前端、后端、协同场景的完整覆盖
 */
public class TestCaseGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TestCaseGenerator.class);
    
    private List<TestCase> testCases = new ArrayList<>();
    private int caseCounter = 0;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 从PRD解析数据生成测试用例
     * 
     * @param prdData PRD解析器输出的结构化数据
     * @return 测试用例列表
     */
    public List<TestCase> generateFromPRDData(Map<String, Object> prdData) {
        logger.info("开始生成测试用例");
        testCases.clear();
        
        // 1. 生成前端测试用例
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frontendModules = 
            (List<Map<String, Object>>) prdData.getOrDefault("frontendModules", new ArrayList<>());
        
        for (Map<String, Object> feModule : frontendModules) {
            generateFrontendCases(feModule);
        }
        
        // 2. 生成后端测试用例
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> backendModules = 
            (List<Map<String, Object>>) prdData.getOrDefault("backendModules", new ArrayList<>());
        
        for (Map<String, Object> beModule : backendModules) {
            generateBackendCases(beModule);
        }
        
        // 3. 生成协同测试用例
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> crossModuleLogics = 
            (List<Map<String, Object>>) prdData.getOrDefault("crossModuleLogics", new ArrayList<>());
        
        for (Map<String, Object> crossLogic : crossModuleLogics) {
            generateIntegrationCases(crossLogic, prdData);
        }
        
        logger.info("测试用例生成完成，共生成 {} 个用例", testCases.size());
        return testCases;
    }
    
    /**
     * 生成测试用例ID
     */
    private String generateCaseId() {
        caseCounter++;
        return String.format("TC_%04d", caseCounter);
    }
    
    /**
     * 生成前端测试用例
     */
    private void generateFrontendCases(Map<String, Object> feModule) {
        String moduleName = (String) feModule.getOrDefault("moduleName", "未命名模块");
        
        // 正常交互流程用例
        @SuppressWarnings("unchecked")
        List<String> interactionFlow = (List<String>) feModule.get("interactionFlow");
        if (interactionFlow != null && !interactionFlow.isEmpty()) {
            TestCase normalCase = createFrontendNormalCase(feModule);
            testCases.add(normalCase);
        }
        
        // 输入校验用例
        @SuppressWarnings("unchecked")
        List<String> validationRules = (List<String>) feModule.get("validationRules");
        if (validationRules != null && !validationRules.isEmpty()) {
            List<TestCase> validationCases = createFrontendValidationCases(feModule);
            testCases.addAll(validationCases);
        }
        
        // 视觉反馈用例
        @SuppressWarnings("unchecked")
        List<String> visualFeedback = (List<String>) feModule.get("visualFeedback");
        if (visualFeedback != null && !visualFeedback.isEmpty()) {
            TestCase feedbackCase = createFrontendFeedbackCase(feModule);
            testCases.add(feedbackCase);
        }
    }
    
    /**
     * 创建前端正常流程用例
     */
    private TestCase createFrontendNormalCase(Map<String, Object> feModule) {
        String moduleName = (String) feModule.getOrDefault("moduleName", "未命名模块");
        
        @SuppressWarnings("unchecked")
        List<String> interactionFlow = (List<String>) feModule.getOrDefault("interactionFlow", new ArrayList<>());
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> pageElements = 
            (List<Map<String, String>>) feModule.getOrDefault("pageElements", new ArrayList<>());
        
        // 构建前端操作步骤
        List<FrontEndStep> frontEndSteps = new ArrayList<>();
        for (int i = 0; i < interactionFlow.size(); i++) {
            String flowStep = interactionFlow.get(i);
            
            // 解析操作类型和目标元素
            String[] actionAndElement = parseInteractionStep(flowStep, pageElements);
            String action = actionAndElement[0];
            String element = actionAndElement[1];
            
            // 生成元素定位符
            Map<String, String> locator = generateLocator(element, pageElements);
            
            FrontEndStep step = new FrontEndStep();
            step.setStepNumber(i + 1);
            step.setAction(action);
            step.setElement(element);
            step.setLocator(locator);
            frontEndSteps.add(step);
        }
        
        // 构建预期结果
        @SuppressWarnings("unchecked")
        List<String> visualFeedback = (List<String>) feModule.get("visualFeedback");
        List<String> frontEndExpected = visualFeedback != null && !visualFeedback.isEmpty() 
            ? visualFeedback 
            : Arrays.asList("操作成功，页面正常显示");
        
        // 构建断言规则
        List<AssertRule> assertRules = new ArrayList<>();
        AssertRule rule = new AssertRule();
        rule.setAssertType("exists");
        rule.setTarget("success_indicator");
        rule.setExpectedValue(true);
        rule.setDescription("验证操作成功标识存在");
        assertRules.add(rule);
        
        // 构建测试用例
        TestCase testCase = new TestCase();
        testCase.setCaseId(generateCaseId());
        testCase.setModule(moduleName);
        testCase.setSceneType(SceneType.FRONTEND);
        testCase.setTitle(moduleName + "_正常交互流程");
        testCase.setPreCondition(Arrays.asList("用户已打开页面", "页面加载完成"));
        testCase.setFrontEndSteps(frontEndSteps);
        testCase.setBackEndSteps(new ArrayList<>());
        testCase.setFrontEndExpected(frontEndExpected);
        testCase.setBackEndExpected(new ArrayList<>());
        testCase.setAssertRules(assertRules);
        testCase.setPriority("P0");
        testCase.setTags(Arrays.asList("前端", "正常流程", moduleName));
        
        return testCase;
    }
    
    /**
     * 创建前端校验用例
     */
    private List<TestCase> createFrontendValidationCases(Map<String, Object> feModule) {
        List<TestCase> cases = new ArrayList<>();
        String moduleName = (String) feModule.getOrDefault("moduleName", "未命名模块");
        
        @SuppressWarnings("unchecked")
        List<String> validationRules = (List<String>) feModule.getOrDefault("validationRules", new ArrayList<>());
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> pageElements = 
            (List<Map<String, String>>) feModule.getOrDefault("pageElements", new ArrayList<>());
        
        for (String rule : validationRules) {
            // 为每个校验规则生成一个测试用例
            List<FrontEndStep> steps = new ArrayList<>();
            
            FrontEndStep inputStep = new FrontEndStep();
            inputStep.setStepNumber(1);
            inputStep.setAction("input");
            inputStep.setElement("输入框");
            inputStep.setValue("invalid_value");
            Map<String, String> locator1 = new HashMap<>();
            locator1.put("type", "id");
            locator1.put("value", "input_field");
            inputStep.setLocator(locator1);
            steps.add(inputStep);
            
            FrontEndStep clickStep = new FrontEndStep();
            clickStep.setStepNumber(2);
            clickStep.setAction("click");
            clickStep.setElement("提交按钮");
            Map<String, String> locator2 = new HashMap<>();
            locator2.put("type", "id");
            locator2.put("value", "submit_btn");
            clickStep.setLocator(locator2);
            steps.add(clickStep);
            
            List<AssertRule> assertRules = new ArrayList<>();
            AssertRule assertRule = new AssertRule();
            assertRule.setAssertType("contains");
            assertRule.setTarget("error_message");
            assertRule.setExpectedValue(rule);
            assertRule.setDescription("验证校验错误提示正确显示");
            assertRules.add(assertRule);
            
            TestCase testCase = new TestCase();
            testCase.setCaseId(generateCaseId());
            testCase.setModule(moduleName);
            testCase.setSceneType(SceneType.EXCEPTION);
            testCase.setTitle(moduleName + "_输入校验_" + rule);
            testCase.setPreCondition(Arrays.asList("用户已打开页面"));
            testCase.setFrontEndSteps(steps);
            testCase.setBackEndSteps(new ArrayList<>());
            testCase.setFrontEndExpected(Arrays.asList("显示校验错误提示：" + rule));
            testCase.setBackEndExpected(new ArrayList<>());
            testCase.setAssertRules(assertRules);
            testCase.setPriority("P1");
            testCase.setTags(Arrays.asList("前端", "输入校验", moduleName));
            
            cases.add(testCase);
        }
        
        return cases;
    }
    
    /**
     * 创建前端视觉反馈用例
     */
    private TestCase createFrontendFeedbackCase(Map<String, Object> feModule) {
        String moduleName = (String) feModule.getOrDefault("moduleName", "未命名模块");
        
        @SuppressWarnings("unchecked")
        List<String> visualFeedback = (List<String>) feModule.getOrDefault("visualFeedback", new ArrayList<>());
        
        List<FrontEndStep> steps = new ArrayList<>();
        FrontEndStep step = new FrontEndStep();
        step.setStepNumber(1);
        step.setAction("trigger");
        step.setElement("触发元素");
        Map<String, String> locator = new HashMap<>();
        locator.put("type", "id");
        locator.put("value", "trigger_element");
        step.setLocator(locator);
        steps.add(step);
        
        List<AssertRule> assertRules = new ArrayList<>();
        AssertRule rule = new AssertRule();
        rule.setAssertType("visible");
        rule.setTarget("feedback_element");
        rule.setExpectedValue(true);
        rule.setDescription("验证视觉反馈元素可见");
        assertRules.add(rule);
        
        TestCase testCase = new TestCase();
        testCase.setCaseId(generateCaseId());
        testCase.setModule(moduleName);
        testCase.setSceneType(SceneType.FRONTEND);
        testCase.setTitle(moduleName + "_视觉反馈验证");
        testCase.setPreCondition(Arrays.asList("用户已打开页面"));
        testCase.setFrontEndSteps(steps);
        testCase.setBackEndSteps(new ArrayList<>());
        testCase.setFrontEndExpected(visualFeedback);
        testCase.setBackEndExpected(new ArrayList<>());
        testCase.setAssertRules(assertRules);
        testCase.setPriority("P2");
        testCase.setTags(Arrays.asList("前端", "视觉反馈", moduleName));
        
        return testCase;
    }
    
    /**
     * 生成后端测试用例
     */
    private void generateBackendCases(Map<String, Object> beModule) {
        String moduleName = (String) beModule.getOrDefault("moduleName", "未命名模块");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> interfaces = 
            (List<Map<String, Object>>) beModule.getOrDefault("interfaces", new ArrayList<>());
        
        for (Map<String, Object> interfaceDef : interfaces) {
            // 正常请求用例
            TestCase normalCase = createBackendNormalCase(beModule, interfaceDef);
            testCases.add(normalCase);
            
            // 参数异常用例
            List<TestCase> paramErrorCases = createBackendParamErrorCases(beModule, interfaceDef);
            testCases.addAll(paramErrorCases);
        }
        
        // 业务规则校验用例
        @SuppressWarnings("unchecked")
        List<String> businessRules = (List<String>) beModule.get("businessRules");
        if (businessRules != null && !businessRules.isEmpty()) {
            List<TestCase> businessCases = createBackendBusinessCases(beModule);
            testCases.addAll(businessCases);
        }
    }
    
    /**
     * 创建后端正常请求用例
     */
    private TestCase createBackendNormalCase(Map<String, Object> beModule, Map<String, Object> interfaceDef) {
        String moduleName = (String) beModule.getOrDefault("moduleName", "未命名模块");
        String method = (String) interfaceDef.get("method");
        String path = (String) interfaceDef.get("path");
        
        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) interfaceDef.getOrDefault("params", new HashMap<>());
        
        List<BackEndStep> steps = new ArrayList<>();
        BackEndStep step = new BackEndStep();
        step.setStepNumber(1);
        step.setAction("api_call");
        step.setApiPath(path);
        step.setMethod(method);
        step.setParams(new HashMap<>(params));
        step.setExpectedCode(200);
        steps.add(step);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) interfaceDef.getOrDefault("response", new HashMap<>());
        
        List<AssertRule> assertRules = new ArrayList<>();
        AssertRule statusRule = new AssertRule();
        statusRule.setAssertType("status_code");
        statusRule.setTarget("response");
        statusRule.setExpectedValue(200);
        statusRule.setDescription("验证响应状态码为200");
        assertRules.add(statusRule);
        
        AssertRule schemaRule = new AssertRule();
        schemaRule.setAssertType("schema");
        schemaRule.setTarget("response.data");
        schemaRule.setExpectedValue(response);
        schemaRule.setDescription("验证响应数据结构正确");
        assertRules.add(schemaRule);
        
        TestCase testCase = new TestCase();
        testCase.setCaseId(generateCaseId());
        testCase.setModule(moduleName);
        testCase.setSceneType(SceneType.BACKEND);
        testCase.setTitle(moduleName + "_" + method + "_" + path + "_正常请求");
        testCase.setPreCondition(Arrays.asList("后端服务正常运行", "测试数据已准备"));
        testCase.setFrontEndSteps(new ArrayList<>());
        testCase.setBackEndSteps(steps);
        testCase.setFrontEndExpected(new ArrayList<>());
        testCase.setBackEndExpected(Arrays.asList("返回状态码200", "返回数据结构正确", "返回数据内容符合预期"));
        testCase.setAssertRules(assertRules);
        testCase.setPriority("P0");
        testCase.setTags(Arrays.asList("后端", "正常请求", moduleName));
        
        return testCase;
    }
    
    /**
     * 创建后端参数异常用例
     */
    private List<TestCase> createBackendParamErrorCases(Map<String, Object> beModule, Map<String, Object> interfaceDef) {
        List<TestCase> cases = new ArrayList<>();
        String moduleName = (String) beModule.getOrDefault("moduleName", "未命名模块");
        String method = (String) interfaceDef.get("method");
        String path = (String) interfaceDef.get("path");
        
        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) interfaceDef.getOrDefault("params", new HashMap<>());
        
        // 为每个参数生成缺失/错误类型用例
        for (String paramName : params.keySet()) {
            // 参数缺失用例
            Map<String, Object> missingParams = new HashMap<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!entry.getKey().equals(paramName)) {
                    missingParams.put(entry.getKey(), entry.getValue());
                }
            }
            
            List<BackEndStep> steps = new ArrayList<>();
            BackEndStep step = new BackEndStep();
            step.setStepNumber(1);
            step.setAction("api_call");
            step.setApiPath(path);
            step.setMethod(method);
            step.setParams(missingParams);
            step.setExpectedCode(400);
            steps.add(step);
            
            List<AssertRule> assertRules = new ArrayList<>();
            AssertRule statusRule = new AssertRule();
            statusRule.setAssertType("status_code");
            statusRule.setTarget("response");
            statusRule.setExpectedValue(400);
            statusRule.setDescription("验证返回400错误");
            assertRules.add(statusRule);
            
            AssertRule messageRule = new AssertRule();
            messageRule.setAssertType("contains");
            messageRule.setTarget("response.message");
            messageRule.setExpectedValue(paramName);
            messageRule.setDescription("验证错误信息包含参数名" + paramName);
            assertRules.add(messageRule);
            
            TestCase testCase = new TestCase();
            testCase.setCaseId(generateCaseId());
            testCase.setModule(moduleName);
            testCase.setSceneType(SceneType.EXCEPTION);
            testCase.setTitle(moduleName + "_" + path + "_参数" + paramName + "缺失");
            testCase.setPreCondition(Arrays.asList("后端服务正常运行"));
            testCase.setFrontEndSteps(new ArrayList<>());
            testCase.setBackEndSteps(steps);
            testCase.setFrontEndExpected(new ArrayList<>());
            testCase.setBackEndExpected(Arrays.asList("返回状态码400", "返回错误信息提示参数" + paramName + "缺失"));
            testCase.setAssertRules(assertRules);
            testCase.setPriority("P1");
            testCase.setTags(Arrays.asList("后端", "参数校验", moduleName));
            
            cases.add(testCase);
        }
        
        return cases;
    }
    
    /**
     * 创建后端业务规则用例
     */
    private List<TestCase> createBackendBusinessCases(Map<String, Object> beModule) {
        List<TestCase> cases = new ArrayList<>();
        String moduleName = (String) beModule.getOrDefault("moduleName", "未命名模块");
        
        @SuppressWarnings("unchecked")
        List<String> businessRules = (List<String>) beModule.getOrDefault("businessRules", new ArrayList<>());
        
        for (String rule : businessRules) {
            List<BackEndStep> steps = new ArrayList<>();
            BackEndStep step = new BackEndStep();
            step.setStepNumber(1);
            step.setAction("verify_business_rule");
            Map<String, Object> stepParams = new HashMap<>();
            stepParams.put("rule", rule);
            step.setParams(stepParams);
            steps.add(step);
            
            List<AssertRule> assertRules = new ArrayList<>();
            AssertRule assertRule = new AssertRule();
            assertRule.setAssertType("business_rule");
            assertRule.setTarget("result");
            assertRule.setExpectedValue(true);
            assertRule.setDescription("验证业务规则：" + rule);
            assertRules.add(assertRule);
            
            TestCase testCase = new TestCase();
            testCase.setCaseId(generateCaseId());
            testCase.setModule(moduleName);
            testCase.setSceneType(SceneType.BACKEND);
            testCase.setTitle(moduleName + "_业务规则_" + rule);
            testCase.setPreCondition(Arrays.asList("后端服务正常运行", "测试数据满足业务规则条件"));
            testCase.setFrontEndSteps(new ArrayList<>());
            testCase.setBackEndSteps(steps);
            testCase.setFrontEndExpected(new ArrayList<>());
            testCase.setBackEndExpected(Arrays.asList("业务规则校验通过：" + rule));
            testCase.setAssertRules(assertRules);
            testCase.setPriority("P1");
            testCase.setTags(Arrays.asList("后端", "业务规则", moduleName));
            
            cases.add(testCase);
        }
        
        return cases;
    }
    
    /**
     * 生成前后端协同测试用例
     */
    private void generateIntegrationCases(Map<String, Object> crossLogic, Map<String, Object> prdData) {
        String name = (String) crossLogic.getOrDefault("name", "协同测试");
        String frontendModule = (String) crossLogic.get("frontendModule");
        String backendModule = (String) crossLogic.get("backendModule");
        
        List<FrontEndStep> frontEndSteps = new ArrayList<>();
        FrontEndStep feStep = new FrontEndStep();
        feStep.setStepNumber(1);
        feStep.setAction("submit_form");
        feStep.setElement("表单");
        Map<String, String> feLocator = new HashMap<>();
        feLocator.put("type", "id");
        feLocator.put("value", "form");
        feStep.setLocator(feLocator);
        frontEndSteps.add(feStep);
        
        List<BackEndStep> backEndSteps = new ArrayList<>();
        BackEndStep receiveStep = new BackEndStep();
        receiveStep.setStepNumber(1);
        receiveStep.setAction("receive_request");
        receiveStep.setApiPath("/api/endpoint");
        receiveStep.setMethod("POST");
        backEndSteps.add(receiveStep);
        
        BackEndStep processStep = new BackEndStep();
        processStep.setStepNumber(2);
        processStep.setAction("process_data");
        backEndSteps.add(processStep);
        
        BackEndStep responseStep = new BackEndStep();
        responseStep.setStepNumber(3);
        responseStep.setAction("send_response");
        responseStep.setExpectedCode(200);
        backEndSteps.add(responseStep);
        
        List<AssertRule> assertRules = new ArrayList<>();
        AssertRule consistencyRule = new AssertRule();
        consistencyRule.setAssertType("data_consistency");
        consistencyRule.setTarget("frontend_backend_data");
        consistencyRule.setExpectedValue(true);
        consistencyRule.setDescription("验证前后端数据一致性");
        assertRules.add(consistencyRule);
        
        AssertRule syncRule = new AssertRule();
        syncRule.setAssertType("state_sync");
        syncRule.setTarget("frontend_state");
        syncRule.setExpectedValue("synced");
        syncRule.setDescription("验证前端状态同步正确");
        assertRules.add(syncRule);
        
        TestCase testCase = new TestCase();
        testCase.setCaseId(generateCaseId());
        testCase.setModule(name);
        testCase.setSceneType(SceneType.INTEGRATION);
        testCase.setTitle(frontendModule + "与" + backendModule + "协同测试");
        testCase.setPreCondition(Arrays.asList("前端页面已打开", "后端服务正常运行", "前后端网络连接正常"));
        testCase.setFrontEndSteps(frontEndSteps);
        testCase.setBackEndSteps(backEndSteps);
        testCase.setFrontEndExpected(Arrays.asList("前端接收到后端响应", "页面状态根据响应更新", "用户看到操作结果反馈"));
        testCase.setBackEndExpected(Arrays.asList("后端成功接收前端请求", "数据处理正确", "返回正确的响应数据"));
        testCase.setAssertRules(assertRules);
        testCase.setPriority("P0");
        testCase.setTags(Arrays.asList("协同测试", frontendModule, backendModule));
        
        testCases.add(testCase);
    }
    
    /**
     * 解析交互步骤，提取操作和元素
     */
    private String[] parseInteractionStep(String flowStep, List<Map<String, String>> pageElements) {
        String action = "unknown";
        String element = flowStep;
        
        if (flowStep.contains("点击") || flowStep.toLowerCase().contains("click")) {
            action = "click";
            element = flowStep.replace("点击", "").trim();
        } else if (flowStep.contains("输入") || flowStep.toLowerCase().contains("input")) {
            action = "input";
            element = flowStep.replace("输入", "").trim();
        } else if (flowStep.contains("选择") || flowStep.toLowerCase().contains("select")) {
            action = "select";
            element = flowStep.replace("选择", "").trim();
        } else if (flowStep.contains("提交") || flowStep.toLowerCase().contains("submit")) {
            action = "submit";
            element = flowStep.replace("提交", "").trim();
        }
        
        return new String[]{action, element};
    }
    
    /**
     * 生成元素定位符
     */
    private Map<String, String> generateLocator(String element, List<Map<String, String>> pageElements) {
        // 在页面元素列表中查找匹配元素
        for (Map<String, String> pageElement : pageElements) {
            String label = pageElement.get("label");
            if (label != null && element.contains(label)) {
                Map<String, String> locator = new HashMap<>();
                locator.put("type", "id");
                locator.put("value", pageElement.getOrDefault("id", "unknown"));
                return locator;
            }
        }
        
        // 默认定位符
        Map<String, String> locator = new HashMap<>();
        locator.put("type", "xpath");
        locator.put("value", "//*[contains(text(), '" + element + "')]");
        return locator;
    }
    
    /**
     * 导出测试用例为JSON格式
     */
    public void exportToJson(String outputPath) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
                   .writeValue(new File(outputPath), testCases);
        logger.info("测试用例已导出到: {}", outputPath);
    }
    
    /**
     * 导出测试用例为Markdown表格格式
     */
    public void exportToMarkdown(String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("# 测试用例集\n\n");
            
            // 按模块分组
            Map<String, List<TestCase>> modulesCases = new HashMap<>();
            for (TestCase testCase : testCases) {
                String module = testCase.getModule();
                modulesCases.computeIfAbsent(module, k -> new ArrayList<>()).add(testCase);
            }
            
            // 逐模块输出
            for (Map.Entry<String, List<TestCase>> entry : modulesCases.entrySet()) {
                String module = entry.getKey();
                List<TestCase> cases = entry.getValue();
                
                writer.write("## " + module + "\n\n");
                writer.write("| 用例ID | 标题 | 场景类型 | 优先级 | 前置条件 | 操作步骤 | 预期结果 |\n");
                writer.write("|--------|------|----------|--------|----------|----------|----------|\n");
                
                for (TestCase testCase : cases) {
                    // 前置条件
                    String preCondStr = String.join("<br>", testCase.getPreCondition());
                    
                    // 操作步骤（合并前后端）
                    List<String> stepsList = new ArrayList<>();
                    for (FrontEndStep step : testCase.getFrontEndSteps()) {
                        stepsList.add("【前端】" + step.getStepNumber() + ". " + step.getAction() + " " + step.getElement());
                    }
                    for (BackEndStep step : testCase.getBackEndSteps()) {
                        stepsList.add("【后端】" + step.getStepNumber() + ". " + step.getAction() + " " + 
                                    (step.getApiPath() != null ? step.getApiPath() : ""));
                    }
                    String stepsStr = String.join("<br>", stepsList);
                    
                    // 预期结果（合并前后端）
                    List<String> expectedList = new ArrayList<>();
                    expectedList.addAll(testCase.getFrontEndExpected());
                    expectedList.addAll(testCase.getBackEndExpected());
                    String expectedStr = String.join("<br>", expectedList);
                    
                    writer.write(String.format("| %s | %s | %s | %s | %s | %s | %s |\n",
                        testCase.getCaseId(),
                        testCase.getTitle(),
                        testCase.getSceneType().getValue(),
                        testCase.getPriority(),
                        preCondStr,
                        stepsStr,
                        expectedStr
                    ));
                }
                
                writer.write("\n");
            }
        }
        logger.info("测试用例Markdown文档已导出到: {}", outputPath);
    }
    
    public List<TestCase> getTestCases() {
        return testCases;
    }
}
