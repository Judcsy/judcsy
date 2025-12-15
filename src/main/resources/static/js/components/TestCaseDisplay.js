const TestCaseDisplay = {
    template: `
        <div class="test-case-display">
            <div class="toolbar" style="margin-bottom: 15px; display: flex; justify-content: space-between; align-items: center;">
                <div class="filters">
                    <el-radio-group v-model="filterType" size="small">
                        <el-radio-button label="ALL">全部</el-radio-button>
                        <el-radio-button label="FRONTEND">前端</el-radio-button>
                        <el-radio-button label="BACKEND">后端</el-radio-button>
                        <el-radio-button label="INTEGRATION">集成</el-radio-button>
                    </el-radio-group>
                </div>
                <div class="actions">
                    <el-dropdown @command="handleExport">
                        <el-button type="success" size="small">
                            导出用例 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                        </el-button>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item command="markdown">Markdown</el-dropdown-item>
                                <el-dropdown-item command="json">JSON</el-dropdown-item>
                                <el-dropdown-item command="csv">Excel (CSV)</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </div>
            </div>

            <el-table :data="displayCases" style="width: 100%;" v-loading="loading" row-key="caseId" border stripe>
                <el-table-column type="expand">
                    <template #default="props">
                        <div style="padding: 10px 20px; background: rgba(30, 41, 59, 0.6);">
                            <el-descriptions :column="1" border size="small">
                                <el-descriptions-item label="前置条件">
                                    <div v-for="(pc, i) in props.row.preCondition" :key="i" style="color: #f1f5f9;">{{ pc }}</div>
                                </el-descriptions-item>
                                <el-descriptions-item label="前端步骤" v-if="props.row.frontEndSteps && props.row.frontEndSteps.length">
                                    <div v-for="(step, i) in props.row.frontEndSteps" :key="i" style="color: #f1f5f9;">
                                        {{ i+1 }}. {{ step.action }} {{ step.element }} {{ step.value ? ': ' + step.value : '' }}
                                    </div>
                                </el-descriptions-item>
                                <el-descriptions-item label="后端步骤" v-if="props.row.backEndSteps && props.row.backEndSteps.length">
                                    <div v-for="(step, i) in props.row.backEndSteps" :key="i" style="color: #f1f5f9;">
                                        {{ i+1 }}. {{ step.action }} {{ step.apiPath ? '[' + step.method + '] ' + step.apiPath : '' }}
                                    </div>
                                </el-descriptions-item>
                                <el-descriptions-item label="预期结果">
                                    <div v-for="(exp, i) in [...(props.row.frontEndExpected||[]), ...(props.row.backEndExpected||[])]" :key="i" style="color: #f1f5f9;">
                                        - {{ exp }}
                                    </div>
                                </el-descriptions-item>
                                <el-descriptions-item label="断言规则" v-if="props.row.assertRules && props.row.assertRules.length">
                                    <div v-for="(rule, i) in props.row.assertRules" :key="i" style="color: #f1f5f9;">
                                        <el-tag size="small" type="warning">{{ rule.type }}</el-tag> {{ rule.description }}
                                    </div>
                                </el-descriptions-item>
                            </el-descriptions>
                        </div>
                    </template>
                </el-table-column>
                
                <el-table-column prop="caseId" label="ID" width="110" sortable></el-table-column>
                <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip>
                    <template #default="scope">
                        <div style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{{ scope.row.title }}</div>
                    </template>
                </el-table-column>
                <el-table-column label="测试步骤" min-width="220">
                    <template #default="scope">
                        <div style="font-size: 13px; line-height: 1.5; color: #f1f5f9;">
                            <div v-if="scope.row.frontEndSteps && scope.row.frontEndSteps.length" style="color: #4ade80; margin-bottom: 4px; white-space: normal; word-break: break-word;">
                                <el-icon><Monitor /></el-icon> 前端: {{ getStepsSummary(scope.row.frontEndSteps, 'frontend') }}
                            </div>
                            <div v-if="scope.row.backEndSteps && scope.row.backEndSteps.length" style="color: #60a5fa; white-space: normal; word-break: break-word;">
                                <el-icon><Setting /></el-icon> 后端: {{ getStepsSummary(scope.row.backEndSteps, 'backend') }}
                            </div>
                            <div v-if="(!scope.row.frontEndSteps || !scope.row.frontEndSteps.length) && (!scope.row.backEndSteps || !scope.row.backEndSteps.length)" style="color: #94a3b8;">
                                暂无步骤信息
                            </div>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column prop="module" label="模块" width="130" sortable show-overflow-tooltip></el-table-column>
                <el-table-column prop="sceneType" label="类型" width="110">
                    <template #default="scope">
                        <el-tag :type="getSceneTypeTag(scope.row.sceneType)" size="small">{{ getSceneTypeName(scope.row.sceneType) }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="priority" label="优先级" width="110" sortable>
                     <template #default="scope">
                        <el-tag :type="getPriorityTag(scope.row.priority)" effect="plain" size="small">{{ scope.row.priority }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="150" fixed="right">
                    <template #default="scope">
                        <el-button type="primary" size="small" @click="showExportDialog(scope.row)" plain>
                            <el-icon><Download /></el-icon> 导出用例
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
            
            <!-- 单个用例导出格式选择对话框 -->
            <el-dialog v-model="exportDialogVisible" title="导出用例" width="400px" class="export-dialog">
                <div style="padding: 10px 0;">
                    <div style="margin-bottom: 16px; color: #e2e8f0;">
                        <el-icon><Document /></el-icon>
                        <span style="margin-left: 8px;">{{ exportingCase?.title }}</span>
                    </div>
                    <div style="margin-bottom: 12px; color: #94a3b8; font-size: 13px;">请选择导出格式：</div>
                    <el-radio-group v-model="exportFormat" style="display: flex; flex-direction: column; gap: 12px;">
                        <el-radio label="markdown" size="large">
                            <span style="color: #f1f5f9;">Markdown (.md)</span>
                            <span style="color: #94a3b8; font-size: 12px; margin-left: 8px;">适合文档查看</span>
                        </el-radio>
                        <el-radio label="json" size="large">
                            <span style="color: #f1f5f9;">JSON (.json)</span>
                            <span style="color: #94a3b8; font-size: 12px; margin-left: 8px;">适合程序导入</span>
                        </el-radio>
                        <el-radio label="txt" size="large">
                            <span style="color: #f1f5f9;">纯文本 (.txt)</span>
                            <span style="color: #94a3b8; font-size: 12px; margin-left: 8px;">简洁格式</span>
                        </el-radio>
                    </el-radio-group>
                </div>
                <template #footer>
                    <el-button type="info" @click="exportDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="confirmExportSingle">
                        <el-icon><Download /></el-icon> 确认导出
                    </el-button>
                </template>
            </el-dialog>
            
            <!-- 标准格式对话框 -->
            <el-dialog v-model="standardDialogVisible" :title="dialogTitle" width="70%" top="5vh" class="standard-dialog">
                <div class="standard-format-container" style="max-height: 70vh; overflow-y: auto; padding-right: 10px;">
                    <el-card shadow="never" class="format-section">
                        <template #header>
                            <div class="section-header" style="color: #ffffff;">
                                <el-icon><InfoFilled /></el-icon>
                                <span>基本信息</span>
                            </div>
                        </template>
                        <el-descriptions :column="2" border size="default">
                            <el-descriptions-item label="用例ID">{{ currentStandardCase?.caseId }}</el-descriptions-item>
                            <el-descriptions-item label="模块">{{ currentStandardCase?.module }}</el-descriptions-item>
                            <el-descriptions-item label="场景类型">
                                <el-tag :type="getSceneTypeTag(currentStandardCase?.sceneType)" size="small">
                                    {{ getSceneTypeName(currentStandardCase?.sceneType) }}
                                </el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="优先级">
                                <el-tag :type="getPriorityTag(currentStandardCase?.priority)" size="small">
                                    {{ currentStandardCase?.priority }}
                                </el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="标签" :span="2" v-if="currentStandardCase?.tags && currentStandardCase.tags.length">
                                <el-tag v-for="tag in currentStandardCase.tags" :key="tag" size="small" style="margin-right: 5px;">
                                    {{ tag }}
                                </el-tag>
                            </el-descriptions-item>
                        </el-descriptions>
                    </el-card>

                    <el-card shadow="never" class="format-section" v-if="currentStandardCase?.preCondition && currentStandardCase.preCondition.length">
                        <template #header>
                            <div class="section-header" style="color: #ffffff;">
                                <el-icon><CircleCheck /></el-icon>
                                <span>前置条件</span>
                            </div>
                        </template>
                        <ul class="condition-list" style="color: #f1f5f9;">
                            <li v-for="(cond, i) in currentStandardCase.preCondition" :key="i" style="color: #f1f5f9;">{{ cond }}</li>
                        </ul>
                    </el-card>

                    <el-card shadow="never" class="format-section">
                        <template #header>
                            <div class="section-header" style="color: #ffffff;">
                                <el-icon><List /></el-icon>
                                <span>操作步骤</span>
                            </div>
                        </template>
                        <div v-if="currentStandardCase?.frontEndSteps && currentStandardCase.frontEndSteps.length" class="steps-group">
                            <div class="steps-title" style="color: #ffffff;">
                                <el-icon color="#4ade80"><Monitor /></el-icon>
                                <strong>前端操作</strong>
                            </div>
                            <ol class="steps-list" style="color: #f1f5f9;">
                                <li v-for="(step, i) in currentStandardCase.frontEndSteps" :key="i" style="color: #f1f5f9;">
                                    {{ step.action }} {{ step.element }}{{ step.value ? ': ' + step.value : '' }}
                                </li>
                            </ol>
                        </div>
                        <div v-if="currentStandardCase?.backEndSteps && currentStandardCase.backEndSteps.length" class="steps-group">
                            <div class="steps-title" style="color: #ffffff;">
                                <el-icon color="#60a5fa"><Setting /></el-icon>
                                <strong>后端接口</strong>
                            </div>
                            <ol class="steps-list" style="color: #f1f5f9;">
                                <li v-for="(step, i) in currentStandardCase.backEndSteps" :key="i" style="color: #f1f5f9;">
                                    {{ step.action }}{{ step.method && step.apiPath ? ' [' + step.method + '] ' + step.apiPath : '' }}
                                </li>
                            </ol>
                        </div>
                    </el-card>

                    <el-card shadow="never" class="format-section">
                        <template #header>
                            <div class="section-header" style="color: #ffffff;">
                                <el-icon><Select /></el-icon>
                                <span>预期结果</span>
                            </div>
                        </template>
                        <div v-if="currentStandardCase?.frontEndExpected && currentStandardCase.frontEndExpected.length" class="expected-group">
                            <div class="expected-title" style="color: #ffffff;">
                                <el-icon color="#4ade80"><Monitor /></el-icon>
                                <strong>前端预期</strong>
                            </div>
                            <ul class="expected-list" style="color: #f1f5f9;">
                                <li v-for="(exp, i) in currentStandardCase.frontEndExpected" :key="i" style="color: #f1f5f9;">{{ exp }}</li>
                            </ul>
                        </div>
                        <div v-if="currentStandardCase?.backEndExpected && currentStandardCase.backEndExpected.length" class="expected-group">
                            <div class="expected-title" style="color: #ffffff;">
                                <el-icon color="#60a5fa"><Setting /></el-icon>
                                <strong>后端预期</strong>
                            </div>
                            <ul class="expected-list" style="color: #f1f5f9;">
                                <li v-for="(exp, i) in currentStandardCase.backEndExpected" :key="i" style="color: #f1f5f9;">{{ exp }}</li>
                            </ul>
                        </div>
                    </el-card>

                    <el-card shadow="never" class="format-section" v-if="currentStandardCase?.assertRules && currentStandardCase.assertRules.length">
                        <template #header>
                            <div class="section-header" style="color: #ffffff;">
                                <el-icon><Check /></el-icon>
                                <span>断言规则</span>
                            </div>
                        </template>
                        <el-table :data="currentStandardCase.assertRules" border size="small">
                            <el-table-column prop="field" label="字段" width="150"></el-table-column>
                            <el-table-column prop="operator" label="操作符" width="100"></el-table-column>
                            <el-table-column prop="expected" label="期望值" width="150"></el-table-column>
                            <el-table-column prop="description" label="说明"></el-table-column>
                        </el-table>
                    </el-card>
                </div>
                
                <template #footer>
                    <span class="dialog-footer">
                        <el-button @click="handleCopyStandard" type="success" plain>
                            <el-icon><CopyDocument /></el-icon> 复制到剪贴板
                        </el-button>
                        <el-button @click="handleExportMarkdown" type="primary" plain>
                            <el-icon><Download /></el-icon> 导出为Markdown
                        </el-button>
                        <el-button type="info" plain @click="standardDialogVisible = false">关闭</el-button>
                    </span>
                </template>
            </el-dialog>
            
            <div style="margin-top: 15px; text-align: right; color: var(--text-secondary); font-size: 14px;">
                共 {{ displayCases.length }} 条用例
            </div>
        </div>
    `,
    props: ['cases', 'loading'],
    setup(props) {
        const filterType = Vue.ref('ALL');

        const displayCases = Vue.computed(() => {
            if (!props.cases) return [];
            if (filterType.value === 'ALL') return props.cases;
            return props.cases.filter(c => c.sceneType === filterType.value);
        });

        const getSceneTypeTag = (type) => {
            const map = { 'FRONTEND': 'success', 'BACKEND': 'primary', 'INTEGRATION': 'warning', 'EXCEPTION': 'danger' };
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
            const map = { 'P0': 'danger', 'P1': 'warning', 'P2': 'primary', 'P3': 'info' };
            return map[p] || 'info';
        };

        const handleExport = (format) => {
            if (!props.cases || props.cases.length === 0) {
                ElementPlus.ElMessage.warning('没有可导出的数据');
                return;
            }

            let content = '';
            let mimeType = 'text/plain';
            let filename = `testcases.${format}`;

            if (format === 'json') {
                content = JSON.stringify(displayCases.value, null, 2);
                mimeType = 'application/json';
            } else if (format === 'markdown') {
                // 详细的 Markdown 生成逻辑
                content = '# 测试用例\n\n';
                displayCases.value.forEach(tc => {
                    content += `## ${tc.caseId}: ${tc.title}\n`;
                    content += `- **模块**: ${tc.module}\n`;
                    content += `- **类型**: ${tc.sceneType}\n`;
                    content += `- **优先级**: ${tc.priority}\n`;
                    content += `- **前置条件**: ${tc.preCondition.join(', ')}\n`;

                    // 前端步骤
                    if (tc.frontEndSteps && tc.frontEndSteps.length) {
                        content += `- **前端步骤**:\n`;
                        tc.frontEndSteps.forEach((step, i) => {
                            let stepText = `  ${i + 1}. ${step.action}`;
                            if (step.element) stepText += ` ${step.element}`;
                            if (step.value) stepText += `: ${step.value}`;
                            content += stepText + '\n';
                        });
                    }

                    // 后端步骤
                    if (tc.backEndSteps && tc.backEndSteps.length) {
                        content += `- **后端步骤**:\n`;
                        tc.backEndSteps.forEach((step, i) => {
                            let stepText = `  ${i + 1}. ${step.action}`;
                            if (step.method && step.apiPath) stepText += ` [${step.method}] ${step.apiPath}`;
                            content += stepText + '\n';
                        });
                    }

                    // 预期结果
                    const allExpected = [...(tc.frontEndExpected || []), ...(tc.backEndExpected || [])];
                    if (allExpected.length) {
                        content += `- **预期结果**:\n`;
                        allExpected.forEach(exp => {
                            content += `  - ${exp}\n`;
                        });
                    }

                    content += `\n`;
                });
                mimeType = 'text/markdown';
                filename = 'testcases.md';
            } else if (format === 'csv') {
                // CSV Header - 添加步骤摘要列
                content = 'ID,Title,Module,Priority,Type,Frontend Steps,Backend Steps\n';
                displayCases.value.forEach(tc => {
                    const frontSteps = tc.frontEndSteps && tc.frontEndSteps.length
                        ? tc.frontEndSteps.map(s => `${s.action} ${s.element || ''}`).join('; ')
                        : '';
                    const backSteps = tc.backEndSteps && tc.backEndSteps.length
                        ? tc.backEndSteps.map(s => `${s.method || ''} ${s.apiPath || ''}`).join('; ')
                        : '';
                    content += `${tc.caseId},"${tc.title}",${tc.module},${tc.priority},${tc.sceneType},"${frontSteps}","${backSteps}"\n`;
                });
                mimeType = 'text/csv';
            }

            const blob = new Blob([content], { type: mimeType });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            a.click();
            URL.revokeObjectURL(url);
        };

        const getStepsSummary = (steps, type) => {
            if (!steps || steps.length === 0) return '无';

            const maxSteps = 10; // 增加显示步骤数
            const displaySteps = steps.slice(0, maxSteps);

            let summary = '';
            if (type === 'frontend') {
                summary = displaySteps.map(step => {
                    let text = step.action || '';
                    if (step.element) text += ' ' + step.element;
                    if (step.value) text += '=' + step.value;
                    return text;
                }).join(' → ');
            } else if (type === 'backend') {
                summary = displaySteps.map(step => {
                    let text = '';
                    if (step.method) text += step.method + ' ';
                    if (step.apiPath) text += step.apiPath;
                    else if (step.action) text += step.action;
                    return text;
                }).join(' → ');
            }

            if (steps.length > maxSteps) {
                summary += ` ... (共${steps.length}步)`;
            }

            return summary || '查看详情';
        };

        // 标准格式对话框状态
        const standardDialogVisible = Vue.ref(false);
        const currentStandardCase = Vue.ref(null);
        
        // 导出对话框状态
        const exportDialogVisible = Vue.ref(false);
        const exportingCase = Vue.ref(null);
        const exportFormat = Vue.ref('markdown');
        
        // 对话框标题
        const dialogTitle = Vue.computed(() => {
            return currentStandardCase.value ? `用例详情 - ${currentStandardCase.value.caseId}` : '用例详情';
        });

        // 查看标准格式
        const handleViewStandard = (row) => {
            currentStandardCase.value = row;
            standardDialogVisible.value = true;
        };

        // 生成Markdown格式文本
        const formatToMarkdown = (tc) => {
            if (!tc) return '';

            let md = `# ${tc.module}\n`;
            md += `## ${tc.title}\n\n`;

            // 前置条件
            if (tc.preCondition && tc.preCondition.length) {
                md += `### 前置条件\n`;
                tc.preCondition.forEach(cond => {
                    md += `${cond}\n`;
                });
                md += '\n';
            }

            // 操作步骤
            md += `### 操作步骤\n`;
            let stepNum = 1;

            if (tc.frontEndSteps && tc.frontEndSteps.length) {
                tc.frontEndSteps.forEach(step => {
                    let stepText = `${stepNum}. ${step.action} ${step.element || ''}`;
                    if (step.value) stepText += `: ${step.value}`;
                    md += stepText + '\n';
                    stepNum++;
                });
            }

            if (tc.backEndSteps && tc.backEndSteps.length) {
                tc.backEndSteps.forEach(step => {
                    let stepText = `${stepNum}. ${step.action}`;
                    if (step.method && step.apiPath) stepText += ` [${step.method}] ${step.apiPath}`;
                    md += stepText + '\n';
                    stepNum++;
                });
            }
            md += '\n';

            // 预期结果
            md += `### 预期结果\n`;
            let resultNum = 1;

            if (tc.frontEndExpected && tc.frontEndExpected.length) {
                tc.frontEndExpected.forEach(exp => {
                    md += `${resultNum}. ${exp}\n`;
                    resultNum++;
                });
            }

            if (tc.backEndExpected && tc.backEndExpected.length) {
                tc.backEndExpected.forEach(exp => {
                    md += `${resultNum}. ${exp}\n`;
                    resultNum++;
                });
            }

            // 断言规则
            if (tc.assertRules && tc.assertRules.length) {
                md += `\n### 断言规则\n`;
                tc.assertRules.forEach(rule => {
                    md += `- ${rule.field} ${rule.operator} ${rule.expected || ''} - ${rule.description}\n`;
                });
            }

            return md;
        };

        // 复制到剪贴板
        const handleCopyStandard = () => {
            const tc = currentStandardCase.value;
            if (!tc) return;

            const markdownText = formatToMarkdown(tc);

            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(markdownText).then(() => {
                    ElementPlus.ElMessage.success('已复制到剪贴板');
                }).catch(err => {
                    console.error('复制失败:', err);
                    ElementPlus.ElMessage.error('复制失败，请手动复制');
                });
            } else {
                // 降级方案
                const textArea = document.createElement('textarea');
                textArea.value = markdownText;
                textArea.style.position = 'fixed';
                textArea.style.left = '-999999px';
                document.body.appendChild(textArea);
                textArea.select();
                try {
                    document.execCommand('copy');
                    ElementPlus.ElMessage.success('已复制到剪贴板');
                } catch (err) {
                    ElementPlus.ElMessage.error('复制失败，请手动复制');
                }
                document.body.removeChild(textArea);
            }
        };

        // 导出为Markdown
        const handleExportMarkdown = () => {
            const tc = currentStandardCase.value;
            if (!tc) return;

            const markdownText = formatToMarkdown(tc);
            const blob = new Blob([markdownText], { type: 'text/markdown;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${tc.caseId}_${tc.title}.md`;
            a.click();
            URL.revokeObjectURL(url);
            ElementPlus.ElMessage.success('导出成功');
        };

        // 导出单个用例（直接导出，不弹窗）
        const handleExportSingle = (tc) => {
            if (!tc) return;

            const markdownText = formatToMarkdown(tc);
            const blob = new Blob([markdownText], { type: 'text/markdown;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            // 清理文件名中的特殊字符
            const safeTitle = tc.title.replace(/[\\/:*?"<>|]/g, '_').substring(0, 50);
            a.download = `${tc.caseId}_${safeTitle}.md`;
            a.click();
            URL.revokeObjectURL(url);
            ElementPlus.ElMessage.success(`用例 ${tc.caseId} 导出成功`);
        };

        // 显示导出格式选择对话框
        const showExportDialog = (tc) => {
            exportingCase.value = tc;
            exportFormat.value = 'markdown';
            exportDialogVisible.value = true;
        };

        // 生成JSON格式
        const formatToJson = (tc) => {
            return JSON.stringify(tc, null, 2);
        };

        // 生成纯文本格式
        const formatToTxt = (tc) => {
            if (!tc) return '';

            let txt = `用例ID: ${tc.caseId}\n`;
            txt += `标题: ${tc.title}\n`;
            txt += `模块: ${tc.module}\n`;
            txt += `场景类型: ${tc.sceneType}\n`;
            txt += `优先级: ${tc.priority}\n\n`;

            if (tc.preCondition && tc.preCondition.length) {
                txt += `前置条件:\n`;
                tc.preCondition.forEach((cond, i) => {
                    txt += `  ${i + 1}. ${cond}\n`;
                });
                txt += '\n';
            }

            txt += `操作步骤:\n`;
            let stepNum = 1;
            if (tc.frontEndSteps && tc.frontEndSteps.length) {
                tc.frontEndSteps.forEach(step => {
                    let stepText = `  ${stepNum}. [前端] ${step.action}`;
                    if (step.element) stepText += ` ${step.element}`;
                    if (step.value) stepText += `: ${step.value}`;
                    txt += stepText + '\n';
                    stepNum++;
                });
            }
            if (tc.backEndSteps && tc.backEndSteps.length) {
                tc.backEndSteps.forEach(step => {
                    let stepText = `  ${stepNum}. [后端] ${step.action}`;
                    if (step.method && step.apiPath) stepText += ` [${step.method}] ${step.apiPath}`;
                    txt += stepText + '\n';
                    stepNum++;
                });
            }
            txt += '\n';

            txt += `预期结果:\n`;
            let resultNum = 1;
            if (tc.frontEndExpected && tc.frontEndExpected.length) {
                tc.frontEndExpected.forEach(exp => {
                    txt += `  ${resultNum}. ${exp}\n`;
                    resultNum++;
                });
            }
            if (tc.backEndExpected && tc.backEndExpected.length) {
                tc.backEndExpected.forEach(exp => {
                    txt += `  ${resultNum}. ${exp}\n`;
                    resultNum++;
                });
            }

            if (tc.assertRules && tc.assertRules.length) {
                txt += `\n断言规则:\n`;
                tc.assertRules.forEach((rule, i) => {
                    txt += `  ${i + 1}. ${rule.field} ${rule.operator} ${rule.expected || ''} - ${rule.description}\n`;
                });
            }

            return txt;
        };

        // 确认导出单个用例
        const confirmExportSingle = () => {
            const tc = exportingCase.value;
            if (!tc) return;

            let content, mimeType, extension;
            switch (exportFormat.value) {
                case 'json':
                    content = formatToJson(tc);
                    mimeType = 'application/json;charset=utf-8';
                    extension = 'json';
                    break;
                case 'txt':
                    content = formatToTxt(tc);
                    mimeType = 'text/plain;charset=utf-8';
                    extension = 'txt';
                    break;
                default:
                    content = formatToMarkdown(tc);
                    mimeType = 'text/markdown;charset=utf-8';
                    extension = 'md';
            }

            const blob = new Blob([content], { type: mimeType });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            const safeTitle = tc.title.replace(/[\\/:*?"<>|]/g, '_').substring(0, 50);
            a.download = `${tc.caseId}_${safeTitle}.${extension}`;
            a.click();
            URL.revokeObjectURL(url);
            exportDialogVisible.value = false;
            ElementPlus.ElMessage.success(`用例 ${tc.caseId} 导出成功`);
        };

        return {
            filterType,
            displayCases,
            getSceneTypeTag,
            getSceneTypeName,
            getPriorityTag,
            handleExport,
            getStepsSummary,
            standardDialogVisible,
            currentStandardCase,
            dialogTitle,
            handleViewStandard,
            handleCopyStandard,
            handleExportMarkdown,
            handleExportSingle,
            // 导出对话框相关
            exportDialogVisible,
            exportingCase,
            exportFormat,
            showExportDialog,
            confirmExportSingle
        };
    }
};
window.TestCaseDisplay = TestCaseDisplay;
