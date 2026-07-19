import axios from 'axios'

const BASE_URL = '/ai'

// 🔥 新增 userId 参数（默认 user001），拼接到 query 中
export const chat = (data, userId = 'user001') => {
  return axios.post(`${BASE_URL}/chat?userId=${userId}`, data).then(res => res.data)
}

// 🔥 新增 userId 参数（默认 user001），拼接到 SSE 请求 URL 中
export const streamManus = (message, userId = 'user001', onData, onDone, onError) => {
  const url = `${BASE_URL}/manus/stream?userId=${userId}&message=${encodeURIComponent(message)}`
  const eventSource = new EventSource(url)
  eventSource.onmessage = (event) => {
    onData(event.data)
  }
  eventSource.onerror = (e) => {
    eventSource.close()
    if (eventSource.readyState === EventSource.CLOSED) {
      onDone()
    } else {
      onError(e)
    }
  }
  return eventSource
}

// 恋爱顾问流式接口（返回 EventSource，由调用方自己绑事件）
export const streamLove = (message, userId = 'user001') => {
  const url = `${BASE_URL}/love/chat/sse/multi-query?userId=${userId}&message=${encodeURIComponent(message)}`
  return new EventSource(url)
}

export default {
  chat,
  streamManus,
  streamLove
}