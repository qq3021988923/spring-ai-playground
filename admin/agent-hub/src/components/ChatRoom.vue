<script setup>
import { ref, computed, nextTick, watch, onBeforeUnmount, onMounted } from 'vue'
import { fetchManus, streamLove } from '../api'

const props = defineProps({ mode: { type: String, default: 'love' }, hints: { type: Array, default: () => [] } })

const inputMessage = ref('')
const messages = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const showScrollBtn = ref(false)
let currentEventSource = null
let searchTimer = null
const searchDots = ref('')
// 每台设备首次访问生成唯一 ID，存 localStorage 持久化
const userId = ref(localStorage.getItem('chatUserId') || 'u_' + Math.random().toString(36).slice(2, 10))
if (!localStorage.getItem('chatUserId')) {
  localStorage.setItem('chatUserId', userId.value)
}

// 自动标题
const emit = defineEmits(['title-ready'])
const titleSet = ref(false)
const maxInputLen = 500

// 点击提示词直接发送
const sendHint = (hint) => { inputMessage.value = hint; sendMessage() }

// 重试失败消息
const retryLast = () => {
  const lastUser = [...messages.value].reverse().find(m => m.role === 'user')
  if (!lastUser) return
  messages.value.push({ role: 'user', content: lastUser.content, time: new Date().toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'}) })
  loading.value = true
  scrollToBottom()
  props.mode === 'manus' ? sendManusStream(lastUser.content) : sendLoveStream(lastUser.content)
}

const modeConfig = {
  love: { icon: '💕', name: '知心 · 恋爱顾问', accent: '#ff00d4' },
  manus: { icon: '⚡', name: '小羊 · 智能助手', accent: '#00f0ff' },
}
const c = () => modeConfig[props.mode] || modeConfig.love

// ====== 滚动到底部按钮 ======
const onScroll = () => {
  if (!messagesRef.value) return
  const el = messagesRef.value
  showScrollBtn.value = el.scrollHeight - el.scrollTop - el.clientHeight > 200
}

// ====== 停止生成 ======
const stopGeneration = () => {
  if (currentEventSource) {
    currentEventSource.close()
    currentEventSource = null
  }
  loading.value = false
}

// ====== 复制消息 ======
const copyMessage = (content) => {
  navigator.clipboard.writeText(content).catch(() => {})
}

// ====== 清空对话 ======
const clearChat = () => {
  stopGeneration()
  messages.value = []
}

// ====== 搜索中动画 ======
// loading 文字：manus → "搜索中"，love → "思考中"
const loadingText = computed(() => props.mode === 'manus' ? '搜索中' : '思考中')

watch(loading, (val) => {
  if (val) {
    let dotCount = 0
    searchTimer = setInterval(() => { dotCount = (dotCount % 3) + 1; searchDots.value = '.'.repeat(dotCount) }, 400)
  } else {
    clearInterval(searchTimer)
    searchDots.value = ''
  }
})

onBeforeUnmount(() => { clearInterval(searchTimer); stopGeneration() })
onMounted(() => { messagesRef.value?.addEventListener('scroll', onScroll) })

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
      showScrollBtn.value = false
    }
  })
}

const sendMessage = () => {
  if (!inputMessage.value.trim() || loading.value) return
  const userMsg = inputMessage.value.trim()
  // 自动标题：首次对话用第一条消息前15字
  if (!titleSet.value && messages.value.length === 0) {
    const title = userMsg.length > 15 ? userMsg.slice(0, 15) + '...' : userMsg
    const key = props.mode + '_title_' + userId.value
    localStorage.setItem(key, title)
    emit('title-ready', title)
    titleSet.value = true
  }
  messages.value.push({ role: 'user', content: userMsg, time: new Date().toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'}) })
  inputMessage.value = ''
  loading.value = true
  scrollToBottom()
  props.mode === 'manus' ? sendManusStream(userMsg) : sendLoveStream(userMsg)
}

const sendManusStream = async (userMsg) => {
  try {
    const answer = await fetchManus(userMsg, userId.value)
    messages.value.push({ role: 'assistant', content: answer || '思考完成，当前对话无需调用工具' })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '网络出错了，请稍后再试' })
  } finally {
    loading.value = false
    currentEventSource = null
    scrollToBottom()
  }
}

const sendLoveStream = (userMsg) => {
  const i = messages.value.length
  messages.value.push({ role: 'assistant', content: '', time: new Date().toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'}) })
  currentEventSource = streamLove(userMsg, userId.value)
  currentEventSource.onmessage = (e) => { messages.value[i].content += e.data; scrollToBottom() }
  currentEventSource.onerror = () => { currentEventSource.close(); loading.value = false; currentEventSource = null; scrollToBottom() }
}

const handleKeyDown = (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }
</script>

<template>
  <div class="chat-container">
    <!-- 清空对话按钮 -->
    <div v-if="messages.length > 0" class="clear-bar" @click="clearChat">
      🗑 清空对话
    </div>

    <div class="messages" ref="messagesRef">
      <div v-if="messages.length === 0" class="empty">
        <div class="empty-icon">{{ c().icon }}</div>
        <p class="empty-name">{{ c().name }}</p>
        <div v-if="hints.length > 0" class="hints-area">
          <p class="hint-label">试试问：</p>
          <p v-for="(h, i) in hints" :key="i" class="hint-item" @click="sendHint(h)">{{ h }}</p>
        </div>
      </div>

      <template v-for="(msg, i) in messages" :key="i">
        <div :class="['msg-row', msg.role]">
          <div :class="['msg-col', msg.role]">
            <div :class="['bubble', msg.role]">{{ msg.content }}</div>
            <div :class="['msg-time', msg.role]">{{ msg.time || '' }}</div>
          </div>
          <div v-if="msg.role === 'assistant' && msg.content && msg.content.includes('出错')"
            class="copy-btn retry-btn" @click="retryLast" title="重新生成">🔄</div>
          <div v-else-if="msg.role === 'assistant' && msg.content"
            class="copy-btn" @click="copyMessage(msg.content)" title="复制">📋</div>
        </div>
      </template>

      <!-- Loading 状态 -->
      <div v-if="loading" class="msg-row assistant">
        <div class="search-box">
          <span class="search-text">{{ loadingText }}{{ searchDots }}</span>
        </div>
      </div>
    </div>

    <!-- 滚动到底部 -->
    <div v-if="showScrollBtn" class="scroll-btn" @click="scrollToBottom">↓</div>

    <div class="input-bar">
      <div class="input-wrap">
        <textarea v-model="inputMessage" placeholder="输入消息，Enter 发送..."
          @keydown="handleKeyDown" rows="1" :disabled="loading" :maxlength="maxInputLen"></textarea>
        <button v-if="loading" class="stop-btn" @click="stopGeneration">⏹ 停止</button>
        <button v-else :disabled="!inputMessage.trim()" @click="sendMessage">发送</button>
      </div>
      <div class="input-info">
        <span :class="['char-count', { over: inputMessage.length > maxInputLen - 50 }]">
          {{ inputMessage.length }}/{{ maxInputLen }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-container { display: flex; flex-direction: column; flex: 1; overflow: hidden; position: relative; z-index: 1; }

/* ===== 清空按钮 ===== */
.clear-bar {
  text-align: center;
  padding: 6px;
  font-size: 0.75rem;
  color: rgba(255,255,255,0.3);
  cursor: pointer;
  transition: color 0.2s;
  background: rgba(255,255,255,0.02);
  border-bottom: 1px solid rgba(255,255,255,0.04);
}
.clear-bar:hover { color: #f56c6c; }

/* ===== 消息区 ===== */
.messages { flex: 1; overflow-y: auto; padding: 20px 16px; max-width: 800px; width: 100%; margin: 0 auto; }

.empty { text-align: center; margin-top: 15vh; }
.empty-icon { font-size: 44px; margin-bottom: 10px; animation: floatIcon 3s ease-in-out infinite; }
@keyframes floatIcon { 0%,100%{ transform: translateY(0); } 50%{ transform: translateY(-6px); } }
.empty-name { font-size: 1rem; font-weight: 600; color: rgba(255,255,255,0.75); margin-bottom: 4px; }

.hints-area { margin-top: 12px; }
.hint-label { font-size: 0.78rem; color: #fff; opacity: 0.5; margin-bottom: 8px; }
.hint-item { font-size: 0.78rem; color: #fff; opacity: 0.6; line-height: 1.8; cursor: pointer; transition: opacity 0.2s; }
.hint-item:hover { opacity: 1; color: #00f0ff; }

/* ===== 消息行 ===== */
.msg-row { display: flex; align-items: flex-end; gap: 6px; margin-bottom: 18px; }
.msg-row.user { justify-content: flex-end; }

/* ===== 气泡 ===== */
.bubble { max-width: 72%; padding: 10px 16px; border-radius: 14px; font-size: 14px; line-height: 1.65; overflow-wrap: break-word; white-space: pre-wrap; backdrop-filter: blur(6px); }
.bubble.user { background: rgba(144,0,255,0.45); color: #fff; border-bottom-right-radius: 2px; border: 1px solid rgba(255,255,255,0.1); }
.bubble.assistant { background: rgba(255,255,255,0.06); backdrop-filter: blur(10px); border: 1px solid rgba(255,255,255,0.08); color: rgba(255,255,255,0.8); border-bottom-left-radius: 2px; }


/* ===== 复制按钮 ===== */
/* 消息列 */
.msg-col { display: flex; flex-direction: column; max-width: 72%; }
.msg-col.user { align-items: flex-end; }
.msg-time { font-size: 0.6rem; color: rgba(255,255,255,0.15); margin-top: 2px; padding: 0 4px; }
.msg-time.user { text-align: right; }
.msg-time.assistant { text-align: left; }

.copy-btn { opacity: 0; cursor: pointer; font-size: 0.75rem; padding: 4px; border-radius: 4px; transition: opacity 0.15s; flex-shrink: 0; }
.msg-row:hover .copy-btn { opacity: 0.5; }
.copy-btn:hover { opacity: 1 !important; background: rgba(255,255,255,0.08); }

/* ===== 搜索中 ===== */
.search-box { display: inline-flex; padding: 10px 18px; background: rgba(255,255,255,0.06); backdrop-filter: blur(10px); border: 1px solid rgba(0,240,255,0.12); border-radius: 14px; border-bottom-left-radius: 2px; }
.search-text { font-size: 13px; color: rgba(0,240,255,0.7); letter-spacing: 1px; }

/* ===== 打字指示器 ===== */
.typing-box { display: inline-flex; gap: 4px; padding: 10px 16px; background: rgba(255,255,255,0.06); backdrop-filter: blur(10px); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; border-bottom-left-radius: 2px; }
.typing-box span { width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,0.2); animation: dot 1.2s infinite both; }
.typing-box span:nth-child(2) { animation-delay: 0.2s; } .typing-box span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot { 0%,80%,100%{opacity:.3} 40%{opacity:1} }

/* ===== 滚动到底部 ===== */
.scroll-btn {
  position: absolute; bottom: 80px; right: 24px;
  width: 36px; height: 36px; border-radius: 50%;
  background: rgba(144,0,255,0.6); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 1.1rem; cursor: pointer; z-index: 10;
  backdrop-filter: blur(8px); border: 1px solid rgba(255,255,255,0.12);
  transition: all 0.2s; animation: fadeIn 0.2s ease;
}
.scroll-btn:hover { background: rgba(144,0,255,0.85); transform: scale(1.05); }
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

/* ===== 输入栏 ===== */
.input-bar { padding: 12px 16px 18px; border-top: 1px solid rgba(255,255,255,0.05); background: rgba(10,0,32,0.5); backdrop-filter: blur(12px); }
.input-wrap { display: flex; gap: 10px; max-width: 800px; margin: 0 auto; align-items: flex-end; }
.input-wrap textarea { flex: 1; border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 10px 14px; font-size: 14px; resize: none; outline: none; background: rgba(255,255,255,0.04); backdrop-filter: blur(4px); color: #e0e0f0; transition: border-color .2s; font-family: inherit; }
.input-wrap textarea:focus { border-color: rgba(144,0,255,0.3); }
.input-wrap textarea::placeholder { color: rgba(255,255,255,0.3); }
.input-wrap textarea:disabled { opacity: 0.4; }

.input-wrap button { padding: 10px 22px; border: none; border-radius: 12px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all .15s; background: #fff; color: #9000ff; }
.input-wrap button:hover:not(:disabled) { background: rgba(255,255,255,0.9); box-shadow: 0 4px 20px rgba(255,255,255,0.2); }
.input-wrap button:disabled { opacity: 0.3; cursor: not-allowed; }

.stop-btn { padding: 10px 22px !important; border: none !important; border-radius: 12px !important; font-size: 14px !important; font-weight: 600 !important; cursor: pointer !important; background: #f56c6c !important; color: #fff !important; animation: pulse-stop 1.5s infinite; }
.stop-btn:hover { background: #e85b5b !important; }
@keyframes pulse-stop { 0%,100%{ opacity: 1; } 50%{ opacity: .75; } }

/* ===== 输入字数统计 ===== */
.input-info { max-width: 800px; margin: 6px auto 0; text-align: right; }
.char-count { font-size: 0.65rem; color: rgba(255,255,255,0.15); }
.char-count.over { color: rgba(255,107,107,0.6); }

/* ===== 重试按钮 ===== */
.retry-btn:hover { color: #ffbe0b !important; }
</style>
