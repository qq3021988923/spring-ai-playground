import axios from 'axios'

export const chat = (data) => {
  return axios.post('http://localhost:8090/ai/chat', data).then(res => res.data)
}

export default {
  chat
}
