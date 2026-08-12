import axios from 'axios'

const BASE_URL = '/ai'

// 🔥 新增 userId 参数（默认 user001），拼接到 query 中
export const chat = (data, userId = 'user001') => {
  return axios.post(`${BASE_URL}/chat?userId=${userId}`, data).then(res => res.data)
}

// 超级智能体（String 返回，后台思考完一次性给，用 fetch）
export const fetchManus = async (message, userId = 'user001') => {
  const url = `${BASE_URL}/manus/stream?userId=${userId}&message=${encodeURIComponent(message)}`
  const res = await fetch(url)
  return await res.text()
}

// 恋爱顾问流式接口（返回 EventSource，由调用方自己绑事件）
export const streamLove = (message, userId = 'user001') => {
  const url = `${BASE_URL}/love/chat/sse/multi-query?userId=${userId}&message=${encodeURIComponent(message)}`
  return new EventSource(url)
}

export default {
  chat,
  fetchManus,
  streamLove
}