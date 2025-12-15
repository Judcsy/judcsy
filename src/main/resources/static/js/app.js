// 检查必要的依赖是否加载成功
if (typeof Vue === 'undefined') {
    document.getElementById('app').innerHTML = '<div style="padding: 50px; text-align: center; color: red;"><h2>Vue.js 加载失败</h2><p>请检查网络连接或刷新页面重试</p></div>';
    throw new Error('Vue is not loaded');
}
if (typeof ElementPlus === 'undefined') {
    document.getElementById('app').innerHTML = '<div style="padding: 50px; text-align: center; color: red;"><h2>Element Plus 加载失败</h2><p>请检查网络连接或刷新页面重试</p></div>';
    throw new Error('ElementPlus is not loaded');
}

const { createApp, ref, reactive } = Vue;

const app = createApp({
    setup() {
        const loading = ref(false);
        const evaluating = ref(false);
        const activeTab = ref('cases');
        const testCases = ref([]);
        const referenceCases = ref([]);
        const evaluationResult = ref(null);
        const comparisonResult = ref(null);
        const currentPrdText = ref('');

        const handleGenerate = async (data) => {
            loading.value = true;
            testCases.value = [];
            evaluationResult.value = null;

            // data 可以是字符串(旧版)或对象(新版)
            const prdText = typeof data === 'string' ? data : data.prdText;
            const refCases = typeof data === 'object' ? data.referenceCases : null;

            // 保存人工用例到状态
            if (refCases && Array.isArray(refCases)) {
                referenceCases.value = refCases;
            }

            // 保存PRD文本用于对比评分
            currentPrdText.value = prdText || '';

            try {
                const res = await api.generateTestCases({
                    prdText,
                    referenceCases: refCases,
                    enableEval: true,
                    useLLM: true
                });

                if (res.data && res.data.success) {
                    testCases.value = res.data.testCases || [];

                    if (res.data.evaluation) {
                        evaluationResult.value = res.data.evaluation;

                        if (evaluationResult.value.totalScore < 60) {
                            ElementPlus.ElNotification({
                                title: '质量预警',
                                message: '生成的用例质量较低，建议优化 PRD 或补充约束条件',
                                type: 'warning',
                                duration: 5000
                            });
                        }
                    }

                    ElementPlus.ElMessage.success(res.data.message);

                    if (activeTab.value === 'mindmap') {
                        Vue.nextTick(() => renderMindmap());
                    }
                } else {
                    ElementPlus.ElMessage.warning(res.data.message || '生成失败');
                }
            } catch (e) {
                console.error(e);
                ElementPlus.ElMessage.error('系统错误: ' + (e.response?.data?.message || e.message));
            } finally {
                loading.value = false;
            }
        };

        Vue.watch(activeTab, (val) => {
            if (val === 'mindmap' && testCases.value.length > 0) {
                Vue.nextTick(() => {
                    renderMindmap();
                });
            }
        });

        // 处理对比评分结果
        const handleCompare = (result) => {
            comparisonResult.value = result;
            // 自动切换到对比视图
            activeTab.value = 'comparison';
        };

        const renderMindmap = () => {
            const container = document.getElementById('mindmap-container');
            if (!container) return;

            // 清除旧实例
            const oldChart = echarts.getInstanceByDom(container);
            if (oldChart) {
                oldChart.dispose();
            }

            const caseCount = testCases.value.length;
            
            // 动态计算容器高度，根据用例数量调整
            const minHeight = 600;
            const heightPerCase = caseCount > 30 ? 25 : 40; // 用例多时减小间距
            const calculatedHeight = Math.max(minHeight, caseCount * heightPerCase + 200);
            container.style.height = calculatedHeight + 'px';

            const chart = echarts.init(container, 'dark', {
                renderer: 'canvas',
                useDirtyRect: false
            });

            // 根据用例数量调整字体大小和样式
            const isLargeDataset = caseCount > 30;
            const fontSize = isLargeDataset ? 10 : 12;
            const nodeSize = isLargeDataset ? 6 : 8;
            const moduleFontSize = isLargeDataset ? 12 : 14;

            const data = {
                name: '测试用例集 (' + caseCount + '条)',
                children: [],
                itemStyle: {
                    color: '#818cf8',
                    borderColor: '#a78bfa',
                    borderWidth: 2
                },
                label: {
                    color: '#ffffff',
                    fontSize: 14,
                    fontWeight: 'bold'
                }
            };

            const modules = {};
            testCases.value.forEach(tc => {
                if (!modules[tc.module]) {
                    modules[tc.module] = {
                        name: tc.module,
                        children: [],
                        collapsed: isLargeDataset, // 用例多时默认折叠
                        itemStyle: {
                            color: '#38bdf8',
                            borderColor: '#60a5fa',
                            borderWidth: 1.5
                        },
                        label: {
                            color: '#ffffff',
                            fontSize: moduleFontSize,
                            fontWeight: '600'
                        }
                    };
                    data.children.push(modules[tc.module]);
                }
                
                // 用例数量多时截断标题
                const maxTitleLength = isLargeDataset ? 25 : 40;
                const displayTitle = tc.title.length > maxTitleLength 
                    ? tc.title.substring(0, maxTitleLength) + '...' 
                    : tc.title;
                
                modules[tc.module].children.push({
                    name: displayTitle,
                    value: tc.caseId,
                    collapsed: false,
                    symbolSize: nodeSize,
                    itemStyle: {
                        color: '#4ade80',
                        borderColor: '#22c55e',
                        borderWidth: 1
                    },
                    label: {
                        color: '#f8fafc',
                        fontSize: fontSize,
                        backgroundColor: 'rgba(15, 23, 42, 0.7)',
                        padding: isLargeDataset ? [3, 6] : [6, 10],
                        borderRadius: 4
                    }
                });
            });

            // 为模块添加用例数量标记
            data.children.forEach(module => {
                module.name = module.name + ' (' + module.children.length + ')';
            });

            const option = {
                backgroundColor: 'transparent',
                tooltip: {
                    trigger: 'item',
                    triggerOn: 'mousemove',
                    backgroundColor: 'rgba(15, 23, 42, 0.95)',
                    borderColor: '#38bdf8',
                    borderWidth: 2,
                    textStyle: {
                        color: '#ffffff',
                        fontSize: 13
                    },
                    formatter: function (params) {
                        return '<div style="padding: 5px;">' +
                            '<strong style="color: #38bdf8;">' + params.name + '</strong><br/>' +
                            (params.value ? '<span style="color: #94a3b8;">ID: ' + params.value + '</span>' : '') +
                            '</div>';
                    }
                },
                series: [
                    {
                        type: 'tree',
                        data: [data],
                        top: '2%',
                        left: '3%',
                        bottom: '2%',
                        right: isLargeDataset ? '35%' : '20%', // 大数据集时给叶子节点更多空间
                        symbolSize: isLargeDataset ? 8 : 12,
                        orient: 'LR', // 左右布局，适合大量数据
                        itemStyle: {
                            color: '#4ade80',
                            borderColor: '#22c55e',
                            borderWidth: 1.5,
                            shadowBlur: isLargeDataset ? 0 : 8,
                            shadowColor: 'rgba(74, 222, 128, 0.3)'
                        },
                        lineStyle: {
                            color: '#475569',
                            width: isLargeDataset ? 1 : 2,
                            curveness: 0.5,
                            shadowBlur: isLargeDataset ? 0 : 4,
                            shadowColor: 'rgba(71, 85, 105, 0.3)'
                        },
                        label: {
                            position: 'left',
                            verticalAlign: 'middle',
                            align: 'right',
                            fontSize: isLargeDataset ? 11 : 13,
                            color: '#f8fafc',
                            backgroundColor: 'rgba(15, 23, 42, 0.6)',
                            padding: isLargeDataset ? [3, 6] : [5, 10],
                            borderRadius: 4,
                            shadowBlur: isLargeDataset ? 0 : 4,
                            shadowColor: 'rgba(0, 0, 0, 0.3)'
                        },
                        leaves: {
                            label: {
                                position: 'right',
                                verticalAlign: 'middle',
                                align: 'left',
                                color: '#f8fafc',
                                fontSize: fontSize,
                                backgroundColor: 'rgba(15, 23, 42, 0.7)',
                                padding: isLargeDataset ? [2, 5] : [6, 10],
                                borderRadius: 4,
                                overflow: 'truncate',
                                ellipsis: '...'
                            }
                        },
                        expandAndCollapse: true,
                        animationDuration: isLargeDataset ? 300 : 550,
                        animationDurationUpdate: isLargeDataset ? 400 : 750,
                        initialTreeDepth: isLargeDataset ? 1 : 2, // 大数据集默认只展开一层
                        roam: true, // 启用缩放和平移
                        scaleLimit: {
                            min: 0.2,
                            max: 4
                        }
                    }
                ]
            };

            chart.setOption(option);

            // 响应式调整
            const resizeHandler = () => {
                chart.resize();
            };
            window.removeEventListener('resize', resizeHandler);
            window.addEventListener('resize', resizeHandler);
        };

        return {
            loading,
            evaluating,
            activeTab,
            testCases,
            referenceCases,
            evaluationResult,
            comparisonResult,
            currentPrdText,
            handleGenerate,
            handleCompare
        };
    }
});

// 注册组件
app.component('prd-input', PrdInput);
app.component('test-case-display', TestCaseDisplay);
app.component('case-comparison', CaseComparison);
app.component('evaluation-result', EvaluationResult);

// 注册 Element Plus 图标
if (typeof ElementPlusIconsVue !== 'undefined') {
    for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
        app.component(key, component);
    }
} else {
    console.warn('Element Plus Icons not loaded, icons may not display correctly');
}

app.use(ElementPlus);
app.mount('#app');

// Vue 挂载成功后隐藏加载指示器
const loadingScreen = document.getElementById('loading-screen');
if (loadingScreen) {
    loadingScreen.classList.add('hidden');
}
console.log('App mounted successfully');
