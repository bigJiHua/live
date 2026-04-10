/**
 * Request 核心模块
 * 整合所有拦截器，创建最终的 request 实例
 */

import axios from 'axios'
import router from '@/router'
import config from './config'
import { createRequestInterceptor } from './interceptors/request'
import { createResponseInterceptor } from './interceptors/response'

// 创建 axios 实例
const request = axios.create({
  baseURL: config.baseURL,
  withCredentials: config.withCredentials,
  timeout: config.timeout,
  // 禁用 Axios 默认的状态码校验，让所有响应都进入拦截器处理
  validateStatus: () => true,
})

// 注册拦截器
request.interceptors.request.use(createRequestInterceptor(router))
request.interceptors.response.use(...createResponseInterceptor(router))

export default request
