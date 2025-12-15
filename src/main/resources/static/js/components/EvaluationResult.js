const EvaluationResult = {
    template: `
        <div class="evaluation-result">
            <div v-if="!result" style="padding: 40px; text-align: center;">
                <el-empty description="暂无评测数据">
                    <template #description>
                        <p style="color: var(--text-secondary);">请先生成测试用例以查看质量评测报告</p>
                    </template>
                </el-empty>
            </div>
            
            <div v-else class="result-container">
                <!-- 总分卡片 -->
                <el-card shadow="hover" class="glass-card score-card" style="margin-bottom: 20px; text-align: center;">
                    <div style="display: flex; justify-content: center; align-items: center; gap: 40px;">
                        <div style="position: relative;">
                            <el-progress 
                                type="dashboard" 
                                :percentage="result.totalScore" 
                                :width="160" 
                                :stroke-width="12"
                                :color="getScoreColor(result.totalScore)"
                            >
                                <template #default="{ percentage }">
                                    <div class="score-value" :style="{ color: getScoreColor(percentage) }">{{ percentage.toFixed(2) }}</div>
                                    <div class="score-label">综合评分</div>
                                </template>
                            </el-progress>
                        </div>
                        
                        <div style="text-align: left; max-width: 400px;">
                            <h3 style="margin: 0 0 10px 0; color: #f1f5f9;">评测总结</h3>
                            <div style="color: #e2e8f0; line-height: 1.6;">
                                {{ result.summary || getSummaryText(result.totalScore) }}
                            </div>
                        </div>
                    </div>
                </el-card>

                <!-- 核心维度评分 -->
                <el-row :gutter="16" style="margin-bottom: 20px;">
                    <el-col :span="4" :offset="2" v-for="(item, key) in dimensionMap" :key="key">
                        <el-card shadow="hover" class="glass-card dimension-card">
                            <div style="text-align: center;">
                                <el-progress 
                                    type="circle" 
                                    :percentage="Math.min(100, result[item.prop] || 0)" 
                                    :width="70" 
                                    :stroke-width="6"
                                    :color="getScoreColor(result[item.prop] || 0)"
                                >
                                    <template #default="{ percentage }">
                                        <span style="font-size: 14px; font-weight: 600;">{{ percentage.toFixed(2) }}</span>
                                    </template>
                                </el-progress>
                                <div style="margin-top: 8px; font-weight: 600; color: #f1f5f9; font-size: 13px;">{{ item.label }}</div>
                                <div style="font-size: 11px; color: #cbd5e1; margin-top: 2px;">{{ item.desc }}</div>
                            </div>
                        </el-card>
                    </el-col>
                </el-row>

                <!-- 详细指标 -->
                <el-row :gutter="20">
                    <el-col :span="16">
                        <el-card shadow="never" class="glass-panel" style="height: 100%;">
                            <template #header>
                                <div style="display: flex; align-items: center;">
                                    <el-icon style="margin-right: 6px; color: var(--accent-primary);"><DataAnalysis /></el-icon>
                                    <span style="font-weight: 600;">详细指标分析</span>
                                </div>
                            </template>
                            
                            <el-descriptions :column="2" border class="glass-descriptions">
                                <el-descriptions-item label="场景覆盖">
                                    <el-tag size="small" :type="getScoreType(result.details?.sceneTypeCoverage)">
                                        {{ (result.details?.sceneTypeCoverage || 0).toFixed(2) }}
                                    </el-tag>
                                </el-descriptions-item>
                                <el-descriptions-item label="模块覆盖">
                                    <el-tag size="small" :type="getScoreType(result.details?.moduleCoverage)">
                                        {{ (result.details?.moduleCoverage || 0).toFixed(2) }}
                                    </el-tag>
                                </el-descriptions-item>
                                <el-descriptions-item label="边界值测试">
                                    <span style="margin-right: 8px; color: #f1f5f9;">{{ result.details?.boundaryCount || 0 }} 个</span>
                                    <el-tag size="small" effect="plain" type="warning">{{ result.details?.boundaryRatio || '0%' }}</el-tag>
                                </el-descriptions-item>
                                <el-descriptions-item label="异常场景">
                                    <span style="margin-right: 8px; color: #f1f5f9;">{{ result.details?.exceptionCount || 0 }} 个</span>
                                    <el-tag size="small" effect="plain" type="danger">{{ result.details?.exceptionRatio || '0%' }}</el-tag>
                                </el-descriptions-item>
                                <el-descriptions-item label="业务动作">
                                    <el-tag size="small" type="info">{{ (result.details?.actionCoverage || 0).toFixed(2) }} 分</el-tag>
                                </el-descriptions-item>
                            </el-descriptions>

                            <div style="margin-top: 20px;">
                                <div style="font-size: 14px; font-weight: 600; margin-bottom: 10px; color: #f1f5f9;">
                                    <el-icon><InfoFilled /></el-icon> 优化建议
                                </div>
                                <el-empty v-if="!result.suggestions || result.suggestions.length === 0" description="暂无建议" :image-size="60" />
                                <div v-else class="suggestion-list">
                                    <div v-for="(suggestion, index) in result.suggestions" :key="index" class="suggestion-item" style="color: #e2e8f0; margin-bottom: 8px; display: flex; align-items: start;">
                                        <el-icon color="#f59e0b" style="margin-right: 8px; margin-top: 2px;"><Warning /></el-icon>
                                        <span>{{ suggestion }}</span>
                                    </div>
                                </div>
                            </div>
                        </el-card>
                    </el-col>
                    
                    <el-col :span="8">
                        <el-card shadow="never" class="glass-panel" style="height: 100%;">
                            <template #header>
                                <div style="display: flex; align-items: center;">
                                    <el-icon style="margin-right: 6px; color: var(--success-color);"><PieChart /></el-icon>
                                    <span style="font-weight: 600;">评分说明</span>
                                </div>
                            </template>
                            <el-timeline>
                                <el-timeline-item color="#ef4444" timestamp="缺陷发现 (25%)">
                                    <span style="color: #f1f5f9; font-size: 13px;">评估用例发现潜在缺陷的能力，包括边界值、异常场景覆盖</span>
                                </el-timeline-item>
                                <el-timeline-item color="#4ade80" timestamp="业务覆盖 (25%)">
                                    <span style="color: #f1f5f9; font-size: 13px;">考察业务场景、功能模块及核心流程的覆盖全面性</span>
                                </el-timeline-item>
                                <el-timeline-item color="#38bdf8" timestamp="可执行性 (20%)">
                                    <span style="color: #f1f5f9; font-size: 13px;">评估测试步骤是否清晰、可操作，预期结果是否明确</span>
                                </el-timeline-item>
                                <el-timeline-item color="#f472b6" timestamp="断言完整 (15%)">
                                    <span style="color: #f1f5f9; font-size: 13px;">检查验证点是否完整，断言规则是否充分</span>
                                </el-timeline-item>
                                <el-timeline-item color="#a78bfa" timestamp="规范性 (15%)">
                                    <span style="color: #f1f5f9; font-size: 13px;">检查用例ID格式、必填项完整性、结构规范等指标</span>
                                </el-timeline-item>
                            </el-timeline>
                        </el-card>
                    </el-col>
                </el-row>
            </div>
        </div>
    `,
    props: {
        result: {
            type: Object,
            default: null
        },
        loading: {
            type: Boolean,
            default: false
        }
    },
    setup(props) {
        const dimensionMap = {
            defect: { label: '缺陷发现', prop: 'robustnessScore', desc: '边界/异常/潜在BUG' },
            business: { label: '业务覆盖', prop: 'coverageScore', desc: '场景/模块/流程' },
            executable: { label: '可执行性', prop: 'qualityScore', desc: '步骤/预期/清晰度' },
            assertion: { label: '断言完整', prop: 'assertionScore', desc: '验证点/断言规则' },
            standard: { label: '规范性', prop: 'standardScore', desc: '格式/完整性' }
        };

        const getScoreColor = (score) => {
            if (score >= 80) return '#4ade80';
            if (score >= 60) return '#fbbf24';
            return '#f87171';
        };

        const getScoreType = (score) => {
            if (score >= 80) return 'success';
            if (score >= 60) return 'warning';
            return 'danger';
        };

        const getSummaryText = (score) => {
            if (score >= 90) return '测试用例质量极佳！覆盖全面，健壮性测试充分，步骤清晰规范。';
            if (score >= 80) return '测试用例质量优秀。功能覆盖较好，包含必要的异常场景，建议关注细节优化。';
            if (score >= 60) return '测试用例质量良好。基本覆盖主要功能，但边界值和异常场景测试有待加强。';
            return '测试用例质量一般。建议补充更多边界条件和异常流程测试，提高步骤详细度。';
        };

        return {
            dimensionMap,
            getScoreColor,
            getScoreType,
            getSummaryText
        };
    }
};

window.EvaluationResult = EvaluationResult;
