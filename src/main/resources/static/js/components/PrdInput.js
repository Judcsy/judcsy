const PrdInput = {
    template: `
        <el-card class="prd-input-card" shadow="never">
            <template #header>
                <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
                    <span style="font-weight: 600;">PRD 输入</span>
                    <el-button type="primary" link @click="fetchFeishu" :loading="fetching">
                        <el-icon style="margin-right: 4px;"><Link /></el-icon> 导入飞书
                    </el-button>
                </div>
            </template>
            
            <el-form :model="form" label-position="top">
                <el-tabs v-model="activeInputTab">
                    <el-tab-pane label="PRD 内容" name="prd">
                        <el-form-item>
                            <el-input 
                                v-model="form.prdText" 
                                type="textarea" 
                                :rows="15" 
                                placeholder="在此粘贴 PRD 内容，或点击上方导入飞书文档..."
                                resize="none"
                            ></el-input>
                        </el-form-item>
                    </el-tab-pane>
                    <el-tab-pane name="reference">
                        <template #label>
                            <span>
                                <el-icon><User /></el-icon> <span style="color: var(--accent-primary); font-weight: 600;">人工用例</span>
                                <el-badge v-if="referenceCaseCount > 0" :value="referenceCaseCount" type="primary" style="margin-left: 8px;" />
                            </span>
                        </template>
                        <el-alert 
                            title="用于质量评测对比" 
                            type="info" 
                            :closable="false" 
                            style="margin-bottom: 10px; --el-alert-title-color: #ffffff !important; --el-alert-description-color: #f1f5f9 !important;" 
                            show-icon
                        >
                            <template #default>
                                <div style="font-size: 13px; color: #f1f5f9 !important;">
                                    粘贴人工编写的标准测试用例JSON数组，系统将对比AI生成质量。
                                    <el-link type="primary" :underline="false" @click="showSampleJson" style="margin-left: 10px; color: #60a5fa !important; font-weight: 500;">
                                        查看示例格式
                                    </el-link>
                                </div>
                            </template>
                        </el-alert>
                        <div style="margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center;">
                            <span style="font-size: 13px; color: #e2e8f0;">
                                <el-icon><DocumentCopy /></el-icon> 已输入 <strong>{{ referenceCaseCount }}</strong> 条人工用例
                            </span>
                            <div>
                                <el-button size="small" type="info" @click="validateJson" :disabled="!form.referenceCasesJson.trim()">
                                    <el-icon><Select /></el-icon> 验证格式
                                </el-button>
                                <el-button size="small" type="danger" @click="clearReferenceJson" :disabled="!form.referenceCasesJson.trim()">
                                    <el-icon><Delete /></el-icon> 清空
                                </el-button>
                            </div>
                        </div>
                        <el-input 
                            v-model="form.referenceCasesJson" 
                            type="textarea" 
                            :rows="12" 
                            placeholder='[
  {
    "caseId": "TC_001",
    "title": "登录成功场景",
    "module": "用户模块",
    "sceneType": "FRONTEND",
    "priority": "P0",
    "preCondition": ["用户已注册", "账号状态正常"],
    "frontEndSteps": [
      {"stepNumber": 1, "action": "输入", "element": "用户名输入框", "value": "testuser"},
      {"stepNumber": 2, "action": "输入", "element": "密码输入框", "value": "password123"},
      {"stepNumber": 3, "action": "点击", "element": "登录按钮", "value": ""}
    ],
    "frontEndExpected": ["页面跳转到首页", "显示用户昵称"],
    "backEndExpected": ["返回token", "记录登录日志"],
    "tags": ["核心功能", "高优先级"]
  }
]'
                            resize="none"
                        ></el-input>
                    </el-tab-pane>
                </el-tabs>
                
                <el-form-item>
                    <!-- PRD标签页：智能生成测试用例按钮 -->
                    <el-button 
                        v-if="activeInputTab === 'prd'"
                        type="primary" 
                        @click="generate" 
                        :loading="loading" 
                        style="width: 100%; margin-top: 10px;" 
                        size="large"
                    >
                        <el-icon style="margin-right: 6px;"><MagicStick /></el-icon> 智能生成测试用例
                    </el-button>
                    
                    <!-- 人工用例标签页：对比评分按钮 -->
                    <el-tooltip 
                        v-else 
                        :disabled="hasAiCases && referenceCaseCount > 0"
                        :content="!hasAiCases ? '请先生成AI测试用例' : '请先输入人工用例'"
                        placement="top"
                    >
                        <span style="width: 100%; display: inline-block;">
                            <el-button 
                                type="success" 
                                @click="performCompare" 
                                :loading="comparing" 
                                :disabled="!hasAiCases || referenceCaseCount === 0"
                                style="width: 100%; margin-top: 10px;" 
                                size="large"
                            >
                                <el-icon style="margin-right: 6px;"><Connection /></el-icon> 对比评分
                            </el-button>
                        </span>
                    </el-tooltip>
                </el-form-item>
            </el-form>
            
            <el-dialog v-model="feishuDialogVisible" title="导入飞书文档" width="550px">
                <el-form label-position="top">
                    <el-form-item label="飞书文档链接">
                        <el-input v-model="feishuUrl" placeholder="https://xxx.feishu.cn/docx/xxx 或 wiki/xxx">
                            <template #prepend>
                                <el-icon><Link /></el-icon>
                            </template>
                        </el-input>
                    </el-form-item>
                    <el-alert 
                        v-if="feishuStatus && !feishuStatus.configured" 
                        type="warning" 
                        title="飞书应用未配置" 
                        :closable="false" 
                        style="margin-bottom: 10px; --el-alert-title-color: #ffffff !important; --el-alert-description-color: #f1f5f9 !important;"
                    >
                        <template #default>
                            <span style="color: #f1f5f9 !important;">请在 .env 文件中设置 FEISHU_APP_ID 和 FEISHU_APP_SECRET，或展开下方设置手动配置。</span>
                        </template>
                    </el-alert>
                    <el-collapse v-model="feishuSettingsExpanded">
                        <el-collapse-item title="飞书应用配置（可选）" name="settings">
                            <el-form-item label="App ID">
                                <el-input v-model="feishuAppId" placeholder="cli_xxxxxx"></el-input>
                            </el-form-item>
                            <el-form-item label="App Secret">
                                <el-input v-model="feishuAppSecret" type="password" placeholder="请输入App Secret" show-password></el-input>
                            </el-form-item>
                            <el-button type="primary" size="small" @click="saveFeishuConfig" :loading="savingConfig">
                                保存配置
                            </el-button>
                        </el-collapse-item>
                    </el-collapse>
                </el-form>
                <template #footer>
                    <span class="dialog-footer">
                        <el-button type="info"  @click="feishuDialogVisible = false">取消</el-button>
                        <el-button type="primary" @click="confirmFetchFeishu" :loading="fetching" :disabled="!feishuUrl">
                            <el-icon style="margin-right: 4px;"><Download /></el-icon> 导入文档
                        </el-button>
                    </span>
                </template>
            </el-dialog>
        </el-card>
    `,
    props: {
        loading: {
            type: Boolean,
            default: false
        },
        aiCases: {
            type: Array,
            default: () => []
        }
    },
    emits: ['generate', 'compare'],
    setup(props, { emit }) {
        const activeInputTab = Vue.ref('prd');
        const form = Vue.reactive({
            prdText: '',
            referenceCasesJson: ''
        });
        const feishuDialogVisible = Vue.ref(false);
        const feishuUrl = Vue.ref('');
        const fetching = Vue.ref(false);
        const comparing = Vue.ref(false);
        // 飞书配置相关
        const feishuAppId = Vue.ref('');
        const feishuAppSecret = Vue.ref('');
        const feishuSettingsExpanded = Vue.ref([]);
        const feishuStatus = Vue.ref(null);
        const savingConfig = Vue.ref(false);

        // 是否有AI生成的用例
        const hasAiCases = Vue.computed(() => {
            return props.aiCases && props.aiCases.length > 0;
        });

        // 计算已输入的人工用例数量
        const referenceCaseCount = Vue.computed(() => {
            if (!form.referenceCasesJson.trim()) return 0;
            try {
                const cases = JSON.parse(form.referenceCasesJson);
                return Array.isArray(cases) ? cases.length : 0;
            } catch (e) {
                return 0;
            }
        });

        // 获取人工用例数据
        const getReferenceCases = () => {
            if (!form.referenceCasesJson.trim()) return null;
            try {
                const cases = JSON.parse(form.referenceCasesJson);
                return Array.isArray(cases) ? cases : null;
            } catch (e) {
                return null;
            }
        };

        const generate = () => {
            if (!form.prdText.trim()) {
                ElementPlus.ElMessage.warning('请输入需求文档内容');
                return;
            }

            let referenceCases = null;
            if (form.referenceCasesJson.trim()) {
                try {
                    referenceCases = JSON.parse(form.referenceCasesJson);
                    if (!Array.isArray(referenceCases)) {
                        throw new Error('必须是 JSON 数组');
                    }
                } catch (e) {
                    ElementPlus.ElMessage.error('标准用例 JSON 格式错误: ' + e.message);
                    return;
                }
            }

            emit('generate', {
                prdText: form.prdText,
                referenceCases: referenceCases
            });
        };

        const performCompare = async () => {
            if (!hasAiCases.value) {
                ElementPlus.ElMessage.warning('请先生成AI测试用例');
                return;
            }

            const referenceCases = getReferenceCases();
            if (!referenceCases || referenceCases.length === 0) {
                ElementPlus.ElMessage.warning('请输入人工标准用例');
                return;
            }

            comparing.value = true;

            try {
                const res = await api.compareTestCases({
                    aiCases: props.aiCases,
                    referenceCases: referenceCases,
                    prdText: form.prdText || ''
                });

                if (res.data && res.data.success) {
                    emit('compare', res.data.result);
                    ElementPlus.ElMessage.success(res.data.message);
                } else {
                    ElementPlus.ElMessage.error(res.data?.message || '对比评分失败');
                }
            } catch (e) {
                console.error('Compare error:', e);
                ElementPlus.ElMessage.error('对比评分失败: ' + (e.response?.data?.message || e.message));
            } finally {
                comparing.value = false;
            }
        };

        const fetchFeishu = async () => {
            feishuDialogVisible.value = true;
            // 检查飞书配置状态
            try {
                const res = await api.checkFeishuStatus();
                if (res.data) {
                    feishuStatus.value = res.data;
                }
            } catch (e) {
                console.error('检查飞书状态失败:', e);
            }
        };

        const saveFeishuConfig = async () => {
            if (!feishuAppId.value || !feishuAppSecret.value) {
                ElementPlus.ElMessage.warning('请输入 App ID 和 App Secret');
                return;
            }
            savingConfig.value = true;
            try {
                const res = await api.configureFeishu({
                    appId: feishuAppId.value,
                    appSecret: feishuAppSecret.value
                });
                if (res.data && res.data.success) {
                    ElementPlus.ElMessage.success('飞书配置保存成功');
                    feishuStatus.value = { configured: true };
                    feishuSettingsExpanded.value = [];
                } else {
                    ElementPlus.ElMessage.error(res.data?.message || '配置失败');
                }
            } catch (e) {
                ElementPlus.ElMessage.error('保存失败: ' + (e.response?.data?.message || e.message));
            } finally {
                savingConfig.value = false;
            }
        };

        const confirmFetchFeishu = async () => {
            if (!feishuUrl.value) return;
            fetching.value = true;
            try {
                const res = await api.fetchFeishuContent(feishuUrl.value);
                if (res.data && res.data.content) {
                    form.prdText = res.data.content;
                    feishuDialogVisible.value = false;
                    ElementPlus.ElMessage.success('导入成功');
                } else {
                    ElementPlus.ElMessage.error('无法获取文档内容');
                }
            } catch (e) {
                ElementPlus.ElMessage.error('导入失败: ' + (e.response?.data?.message || e.message));
            } finally {
                fetching.value = false;
            }
        };

        const validateJson = () => {
            try {
                const cases = JSON.parse(form.referenceCasesJson);
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
                form.referenceCasesJson = '';
                ElementPlus.ElMessage.success('已清空');
            }).catch(() => { });
        };

        const showSampleJson = () => {
            ElementPlus.ElMessageBox.alert(
                '请参考输入框中的示例格式，必须包含字段：caseId, title, module, sceneType, priority, preCondition, frontEndExpected 等。',
                '示例格式说明',
                {
                    confirmButtonText: '知道了',
                    type: 'info'
                }
            );
        };

        return {
            activeInputTab,
            form,
            hasAiCases,
            referenceCaseCount,
            feishuDialogVisible,
            feishuUrl,
            fetching,
            comparing,
            // 飞书配置相关
            feishuAppId,
            feishuAppSecret,
            feishuSettingsExpanded,
            feishuStatus,
            savingConfig,
            generate,
            performCompare,
            fetchFeishu,
            saveFeishuConfig,
            confirmFetchFeishu,
            validateJson,
            clearReferenceJson,
            showSampleJson
        };
    }
};

window.PrdInput = PrdInput;
