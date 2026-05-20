import axios from 'axios'

const BASE_URL = 'http://localhost:8090/ai'

export const chat = (data) => {
  return axios.post(BASE_URL + '/chat', data).then(res => res.data)
}

export const streamManus = (message, onData, onDone, onError) => {
  const url = BASE_URL + '/manus/stream?message=' + encodeURIComponent(message)
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