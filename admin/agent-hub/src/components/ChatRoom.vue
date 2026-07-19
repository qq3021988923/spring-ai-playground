<script setup>
import { ref, nextTick, watch, onBeforeUnmount, onMounted } from 'vue'
import { streamManus, streamLove } from '../api'

const props = defineProps({ mode: { type: String, default: 'love' }, hints: { type: Array, default: () => [] } })

const inputMessage = ref('')
const messages = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const showScrollBtn = ref(false)
let currentEventSource = null
let searchTimer = null
const searchDots = ref('')
const userId = ref(localStorage.getItem('chatUserId') || 'user001')

const modeConfig = {
  love: { icon: '💕', name: '恋爱顾问小红娘', accent: '#ff00d4' },
  manus: { icon: '⚡', name: '超级智能体 小羊~', accent: '#00f0ff' },
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
  messages.value.push({ role: 'user', content: inputMessage.value.trim() })
  inputMessage.value = ''
  loading.value = true
  scrollToBottom()
  props.mode === 'manus' ? sendManusStream(messages.value[messages.value.length-1].content) : sendLoveStream(messages.value[messages.value.length-1].content)
}

const sendManusStream = (userMsg) => {
  // 插入占位消息用于显示最终回答
  const aiMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
  let statusMsgs = []  // 临时收集状态/工具消息

  currentEventSource = streamManus(userMsg, userId.value,
    (data) => {
      if (!data || !data.trim()) return

      // 状态消息：[STATUS] xxx
      if (data.startsWith('[STATUS]')) {
        statusMsgs.push(data.replace('[STATUS] ', ''))
        messages.value[aiMsgIndex].toolCalls = [...statusMsgs]  // Vue 追踪
        scrollToBottom()
        return
      }

      // 工具消息：[TOOL] xxx
      if (data.startsWith('[TOOL]')) {
        statusMsgs.push(data.replace('[TOOL] ', '🔧 '))
        messages.value[aiMsgIndex].toolCalls = [...statusMsgs]
        scrollToBottom()
        return
      }

      // 错误消息
      if (data.startsWith('[ERROR]')) {
        messages.value[aiMsgIndex].content = data.replace('[ERROR] ', '⚠️ ')
        loading.value = false
        return
      }

      // 完成标记
      if (data === '[DONE]') {
        return
      }

      // 普通内容：追加到回答
      messages.value[aiMsgIndex].content += data
      scrollToBottom()
    },
    () => { loading.value = false; currentEventSource = null; scrollToBottom() },
    () => { messages.value[aiMsgIndex].content = '网络出错了，请稍后再试'; loading.value = false; currentEventSource = null; scrollToBottom() }
  )
}

const sendLoveStream = (userMsg) => {
  const i = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
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
          <p v-for="(h, i) in hints" :key="i" class="hint-item">{{ h }}</p>
        </div>
      </div>

      <template v-for="(msg, i) in messages" :key="i">
        <div :class="['msg-row', msg.role]">
          <div style="display:flex;flex-direction:column;gap:4px;max-width:72%;">
            <!-- 工具调用状态（可折叠） -->
            <div v-if="msg.toolCalls && msg.toolCalls.length > 0 && !msg.content" class="tool-status">
              <div v-for="(t, j) in msg.toolCalls" :key="j" class="tool-line">{{ t }}</div>
            </div>
            <!-- 回答气泡 -->
            <div v-if="msg.content" :class="['bubble', msg.role]">
              <!-- 工具调用历史（已折叠） -->
              <div v-if="msg.toolCalls && msg.toolCalls.length > 0" class="tool-summary" @click="msg.showTools = !msg.showTools">
                🔧 调用了 {{ msg.toolCalls.length }} 个工具 {{ msg.showTools ? '▲' : '▼' }}
              </div>
              <div v-if="msg.showTools && msg.toolCalls" class="tool-detail">
                <div v-for="(t, j) in msg.toolCalls" :key="j" class="tool-line-detail">{{ t }}</div>
              </div>
              {{ msg.content }}
            </div>
          </div>
          <!-- 复制按钮 -->
          <div v-if="msg.role === 'assistant' && msg.content"
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
          @keydown="handleKeyDown" rows="1" :disabled="loading"></textarea>
        <!-- 停止生成 / 发送 -->
        <button v-if="loading" class="stop-btn" @click="stopGeneration">⏹ 停止</button>
        <button v-else :disabled="!inputMessage.trim()" @click="sendMessage">发送</button>
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
.bubble { max-width: 72%; padding: 10px 16px; border-radius: 14px; font-size: 14px; line-height: 1.65; word-break: break-word; backdrop-filter: blur(6px); }
.bubble.user { background: rgba(144,0,255,0.45); color: #fff; border-bottom-right-radius: 2px; border: 1px solid rgba(255,255,255,0.1); }
.bubble.assistant { background: rgba(255,255,255,0.06); backdrop-filter: blur(10px); border: 1px solid rgba(255,255,255,0.08); color: rgba(255,255,255,0.8); border-bottom-left-radius: 2px; }

/* ===== 工具调用状态 ===== */
.tool-status { display: flex; flex-direction: column; gap: 3px; }
.tool-line { font-size: 0.7rem; color: rgba(0,240,255,0.5); padding: 4px 10px; background: rgba(0,240,255,0.04); border-radius: 6px; border-left: 2px solid rgba(0,240,255,0.15); }

.tool-summary { font-size: 0.68rem; color: rgba(0,240,255,0.5); cursor: pointer; padding: 3px 0; margin-bottom: 6px; border-bottom: 1px solid rgba(255,255,255,0.04); user-select: none; }
.tool-summary:hover { color: rgba(0,240,255,0.8); }

.tool-detail { display: flex; flex-direction: column; gap: 2px; margin-bottom: 8px; }
.tool-line-detail { font-size: 0.65rem; color: rgba(255,255,255,0.3); padding: 2px 6px; }

/* ===== 复制按钮 ===== */
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
</style>
