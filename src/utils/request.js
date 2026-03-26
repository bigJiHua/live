import axios from "axios";
import { showToast, showSuccessToast, showFailToast } from "vant";

// 必备
const baseUrl = "http://192.168.0.103:3001/api/v1";

// 创建axios实例
const request = axios.create({
  baseURL: baseUrl,
  withCredentials: true, // 允许携带cookie
  timeout: 15000, // 请求超时时间
});

// 请求拦截器
request.interceptors.request.use(
  async (config) => {
    // 添加 token
    const token = localStorage.getItem("finance_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    showToast({
      message: error.message,
      type: "fail",
      duration: 1500,
    });
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const { data: res } = response;

    // 选择性展示通知
    if (res.ismessage === false) return response;

    // 状态码处理
    if (res.status === 200 || res.status === 201) {
      // 成功
      showSuccessToast({
        message: res.message || "操作成功",
        duration: 1500,
      });
    } else {
      // 失败或警告
      showToast({
        message: res.message || "请求失败",
        type: "warning",
        duration: 1500,
      });
    }

    return response;
  },
  (error) => {
    // 获取错误状态码
    const errorCode = error.response ? error.response.status : 200;
    // 获取错误信息
    const message = error.response?.data?.message || error.message;

    // 显示错误提示
    showFailToast({
      message: message || "请求失败",
      duration: 1500,
    });

    // 401: 未认证或认证失败
    if (errorCode === 401) {
      localStorage.removeItem("finance_token");
      localStorage.removeItem("Username");
      localStorage.removeItem("Useridentity");
      setTimeout(() => {
        window.location.reload();
      }, 1500);
    }

    // 423: 被锁定（需要 PIN 验证）
    if (errorCode === 423) {
      // 可以在这里触发 PIN 验证弹窗
      console.log("需要 PIN 验证");
      // 可以触发全局事件让组件处理
      window.dispatchEvent(new CustomEvent("needPinVerify"));
    }

    return Promise.reject(error.response);
  }
);

export default request;
