import axios from 'axios'

const BASE_URL = 'http://localhost:8090/ai'

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

export default {
  chat,
  streamManus
}