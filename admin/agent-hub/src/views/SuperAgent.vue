<template>
  <div class="agent-container">
    <div class="top-bar">
      <div class="back-btn" @click="$router.push('/')">← 返回首页</div>
      <div class="top-center">⚡ AI 超级智能体 · 小羊~</div>
      <div class="top-dot"><span class="dot agent-dot"></span></div>
    </div>
    <ChatRoom mode="manus" :hints="hints" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ChatRoom from '../components/ChatRoom.vue'

// 每次进入页面随机选 3 条提示
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
.top-bar { display: flex; align-items: center; justify-content: space-between; padding: 0 20px; height: 52px; border-bottom: 1px solid rgba(0,240,255,0.1); flex-shrink: 0; background: rgba(255,255,255,0.02); backdrop-filter: blur(8px); }
.back-btn { color: rgba(255,255,255,0.75); cursor: pointer; font-size: 0.9rem; font-weight: 600; padding: 4px 10px; border-radius: 6px; transition: all 0.15s; position: relative; z-index: 1; }
.back-btn:hover { color: #00f0ff; background: rgba(0,240,255,0.08); }
.top-center { flex: 1; text-align: center; font-size: 0.9rem; font-weight: 600; color: rgba(255,255,255,0.75); position: relative; z-index: 1; }
.top-dot { width: 72px; display: flex; justify-content: flex-end; position: relative; z-index: 1; }
.dot { width: 7px; height: 7px; border-radius: 50%; }
.agent-dot { background: #00f0ff; box-shadow: 0 0 8px rgba(0,240,255,0.5); animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%,100%{ opacity:.6; } 50%{ opacity:1; } }
</style>
