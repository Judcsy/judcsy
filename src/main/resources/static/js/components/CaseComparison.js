const CaseComparison = {
    template: `
        <div class="case-comparison">
            <div v-if="!aiCases.length && !localReferenceCases.length" style="padding: 40px; text-align: center;">
                <el-empty description="暂无数据">
                    <template #description>
                        <p style="color: var(--text-secondary);">请先在左侧输入PRD并生成测试用例</p>
                    </template>
                    <el-button type="primary" @click="$emit('switch-tab', 'prd')">去生成用例</el-button>
                </el-empty>
            </div>
            
            <div v-else class="comparison-container">
                <!-- 评分标准说明 -->
                <el-card shadow="never" class="glass-panel" style="margin-bottom: 20px;">
                    <template #header>
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span style="color: #fbbf24; font-weight: 600;">
                                <el-icon><Document /></el-icon> 评分标准
                            </span>
                            <el-button text type="primary" size="small" @click="criteriaExpanded = !criteriaExpanded">
                                {{ criteriaExpanded ? '收起' : '展开' }}
                            </el-button>
                        </div>
                    </template>
                    
                    <el-collapse-transition>
                        <div v-show="criteriaExpanded">
                            <el-descriptions :column="1" border class="glass-descriptions" size="small">
                                <el-descriptions-item>
                                    <template #label>
                                        <el-tag type="danger" effect="dark" size="small">25%</el-tag> 缺陷发现能力
                                    </template>
                                    边界值测试、异常场景覆盖、错误处理、权限校验、超时等负面测试
                                </el-descriptions-item>
                                <el-descriptions-item>
                                    <template #label>
                                        <el-tag type="warning" effect="dark" size="small">20%</el-tag> 业务覆盖度
                                    </template>
                                    核心业务场景、关键用户路径、功能模块完整性
                                </el-descriptions-item>
                                <el-descriptions-item>
                                    <template #label>
                                        <el-tag type="primary" effect="dark" size="small">15%</el-tag> 可执行性
                                    </template>
                                    测试步骤具体可操作、测试数据明确、新手可执行
                                </el-descriptions-item>
                                <el-descriptions-item>
                                    <template #label>
                                        <el-tag type="success" effect="dark" size="small">10%</el-tag> 断言完整性
                                    </template>
                                    预期结果明确、验证点充分（UI/接口/数据库）
                                </el-descriptions-item>
                                <el-descriptions-item>
                                    <template #label>
                                        <el-tag type="info" effect="dark" size="small">10%</el-tag> 规范性
                                    </template>
                                    用例ID、优先级、标签规范，结构清晰易读
                                </el-descriptions-item>
                                <el-descriptions-item>
                                    <template #label>
                                        <el-tag effect="dark" size="small" style="background: #8b5cf6; border-color: #8b5cf6;">20%</el-tag> 非冗余性
                                    </template>
                                    用例无重复、不存在高度相似的冗余内容、用例集简洁高效
                                </el-descriptions-item>
                            </el-descriptions>
                        </div>
                    </el-collapse-transition>
                </el-card>

                <!-- 评分操作栏 -->
                <div v-if="!internalComparisonResult && aiCases.length > 0" style="text-align: center; margin-bottom: 20px;">
                    <!-- 有人工用例：对比评分模式 -->
                    <div v-if="localReferenceCases.length > 0">
                        <el-button 
                            type="success" 
                            size="large" 
                            @click="performComparison" 
                            :loading="comparing"
                        >
                            <el-icon><Connection /></el-icon> 开始AI对比评分
                        </el-button>
                        <div style="margin-top: 8px; font-size: 12px; color: #94a3b8;">
                            将对比 {{ aiCases.length }} 条AI用例 与 {{ localReferenceCases.length }} 条人工标准用例
                        </div>
                    </div>
                    <!-- 无人工用例：独立评估模式 -->
                    <div v-else>
                        <el-button 
                            type="primary" 
                            size="large" 
                            @click="performComparison" 
                            :loading="comparing"
                        >
                            <el-icon><Aim /></el-icon> 开始AI独立评估
                        </el-button>
                        <div style="margin-top: 8px; font-size: 12px; color: #94a3b8;">
                            将直接评估 {{ aiCases.length }} 条AI生成用例的质量（无需人工用例）
                        </div>
                        <div style="margin-top: 12px;">
                            <el-alert
                                title="提示：添加人工标准用例后可进行更全面的对比评分"
                                type="info"
                                :closable="false"
                                show-icon
                                style="background: rgba(59, 130, 246, 0.1); border: none;"
                            />
                        </div>
                    </div>
                </div>

                <!-- 对比评分结果 -->
                <el-card 
                    v-if="internalComparisonResult" 
                    shadow="never" 
                    class="glass-panel" 
                    style="margin-bottom: 20px;"
                >
                    <template #header>
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span style="color: #f472b6; font-weight: 600;">
                                <el-icon><DataAnalysis /></el-icon> 
                                {{ internalComparisonResult.scoreDetails && internalComparisonResult.scoreDetails.evaluationMode === 'standalone' ? 'AI独立评估结果' : '对比评分结果' }}
                            </span>
                            <div style="display: flex; align-items: center; gap: 12px;">
                                <el-tag 
                                    :type="internalComparisonResult.scoreDetails && internalComparisonResult.scoreDetails.evaluationMode === 'standalone' ? 'primary' : 'warning'" 
                                    effect="plain" 
                                    size="small"
                                >
                                    {{ internalComparisonResult.scoreDetails && internalComparisonResult.scoreDetails.evaluationMode === 'standalone' ? '独立评估' : '对比评分' }}
                                </el-tag>
                                <el-tag 
                                    :type="internalComparisonResult.scoreMethod === 'LLM' ? 'success' : 'danger'" 
                                    effect="plain" 
                                    size="small"
                                >
                                    {{ internalComparisonResult.scoreMethod === 'LLM' ? 'LLM评分' : 'LLM调用失败' }}
                                </el-tag>
                                <el-tag v-if="internalComparisonResult.scoringTimeMs" type="info" effect="plain" size="small">
                                    耗时: {{ internalComparisonResult.scoringTimeMs }}ms
                                </el-tag>
                                <el-tag :type="getScoreType(internalComparisonResult.totalScore)" effect="dark" size="large">
                                    综合评分: {{ internalComparisonResult.totalScore.toFixed(2) }}分
                                </el-tag>
                            </div>
                        </div>
                    </template>
                    
                    <!-- 维度评分 -->
                    <el-row :gutter="16" style="margin-bottom: 20px;" :justify="Object.keys(dimensionScores).length === 5 ? 'center' : 'start'">
                        <el-col :span="4" v-for="(item, key) in dimensionScores" :key="key">
                            <div style="text-align: center; padding: 12px; background: rgba(30, 41, 59, 0.5); border-radius: 8px;">
                                <el-progress 
                                    type="circle" 
                                    :percentage="Math.min(100, Math.round(item.score))" 
                                    :width="70" 
                                    :stroke-width="6"
                                    :color="getProgressColor(item.score)"
                                >
                                    <template #default="{ percentage }">
                                        <span style="font-size: 12px; font-weight: 600;">{{ item.score.toFixed(2) }}</span>
                                    </template>
                                </el-progress>
                                <div style="margin-top: 8px; font-size: 12px; color: #e2e8f0;">
                                    {{ item.label }}
                                </div>
                                <div style="font-size: 10px; color: #94a3b8;">
                                    {{ item.weight }}
                                </div>
                            </div>
                        </el-col>
                    </el-row>
                    
                    <!-- 评分细节 -->
                    <div v-if="internalComparisonResult.scoreDetails && Object.keys(internalComparisonResult.scoreDetails).length > 0" 
                         style="margin-bottom: 16px; padding: 12px; background: rgba(30, 41, 59, 0.5); border-radius: 8px;">
                        <div style="font-size: 13px; font-weight: 600; margin-bottom: 10px; color: #f1f5f9;">
                            <el-icon><Histogram /></el-icon> 评分细节
                        </div>
                        <el-row :gutter="12">
                            <el-col :span="6" v-if="internalComparisonResult.scoreDetails.coverageRate">
                                <div class="detail-item">
                                    <span class="detail-label">用例覆盖率</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.coverageRate }}</span>
                                </div>
                            </el-col>
                            <el-col :span="6" v-if="internalComparisonResult.scoreDetails.moduleOverlap">
                                <div class="detail-item">
                                    <span class="detail-label">模块覆盖</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.moduleOverlap }}</span>
                                </div>
                            </el-col>
                            <el-col :span="6" v-if="internalComparisonResult.scoreDetails.stepsMatchRate">
                                <div class="detail-item">
                                    <span class="detail-label">步骤匹配</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.stepsMatchRate }}</span>
                                </div>
                            </el-col>
                            <el-col :span="6" v-if="internalComparisonResult.scoreDetails.exceptionMatchRate">
                                <div class="detail-item">
                                    <span class="detail-label">异常场景</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.exceptionMatchRate }}</span>
                                </div>
                            </el-col>
                            <el-col :span="6" v-if="internalComparisonResult.scoreDetails.redundancyRate">
                                <div class="detail-item">
                                    <span class="detail-label">冗余率</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.redundancyRate }}</span>
                                </div>
                            </el-col>
                        </el-row>
                        <el-row :gutter="12" style="margin-top: 8px;" v-if="internalComparisonResult.scoreDetails.aiCaseCount">
                            <el-col :span="6">
                                <div class="detail-item">
                                    <span class="detail-label">AI用例数</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.aiCaseCount }}</span>
                                </div>
                            </el-col>
                            <el-col :span="6">
                                <div class="detail-item">
                                    <span class="detail-label">人工用例数</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.refCaseCount }}</span>
                                </div>
                            </el-col>
                            <el-col :span="6" v-if="internalComparisonResult.scoreDetails.refModuleCount">
                                <div class="detail-item">
                                    <span class="detail-label">模块数</span>
                                    <span class="detail-value">{{ internalComparisonResult.scoreDetails.refModuleCount }} / {{ internalComparisonResult.scoreDetails.aiModuleCount }}</span>
                                </div>
                            </el-col>
                            <el-col :span="6" v-if="internalComparisonResult.scoreDetails.hasPrdContext !== undefined">
                                <div class="detail-item">
                                    <span class="detail-label">PRD上下文</span>
                                    <el-tag :type="internalComparisonResult.scoreDetails.hasPrdContext ? 'success' : 'info'" size="small">
                                        {{ internalComparisonResult.scoreDetails.hasPrdContext ? '已使用' : '未提供' }}
                                    </el-tag>
                                </div>
                            </el-col>
                        </el-row>
                    </div>
                    
                    <!-- 匹配详情 - 仅在对比模式下显示 -->
                    <el-descriptions 
                        v-if="!(internalComparisonResult.scoreDetails && internalComparisonResult.scoreDetails.evaluationMode === 'standalone')"
                        :column="2" border class="glass-descriptions" style="margin-bottom: 16px;">
                        <el-descriptions-item label="匹配用例数">
                            <el-tag type="success" effect="dark">{{ internalComparisonResult.matchedCount }} 个</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="未匹配用例数">
                            <el-tag type="danger" effect="dark">{{ internalComparisonResult.unmatchedCount }} 个</el-tag>
                        </el-descriptions-item>
                    </el-descriptions>
                    
                    <!-- 独立评估模式下显示用例数量 -->
                    <el-descriptions 
                        v-else
                        :column="2" border class="glass-descriptions" style="margin-bottom: 16px;">
                        <el-descriptions-item label="评估用例数">
                            <el-tag type="primary" effect="dark">{{ internalComparisonResult.scoreDetails.aiCaseCount || aiCases.length }} 个</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="评估模式">
                            <el-tag type="info" effect="dark">独立评估（无人工参照）</el-tag>
                        </el-descriptions-item>
                    </el-descriptions>
                    
                    <!-- 总体分析 -->
                    <div v-if="internalComparisonResult.overallAnalysis" style="margin-bottom: 16px; padding: 12px; background: rgba(30, 41, 59, 0.5); border-radius: 8px;">
                        <div style="font-size: 13px; font-weight: 600; margin-bottom: 8px; color: #f1f5f9;">
                            <el-icon><ChatLineRound /></el-icon> {{ internalComparisonResult.scoreMethod === 'LLM' ? 'LLM分析' : '评分分析' }}
                        </div>
                        <div style="font-size: 13px; color: #e2e8f0; line-height: 1.6;">
                            {{ internalComparisonResult.overallAnalysis }}
                        </div>
                    </div>
                    
                    <!-- AI用例优缺点分析 -->
                    <el-row :gutter="16" style="margin-bottom: 16px;" v-if="(internalComparisonResult.strengths && internalComparisonResult.strengths.length) || (internalComparisonResult.weaknesses && internalComparisonResult.weaknesses.length)">
                        <!-- 优点 -->
                        <el-col :span="12">
                            <div style="padding: 12px; background: rgba(74, 222, 128, 0.1); border: 1px solid rgba(74, 222, 128, 0.3); border-radius: 8px; height: 100%;">
                                <div style="font-size: 13px; font-weight: 600; margin-bottom: 10px; color: #4ade80; display: flex; align-items: center; gap: 6px;">
                                    <el-icon><CircleCheckFilled /></el-icon> AI用例优点
                                    <el-tag type="success" size="small" effect="plain" v-if="internalComparisonResult.strengths">{{ internalComparisonResult.strengths.length }}项</el-tag>
                                </div>
                                <div v-if="internalComparisonResult.strengths && internalComparisonResult.strengths.length">
                                    <div 
                                        v-for="(strength, idx) in internalComparisonResult.strengths" 
                                        :key="'s'+idx"
                                        style="font-size: 13px; color: #e2e8f0; padding: 6px 0; padding-left: 8px; border-left: 2px solid #4ade80; margin-bottom: 6px; background: rgba(74, 222, 128, 0.05); border-radius: 0 4px 4px 0;"
                                    >
                                        {{ strength }}
                                    </div>
                                </div>
                                <div v-else style="font-size: 13px; color: #94a3b8; font-style: italic;">
                                    暂无优点分析
                                </div>
                            </div>
                        </el-col>
                        <!-- 缺点 -->
                        <el-col :span="12">
                            <div style="padding: 12px; background: rgba(248, 113, 113, 0.1); border: 1px solid rgba(248, 113, 113, 0.3); border-radius: 8px; height: 100%;">
                                <div style="font-size: 13px; font-weight: 600; margin-bottom: 10px; color: #f87171; display: flex; align-items: center; gap: 6px;">
                                    <el-icon><CircleCloseFilled /></el-icon> AI用例缺点
                                    <el-tag type="danger" size="small" effect="plain" v-if="internalComparisonResult.weaknesses">{{ internalComparisonResult.weaknesses.length }}项</el-tag>
                                </div>
                                <div v-if="internalComparisonResult.weaknesses && internalComparisonResult.weaknesses.length">
                                    <div 
                                        v-for="(weakness, idx) in internalComparisonResult.weaknesses" 
                                        :key="'w'+idx"
                                        style="font-size: 13px; color: #e2e8f0; padding: 6px 0; padding-left: 8px; border-left: 2px solid #f87171; margin-bottom: 6px; background: rgba(248, 113, 113, 0.05); border-radius: 0 4px 4px 0;"
                                    >
                                        {{ weakness }}
                                    </div>
                                </div>
                                <div v-else style="font-size: 13px; color: #94a3b8; font-style: italic;">
                                    暂无缺点分析
                                </div>
                            </div>
                        </el-col>
                    </el-row>
                    
                    <!-- 建议 -->
                    <div v-if="internalComparisonResult.suggestions && internalComparisonResult.suggestions.length">
                        <div style="font-size: 13px; font-weight: 600; margin-bottom: 8px; color: #f1f5f9;">
                            <el-icon><InfoFilled /></el-icon> 优化建议
                        </div>
                        <div 
                            v-for="(suggestion, idx) in internalComparisonResult.suggestions" 
                            :key="idx"
                            style="font-size: 13px; color: #e2e8f0; padding: 4px 0; padding-left: 16px;"
                        >
                            • {{ suggestion }}
                        </div>
                    </div>
                </el-card>

                <!-- 详细匹配对比视图 -->
                <el-card 
                    v-if="internalComparisonResult && internalComparisonResult.matchDetails && internalComparisonResult.matchDetails.length" 
                    shadow="never" 
                    class="glass-panel" 
                    style="margin-bottom: 20px;"
                >
                    <template #header>
                        <span style="color: #a78bfa; font-weight: 600;">
                            <el-icon><Connection /></el-icon> 逐条对比详情
                        </span>
                    </template>
                    
                    <el-collapse accordion>
                        <el-collapse-item 
                            v-for="(detail, idx) in internalComparisonResult.matchDetails" 
                            :key="idx"
                            :name="idx"
                        >
                            <template #title>
                                <div style="display: flex; align-items: center; gap: 12px; width: 100%;">
                                    <el-tag :type="detail.matched ? 'success' : 'danger'" size="small">
                                        {{ detail.matched ? '已匹配' : '未匹配' }}
                                    </el-tag>
                                    <span style="color: #4ade80;">{{ detail.referenceCaseId }}</span>
                                    <span style="color: #e2e8f0; flex: 1;">{{ detail.referenceCaseTitle }}</span>
                                    <el-tag :type="getScoreType(detail.matchScore)" size="small">
                                        {{ detail.matchScore.toFixed(2) }}%
                                    </el-tag>
                                </div>
                            </template>
                            
                            <!-- 左右对比视图 -->
                            <el-row :gutter="16">
                                <el-col :span="12">
                                    <div style="background: rgba(74, 222, 128, 0.1); border: 1px solid rgba(74, 222, 128, 0.3); border-radius: 8px; padding: 12px;">
                                        <div style="font-weight: 600; color: #4ade80; margin-bottom: 8px;">
                                            <el-icon><User /></el-icon> 人工用例
                                        </div>
                                        <div style="font-size: 13px; color: #e2e8f0;">
                                            <div><strong>ID:</strong> {{ detail.referenceCaseId }}</div>
                                            <div><strong>标题:</strong> {{ detail.referenceCaseTitle }}</div>
                                            <div v-if="getRefCaseById(detail.referenceCaseId)">
                                                <div><strong>模块:</strong> {{ getRefCaseById(detail.referenceCaseId).module }}</div>
                                                <div><strong>场景:</strong> {{ getSceneTypeName(getRefCaseById(detail.referenceCaseId).sceneType) }}</div>
                                                <div><strong>优先级:</strong> {{ getRefCaseById(detail.referenceCaseId).priority }}</div>
                                                <div v-if="getRefCaseById(detail.referenceCaseId).frontEndSteps">
                                                    <strong>前端步骤:</strong> {{ getRefCaseById(detail.referenceCaseId).frontEndSteps.length }} 步
                                                </div>
                                                <div v-if="getRefCaseById(detail.referenceCaseId).backEndSteps">
                                                    <strong>后端步骤:</strong> {{ getRefCaseById(detail.referenceCaseId).backEndSteps.length }} 步
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </el-col>
                                <el-col :span="12">
                                    <div :style="{
                                        background: detail.matched ? 'rgba(56, 189, 248, 0.1)' : 'rgba(248, 113, 113, 0.1)',
                                        border: detail.matched ? '1px solid rgba(56, 189, 248, 0.3)' : '1px solid rgba(248, 113, 113, 0.3)',
                                        borderRadius: '8px',
                                        padding: '12px'
                                    }">
                                        <div :style="{fontWeight: 600, color: detail.matched ? '#38bdf8' : '#f87171', marginBottom: '8px'}">
                                            <el-icon><MagicStick /></el-icon> {{ detail.matched ? 'AI匹配用例' : '无匹配' }}
                                        </div>
                                        <div v-if="detail.matched && detail.matchedAiCaseId" style="font-size: 13px; color: #e2e8f0;">
                                            <div><strong>ID:</strong> {{ detail.matchedAiCaseId }}</div>
                                            <div><strong>标题:</strong> {{ detail.matchedAiCaseTitle }}</div>
                                            <div v-if="getAiCaseById(detail.matchedAiCaseId)">
                                                <div><strong>模块:</strong> {{ getAiCaseById(detail.matchedAiCaseId).module }}</div>
                                                <div><strong>场景:</strong> {{ getSceneTypeName(getAiCaseById(detail.matchedAiCaseId).sceneType) }}</div>
                                                <div><strong>优先级:</strong> {{ getAiCaseById(detail.matchedAiCaseId).priority }}</div>
                                                <div v-if="getAiCaseById(detail.matchedAiCaseId).frontEndSteps">
                                                    <strong>前端步骤:</strong> {{ getAiCaseById(detail.matchedAiCaseId).frontEndSteps.length }} 步
                                                </div>
                                                <div v-if="getAiCaseById(detail.matchedAiCaseId).backEndSteps">
                                                    <strong>后端步骤:</strong> {{ getAiCaseById(detail.matchedAiCaseId).backEndSteps.length }} 步
                                                </div>
                                            </div>
                                        </div>
                                        <div v-else style="font-size: 13px; color: #f87171;">
                                            AI生成的用例中未找到对应匹配项
                                        </div>
                                    </div>
                                </el-col>
                            </el-row>
                            
                            <!-- LLM分析 -->
                            <div v-if="detail.analysis" style="margin-top: 12px; padding: 10px; background: rgba(30, 41, 59, 0.5); border-radius: 6px;">
                                <div style="font-size: 12px; color: #94a3b8; margin-bottom: 4px;">LLM匹配分析:</div>
                                <div style="font-size: 13px; color: #e2e8f0;">{{ detail.analysis }}</div>
                            </div>
                        </el-collapse-item>
                    </el-collapse>
                </el-card>

                <!-- AI用例列表 -->
                <el-card shadow="never" class="glass-panel" style="margin-top: 20px;">
                    <template #header>
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span style="color: var(--accent-primary); font-weight: 600;"><el-icon><MagicStick /></el-icon> AI生成用例 ({{ aiCases.length }})</span>
                            <el-tag type="primary" size="small" effect="plain">自动</el-tag>
                        </div>
                    </template>
                    <div style="max-height: 400px; overflow-y: auto; padding-right: 5px;">
                        <el-empty v-if="aiCases.length === 0" description="暂无AI生成用例" />
                        <el-row v-else :gutter="12">
                            <el-col :span="12" v-for="(tc, index) in aiCases" :key="tc.caseId" style="margin-bottom: 12px;">
                                <el-card shadow="hover" :body-style="{ padding: '12px', backgroundColor: 'rgba(30, 41, 59, 0.4)' }" class="item-card">
                                    <div style="font-weight: 600; color: var(--text-primary); margin-bottom: 6px; display: flex; align-items: center; gap: 8px;">
                                        <el-tag size="small" type="info" effect="plain">{{ tc.caseId }}</el-tag>
                                        {{ tc.title }}
                                    </div>
                                    <div style="font-size: 12px; color: var(--text-secondary);">
                                        <el-icon><Folder /></el-icon> {{ tc.module }} 
                                        <el-divider direction="vertical" />
                                        <el-tag :type="getSceneTypeTag(tc.sceneType)" size="small" effect="plain">{{ getSceneTypeName(tc.sceneType) }}</el-tag>
                                    </div>
                                </el-card>
                            </el-col>
                        </el-row>
                    </div>
                </el-card>
            </div>
        </div>
    `,
    props: {
        aiCases: {
            type: Array,
            default: () => []
        },
        referenceCases: {
            type: Array,
            default: () => []
        },
        loading: {
            type: Boolean,
            default: false
        },
        comparisonResult: {
            type: Object,
            default: null
        },
        prdText: {
            type: String,
            default: ''
        }
    },
    setup(props) {
        const comparing = Vue.ref(false);
        const internalComparisonResult = Vue.ref(null);
        const referenceCasesJson = Vue.ref('');
        const criteriaExpanded = Vue.ref(false);

        // 本地解析的人工用例
        const localReferenceCases = Vue.computed(() => {
            // 优先使用本地输入
            if (referenceCasesJson.value.trim()) {
                try {
                    const cases = JSON.parse(referenceCasesJson.value);
                    return Array.isArray(cases) ? cases : [];
                } catch (e) {
                    return [];
                }
            }
            // 其次使用props传入
            return props.referenceCases || [];
        });

        // 监听外部传入的对比结果
        Vue.watch(() => props.comparisonResult, (newVal) => {
            if (newVal) {
                internalComparisonResult.value = newVal;
            }
        }, { immediate: true });

        // 监听外部传入的人工用例
        Vue.watch(() => props.referenceCases, (newVal) => {
            if (newVal && newVal.length > 0 && !referenceCasesJson.value.trim()) {
                try {
                    referenceCasesJson.value = JSON.stringify(newVal, null, 2);
                } catch (e) {
                    console.error('Failed to stringify reference cases:', e);
                }
            }
        }, { immediate: true });

        const coverageRate = Vue.computed(() => {
            if (localReferenceCases.value.length === 0) return 0;
            if (props.aiCases.length === 0) return 0;

            let matchedCount = 0;
            localReferenceCases.value.forEach(refCase => {
                const hasMatch = props.aiCases.some(aiCase => {
                    return isSimilarCase(aiCase, refCase);
                });
                if (hasMatch) matchedCount++;
            });

            return (matchedCount / localReferenceCases.value.length) * 100;
        });

        const dimensionScores = Vue.computed(() => {
            if (!internalComparisonResult.value) return {};
            const r = internalComparisonResult.value;
            const isStandalone = r.scoreDetails && r.scoreDetails.evaluationMode === 'standalone';
            
            if (isStandalone) {
                // 独立评估模式：6个维度（与对比模式一致）
                return {
                    defect: { label: '缺陷发现', weight: '25%', score: r.defectDetectionScore || 0 },
                    business: { label: '业务覆盖', weight: '20%', score: r.businessCoverageScore || 0 },
                    exec: { label: '可执行性', weight: '15%', score: r.executabilityScore || 0 },
                    assertion: { label: '断言完整', weight: '10%', score: r.assertionScore || 0 },
                    standard: { label: '规范性', weight: '10%', score: r.standardScore || 0 },
                    nonRedundancy: { label: '非冗余性', weight: '20%', score: r.nonRedundancyScore || 0 }
                };
            } else {
                // 对比评分模式：6个维度
                return {
                    defect: { label: '缺陷发现', weight: '25%', score: r.defectDetectionScore || r.sceneTypeMatchScore || 0 },
                    business: { label: '业务覆盖', weight: '20%', score: r.businessCoverageScore || r.titleMatchScore || 0 },
                    exec: { label: '可执行性', weight: '15%', score: r.executabilityScore || r.stepsMatchScore || 0 },
                    assertion: { label: '断言完整', weight: '10%', score: r.assertionScore || r.expectedMatchScore || 0 },
                    standard: { label: '规范性', weight: '10%', score: r.standardScore || 0 },
                    nonRedundancy: { label: '非冗余性', weight: '20%', score: r.nonRedundancyScore || 0 }
                };
            }
        });

        const isSimilarCase = (case1, case2) => {
            const title1 = case1.title?.toLowerCase() || '';
            const title2 = case2.title?.toLowerCase() || '';
            if (title1 === title2) return true;
            const words1 = title1.split(/\s+/);
            const words2 = title2.split(/\s+/);
            const commonWords = words1.filter(w => w.length > 2 && words2.includes(w));
            return commonWords.length >= 2;
        };

        const validateJson = () => {
            try {
                const cases = JSON.parse(referenceCasesJson.value);
                if (!Array.isArray(cases)) {
                    throw new Error('必须是 JSON 数组');
                }
                ElementPlus.ElMessage.success(`格式正确！共 ${cases.length} 条用例`);
            } catch (e) {
                ElementPlus.ElMessage.error('JSON 格式错误: ' + e.message);
            }
        };

        const clearReferenceJson = () => {
            ElementPlus.ElMessageBox.confirm('确定清空人工标准用例？', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                referenceCasesJson.value = '';
                internalComparisonResult.value = null;
                ElementPlus.ElMessage.success('已清空');
            }).catch(() => { });
        };

        const performComparison = async () => {
            if (!props.aiCases.length) {
                ElementPlus.ElMessage.warning('请先生成AI测试用例');
                return;
            }

            comparing.value = true;
            internalComparisonResult.value = null;

            const isStandalone = localReferenceCases.value.length === 0;
            const modeText = isStandalone ? '独立评估' : '对比评分';

            try {
                const res = await api.compareTestCases({
                    aiCases: props.aiCases,
                    referenceCases: localReferenceCases.value,
                    prdText: props.prdText || ''
                });

                if (res.data && res.data.success) {
                    internalComparisonResult.value = res.data.result;
                    ElementPlus.ElMessage.success(modeText + '完成');
                } else {
                    ElementPlus.ElMessage.error(res.data?.message || modeText + '失败');
                }
            } catch (e) {
                console.error('Evaluation error:', e);
                ElementPlus.ElMessage.error(modeText + '失败: ' + (e.response?.data?.message || e.message));
            } finally {
                comparing.value = false;
            }
        };

        const getRefCaseById = (caseId) => {
            return localReferenceCases.value.find(c => c.caseId === caseId);
        };

        const getAiCaseById = (caseId) => {
            return props.aiCases.find(c => c.caseId === caseId);
        };

        const getSceneTypeTag = (type) => {
            const map = {
                'FRONTEND': 'success',
                'BACKEND': 'primary',
                'INTEGRATION': 'warning',
                'EXCEPTION': 'danger'
            };
            return map[type] || 'info';
        };

        // 场景类型中文名称
        const getSceneTypeName = (type) => {
            const map = {
                'FRONTEND': '前端',
                'BACKEND': '后端',
                'INTEGRATION': '集成',
                'EXCEPTION': '异常'
            };
            return map[type] || type || '未知';
        };

        const getPriorityTag = (p) => {
            const map = {
                'P0': 'danger',
                'P1': 'warning',
                'P2': 'primary',
                'P3': 'info'
            };
            return map[p] || 'info';
        };

        const getProgressColor = (percentage) => {
            if (percentage >= 80) return '#4ade80';
            if (percentage >= 60) return '#fbbf24';
            return '#f87171';
        };

        const getScoreType = (score) => {
            if (score >= 80) return 'success';
            if (score >= 60) return 'warning';
            return 'danger';
        };

        return {
            comparing,
            internalComparisonResult,
            referenceCasesJson,
            criteriaExpanded,
            localReferenceCases,
            dimensionScores,
            coverageRate,
            validateJson,
            clearReferenceJson,
            performComparison,
            getRefCaseById,
            getAiCaseById,
            getSceneTypeTag,
            getSceneTypeName,
            getPriorityTag,
            getProgressColor,
            getScoreType
        };
    }
};

window.CaseComparison = CaseComparison;
