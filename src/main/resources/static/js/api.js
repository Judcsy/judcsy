const API_BASE = '/api';

// 配置 Axios 默认值
axios.defaults.headers.post['Content-Type'] = 'application/json';

const api = {
    /**
     * 生成测试用例
     * @param {Object} data - { prdText: string, options: Object }
     * @returns {Promise}
     */
    generateTestCases: (data) => {
        return axios.post(`${API_BASE}/testcase/generate`, data);
    },

    /**
     * 获取飞书文档内容
     * @param {string} url - 飞书文档链接
     * @returns {Promise}
     */
    fetchFeishuContent: (url) => {
        return axios.get(`${API_BASE}/feishu/content`, { params: { url } });
    },

    /**
     * 评测测试用例
     * @param {Array} testCases - 测试用例列表
     * @returns {Promise}
     */
    evaluateTestCases: (testCases) => {
        return axios.post(`${API_BASE}/evaluate`, testCases);
    },

    /**
     * 对比评分 - 比较人工用例与AI生成用例
     * @param {Object} data - { aiCases: Array, referenceCases: Array }
     * @returns {Promise}
     */
    compareTestCases: (data) => {
        return axios.post(`${API_BASE}/testcase/compare`, data);
    },

    /**
     * 检查飞书配置状态
     * @returns {Promise}
     */
    checkFeishuStatus: () => {
        return axios.get(`${API_BASE}/feishu/status`);
    },

    /**
     * 配置飞书应用凭证
     * @param {Object} config - { appId: string, appSecret: string }
     * @returns {Promise}
     */
    configureFeishu: (config) => {
        return axios.post(`${API_BASE}/feishu/config`, config);
    }
};

// 导出给全局使用 (因为没有模块系统)
window.api = api;
