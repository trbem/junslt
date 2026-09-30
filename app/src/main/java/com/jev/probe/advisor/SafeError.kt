package com.jev.probe.advisor

import com.jev.probe.jev.ApiException

/** No raw provider response, exception message, chat, URL query, or API key in UI/logs. */
fun safeError(error: Exception): String = when (error) {
    is InvalidAdvisorResult -> "军师结果格式不完整，请重新生成"
    is ApiException -> if (error.status != null) "${error.route} HTTP ${error.status}，请检查密钥、模型与地址" else "${error.route}连接失败或超时，请检查网络与地址"
    else -> "请求失败，请检查配置和网络后重试"
}
