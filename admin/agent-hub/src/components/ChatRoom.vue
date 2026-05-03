<script setup>
import { ref, nextTick, watch } from 'vue'
import { chat } from '../api'

const props = defineProps({
  mode: { type: String, default: 'agent' }
})

const inputMessage = ref('')
const messages = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const inputRef = ref(null)

const modes = {
  agent: {
    icon: '',
    name: '智能助手小智',
    hint: '试试问：现在几点了？ / 100加200等于多少',
    color: '#1F67FF'
  },
  love: {
    icon: '💕',
    name: '恋爱顾问小红娘',
    hint: '试试问：不敢表白怎么办？ / 吵架了怎么和好？',
    color: '#F56C6C'
  },
  ollama: {
    icon: '🦙',
    name: 'Ollama 本地模型',
    hint: '试试问：用 Python 写个冒泡排序 / 解释一下什么是递归',
    color: '#67C23A'
  }
}

const current = () => modes[props.mode] || modes.agent

watch(() => props.mode, () => {
  messages.value = []
})

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
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
    const res = await chat({ message: userMsg, mode: props.mode })
    messages.value.push({ role: 'assistant', content: res || '抱歉，没有收到回复' })
  } catch (e) {
    console.error('发送失败', e)
    messages.value.push({ role: 'assistant', content: '网络出错了，请稍后再试' })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const handleKeyDown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() }
}
</script>

<template>
  <div class="chat-container">
    <!-- 顶部标题栏 -->
    <div class="header">
      <span class="header-icon">{{ current().icon }}</span>
      <div class="header-info">
        <span class="header-name">{{ current().name }}</span>
        <span class="header-desc">{{ props.mode === 'agent' ? 'ReAct Agent · 工具调用' : props.mode === 'love' ? 'RAG 检索增强 · 知识库问答' : '本地模型 · 免费· 离线 · 隐私安全' }}</span>
      </div>
    </div>

    <!-- 消息区 -->
    <div class="messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="empty">
        <div class="empty-icon">{{ current().icon }}</div>
        <p>你好，{{ current().name }}</p>
        <p class="hint">{{ current().hint }}</p>
      </div>

      <template v-for="(msg, i) in messages" :key="i">
        <div :class="['msg', msg.role]">
          <div :class="['bubble', msg.role]" :style="msg.role === 'user' ? { background: current().color } : {}">
            {{ msg.content }}
          </div>
        </div>
      </template>

      <div v-if="loading" class="msg assistant">
        <div class="bubble assistant typing"><span></span><span></span><span></span></div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <textarea
        v-model="inputMessage"
        placeholder="输入消息... Enter 发送"
        @keydown="handleKeyDown"
        rows="1" ref="inputRef"
      ></textarea>
      <button
        :disabled="!inputMessage.trim() || loading"
        :style="{ background: current().color }"
        @click="sendMessage"
      >发送</button>
    </div>
  </div>
</template>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f6fa;
}

/* ====== 顶部标题栏 ====== */
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 28px;
  background: white;
  border-bottom: 1px solid #eee;
}
.header-icon { font-size: 28px; }
.header-info { display: flex; flex-direction: column; gap: 2px; }
.header-name { font-size: 16px; font-weight: 600; color: #303030; }
.header-desc { font-size: 12px; color: #999; }

/* ====== 消息区 ====== */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 20px;
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
}
.empty { text-align: center; color: #999; margin-top: 12vh; }
.empty-icon { font-size: 52px; margin-bottom: 14px; }
.empty p { font-size: 14px; line-height: 1.8; }
.hint { color: #1F67FF !important; font-size: 13px; margin-top: 8px; }

.msg { display: flex; margin-bottom: 16px; }
.msg.user { justify-content: flex-end; }

.bubble {
  max-width: 70%;
  padding: 12px 18px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.bubble.user { color: white; border-bottom-right-radius: 4px; }
.bubble.assistant { background: white; color: #333; border-bottom-left-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }

.typing { display: inline-flex; gap: 5px; padding: 14px 22px !important; }
.typing span {
  width: 8px; height: 8px; border-radius: 50%; background: #bbb;
  animation: dot 1.2s infinite both;
}
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot { 0%,80%,100%{opacity:.3;transform:scale(.7)} 40%{opacity:1;transform:scale(1)} }

/* ====== 输入区 ====== */
.input-area {
  display: flex; gap: 10px;
  padding: 14px 20px 22px;
  background: white;
  border-top: 1px solid #eee;
  max-width: 800px; width: 100%; margin: 0 auto;
}
.input-area textarea {
  flex: 1; border: 1px solid #e0e0e0; border-radius: 10px;
  padding: 11px 15px; font-size: 14px; resize: none;
  outline: none; transition: border-color .2s;
}
.input-area textarea:focus { border-color: #1F67FF; }
.input-area button {
  padding: 0 24px; color: white; border: none; border-radius: 10px;
  font-size: 14px; cursor: pointer; transition: opacity .2s;
}
.input-area button:hover:not(:disabled) { opacity: .88; }
.input-area button:disabled { opacity: .35; cursor: not-allowed; }

::-webkit-scrollbar { width: 5px; }
::-webkit-scrollbar-thumb { background: #ddd; border-radius: 4px; }
</style>
