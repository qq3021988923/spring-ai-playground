import axios from 'axios'

export const chatAgent = (message) => {
  return axios.get('http://localhost:8090/ai/agent', {
    params: { input: message }
  }).then(res => res.data)
}

export default {
  chatAgent
}
