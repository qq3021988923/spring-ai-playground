<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { chatAgent } from '../api'

const inputMessage = ref('')
const messages = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const inputRef = ref(null)

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return

  const userMsg = inputMessage.value.trim()
  messages.value.push({ role: 'user', content: userMsg })
  inputMessage.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await chatAgent(userMsg)
    messages.value.push({ role: 'assistant', content: res || '抱歉，没有收到回复' })
  } catch (error) {
    console.error('发送失败', error)
    messages.value.push({ role: 'assistant', content: '网络出错了，请稍后再试' })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const handleKeyDown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const autoResize = () => {
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
      inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 120) + 'px'
    }
  })
}
</script>

<template>
  <div class="chat-container">
    <!-- 消息区域 -->
    <div class="messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="empty">
        <div class="empty-icon">🤖</div>
        <p>你好，我是 AI Agent，可以帮你查时间、算数、查用户信息</p>
        <p class="hint">试试问：现在几点了？</p>
      </div>

      <template v-for="(msg, i) in messages" :key="i">
        <div :class="['msg', msg.role]">
          <div class="bubble">{{ msg.content }}</div>
        </div>
      </template>

      <div v-if="loading" class="msg assistant">
        <div class="bubble typing">
          <span></span><span></span><span></span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <textarea
        v-model="inputMessage"
        placeholder="输入消息... Enter 发送"
        @keydown="handleKeyDown"
        @input="autoResize"
        rows="1"
        ref="inputRef"
      ></textarea>
      <button :disabled="!inputMessage.trim() || loading" @click="sendMessage">
        发送
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f0f2f5;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 32px 20px;
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
}

.empty {
  text-align: center;
  color: #999;
  margin-top: 15vh;
}

.empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
}

.empty p {
  font-size: 14px;
  line-height: 1.8;
}

.hint {
  color: #1F67FF !important;
  font-size: 13px;
  margin-top: 8px;
}

.msg {
  display: flex;
  margin-bottom: 16px;
}

.msg.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 70%;
  padding: 12px 18px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.msg.user .bubble {
  background: #1F67FF;
  color: white;
  border-bottom-right-radius: 4px;
}

.msg.assistant .bubble {
  background: white;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.typing {
  display: inline-flex;
  gap: 5px;
  padding: 14px 22px !important;
}

.typing span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #bbb;
  animation: dot 1.2s infinite both;
}

.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.7); }
  40% { opacity: 1; transform: scale(1); }
}

.input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px 24px;
  background: white;
  border-top: 1px solid #e8e8e8;
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
}

.input-area textarea {
  flex: 1;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 14px;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
}

.input-area textarea:focus {
  border-color: #1F67FF;
}

.input-area button {
  padding: 0 24px;
  background: #1F67FF;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.input-area button:hover:not(:disabled) {
  opacity: 0.9;
}

.input-area button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
