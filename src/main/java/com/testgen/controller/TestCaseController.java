package com.testgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testgen.parser.PRDParser;
import com.testgen.generator.TestCaseGenerator;
import com.testgen.model.testcase.TestCase;
import com.testgen.evaluation.TestCaseEvaluator;
import com.testgen.evaluation.TestCaseEvaluator.EvaluationResult;
import com.testgen.evaluation.CaseComparisonService;

import java.util.*;

/**
 * 测试用例生成REST API控制器
 * 提供Web界面调用的HTTP接口
 * 支持LLM生成和质量评测
 */
public class TestCaseController {

    private PRDParser prdParser;
    private TestCaseGenerator testCaseGenerator;
    private ObjectMapper objectMapper;
    private boolean useLLM;

    public TestCaseController() {
        this(false); // 默认不使用LLM
    }

    public TestCaseController(boolean useLLM) {
        this.useLLM = useLLM;
        this.prdParser = new PRDParser(useLLM);
        this.testCaseGenerator = new TestCaseGenerator();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 生成测试用例接口
     * POST /api/testcase/generate
     * 
     * @param requestBody JSON格式: {"prdText": "...", "useLLM": true, "enableEval":
     *                    false}
     * @return JSON格式响应
     */
    public String generateTestCases(String requestBody) {
        try {
            // 解析请求
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
            String prdText = (String) request.get("prdText");
            Boolean useLLM = request.containsKey("useLLM") ? (Boolean) request.get("useLLM") : this.useLLM;
            Boolean enableEval = request.containsKey("enableEval") ? (Boolean) request.get("enableEval") : false;

            if (prdText == null || prdText.trim().isEmpty()) {
                return buildErrorResponse("PRD内容不能为空");
            }

            // 根据请求决定是否使用LLM
            PRDParser parser = new PRDParser(useLLM);

            // 1. 解析PRD
            Map<String, Object> prdData = parser.parsePRD(prdText);

            // 2. 生成测试用例
            List<TestCase> testCases = testCaseGenerator.generateFromPRDData(prdData);

            // 3. 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", useLLM ? "使用火山引擎大模型生成成功" : "测试用例生成成功");
            response.put("testCases", testCases);
            response.put("count", testCases.size());
            response.put("useLLM", useLLM);

            // 统计信息
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("total", testCases.size());
            statistics.put("frontend", testCases.stream()
                    .filter(tc -> "FRONTEND".equals(tc.getSceneType().name())).count());
            statistics.put("backend", testCases.stream()
                    .filter(tc -> "BACKEND".equals(tc.getSceneType().name())).count());
            statistics.put("integration", testCases.stream()
                    .filter(tc -> "INTEGRATION".equals(tc.getSceneType().name())).count());
            statistics.put("exception", testCases.stream()
                    .filter(tc -> "EXCEPTION".equals(tc.getSceneType().name())).count());

            response.put("statistics", statistics);

            // 4. 如果启用评测，执行质量评估
            if (enableEval && testCases.size() > 0) {
                try {
                    TestCaseEvaluator evaluator = new TestCaseEvaluator();
                    EvaluationResult evalResult = evaluator.evaluate(testCases);

                    // 直接返回完整的评测结果对象
                    response.put("evaluation", evalResult);

                    response.put("message",
                            useLLM ? String.format("使用火山引擎大模型生成成功，质量评分: %.1f", evalResult.getTotalScore())
                                    : String.format("测试用例生成成功，质量评分: %.1f", evalResult.getTotalScore()));
                } catch (Exception e) {
                    System.err.println("评测失败: " + e.getMessage());
                    e.printStackTrace();
                    // 评测失败不影响用例生成，继续返回
                }
            }

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            e.printStackTrace();
            return buildErrorResponse("生成失败: " + e.getMessage());
        }
    }

    /**
     * 对比人工用例与AI生成用例
     * POST /api/testcase/compare
     * 
     * @param requestBody JSON格式: {"aiCases": [...], "referenceCases": [...]}
     * @return JSON格式响应
     */
    public String compareTestCases(String requestBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aiCasesData = (List<Map<String, Object>>) request.get("aiCases");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> refCasesData = (List<Map<String, Object>>) request.get("referenceCases");

            // 获取PRD文本用于LLM评估上下文
            String prdText = (String) request.get("prdText");

            if (aiCasesData == null || aiCasesData.isEmpty()) {
                return buildErrorResponse("请先生成AI测试用例");
            }

            // 解析AI用例
            List<TestCase> aiCases = parseReferenceCases(aiCasesData);
            
            // 解析人工用例（可为空，支持独立评估模式）
            List<TestCase> referenceCases = new ArrayList<>();
            if (refCasesData != null && !refCasesData.isEmpty()) {
                referenceCases = parseReferenceCases(refCasesData);
            }
            
            // 根据是否有人工用例选择评估模式
            String evalMode = referenceCases.isEmpty() ? "独立评估" : "对比评分";
            System.out.println("[评估] 模式: " + evalMode + " - AI用例: " + aiCases.size() + "条，人工用例: " + referenceCases.size() + "条");

            // 初始化火山引擎LLM服务
            com.testgen.llm.VolcanoService volcanoService = null;
            try {
                // 始终尝试初始化火山引擎服务（它有默认配置）
                System.out.println("[对比] 正在初始化火山引擎LLM服务...");
                volcanoService = new com.testgen.llm.VolcanoService();
                System.out.println("[对比] 火山引擎LLM服务初始化成功");
            } catch (Exception e) {
                System.out.println("[对比] 火山引擎服务初始化失败，将使用启发式评分: " + e.getMessage());
                e.printStackTrace();
            }

            CaseComparisonService comparisonService = new CaseComparisonService(volcanoService);

            // 如果有PRD文本，使用带PRD上下文的对比方法
            CaseComparisonService.ComparisonResult result;
            if (prdText != null && !prdText.trim().isEmpty()) {
                System.out.println("[对比] 使用PRD上下文进行LLM评分...");
                result = comparisonService.compare(aiCases, referenceCases, prdText);
            } else {
                result = comparisonService.compare(aiCases, referenceCases);
            }

            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            if (referenceCases.isEmpty()) {
                response.put("message", String.format("独立评估完成，质量评分: %.1f", result.getTotalScore()));
            } else {
                response.put("message", String.format("对比评分完成，匹配度: %.1f%%", result.getTotalScore()));
            }
            response.put("result", result);

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            e.printStackTrace();
            return buildErrorResponse("对比评分失败: " + e.getMessage());
        }
    }

    /**
     * 解析人工标准用例
     */
    private List<TestCase> parseReferenceCases(List<Map<String, Object>> refCasesData) {
        List<TestCase> referenceCases = new ArrayList<>();

        for (Map<String, Object> caseData : refCasesData) {
            try {
                TestCase tc = new TestCase();

                // 基本信息
                tc.setCaseId((String) caseData.get("caseId"));
                tc.setModule((String) caseData.get("module"));
                tc.setTitle((String) caseData.get("title"));
                tc.setPriority((String) caseData.get("priority"));

                // 场景类型
                String sceneTypeStr = (String) caseData.get("sceneType");
                if (sceneTypeStr != null) {
                    try {
                        tc.setSceneType(com.testgen.model.testcase.SceneType.valueOf(sceneTypeStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        // 默认为 FRONTEND 或忽略
                        tc.setSceneType(com.testgen.model.testcase.SceneType.FRONTEND);
                    }
                }

                // 前置条件 - 安全类型转换
                Object preConditionObj = caseData.get("preCondition");
                if (preConditionObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> preCondition = (List<String>) preConditionObj;
                    tc.setPreCondition(preCondition);
                } else if (preConditionObj instanceof String) {
                    tc.setPreCondition(Collections.singletonList((String) preConditionObj));
                } else {
                    tc.setPreCondition(new ArrayList<>());
                }

                // 前端预期结果 - 安全类型转换
                Object frontEndExpectedObj = caseData.get("frontEndExpected");
                if (frontEndExpectedObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> frontEndExpected = (List<String>) frontEndExpectedObj;
                    tc.setFrontEndExpected(frontEndExpected);
                } else if (frontEndExpectedObj instanceof String) {
                    tc.setFrontEndExpected(Collections.singletonList((String) frontEndExpectedObj));
                } else {
                    tc.setFrontEndExpected(new ArrayList<>());
                }

                // 后端预期结果 - 安全类型转换
                Object backEndExpectedObj = caseData.get("backEndExpected");
                if (backEndExpectedObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> backEndExpected = (List<String>) backEndExpectedObj;
                    tc.setBackEndExpected(backEndExpected);
                } else if (backEndExpectedObj instanceof String) {
                    tc.setBackEndExpected(Collections.singletonList((String) backEndExpectedObj));
                } else {
                    tc.setBackEndExpected(new ArrayList<>());
                }

                // 标签 - 安全类型转换
                Object tagsObj = caseData.get("tags");
                if (tagsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) tagsObj;
                    tc.setTags(tags);
                } else if (tagsObj instanceof String) {
                    tc.setTags(Collections.singletonList((String) tagsObj));
                } else {
                    tc.setTags(new ArrayList<>());
                }

                // 前端步骤
                if (caseData.containsKey("frontEndSteps")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> frontStepsData = (List<Map<String, Object>>) caseData
                            .get("frontEndSteps");
                    List<com.testgen.model.testcase.FrontEndStep> frontSteps = new ArrayList<>();
                    for (Map<String, Object> stepData : frontStepsData) {
                        com.testgen.model.testcase.FrontEndStep step = new com.testgen.model.testcase.FrontEndStep();
                        if (stepData.get("stepNumber") instanceof Integer) {
                            step.setStepNumber((Integer) stepData.get("stepNumber"));
                        }
                        step.setAction((String) stepData.get("action"));
                        step.setElement((String) stepData.get("element"));
                        step.setValue((String) stepData.get("value"));
                        frontSteps.add(step);
                    }
                    tc.setFrontEndSteps(frontSteps);
                }

                // 后端步骤
                if (caseData.containsKey("backEndSteps")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> backStepsData = (List<Map<String, Object>>) caseData.get("backEndSteps");
                    List<com.testgen.model.testcase.BackEndStep> backSteps = new ArrayList<>();
                    for (Map<String, Object> stepData : backStepsData) {
                        com.testgen.model.testcase.BackEndStep step = new com.testgen.model.testcase.BackEndStep();
                        if (stepData.get("stepNumber") instanceof Integer) {
                            step.setStepNumber((Integer) stepData.get("stepNumber"));
                        }
                        step.setAction((String) stepData.get("action"));
                        step.setMethod((String) stepData.get("method"));
                        step.setApiPath((String) stepData.get("apiPath"));
                        backSteps.add(step);
                    }
                    tc.setBackEndSteps(backSteps);
                }

                // 断言规则
                if (caseData.containsKey("assertRules")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> assertRulesData = (List<Map<String, Object>>) caseData.get("assertRules");
                    List<com.testgen.model.testcase.AssertRule> assertRules = new ArrayList<>();
                    for (Map<String, Object> ruleData : assertRulesData) {
                        com.testgen.model.testcase.AssertRule rule = new com.testgen.model.testcase.AssertRule(
                                (String) ruleData.get("field"),
                                (String) ruleData.get("operator"),
                                (String) ruleData.get("expected"),
                                (String) ruleData.get("description"));
                        assertRules.add(rule);
                    }
                    tc.setAssertRules(assertRules);
                }

                referenceCases.add(tc);
            } catch (Exception e) {
                System.err.println("解析人工用例失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return referenceCases;
    }

    /**
     * 构建错误响应
     */
    private String buildErrorResponse(String errorMessage) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", errorMessage);
            response.put("testCases", Collections.emptyList());
            response.put("count", 0);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"系统错误\"}";
        }
    }
}
