package com.testgen.evaluation;

import com.testgen.model.testcase.TestCase;
import com.testgen.model.testcase.SceneType;
import com.testgen.model.testcase.FrontEndStep;
import com.testgen.model.testcase.BackEndStep;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试用例质量评估器 (高级测试工程师维度)
 * 核心理念：从高级测试工程师视角评估用例质量
 * 评测维度：缺陷发现(25%)|业务覆盖(25%)|可执行性(20%)|断言完整(15%)|规范性(15%)
 */
public class TestCaseEvaluator {

    /**
     * 评估结果
     */
    public static class EvaluationResult {
        // 1. 缺陷发现能力 (25%) - 原健壮性
        private double robustnessScore;
        private double boundaryScore; // 边界值得分
        private double exceptionScore; // 异常场景得分

        // 2. 业务覆盖度 (25%) - 原功能覆盖
        private double coverageScore;

        // 3. 可执行性 (20%) - 原用例质量
        private double qualityScore;
        private double stepDetailScore; // 步骤详细度

        // 4. 断言完整性 (15%)
        private double assertionScore;

        // 5. 规范性 (15%)
        private double standardScore;

        private double totalScore; // 总分 (0-100)
        private String summary; // 评测总结
        private Map<String, Object> details; // 详细指标
        private List<String> suggestions; // 优化建议

        public EvaluationResult() {
            this.details = new HashMap<>();
            this.suggestions = new ArrayList<>();
        }

        // Getters and Setters
        public double getCoverageScore() {
            return coverageScore;
        }

        public void setCoverageScore(double coverageScore) {
            this.coverageScore = coverageScore;
        }

        public double getRobustnessScore() {
            return robustnessScore;
        }

        public void setRobustnessScore(double robustnessScore) {
            this.robustnessScore = robustnessScore;
        }

        public double getBoundaryScore() {
            return boundaryScore;
        }

        public void setBoundaryScore(double boundaryScore) {
            this.boundaryScore = boundaryScore;
        }

        public double getExceptionScore() {
            return exceptionScore;
        }

        public void setExceptionScore(double exceptionScore) {
            this.exceptionScore = exceptionScore;
        }

        public double getQualityScore() {
            return qualityScore;
        }

        public void setQualityScore(double qualityScore) {
            this.qualityScore = qualityScore;
        }

        public double getAssertionScore() {
            return assertionScore;
        }

        public void setAssertionScore(double assertionScore) {
            this.assertionScore = assertionScore;
        }

        public double getStandardScore() {
            return standardScore;
        }

        public void setStandardScore(double standardScore) {
            this.standardScore = standardScore;
        }

        public double getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(double totalScore) {
            this.totalScore = totalScore;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }

        public List<String> getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(List<String> suggestions) {
            this.suggestions = suggestions;
        }

        public void addSuggestion(String suggestion) {
            if (!this.suggestions.contains(suggestion)) {
                this.suggestions.add(suggestion);
            }
        }
    }

    // 关键词库定义（扩充版）
    private static final Set<String> BOUNDARY_KEYWORDS = new HashSet<>(Arrays.asList(
            // 数值边界
            "最大", "最小", "超过", "低于", "超出", "极限", "limit", "max", "min",
            "上限", "下限", "最长", "最短", "临界", "阈值", "极值", "边界值",
            // 空值边界
            "空", "null", "零", "0", "负数", "超长", "为空", "empty", "边界",
            "空字符", "空格", "空白", "无", "不填", "留空", "未填写",
            // 长度边界
            "1位", "2位", "长度", "字符", "位数", "字数", "超长", "过短",
            // 数量边界
            "0个", "1个", "100", "1000", "满", "溢出", "越界"));

    private static final Set<String> EXCEPTION_KEYWORDS = new HashSet<>(Arrays.asList(
            // 错误状态
            "失败", "错误", "异常", "拒绝", "超时", "不存在", "无效", "非法",
            "error", "fail", "exception", "404", "500", "403", "401", "断网", "崩溃",
            // 权限相关
            "未授权", "无权限", "禁止", "拒绝访问", "未登录", "登录失效", "权限不足",
            // 验证相关
            "格式错误", "校验失败", "不匹配", "不正确", "验证码错误", "密码错误",
            // 状态相关
            "已删除", "已禁用", "已过期", "不可用", "已存在", "重复", "冲突",
            // 网络相关
            "网络异常", "连接失败", "请求失败", "服务器错误", "系统繁忙"));

    // 笼统/模糊步骤关键词（需要扣分）
    private static final Set<String> VAGUE_STEP_KEYWORDS = new HashSet<>(Arrays.asList(
            "填写表单", "测试功能", "检查结果", "验证功能", "操作页面",
            "执行测试", "进行操作", "完成操作", "查看页面", "打开页面",
            "有效数据", "测试数据", "正确数据", "合法数据", "有效值",
            "验证成功", "操作成功", "测试通过", "等等", "其他"));

    private static final Set<String> ACTION_KEYWORDS = new HashSet<>(Arrays.asList(
            "点击", "输入", "选择", "提交", "上传", "下载", "查看", "校验", "验证", "检查"));

    /**
     * 评估生成的测试用例质量
     * 
     * @param generatedCases LLM生成的测试用例
     * @return 评估结果
     */
    public EvaluationResult evaluate(List<TestCase> generatedCases) {
        EvaluationResult result = new EvaluationResult();

        if (generatedCases == null || generatedCases.isEmpty()) {
            return result;
        }

        // 1. 缺陷发现能力评估 (25%) - 边界值/异常场景
        evaluateDefectDetection(generatedCases, result);

        // 2. 业务覆盖度评估 (25%) - 场景/模块/流程
        evaluateBusinessCoverage(generatedCases, result);

        // 3. 可执行性评估 (20%) - 步骤清晰度/预期明确
        evaluateExecutability(generatedCases, result);

        // 4. 断言完整性评估 (15%)
        evaluateAssertion(generatedCases, result);

        // 5. 规范性评估 (15%)
        evaluateStandard(generatedCases, result);

        // 计算总分 (高级测试工程师维度权重)
        double totalScore = result.getRobustnessScore() * 0.25 +  // 缺陷发现 25%
                result.getCoverageScore() * 0.25 +               // 业务覆盖 25%
                result.getQualityScore() * 0.20 +                // 可执行性 20%
                result.getAssertionScore() * 0.15 +              // 断言完整 15%
                result.getStandardScore() * 0.15;                // 规范性 15%

        result.setTotalScore(Math.min(100.0, totalScore));

        // 生成总体评价
        generateSummary(result);

        return result;
    }

    /**
     * 1. 评估缺陷发现能力 (25%)
     * - 边界值覆盖
     * - 异常场景覆盖
     */
    private void evaluateDefectDetection(List<TestCase> generated, EvaluationResult result) {
        Map<String, Object> details = result.getDetails();
        int totalCases = generated.size();

        // 1.1 边界值分析
        long boundaryCount = generated.stream()
                .filter(this::isBoundaryCase)
                .count();

        // 期望边界用例占比至少 15%
        double boundaryRatio = (double) boundaryCount / totalCases;
        double boundaryScore = Math.min(100.0, (boundaryRatio / 0.15) * 100.0);
        result.setBoundaryScore(boundaryScore);
        details.put("boundaryCount", boundaryCount);
        details.put("boundaryRatio", String.format("%.1f%%", boundaryRatio * 100));

        // 1.2 异常场景分析
        long exceptionCount = generated.stream()
                .filter(this::isExceptionCase)
                .count();

        // 期望异常用例占比至少 20%
        double exceptionRatio = (double) exceptionCount / totalCases;
        double exceptionScore = Math.min(100.0, (exceptionRatio / 0.20) * 100.0);
        result.setExceptionScore(exceptionScore);
        details.put("exceptionCount", exceptionCount);
        details.put("exceptionRatio", String.format("%.1f%%", exceptionRatio * 100));

        // 综合缺陷发现分
        result.setRobustnessScore((boundaryScore + exceptionScore) / 2.0);

        if (boundaryScore < 60)
            result.addSuggestion("严重缺乏边界值测试，请补充最大/最小/空值等边界场景");
        if (exceptionScore < 60)
            result.addSuggestion("异常场景覆盖不足，请补充失败/报错/权限不足等负面测试");
    }

    /**
     * 2. 评估业务覆盖度 (25%)
     * - 场景类型覆盖
     * - 模块覆盖
     * - 业务动作覆盖
     */
    private void evaluateBusinessCoverage(List<TestCase> generated, EvaluationResult result) {
        Map<String, Object> details = result.getDetails();

        // 1.1 场景类型覆盖
        Set<SceneType> coveredTypes = generated.stream()
                .map(TestCase::getSceneType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 期望至少覆盖: FRONTEND, BACKEND, EXCEPTION (如果有)
        double typeScore = 0.0;
        if (coveredTypes.contains(SceneType.FRONTEND))
            typeScore += 40;
        if (coveredTypes.contains(SceneType.BACKEND))
            typeScore += 40;
        if (coveredTypes.contains(SceneType.INTEGRATION))
            typeScore += 20;

        details.put("sceneTypeCoverage", typeScore);

        // 1.2 模块覆盖
        Set<String> modules = generated.stream()
                .map(TestCase::getModule)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        double moduleScore = Math.min(100.0, modules.size() * 20.0); // 假设至少5个模块/功能点算满分
        details.put("moduleCoverage", moduleScore);

        // 1.3 业务动作覆盖
        long actionCount = generated.stream()
                .flatMap(tc -> {
                    List<String> steps = new ArrayList<>();
                    if (tc.getFrontEndSteps() != null)
                        tc.getFrontEndSteps().forEach(s -> steps.add(s.getAction()));
                    if (tc.getBackEndSteps() != null)
                        tc.getBackEndSteps().forEach(s -> steps.add(s.getAction()));
                    return steps.stream();
                })
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double actionScore = Math.min(100.0, actionCount * 10.0); // 10个不同动作算满分
        details.put("actionCoverage", actionScore);

        // 综合覆盖分
        double totalCoverage = (typeScore + moduleScore + actionScore) / 3.0;
        result.setCoverageScore(totalCoverage);

        if (typeScore < 60)
            result.addSuggestion("建议增加不同类型的测试场景（如后端接口测试、集成测试）");
    }

    /**
     * 3. 评估可执行性 (20%)
     * - 步骤详细度
     * - 预期结果明确性
     * - 步骤内容完整性
     * - 步骤具体性（笼统步骤扣分）
     */
    private void evaluateExecutability(List<TestCase> generated, EvaluationResult result) {
        double totalQuality = 0.0;
        int vagueStepCaseCount = 0;

        for (TestCase tc : generated) {
            double caseScore = 0.0;
            boolean hasVagueStep = false;

            // 3.1 步骤数量评估 (25%)
            int frontSteps = tc.getFrontEndSteps() != null ? tc.getFrontEndSteps().size() : 0;
            int backSteps = tc.getBackEndSteps() != null ? tc.getBackEndSteps().size() : 0;
            int totalSteps = frontSteps + backSteps;

            // 3-8步为最佳区间
            if (totalSteps >= 3 && totalSteps <= 8) {
                caseScore += 25;
            } else if (totalSteps >= 2 && totalSteps <= 10) {
                caseScore += 20;
            } else if (totalSteps >= 1) {
                caseScore += 10;
            }

            // 3.2 步骤具体性评估 (30%) - 检查是否有笼统/模糊步骤
            double stepSpecificityScore = 30.0;
            int specificStepCount = 0;
            
            // 检查前端步骤具体性
            if (tc.getFrontEndSteps() != null) {
                for (FrontEndStep step : tc.getFrontEndSteps()) {
                    String action = step.getAction();
                    String element = step.getElement();
                    String value = step.getValue();
                    
                    // 检查是否包含笼统关键词
                    if (containsKeyword(action, VAGUE_STEP_KEYWORDS) || 
                        containsKeyword(element, VAGUE_STEP_KEYWORDS) ||
                        containsKeyword(value, VAGUE_STEP_KEYWORDS)) {
                        hasVagueStep = true;
                        stepSpecificityScore -= 5;
                    }
                    
                    // 有具体元素定位符加分
                    if (step.getLocator() != null && !step.getLocator().isEmpty()) {
                        specificStepCount++;
                    }
                    // 有具体输入值加分
                    if (value != null && !value.isEmpty() && !containsKeyword(value, VAGUE_STEP_KEYWORDS)) {
                        specificStepCount++;
                    }
                }
            }
            
            // 检查后端步骤具体性
            if (tc.getBackEndSteps() != null) {
                for (BackEndStep step : tc.getBackEndSteps()) {
                    String action = step.getAction();
                    
                    // 检查是否包含笼统关键词
                    if (containsKeyword(action, VAGUE_STEP_KEYWORDS)) {
                        hasVagueStep = true;
                        stepSpecificityScore -= 5;
                    }
                    
                    // 有完整的API信息加分
                    if (step.getApiPath() != null && !step.getApiPath().isEmpty()) {
                        specificStepCount++;
                    }
                    if (step.getMethod() != null && !step.getMethod().isEmpty()) {
                        specificStepCount++;
                    }
                }
            }
            
            // 具体步骤占比加分
            if (totalSteps > 0) {
                double specificRatio = (double) specificStepCount / (totalSteps * 2); // 每步最多2个具体项
                stepSpecificityScore = Math.max(0, stepSpecificityScore) + (specificRatio * 10);
            }
            caseScore += Math.min(30, Math.max(0, stepSpecificityScore));

            // 3.3 步骤内容完整性 (20%)
            double stepContentScore = 0.0;
            int validStepCount = 0;
            
            if (tc.getFrontEndSteps() != null) {
                for (FrontEndStep step : tc.getFrontEndSteps()) {
                    if (step.getAction() != null && !step.getAction().isEmpty()) {
                        validStepCount++;
                        if (step.getValue() != null && !step.getValue().isEmpty()) {
                            stepContentScore += 1.5;
                        } else {
                            stepContentScore += 1.0;
                        }
                    }
                }
            }
            
            if (tc.getBackEndSteps() != null) {
                for (BackEndStep step : tc.getBackEndSteps()) {
                    if (step.getAction() != null && !step.getAction().isEmpty()) {
                        validStepCount++;
                        stepContentScore += 1.0;
                    }
                }
            }
            
            if (validStepCount > 0) {
                caseScore += Math.min(20.0, (stepContentScore / validStepCount) * 20.0);
            }

            // 3.4 预期结果明确性 (25%)
            int expectedCount = 0;
            if (tc.getFrontEndExpected() != null) {
                expectedCount += tc.getFrontEndExpected().size();
            }
            if (tc.getBackEndExpected() != null) {
                expectedCount += tc.getBackEndExpected().size();
            }
            
            if (expectedCount >= 3) {
                caseScore += 25;
            } else if (expectedCount >= 2) {
                caseScore += 22;
            } else if (expectedCount == 1) {
                caseScore += 18;
            } else if (tc.getAssertRules() != null && !tc.getAssertRules().isEmpty()) {
                caseScore += 20;
            }

            if (hasVagueStep) {
                vagueStepCaseCount++;
            }
            
            totalQuality += Math.min(100.0, caseScore);
        }

        result.setQualityScore(totalQuality / generated.size());

        // 根据具体问题给出建议
        if (result.getQualityScore() < 60) {
            result.addSuggestion("测试步骤过于简单或缺乏明确的预期结果");
        }
        if (vagueStepCaseCount > generated.size() * 0.3) {
            result.addSuggestion("部分用例步骤过于笼统，建议拆分为具体的原子操作，并提供实际测试数据");
        }
    }

    /**
     * 4. 评估断言完整性 (15%)
     * 综合考虑断言规则数量、质量和预期结果
     */
    private void evaluateAssertion(List<TestCase> generated, EvaluationResult result) {
        double totalAssertion = 0.0;

        // 高质量断言类型（可自动化执行）
        Set<String> highQualityAssertTypes = new HashSet<>(Arrays.asList(
            "statusEquals", "status_code", "fieldEquals", "fieldContains",
            "textEquals", "textContains", "elementVisible", "elementEnabled",
            "schemaValidate", "schema", "urlEquals", "urlContains",
            "contains", "equals", "exists", "responseTime", "dbRecordExists"
        ));

        for (TestCase tc : generated) {
            double caseScore = 0.0;
            int assertRuleCount = tc.getAssertRules() != null ? tc.getAssertRules().size() : 0;
            int frontExpectedCount = tc.getFrontEndExpected() != null ? tc.getFrontEndExpected().size() : 0;
            int backExpectedCount = tc.getBackEndExpected() != null ? tc.getBackEndExpected().size() : 0;
            int totalExpectedCount = frontExpectedCount + backExpectedCount;

            // 4.1 断言规则数量评估 (40%)
            if (assertRuleCount >= 3) {
                caseScore += 40;
            } else if (assertRuleCount == 2) {
                caseScore += 32;  // 2条断言也算较好
            } else if (assertRuleCount == 1) {
                caseScore += 20;
            }
            
            // 4.2 断言质量评估 (30%) - 检查断言类型是否为高质量类型
            if (tc.getAssertRules() != null && !tc.getAssertRules().isEmpty()) {
                int highQualityCount = 0;
                int completeCount = 0;
                for (var assertRule : tc.getAssertRules()) {
                    String assertType = assertRule.getAssertType();
                    // 检查是否为高质量断言类型
                    if (assertType != null && highQualityAssertTypes.contains(assertType.toLowerCase())) {
                        highQualityCount++;
                    }
                    // 检查断言是否完整（有target和expectedValue）
                    if (assertRule.getTarget() != null && !assertRule.getTarget().isEmpty()
                            && assertRule.getExpectedValue() != null) {
                        completeCount++;
                    }
                }
                // 高质量断言占比
                double qualityRatio = (double) highQualityCount / assertRuleCount;
                double completenessRatio = (double) completeCount / assertRuleCount;
                caseScore += (qualityRatio * 15 + completenessRatio * 15);
            }
            
            // 4.3 预期结果评估 (30%) - 预期结果是断言的重要补充
            if (totalExpectedCount >= 3) {
                caseScore += 30;
            } else if (totalExpectedCount == 2) {
                caseScore += 24;
            } else if (totalExpectedCount == 1) {
                caseScore += 15;
            }
            
            // 补偿机制：如果断言规则较少但预期结果丰富，给予额外分数
            if (assertRuleCount < 2 && totalExpectedCount >= 3) {
                caseScore = Math.min(100.0, caseScore + 15);
            }

            totalAssertion += Math.min(100.0, caseScore);
        }

        result.setAssertionScore(totalAssertion / generated.size());

        if (result.getAssertionScore() < 60)
            result.addSuggestion("断言规则不完整，建议为每个用例添加至少3条明确的验证点");
    }

    /**
     * 5. 评估规范性 (15%)
     */
    private void evaluateStandard(List<TestCase> generated, EvaluationResult result) {
        double totalStandard = 0.0;

        for (TestCase tc : generated) {
            double caseScore = 0.0;

            // ID格式 (TC_XXXX)
            if (tc.getCaseId() != null && tc.getCaseId().matches("TC_\\d{4}")) {
                caseScore += 50;
            }

            // 必填项完整
            if (tc.getTitle() != null && tc.getPriority() != null && tc.getModule() != null) {
                caseScore += 50;
            }

            totalStandard += caseScore;
        }

        result.setStandardScore(totalStandard / generated.size());
    }

    // 辅助方法：判断是否为边界测试用例
    private boolean isBoundaryCase(TestCase tc) {
        // 1. 检查标题
        if (containsKeyword(tc.getTitle(), BOUNDARY_KEYWORDS))
            return true;

        // 2. 检查前端步骤参数
        if (tc.getFrontEndSteps() != null && tc.getFrontEndSteps().stream()
                .anyMatch(s -> containsKeyword(s.getValue(), BOUNDARY_KEYWORDS) ||
                              containsKeyword(s.getAction(), BOUNDARY_KEYWORDS)))
            return true;

        // 3. 检查后端步骤参数
        if (tc.getBackEndSteps() != null && tc.getBackEndSteps().stream()
                .anyMatch(s -> containsKeyword(s.getAction(), BOUNDARY_KEYWORDS)))
            return true;

        // 4. 检查预期结果中的边界描述
        if (tc.getFrontEndExpected() != null && tc.getFrontEndExpected().stream()
                .anyMatch(s -> containsKeyword(s, BOUNDARY_KEYWORDS)))
            return true;

        if (tc.getBackEndExpected() != null && tc.getBackEndExpected().stream()
                .anyMatch(s -> containsKeyword(s, BOUNDARY_KEYWORDS)))
            return true;

        // 5. 检查标签
        if (tc.getTags() != null && tc.getTags().stream()
                .anyMatch(tag -> containsKeyword(tag, BOUNDARY_KEYWORDS)))
            return true;

        return false;
    }

    // 辅助方法：判断是否为异常测试用例
    private boolean isExceptionCase(TestCase tc) {
        // 1. 检查场景类型
        if (tc.getSceneType() == SceneType.EXCEPTION)
            return true;

        // 2. 检查标题
        if (containsKeyword(tc.getTitle(), EXCEPTION_KEYWORDS))
            return true;

        // 3. 检查前端预期结果
        if (tc.getFrontEndExpected() != null && tc.getFrontEndExpected().stream()
                .anyMatch(s -> containsKeyword(s, EXCEPTION_KEYWORDS)))
            return true;

        // 4. 检查后端预期结果
        if (tc.getBackEndExpected() != null && tc.getBackEndExpected().stream()
                .anyMatch(s -> containsKeyword(s, EXCEPTION_KEYWORDS)))
            return true;

        // 5. 检查前端步骤
        if (tc.getFrontEndSteps() != null && tc.getFrontEndSteps().stream()
                .anyMatch(s -> containsKeyword(s.getAction(), EXCEPTION_KEYWORDS) ||
                              containsKeyword(s.getValue(), EXCEPTION_KEYWORDS)))
            return true;

        // 6. 检查后端步骤
        if (tc.getBackEndSteps() != null && tc.getBackEndSteps().stream()
                .anyMatch(s -> containsKeyword(s.getAction(), EXCEPTION_KEYWORDS)))
            return true;

        // 7. 检查标签
        if (tc.getTags() != null && tc.getTags().stream()
                .anyMatch(tag -> containsKeyword(tag, EXCEPTION_KEYWORDS)))
            return true;

        return false;
    }

    private boolean containsKeyword(String text, Set<String> keywords) {
        if (text == null)
            return false;
        String lowerText = text.toLowerCase();
        return keywords.stream().anyMatch(lowerText::contains);
    }

    private void generateSummary(EvaluationResult result) {
        StringBuilder summary = new StringBuilder();
        double totalScore = result.getTotalScore();
        
        // 1. 总体评价
        if (totalScore >= 90) {
            summary.append("测试用例质量极佳！");
        } else if (totalScore >= 80) {
            summary.append("测试用例质量优秀。");
        } else if (totalScore >= 60) {
            summary.append("测试用例质量良好。");
        } else {
            summary.append("测试用例质量待提升。");
        }
        
        // 2. 分维度评价
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        
        // 缺陷发现能力
        if (result.getRobustnessScore() >= 80) {
            strengths.add("缺陷发现能力强");
        } else if (result.getRobustnessScore() < 60) {
            if (result.getBoundaryScore() < 60) {
                weaknesses.add("边界值测试不足");
            }
            if (result.getExceptionScore() < 60) {
                weaknesses.add("异常场景覆盖不全");
            }
        }
        
        // 业务覆盖度
        if (result.getCoverageScore() >= 80) {
            strengths.add("业务场景覆盖全面");
        } else if (result.getCoverageScore() < 60) {
            weaknesses.add("业务场景覆盖有缺失");
        }
        
        // 可执行性
        if (result.getQualityScore() >= 80) {
            strengths.add("步骤清晰可执行");
        } else if (result.getQualityScore() < 60) {
            weaknesses.add("测试步骤不够清晰");
        }
        
        // 断言完整性
        if (result.getAssertionScore() >= 80) {
            strengths.add("断言规则完整");
        } else if (result.getAssertionScore() < 60) {
            weaknesses.add("断言验证点不足");
        }
        
        // 规范性
        if (result.getStandardScore() >= 80) {
            strengths.add("用例结构规范");
        } else if (result.getStandardScore() < 60) {
            weaknesses.add("用例格式待规范");
        }
        
        // 3. 拼接优势
        if (!strengths.isEmpty()) {
            summary.append("优势：").append(String.join("、", strengths)).append("。");
        }
        
        // 4. 拼接待改进项
        if (!weaknesses.isEmpty()) {
            summary.append("待改进：").append(String.join("、", weaknesses)).append("。");
        }
        
        // 5. 给出核心建议
        if (totalScore < 60) {
            summary.append("建议重点补充边界值和异常场景用例。");
        } else if (totalScore < 80) {
            if (result.getAssertionScore() < 70) {
                summary.append("建议为每个用例添加更多断言规则。");
            } else if (result.getRobustnessScore() < 70) {
                summary.append("建议增加更多异常测试场景。");
            }
        }
        
        result.setSummary(summary.toString());
    }

    /**
     * 打印评估报告
     */
    public void printReport(EvaluationResult result) {
        System.out.println("\n========== 测试用例质量评估报告 (高级测试工程师维度) ==========");
        System.out.println(String.format("总分: %.2f / 100", result.getTotalScore()));
        System.out.println("\n核心维度得分:");
        System.out.println(String.format("  1. 缺陷发现 (25%%): %.2f [边界:%.1f, 异常:%.1f]",
                result.getRobustnessScore(), result.getBoundaryScore(), result.getExceptionScore()));
        System.out.println(String.format("  2. 业务覆盖 (25%%): %.2f", result.getCoverageScore()));
        System.out.println(String.format("  3. 可执行性 (20%%): %.2f", result.getQualityScore()));
        System.out.println(String.format("  4. 断言完整 (15%%): %.2f", result.getAssertionScore()));
        System.out.println(String.format("  5. 规范性   (15%%): %.2f", result.getStandardScore()));

        if (!result.getSuggestions().isEmpty()) {
            System.out.println("\n优化建议:");
            result.getSuggestions().forEach(s -> System.out.println("  - " + s));
        }
        System.out.println("=====================================================\n");
    }
}
