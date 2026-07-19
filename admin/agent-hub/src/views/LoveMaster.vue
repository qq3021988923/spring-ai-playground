<template>
  <div class="love-container">
    <div class="top-bar">
      <div class="back-btn" @click="$router.push('/')">← 返回首页</div>
      <div class="top-center">💕 AI 恋爱大师 · 小红娘</div>
      <div class="top-dot"><span class="dot love-dot"></span></div>
    </div>
    <ChatRoom mode="love" :hints="hints" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ChatRoom from '../components/ChatRoom.vue'

const pool = [
  '如何提升自身魅力吸引潜在伴侣？',
  '怎样在社交场合主动结识心仪异性？',
  '线上交友有哪些注意事项能提高脱单成功率？',
  '如何克服单身时对恋爱的焦虑情绪？',
  '如何判断相亲对象是否值得深入发展？',
  '婚后如何平衡工作与家庭责任？',
  '怎样维护婚后夫妻间的亲密关系？',
  '婚后与伴侣家人产生矛盾，如何妥善解决？',
  '如何在婚后保持自我成长？',
  '婚后夫妻消费观念不同，如何协调理财规划？',
  '恋爱中如何有效处理双方的争吵？',
  '怎样给恋爱中的对方制造浪漫惊喜？',
  '恋爱中如何保持自我，避免过度依赖对方？',
  '如何在恋爱中与对方有效沟通未来规划？',
  '恋爱中发现对方缺点，如何巧妙沟通让其改善？',
]
const hints = computed(() => {
  const shuffled = [...pool].sort(() => Math.random() - 0.5)
  return shuffled.slice(0, 3)
})
</script>

<style scoped>
.love-container { height: 100vh; display: flex; flex-direction: column; background: linear-gradient(180deg, #0a0020, #0d0d28); position: relative; }

.love-container::before {
  content: ''; position: absolute;
  inset: 0; background: url('/images/wx.png') center/cover no-repeat;
  opacity: 0.40;
  pointer-events: none;
  z-index: 0; }

.top-bar { display: flex; align-items: center; justify-content: space-between; padding: 0 20px; height: 52px; border-bottom: 1px solid rgba(255,0,122,0.12); flex-shrink: 0; background: rgba(255,255,255,0.02); backdrop-filter: blur(8px); }
.back-btn { color: rgba(255,255,255,0.75); cursor: pointer; font-size: 0.9rem; font-weight: 600; padding: 4px 10px; border-radius: 6px; transition: all 0.15s; position: relative; z-index: 1; }
.back-btn:hover { color: #ff00d4; background: rgba(255,0,212,0.08); }
.top-center { flex: 1; text-align: center; font-size: 0.9rem; font-weight: 600; color: rgba(255,255,255,0.75); position: relative; z-index: 1; }
.top-dot { width: 72px; display: flex; justify-content: flex-end; position: relative; z-index: 1; }
.dot { width: 7px; height: 7px; border-radius: 50%; }
.love-dot { background: #ff00d4; box-shadow: 0 0 8px rgba(255,0,212,0.5); animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%,100%{ opacity:.6; } 50%{ opacity:1; } }
</style>
