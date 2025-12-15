package com.testgen.evaluation;

import com.testgen.model.testcase.TestCase;
import com.testgen.llm.VolcanoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 测试用例对比评分服务
 * 使用LLM（高级测试工程师视角）对比AI生成用例与人工标准用例
 */
public class CaseComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(CaseComparisonService.class);

    private VolcanoService volcanoService;
    private ObjectMapper objectMapper;

    public CaseComparisonService() {
        this.volcanoService = new VolcanoService();
        this.objectMapper = new ObjectMapper();
    }

    public CaseComparisonService(VolcanoService volcanoService) {
        this.volcanoService = volcanoService != null ? volcanoService : new VolcanoService();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 对比结果类
     */
    public static class ComparisonResult {
        private double totalScore; // 总分 (0-100)
        private double defectDetectionScore; // 缺陷发现能力 (25%)
        private double businessCoverageScore;// 业务覆盖度 (20%)
        private double executabilityScore; // 可执行性 (15%)
        private double assertionScore; // 断言完整性 (10%)
        private double standardScore; // 规范性 (10%)
        private double nonRedundancyScore; // 非冗余性 (15%)

        private int matchedCount; // 匹配用例数
        private int unmatchedCount; // 未匹配用例数
        private List<CaseMatchDetail> matchDetails;
        private List<String> suggestions;
        private String overallAnalysis; // LLM总体分析
        
        // AI用例优缺点分析
        private List<String> strengths; // AI用例优点
        private List<String> weaknesses; // AI用例缺点
        
        // 新增: 评分细节信息
        private String scoreMethod; // 评分方式: "LLM" 或 "HEURISTIC"
        private Map<String, Object> scoreDetails; // 详细评分信息
        private long scoringTimeMs; // 评分耗时(ms)

        public ComparisonResult() {
            this.matchDetails = new ArrayList<>();
            this.suggestions = new ArrayList<>();
            this.strengths = new ArrayList<>();
            this.weaknesses = new ArrayList<>();
            this.scoreDetails = new HashMap<>();
            this.scoreMethod = "LLM";
        }

        // Getters and Setters
        public double getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(double totalScore) {
            this.totalScore = totalScore;
        }

        public double getDefectDetectionScore() {
            return defectDetectionScore;
        }

        public void setDefectDetectionScore(double score) {
            this.defectDetectionScore = score;
        }

        public double getBusinessCoverageScore() {
            return businessCoverageScore;
        }

        public void setBusinessCoverageScore(double score) {
            this.businessCoverageScore = score;
        }

        public double getExecutabilityScore() {
            return executabilityScore;
        }

        public void setExecutabilityScore(double score) {
            this.executabilityScore = score;
        }

        public double getAssertionScore() {
            return assertionScore;
        }

        public void setAssertionScore(double score) {
            this.assertionScore = score;
        }

        public double getStandardScore() {
            return standardScore;
        }

        public void setStandardScore(double score) {
            this.standardScore = score;
        }
        
        public double getNonRedundancyScore() {
            return nonRedundancyScore;
        }
        
        public void setNonRedundancyScore(double score) {
            this.nonRedundancyScore = score;
        }

        public int getMatchedCount() {
            return matchedCount;
        }

        public void setMatchedCount(int matchedCount) {
            this.matchedCount = matchedCount;
        }

        public int getUnmatchedCount() {
            return unmatchedCount;
        }

        public void setUnmatchedCount(int unmatchedCount) {
            this.unmatchedCount = unmatchedCount;
        }

        public List<CaseMatchDetail> getMatchDetails() {
            return matchDetails;
        }

        public void setMatchDetails(List<CaseMatchDetail> matchDetails) {
            this.matchDetails = matchDetails;
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

        public String getOverallAnalysis() {
            return overallAnalysis;
        }

        public void setOverallAnalysis(String overallAnalysis) {
            this.overallAnalysis = overallAnalysis;
        }
        
        public List<String> getStrengths() {
            return strengths;
        }
        
        public void setStrengths(List<String> strengths) {
            this.strengths = strengths;
        }
        
        public void addStrength(String strength) {
            if (!this.strengths.contains(strength)) {
                this.strengths.add(strength);
            }
        }
        
        public List<String> getWeaknesses() {
            return weaknesses;
        }
        
        public void setWeaknesses(List<String> weaknesses) {
            this.weaknesses = weaknesses;
        }
        
        public void addWeakness(String weakness) {
            if (!this.weaknesses.contains(weakness)) {
                this.weaknesses.add(weakness);
            }
        }
        
        public String getScoreMethod() {
            return scoreMethod;
        }
        
        public void setScoreMethod(String scoreMethod) {
            this.scoreMethod = scoreMethod;
        }
        
        public Map<String, Object> getScoreDetails() {
            return scoreDetails;
        }
        
        public void setScoreDetails(Map<String, Object> scoreDetails) {
            this.scoreDetails = scoreDetails;
        }
        
        public void addScoreDetail(String key, Object value) {
            this.scoreDetails.put(key, value);
        }
        
        public long getScoringTimeMs() {
            return scoringTimeMs;
        }
        
        public void setScoringTimeMs(long scoringTimeMs) {
            this.scoringTimeMs = scoringTimeMs;
        }

        // 兼容旧接口
        public double getTitleMatchScore() {
            return businessCoverageScore;
        }

        public double getModuleMatchScore() {
            return businessCoverageScore;
        }

        public double getSceneTypeMatchScore() {
            return defectDetectionScore;
        }

        public double getStepsMatchScore() {
            return executabilityScore;
        }

        public double getExpectedMatchScore() {
            return assertionScore;
        }
    }

    /**
     * 单个用例匹配详情
     */
    public static class CaseMatchDetail {
        private String referenceCaseId;
        private String referenceCaseTitle;
        private String matchedAiCaseId;
        private String matchedAiCaseTitle;
        private double matchScore;
        private boolean isMatched;
        private Map<String, Double> dimensionScores;
        private String analysis; // LLM分析

        public CaseMatchDetail() {
            this.dimensionScores = new HashMap<>();
        }

        // Getters and Setters
        public String getReferenceCaseId() {
            return referenceCaseId;
        }

        public void setReferenceCaseId(String id) {
            this.referenceCaseId = id;
        }

        public String getReferenceCaseTitle() {
            return referenceCaseTitle;
        }

        public void setReferenceCaseTitle(String title) {
            this.referenceCaseTitle = title;
        }

        public String getMatchedAiCaseId() {
            return matchedAiCaseId;
        }

        public void setMatchedAiCaseId(String id) {
            this.matchedAiCaseId = id;
        }

        public String getMatchedAiCaseTitle() {
            return matchedAiCaseTitle;
        }

        public void setMatchedAiCaseTitle(String title) {
            this.matchedAiCaseTitle = title;
        }

        public double getMatchScore() {
            return matchScore;
        }

        public void setMatchScore(double score) {
            this.matchScore = score;
        }

        public boolean isMatched() {
            return isMatched;
        }

        public void setMatched(boolean matched) {
            this.isMatched = matched;
        }

        public Map<String, Double> getDimensionScores() {
            return dimensionScores;
        }

        public void setDimensionScores(Map<String, Double> scores) {
            this.dimensionScores = scores;
        }

        public String getAnalysis() {
            return analysis;
        }

        public void setAnalysis(String analysis) {
            this.analysis = analysis;
        }
    }

    /**
     * 使用LLM对比AI生成用例与人工标准用例
     * 
     * @param aiCases        AI生成的测试用例
     * @param referenceCases 人工标准用例
     * @return 对比评分结果
     */
    public ComparisonResult compare(List<TestCase> aiCases, List<TestCase> referenceCases) {
        return compare(aiCases, referenceCases, null);
    }


    /**
     * 使用LLM评估AI生成用例（带PRD上下文）
     * 支持两种模式：
     * 1. 有人工用例：对比评分模式
     * 2. 无人工用例：独立评估模式
     * 
     * @param aiCases        AI生成的测试用例
     * @param referenceCases 人工标准用例（可为空）
     * @param prdText        原始PRD文档内容
     * @return 评分结果
     */
    public ComparisonResult compare(List<TestCase> aiCases, List<TestCase> referenceCases, String prdText) {
        ComparisonResult result = new ComparisonResult();
        long startTime = System.currentTimeMillis();

        if (aiCases == null || aiCases.isEmpty()) {
            result.setTotalScore(0);
            result.addSuggestion("请先生成AI测试用例");
            return result;
        }

        // 记录基本信息
        result.addScoreDetail("aiCaseCount", aiCases.size());
        result.addScoreDetail("refCaseCount", referenceCases != null ? referenceCases.size() : 0);
        result.addScoreDetail("hasPrdContext", prdText != null && !prdText.trim().isEmpty());

        boolean hasReferenceCases = referenceCases != null && !referenceCases.isEmpty();
        boolean usePrd = prdText != null && !prdText.trim().isEmpty();
        
        // 根据是否有人工用例选择不同的评分模式
        if (hasReferenceCases) {
            // 有人工用例：对比评分模式
            result.addScoreDetail("evaluationMode", "comparison");
            System.out.println("[评分] 模式: 对比评分 - 正在调用LLM对比AI用例与人工用例" + (usePrd ? "（含PRD上下文）" : "") + "...");
            logger.info("开始LLM对比评分，AI用例: {} 个，人工用例: {} 个", aiCases.size(), referenceCases.size());
            performComparisonEvaluation(aiCases, referenceCases, prdText, result);
        } else {
            // 无人工用例：独立评估模式
            result.addScoreDetail("evaluationMode", "standalone");
            System.out.println("[评分] 模式: 独立评估 - 正在调用LLM直接评估AI用例质量" + (usePrd ? "（含PRD上下文）" : "") + "...");
            logger.info("开始LLM独立评估，AI用例: {} 个", aiCases.size());
            performStandaloneEvaluation(aiCases, prdText, result);
        }

        // 记录耗时
        result.setScoringTimeMs(System.currentTimeMillis() - startTime);
        result.addScoreDetail("scoringTimeMs", result.getScoringTimeMs());
        System.out.println("[评分] 评分完成，耗时: " + result.getScoringTimeMs() + "ms，方式: " + result.getScoreMethod());

        return result;
    }

    /**
     * 执行对比评分（有人工用例）
     */
    private void performComparisonEvaluation(List<TestCase> aiCases, List<TestCase> referenceCases, 
            String prdText, ComparisonResult result) {
        try {
            // 调用LLM进行批量对比评分
            String llmResponse = callLLMForComparisonWithPRD(aiCases, referenceCases, prdText);
            
            // 打印LLM原始响应用于调试
            System.out.println("[评分] LLM响应长度: " + (llmResponse != null ? llmResponse.length() : 0) + " 字符");
            if (llmResponse != null && llmResponse.length() < 500) {
                System.out.println("[评分] LLM响应内容: " + llmResponse);
            }

            // 解析LLM返回的评分结果
            parseComparisonResult(llmResponse, result, aiCases, referenceCases);
            
            result.setScoreMethod("LLM");
            result.addScoreDetail("llmResponseLength", llmResponse != null ? llmResponse.length() : 0);

            System.out.println("[评分] LLM对比评分完成 ✓");
            logger.info("LLM对比评分完成，总分: {}", result.getTotalScore());

        } catch (Exception e) {
            logger.error("LLM对比评分失败: {}", e.getMessage(), e);
            System.out.println("[ERROR] LLM对比评分失败: " + e.getMessage());
            e.printStackTrace();
            result.addScoreDetail("llmError", e.getMessage());
            
            result.setScoreMethod("ERROR");
            result.setOverallAnalysis("LLM评分服务调用失败：" + e.getMessage());
            result.addSuggestion("请检查LLM服务配置是否正确");
            result.addSuggestion("确保API Key和Endpoint已正确设置");
        }
    }

    /**
     * 执行独立评估（无人工用例）
     */
    private void performStandaloneEvaluation(List<TestCase> aiCases, String prdText, ComparisonResult result) {
        try {
            // 调用LLM进行独立评估
            String llmResponse = callLLMForStandaloneEvaluation(aiCases, prdText);
            
            System.out.println("[评分] LLM响应长度: " + (llmResponse != null ? llmResponse.length() : 0) + " 字符");
            if (llmResponse != null && llmResponse.length() < 500) {
                System.out.println("[评分] LLM响应内容: " + llmResponse);
            }

            // 解析LLM返回的评分结果
            parseStandaloneResult(llmResponse, result, aiCases);
            
            result.setScoreMethod("LLM");
            result.addScoreDetail("llmResponseLength", llmResponse != null ? llmResponse.length() : 0);

            System.out.println("[评分] LLM独立评估完成 ✓");
            logger.info("LLM独立评估完成，总分: {}", result.getTotalScore());

        } catch (Exception e) {
            logger.error("LLM独立评估失败: {}", e.getMessage(), e);
            System.out.println("[ERROR] LLM独立评估失败: " + e.getMessage());
            e.printStackTrace();
            result.addScoreDetail("llmError", e.getMessage());
            
            result.setScoreMethod("ERROR");
            result.setOverallAnalysis("LLM评分服务调用失败：" + e.getMessage());
            result.addSuggestion("请检查LLM服务配置是否正确");
            result.addSuggestion("确保API Key和Endpoint已正确设置");
        }
    }

    /**
     * 调用LLM进行独立评估（无人工用例参照）
     */
    private String callLLMForStandaloneEvaluation(List<TestCase> aiCases, String prdText) throws Exception {
        String systemPrompt = buildStandaloneEvaluationPrompt(prdText);

        // 构建用例数据
        Map<String, Object> evaluationData = new HashMap<>();
        evaluationData.put("aiGeneratedCases", aiCases);

        String userPrompt = "请评估以下AI生成的测试用例质量：\n\n" +
                objectMapper.writeValueAsString(evaluationData);

        return volcanoService.chat(systemPrompt, userPrompt);
    }

    /**
     * 构建独立评估prompt（无人工用例参照）
     */
    private String buildStandaloneEvaluationPrompt(String prdText) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一位拥有10年经验的高级测试工程师（Senior QA Engineer）。\n");
        prompt.append("现在需要你以专业视角独立评估【AI自动生成的测试用例】的质量。\n");
        prompt.append("重要：请全程使用中文进行分析和回复，所有分析内容、建议和评价都必须用中文。\n\n");

        // 如果有PRD，添加PRD上下文
        if (prdText != null && !prdText.trim().isEmpty()) {
            prompt.append("## 原始需求文档（PRD）\n");
            prompt.append("以下是产品需求文档，请基于此文档的功能需求来评估测试用例的覆盖度和质量：\n\n");
            String truncatedPrd = prdText.length() > 2000 ? prdText.substring(0, 2000) + "...（已截断）" : prdText;
            prompt.append("```\n").append(truncatedPrd).append("\n```\n\n");
            prompt.append("请特别关注：\n");
            prompt.append("- AI用例是否完整覆盖了PRD中描述的所有功能点？\n");
            prompt.append("- AI用例是否考虑了PRD中提到的边界条件和异常情况？\n\n");
        }

        prompt.append("## 评分维度（满分100分）\n\n");

        prompt.append("### 1. 缺陷发现能力 (25%)\n");
        prompt.append("- 边界值测试是否覆盖≥80%的典型边界场景（如数值上下限、字符长度极值等）\n");
        prompt.append("- 是否异常场景覆盖≥70%的常见异常类型（错误处理、权限校验、超时等）？\n");
        prompt.append("- 用例能否发现≥60%的潜在Bug（基于类似功能历史缺陷率）\n\n");

        prompt.append("### 2. 业务覆盖度 (20%)\n");
        prompt.append("- 是否覆盖核心业务场景≥90%？\n");
        if (prdText != null && !prdText.trim().isEmpty()) {
            prompt.append("- 是否覆盖PRD中描述的所有功能需求？\n");
        }
        prompt.append("- 关键用户路径是否覆盖≥95%？\n\n");

        prompt.append("### 3. 可执行性 (15%)\n");
        prompt.append("- 测试步骤是否具体可操作（每个步骤仅包含一个动作，无模糊表述）？\n");
        prompt.append("- 测试数据是否明确（包含具体数值、格式、范围等）？\n");
        prompt.append("- 具备基础测试知识（入职1-3个月）的新手测试人员能否独立按步骤执行？\n\n");

        prompt.append("### 4. 断言完整性 (10%)\n");
        prompt.append("- 预期结果准确率是否≥90%？\n");
        prompt.append("- 验证点是否≥80%（覆盖UI状态、接口返回、数据库变化等关键验证点）？\n\n");

        prompt.append("### 5. 规范性 (10%)\n");
        prompt.append("- 用例ID、优先级、标签规范率需≥95%？\n");
        prompt.append("- 用例结构清晰易读率需≥90%（包含测试目的、前置条件、测试步骤、预期结果等要素）？\n\n");

        prompt.append("### 6. 非冗余性 (20%)\n");
        prompt.append("- I用例之间是否存在重复或相似度≥80%的高度相似情况（基于测试步骤、测试数据、预期结果的重合度）？\n");
        prompt.append("- 是否有多个用例测试完全相同的功能点（功能点重合度100%）？\n");
        prompt.append("- 用例集合是否简洁高效，冗余用例占比≤5%\n\n");

        prompt.append("## 客观评价要求\n\n");
        prompt.append("请客观分析AI生成用例的优缺点：\n");
        prompt.append("1. **优点(strengths)**：AI用例做得好的地方\n");
        prompt.append("2. **缺点(weaknesses)**：AI用例的不足之处\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("请返回JSON格式（不要包含```json标记）：\n");
        prompt.append("{\n");
        prompt.append("  \"totalScore\": 0-100,\n");
        prompt.append("  \"dimensionScores\": {\n");
        prompt.append("    \"defectDetection\": 0-100,\n");
        prompt.append("    \"businessCoverage\": 0-100,\n");
        prompt.append("    \"executability\": 0-100,\n");
        prompt.append("    \"assertion\": 0-100,\n");
        prompt.append("    \"standard\": 0-100,\n");
        prompt.append("    \"nonRedundancy\": 0-100\n");
        prompt.append("  },\n");
        prompt.append("  \"strengths\": [\"AI用例优点1\", \"AI用例优点2\"],\n");
        prompt.append("  \"weaknesses\": [\"AI用例缺点1\", \"AI用例缺点2\"],\n");
        prompt.append("  \"overallAnalysis\": \"整体评价和改进建议\",\n");
        prompt.append("  \"suggestions\": [\"具体改进建议1\", \"具体改进建议2\"]\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * 解析独立评估结果
     */
    @SuppressWarnings("unchecked")
    private void parseStandaloneResult(String llmResponse, ComparisonResult result, List<TestCase> aiCases) {
        try {
            String cleaned = cleanJsonResponse(llmResponse);
            Map<String, Object> llmResult = objectMapper.readValue(cleaned, Map.class);

            // 解析总分
            if (llmResult.containsKey("totalScore")) {
                result.setTotalScore(parseDouble(llmResult.get("totalScore")));
            }

            // 解析维度分数
            if (llmResult.containsKey("dimensionScores")) {
                Map<String, Object> dims = (Map<String, Object>) llmResult.get("dimensionScores");

                result.setDefectDetectionScore(parseDouble(dims.getOrDefault("defectDetection", 0)));
                result.setBusinessCoverageScore(parseDouble(dims.getOrDefault("businessCoverage", 0)));
                result.setExecutabilityScore(parseDouble(dims.getOrDefault("executability", 0)));
                result.setAssertionScore(parseDouble(dims.getOrDefault("assertion", 0)));
                result.setStandardScore(parseDouble(dims.getOrDefault("standard", 0)));
                result.setNonRedundancyScore(parseDouble(dims.getOrDefault("nonRedundancy", 0)));
            }

            // 解析总体分析
            if (llmResult.containsKey("overallAnalysis")) {
                result.setOverallAnalysis(String.valueOf(llmResult.get("overallAnalysis")));
            }

            // 解析优点
            if (llmResult.containsKey("strengths")) {
                List<String> strengths = (List<String>) llmResult.get("strengths");
                for (String s : strengths) {
                    result.addStrength(s);
                }
            }

            // 解析缺点
            if (llmResult.containsKey("weaknesses")) {
                List<String> weaknesses = (List<String>) llmResult.get("weaknesses");
                for (String w : weaknesses) {
                    result.addWeakness(w);
                }
            }

            // 解析建议
            if (llmResult.containsKey("suggestions")) {
                List<String> suggestions = (List<String>) llmResult.get("suggestions");
                for (String s : suggestions) {
                    result.addSuggestion(s);
                }
            }

            // 独立评估无匹配详情
            result.setMatchedCount(aiCases.size());
            result.setUnmatchedCount(0);

        } catch (Exception e) {
            logger.warn("解析LLM独立评估结果失败: {}", e.getMessage());
            result.addSuggestion("LLM返回格式解析失败，请检查日志");
        }
    }

    private String callLLMForComparison(List<TestCase> aiCases, List<TestCase> referenceCases) throws Exception {
        return callLLMForComparisonWithPRD(aiCases, referenceCases, null);
    }

    /**
     * 调用LLM进行对比评分（带PRD上下文）
     */
    private String callLLMForComparisonWithPRD(List<TestCase> aiCases, List<TestCase> referenceCases, String prdText)
            throws Exception {
        String systemPrompt = buildSeniorTesterPrompt(prdText);

        // 构建用例数据
        Map<String, Object> comparisonData = new HashMap<>();
        comparisonData.put("aiGeneratedCases", aiCases);
        comparisonData.put("humanReferenceCases", referenceCases);

        String userPrompt = "请对比评估以下AI生成的测试用例与人工标准用例：\n\n" +
                objectMapper.writeValueAsString(comparisonData);

        return volcanoService.chat(systemPrompt, userPrompt);
    }

    /**
     * 构建高级测试工程师评分prompt
     */
    private String buildSeniorTesterPrompt(String prdText) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一位拥有10年经验的高级测试工程师（Senior QA Engineer）。\n");
        prompt.append("现在需要你以专业视角评估【AI自动生成的测试用例】与【人工编写的标准测试用例】的匹配度和质量。\n");
        prompt.append("重要：请全程使用中文进行分析和回复，所有分析内容、建议和评价都必须用中文。\n\n");

        // 如果有PRD，添加PRD上下文
        if (prdText != null && !prdText.trim().isEmpty()) {
            prompt.append("## 原始需求文档（PRD）\n");
            prompt.append("以下是产品需求文档，请基于此文档的功能需求来评估测试用例的覆盖度和质量：\n\n");
            // 截取PRD前2000字符避免过长
            String truncatedPrd = prdText.length() > 200000 ? prdText.substring(0, 200000) + "...（已截断）" : prdText;
            prompt.append("```\n").append(truncatedPrd).append("\n```\n\n");
            prompt.append("请特别关注：\n");
            prompt.append("- AI用例是否完整覆盖了PRD中描述的所有功能点？\n");
            prompt.append("- AI用例是否考虑了PRD中提到的边界条件和异常情况？\n");
            prompt.append("- 人工用例中是否有PRD相关的测试点被AI遗漏？\n\n");
        }

        prompt.append("## 评分维度（满分100分）\n\n");

        prompt.append("### 1. 缺陷发现能力 (25%)\n");
        prompt.append("- AI用例是否覆盖人工用例中的边界值测试？\n");
        prompt.append("- AI用例是否覆盖异常场景（错误处理、权限校验、超时等）？\n");
        prompt.append("- AI用例能否有效发现潜在Bug？\n");
        prompt.append("- 是否有人工用例提到但AI遗漏的关键负面测试？\n\n");

        prompt.append("### 2. 业务覆盖度 (20%)\n");
        prompt.append("- AI用例是否覆盖人工用例的核心业务场景？\n");
        if (prdText != null && !prdText.trim().isEmpty()) {
            prompt.append("- AI用例是否覆盖PRD中描述的所有功能需求？\n");
        }
        prompt.append("- 关键用户路径是否被完整覆盖？\n");
        prompt.append("- 是否遗漏人工用例中的重要功能模块？\n\n");

        prompt.append("### 3. 可执行性 (15%)\n");
        prompt.append("- AI用例的测试步骤是否具体可操作？\n");
        prompt.append("- 测试数据是否明确（而非\"输入有效值\"这类模糊描述）？\n");
        prompt.append("- 新手测试人员能否按步骤执行？\n\n");

        prompt.append("### 4. 断言完整性 (10%)\n");
        prompt.append("- AI用例的预期结果是否明确？\n");
        prompt.append("- 验证点是否充分（UI状态、接口返回、数据库变化）？\n");
        prompt.append("- 是否覆盖人工用例的所有验证点？\n\n");

        prompt.append("### 5. 规范性 (10%)\n");
        prompt.append("- 用例ID、优先级、标签是否规范？\n");
        prompt.append("- 用例结构是否清晰易读？\n\n");

        prompt.append("### 6. 非冗余性 (15%)\n");
        prompt.append("- AI用例之间是否存在重复或高度相似的情况？\n");
        prompt.append("- 是否有多个用例测试相同的功能点？\n");
        prompt.append("- 用例集合是否简洁高效，没有冗余内容？\n\n");

        prompt.append("## 客观评价要求\n\n");
        prompt.append("请客观分析AI生成用例的优缺点：\n");
        prompt.append("1. **优点(strengths)**：AI用例相比人工用例做得好的地方，如：\n");
        prompt.append("   - 覆盖了人工未考虑的场景\n");
        prompt.append("   - 测试步骤更详细具体\n");
        prompt.append("   - 结构更规范清晰\n");
        prompt.append("   - 异常场景考虑更全面\n");
        prompt.append("2. **缺点(weaknesses)**：AI用例的不足之处，如：\n");
        prompt.append("   - 遗漏了人工用例中的关键测试点\n");
        prompt.append("   - 某些测试步骤过于笼统模糊\n");
        prompt.append("   - 存在冗余或重复的用例\n");
        prompt.append("   - 业务理解不到位\n\n");

        prompt.append("## 逐条对比要求（重要！）\n\n");
        prompt.append("**必须对每一条人工用例进行逐条分析和匹配**：\n");
        prompt.append("1. 遍历所有人工用例，为每条人工用例找出AI用例中最匹配的一条\n");
        prompt.append("2. 如果某条人工用例在AI用例中找不到相似的，aiCaseId填null，matchScore填0\n");
        prompt.append("3. 匹配依据：用例标题相似度、测试目标一致性、覆盖场景相同\n");
        prompt.append("4. 每条人工用例必须在matchedCases数组中有对应的记录\n");
        prompt.append("5. 一个AI用例可以匹配多个人工用例（如果它确实覆盖了多个测试点）\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("请返回JSON格式（不要包含```json标记）：\n");
        prompt.append("{\n");
        prompt.append("  \"totalScore\": 0-100,\n");
        prompt.append("  \"dimensionScores\": {\n");
        prompt.append("    \"defectDetection\": 0-100,\n");
        prompt.append("    \"businessCoverage\": 0-100,\n");
        prompt.append("    \"executability\": 0-100,\n");
        prompt.append("    \"assertion\": 0-100,\n");
        prompt.append("    \"standard\": 0-100,\n");
        prompt.append("    \"nonRedundancy\": 0-100\n");
        prompt.append("  },\n");
        prompt.append("  \"matchedCases\": [\n");
        prompt.append("    // 注意：必须包含每一条人工用例的匹配记录！\n");
        prompt.append("    {\n");
        prompt.append("      \"humanCaseId\": \"人工用例的caseId\",\n");
        prompt.append("      \"humanCaseTitle\": \"人工用例标题\",\n");
        prompt.append("      \"aiCaseId\": \"最匹配的AI用例caseId，无匹配填null\",\n");
        prompt.append("      \"aiCaseTitle\": \"匹配的AI用例标题，无匹配填null\",\n");
        prompt.append("      \"matchScore\": 0-100,\n");
        prompt.append("      \"analysis\": \"简要说明匹配理由或未匹配原因\"\n");
        prompt.append("    }\n");
        prompt.append("    // ... 每条人工用例都要有一条记录\n");
        prompt.append("  ],\n");
        prompt.append("  \"unmatchedAiCases\": [\n");
        prompt.append("    // AI用例中有但人工用例没有覆盖的测试点\n");
        prompt.append("    {\"aiCaseId\": \"TC_XXXX\", \"aiCaseTitle\": \"标题\", \"reason\": \"AI额外覆盖的场景说明\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"strengths\": [\"AI用例优点1\", \"AI用例优点2\"],\n");
        prompt.append("  \"weaknesses\": [\"AI用例缺点1\", \"AI用例缺点2\"],\n");
        prompt.append("  \"overallAnalysis\": \"整体评价和改进建议\",\n");
        prompt.append("  \"suggestions\": [\"具体改进建议1\", \"具体改进建议2\"]\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * 解析LLM返回的评分结果
     */
    private void parseComparisonResult(String llmResponse, ComparisonResult result,
            List<TestCase> aiCases, List<TestCase> referenceCases) {
        try {
            // 清理JSON响应
            String cleaned = cleanJsonResponse(llmResponse);

            Map<String, Object> llmResult = objectMapper.readValue(cleaned,
                    new TypeReference<Map<String, Object>>() {
                    });

            // 解析总分
            if (llmResult.containsKey("totalScore")) {
                result.setTotalScore(parseDouble(llmResult.get("totalScore")));
            }

            // 解析维度分数
            if (llmResult.containsKey("dimensionScores")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dims = (Map<String, Object>) llmResult.get("dimensionScores");

                result.setDefectDetectionScore(parseDouble(dims.getOrDefault("defectDetection", 0)));
                result.setBusinessCoverageScore(parseDouble(dims.getOrDefault("businessCoverage", 0)));
                result.setExecutabilityScore(parseDouble(dims.getOrDefault("executability", 0)));
                result.setAssertionScore(parseDouble(dims.getOrDefault("assertion", 0)));
                result.setStandardScore(parseDouble(dims.getOrDefault("standard", 0)));
                result.setNonRedundancyScore(parseDouble(dims.getOrDefault("nonRedundancy", 0)));
            }

            // 解析匹配详情
            if (llmResult.containsKey("matchedCases")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> matches = (List<Map<String, Object>>) llmResult.get("matchedCases");

                int matchedCount = 0;
                for (Map<String, Object> match : matches) {
                    CaseMatchDetail detail = new CaseMatchDetail();
                    detail.setReferenceCaseId(String.valueOf(match.getOrDefault("humanCaseId", "")));
                    detail.setMatchedAiCaseId(String.valueOf(match.getOrDefault("aiCaseId", "")));
                    detail.setMatchScore(parseDouble(match.getOrDefault("matchScore", 0)));
                    detail.setAnalysis(String.valueOf(match.getOrDefault("analysis", "")));
                    
                    // 优先使用LLM返回的标题
                    if (match.containsKey("humanCaseTitle") && match.get("humanCaseTitle") != null) {
                        detail.setReferenceCaseTitle(String.valueOf(match.get("humanCaseTitle")));
                    }
                    if (match.containsKey("aiCaseTitle") && match.get("aiCaseTitle") != null 
                            && !"null".equals(String.valueOf(match.get("aiCaseTitle")))) {
                        detail.setMatchedAiCaseTitle(String.valueOf(match.get("aiCaseTitle")));
                    }

                    // 如果LLM没返回标题，从用例列表中查找
                    if (detail.getReferenceCaseTitle() == null || detail.getReferenceCaseTitle().isEmpty()) {
                        findCaseTitles(detail, aiCases, referenceCases);
                    } else if (detail.getMatchedAiCaseTitle() == null || detail.getMatchedAiCaseTitle().isEmpty()) {
                        // 只查找AI用例标题
                        if (detail.getMatchedAiCaseId() != null && !detail.getMatchedAiCaseId().equals("null")) {
                            for (TestCase ai : aiCases) {
                                if (ai.getCaseId() != null && ai.getCaseId().equals(detail.getMatchedAiCaseId())) {
                                    detail.setMatchedAiCaseTitle(ai.getTitle());
                                    break;
                                }
                            }
                        }
                    }

                    detail.setMatched(detail.getMatchedAiCaseId() != null &&
                            !detail.getMatchedAiCaseId().isEmpty() &&
                            !detail.getMatchedAiCaseId().equals("null"));

                    if (detail.isMatched())
                        matchedCount++;
                    result.getMatchDetails().add(detail);
                }

                result.setMatchedCount(matchedCount);
                result.setUnmatchedCount(referenceCases.size() - matchedCount);
            } else {
                // 如果LLM没有返回matchedCases，为每个人工用例创建默认记录
                logger.warn("LLM未返回matchedCases数组，创建默认匹配记录");
                for (TestCase refCase : referenceCases) {
                    CaseMatchDetail detail = new CaseMatchDetail();
                    detail.setReferenceCaseId(refCase.getCaseId());
                    detail.setReferenceCaseTitle(refCase.getTitle());
                    detail.setMatchedAiCaseId(null);
                    detail.setMatchedAiCaseTitle(null);
                    detail.setMatchScore(0);
                    detail.setMatched(false);
                    detail.setAnalysis("LLM未返回此用例的匹配结果");
                    result.getMatchDetails().add(detail);
                }
                result.setMatchedCount(0);
                result.setUnmatchedCount(referenceCases.size());
            }
            
            // 解析AI额外覆盖的用例（人工用例未覆盖的部分）
            if (llmResult.containsKey("unmatchedAiCases")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> unmatchedAi = (List<Map<String, Object>>) llmResult.get("unmatchedAiCases");
                result.addScoreDetail("unmatchedAiCasesCount", unmatchedAi.size());
                
                // 将AI额外覆盖的场景添加到优点中
                if (!unmatchedAi.isEmpty()) {
                    StringBuilder extraCoverage = new StringBuilder("AI额外覆盖了" + unmatchedAi.size() + "个人工用例未涉及的测试点");
                    result.addStrength(extraCoverage.toString());
                }
            }

            // 解析总体分析
            if (llmResult.containsKey("overallAnalysis")) {
                result.setOverallAnalysis(String.valueOf(llmResult.get("overallAnalysis")));
            }

            // 解析AI用例优点
            if (llmResult.containsKey("strengths")) {
                @SuppressWarnings("unchecked")
                List<String> strengths = (List<String>) llmResult.get("strengths");
                for (String s : strengths) {
                    result.addStrength(s);
                }
            }

            // 解析AI用例缺点
            if (llmResult.containsKey("weaknesses")) {
                @SuppressWarnings("unchecked")
                List<String> weaknesses = (List<String>) llmResult.get("weaknesses");
                for (String w : weaknesses) {
                    result.addWeakness(w);
                }
            }

            // 解析建议
            if (llmResult.containsKey("suggestions")) {
                @SuppressWarnings("unchecked")
                List<String> suggestions = (List<String>) llmResult.get("suggestions");
                for (String s : suggestions) {
                    result.addSuggestion(s);
                }
            }

        } catch (Exception e) {
            logger.warn("解析LLM评分结果失败: {}", e.getMessage());
            result.addSuggestion("LLM返回格式解析失败，请检查日志");
        }
    }

    /**
     * 清理JSON响应
     */
    private String cleanJsonResponse(String response) {
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("LLM返回内容为空");
        }

        String cleaned = response.trim();

        // 移除Markdown代码块标记
        cleaned = cleaned.replaceAll("```json\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*", "");
        cleaned = cleaned.trim();

        // 提取JSON对象
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        // 检测并修复截断的JSON
        cleaned = fixTruncatedJson(cleaned);

        return cleaned;
    }

    /**
     * 修复截断的JSON
     */
    private String fixTruncatedJson(String json) {
        int openBraces = 0, closeBraces = 0;
        int openBrackets = 0, closeBrackets = 0;

        for (char c : json.toCharArray()) {
            if (c == '{')
                openBraces++;
            else if (c == '}')
                closeBraces++;
            else if (c == '[')
                openBrackets++;
            else if (c == ']')
                closeBrackets++;
        }

        if (openBraces == closeBraces && openBrackets == closeBrackets) {
            return json;
        }

        logger.warn("检测到JSON被截断，正在修复...");

        StringBuilder fixed = new StringBuilder(json);
        while (openBrackets > closeBrackets) {
            fixed.append("]");
            closeBrackets++;
        }
        while (openBraces > closeBraces) {
            fixed.append("}");
            closeBraces++;
        }

        return fixed.toString();
    }

    /**
     * 查找用例标题
     */
    private void findCaseTitles(CaseMatchDetail detail, List<TestCase> aiCases, List<TestCase> refCases) {
        // 查找人工用例标题
        for (TestCase ref : refCases) {
            if (ref.getCaseId() != null && ref.getCaseId().equals(detail.getReferenceCaseId())) {
                detail.setReferenceCaseTitle(ref.getTitle());
                break;
            }
        }

        // 查找AI用例标题
        if (detail.getMatchedAiCaseId() != null && !detail.getMatchedAiCaseId().equals("null")) {
            for (TestCase ai : aiCases) {
                if (ai.getCaseId() != null && ai.getCaseId().equals(detail.getMatchedAiCaseId())) {
                    detail.setMatchedAiCaseTitle(ai.getTitle());
                    break;
                }
            }
        }
    }

    /**
     * 安全解析double
     */
    private double parseDouble(Object value) {
        if (value == null)
            return 0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 打印对比报告
     */
    public void printReport(ComparisonResult result) {
        System.out.println("\n========== 高级测试工程师评分报告 ==========");
        System.out.println(String.format("总分: %.1f / 100", result.getTotalScore()));
        System.out.println("\n各维度得分:");
        System.out.println(String.format("  1. 缺陷发现能力 (30%%): %.1f", result.getDefectDetectionScore()));
        System.out.println(String.format("  2. 业务覆盖度  (25%%): %.1f", result.getBusinessCoverageScore()));
        System.out.println(String.format("  3. 可执行性    (20%%): %.1f", result.getExecutabilityScore()));
        System.out.println(String.format("  4. 断言完整性  (15%%): %.1f", result.getAssertionScore()));
        System.out.println(String.format("  5. 规范性      (10%%): %.1f", result.getStandardScore()));

        System.out.println(String.format("\n匹配统计: %d/%d 用例匹配",
                result.getMatchedCount(),
                result.getMatchedCount() + result.getUnmatchedCount()));

        if (result.getOverallAnalysis() != null && !result.getOverallAnalysis().isEmpty()) {
            System.out.println("\n总体分析:");
            System.out.println("  " + result.getOverallAnalysis());
        }

        if (!result.getSuggestions().isEmpty()) {
            System.out.println("\n改进建议:");
            for (String s : result.getSuggestions()) {
                System.out.println("  - " + s);
            }
        }
        System.out.println("================================================\n");
    }
}
