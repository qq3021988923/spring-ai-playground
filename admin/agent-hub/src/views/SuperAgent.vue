<template>
  <div class="agent-container">
    <div class="top-bar">
      <div class="back-btn" @click="$router.push('/')">← 返回</div>
      <div class="top-center" v-if="!title">小羊 · 智能助手</div>
      <div class="top-center chat-title" v-else>{{ title }}</div>
      <div class="top-actions">
        <span class="dot agent-dot"></span>
      </div>
    </div>
    <ChatRoom mode="manus" :hints="hints" :key="chatKey" @title-ready="onTitle" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import ChatRoom from '../components/ChatRoom.vue'

const title = ref('')
const chatKey = ref(0)
const onTitle = (t) => { title.value = t }

const pool = [
  '2026 年 AI 发展趋势',
  '搜索 Spring AI 教程',
  '帮我搜索深圳福田风景图片',
  '查看电脑系统详细信息',
  '查看网络连接状态',
  '查看电脑系统版本',
  '帮我抓取网页内容',
  '帮我下载一张猫咪图片',
  '查询今天的天气',
  '帮我生成一份学习计划',
]
const hints = computed(() => {
  const shuffled = [...pool].sort(() => Math.random() - 0.5)
  return shuffled.slice(0, 3)
})
</script>

<style scoped>
.agent-container { height: 100vh; display: flex; flex-direction: column; background: linear-gradient(180deg, #0a0020, #0d0d28); position: relative; }
.agent-container::before { content: ''; position: absolute; inset: 0; background: url('/images/wx.png') center/cover no-repeat; opacity: 0.40; pointer-events: none; z-index: 0; }

.top-bar { display: flex; align-items: center; padding: 0 16px; height: 52px; border-bottom: 1px solid rgba(0,240,255,0.1); flex-shrink: 0; background: rgba(255,255,255,0.02); backdrop-filter: blur(8px); }
.back-btn { color: rgba(255,255,255,0.75); cursor: pointer; font-size: 0.85rem; font-weight: 600; padding: 4px 8px; border-radius: 6px; transition: all 0.15s; position: relative; z-index: 1; }
.back-btn:hover { color: #00f0ff; background: rgba(0,240,255,0.08); }
.top-center { flex: 1; text-align: center; font-size: 0.88rem; font-weight: 600; color: rgba(255,255,255,0.7); position: relative; z-index: 1; }
.chat-title { color: rgba(255,255,255,0.5); font-size: 0.8rem; }
.top-actions { display: flex; align-items: center; gap: 12px; position: relative; z-index: 1; }
.new-chat-btn { color: rgba(255,255,255,0.35); cursor: pointer; font-size: 1.3rem; font-weight: 300; transition: all 0.2s; line-height: 1; }
.new-chat-btn:hover { color: #00f0ff; transform: scale(1.2); }
.dot { width: 7px; height: 7px; border-radius: 50%; }
.agent-dot { background: #00f0ff; box-shadow: 0 0 8px rgba(0,240,255,0.5); animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%,100%{ opacity:.6; } 50%{ opacity:1; } }
</style>
