<template>
  <div class="home-container">
    <div class="bg-circles">
      <div class="circle c1"></div><div class="circle c2"></div><div class="circle c3"></div>
    </div>

    <!-- 雪花粒子 -->
    <div class="snowflakes">
      <span v-for="i in 25" :key="i" class="snowflake" :style="{
        left: Math.random() * 100 + '%',
        animationDuration: (Math.random() * 5 + 5) + 's',
        animationDelay: (Math.random() * 10) + 's',
        opacity: Math.random() * 0.4 + 0.1,
        fontSize: (Math.random() * 0.8 + 0.4) + 'rem'
      }">{{ ['❄','✦','❅','❆'][i % 4] }}</span>
    </div>

    <div class="header">
      <h1 class="title">小杨 AI 智能体平台</h1>
      <p class="subtitle">/ 探索 AI 的无限可能 /</p>
      <div class="line"></div>
    </div>

    <div class="cards-container">
      <div class="card" @click="$router.push('/love-master')">
        <div class="card-glow"></div>
        <div class="card-icon love-icon">心</div>
        <div class="card-title">漫漫</div>
        <div class="card-desc">温柔倾听，陪你聊聊感情</div>
        <div class="card-tags">
          <span>RAG 检索增强</span><span>Multi-Query</span><span>8 工具</span><span>流式 SSE</span>
        </div>
        <div class="card-btn">立即体验 →</div>
      </div>

      <div class="card" @click="$router.push('/super-agent')">
        <div class="card-glow"></div>
        <div class="card-icon agent-icon">咩</div>
        <div class="card-title">小羊</div>
        <div class="card-desc">你的全能伙伴，随时帮你搞定一切</div>
        <div class="card-tags">
          <span>ReAct 推理</span><span>联网搜索</span><span>文件操作</span><span>流式 SSE</span>
        </div>
        <div class="card-btn">立即体验 →</div>
      </div>
    </div>

    <div class="reset-area">
      <button class="reset-btn" @click="showMyReset = true">🗑 清空我的缓存</button>
      <button class="reset-btn admin-btn" @click="showSysReset = true">🔧 重置系统</button>
    </div>

    <!-- 弹窗1：清空我的缓存 -->
    <div v-if="showMyReset" class="modal-overlay" @click.self="showMyReset = false">
      <div class="modal-box">
        <div class="modal-icon">🗑</div>
        <div class="modal-title">清空我的缓存</div>
        <div class="modal-body">
          <div class="modal-item">删除我的聊天记忆文件</div>
          <div class="modal-item">删除我的对话记录</div>
          <div class="modal-item" style="color:rgba(0,240,255,0.4)">不影响其他人，知识库不变</div>
        </div>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showMyReset = false">取消</button>
          <button class="modal-btn confirm" @click="doMyReset" :disabled="resetting">
            {{ resetting ? '清空中...' : '确认清空' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗2：重置系统（需密码） -->
    <div v-if="showSysReset" class="modal-overlay" @click.self="showSysReset = false">
      <div class="modal-box">
        <div class="modal-icon">⚠️</div>
        <div class="modal-title">重置系统</div>
        <div class="modal-body">
          <div class="modal-item" style="color:#f56c6c">清空所有人的聊天记忆</div>
          <div class="modal-item" style="color:#f56c6c">清空所有向量数据库</div>
          <div class="modal-item">重新加载标准知识库</div>
          <input v-model="adminPwd" type="password" placeholder="请输入管理密码" class="pwd-input" @keyup.enter="doSysReset" />
          <div v-if="pwdError" class="pwd-error">密码错误</div>
        </div>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showSysReset = false; pwdError = false">取消</button>
          <button class="modal-btn confirm danger-btn" @click="doSysReset" :disabled="resetting || !adminPwd">
            {{ resetting ? '重置中...' : '确认重置' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 结果提示 -->
    <div v-if="resetResult" class="reset-toast">{{ resetResult }}</div>

    <footer class="footer">
      <p class="footer-tech">Spring AI 1.0 · DashScope qwen-plus · PgVector · SSE Streaming</p>
      <div class="footer-contact">
        <span class="contact-title">系统优化建议 请联系：</span>
        <span class="contact-item">📧 QQ邮箱：3021988923@qq.com</span>
        <span class="contact-divider">|</span>
        <span class="contact-item">💬 微信：yang18776423429</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const resetting = ref(false)
const resetResult = ref('')
const showMyReset = ref(false)
const showSysReset = ref(false)
const adminPwd = ref('')
const pwdError = ref(false)

// 清空我的缓存
const doMyReset = async () => {
  resetting.value = true
  try {
    const uid = localStorage.getItem('chatUserId') || 'unknown'
    const res = await fetch('/ai/admin/reset?userId=' + uid, { method: 'POST' })
    resetResult.value = await res.text()
  } catch (e) {
    resetResult.value = '清空失败：' + e.message
  } finally {
    resetting.value = false
    showMyReset.value = false
    setTimeout(() => { resetResult.value = '' }, 3000)
  }
}

// 重置系统（需密码）
const doSysReset = async () => {
  if (adminPwd.value !== '123456') {
    pwdError.value = true
    return
  }
  pwdError.value = false
  resetting.value = true
  try {
    const res = await fetch('/ai/admin/reset-all?pwd=' + adminPwd.value, { method: 'POST' })
    resetResult.value = await res.text()
    adminPwd.value = ''
  } catch (e) {
    resetResult.value = '重置失败：' + e.message
  } finally {
    resetting.value = false
    showSysReset.value = false
    setTimeout(() => { resetResult.value = '' }, 3000)
  }
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 70px 20px 30px;
  position: relative;
  overflow: hidden;
  /* 背景图片 + 暗色蒙层 */
  background: linear-gradient(29deg, rgba(20,50,22,0.9), rgba(90,90,90,0.6), rgba(33,63,48,0.9), rgba(40,60,32,0.9)),
  url('/images/wx.png') center/cover no-repeat;
}

/* 浮动光圈 */
.bg-circles { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }
.circle { position: absolute; border-radius: 50%; opacity: 0.12; }
.c1 { width: 400px; height: 400px; top: -120px; right: -120px; background: radial-gradient(circle, #9000ff, transparent); animation: drift 18s infinite alternate; }
.c2 { width: 500px; height: 500px; bottom: -180px; left: -180px; background: radial-gradient(circle, #ff00d4, transparent); animation: drift 22s infinite alternate-reverse; }
.c3 { width: 250px; height: 250px; top: 35%; right: 10%; background: radial-gradient(circle, #00f0ff, transparent); animation: drift 14s infinite alternate; }
@keyframes drift { 0% { transform: translate(0,0) rotate(0deg); } 100% { transform: translate(40px,40px) rotate(8deg); } }

/* 雪花 */
.snowflakes { position: fixed; inset: 0; pointer-events: none; z-index: 0; overflow: hidden; }
.snowflake { position: absolute; top: -30px; color: rgba(255,255,255,0.5); animation: fall linear infinite; user-select: none; }
@keyframes fall { 0% { transform: translateY(-30px) rotate(0deg); } 100% { transform: translateY(100vh) rotate(360deg); } }
.snowflake:nth-child(odd) { animation-name: fall-wind; }
@keyframes fall-wind { 0% { transform: translateY(-30px) translateX(0) rotate(0deg); } 50% { transform: translateY(50vh) translateX(30px) rotate(180deg); } 100% { transform: translateY(100vh) translateX(-20px) rotate(360deg); } }

/* 头部 */
.header { text-align: center; margin-bottom: 50px; z-index: 1; }
.title {
  font-size: 2.4rem; font-weight: 800;
  background: linear-gradient(90deg, #00f0ff, #9000ff, #ff00d4);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
  margin-bottom: 10px;
}
.subtitle { font-size: 0.9rem; color: rgba(255,255,255,0.4); letter-spacing: 4px; text-transform: uppercase; margin-bottom: 14px; }
.line { width: 200px; height: 1px; margin: 0 auto; background: linear-gradient(90deg, transparent, #9000ff, transparent); }

/* 卡片 */
.cards-container { display: flex; flex-wrap: wrap; justify-content: center; gap: 36px; max-width: 820px; width: 100%; z-index: 1; }
.card {
  width: 360px; background: rgba(255,255,255,0.04);
  backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px; padding: 36px 28px; cursor: pointer;
  text-align: center; position: relative; overflow: hidden;
  transition: all 0.4s ease;
}
.card:hover { transform: translateY(-10px); border-color: rgba(144,0,255,0.4); box-shadow: 0 20px 60px rgba(144,0,255,0.12); }
.card-glow { position: absolute; inset: 0; background: radial-gradient(circle at 50% 0%, rgba(144,0,255,0.1), transparent 60%); opacity: 0; transition: opacity 0.4s; pointer-events: none; }
.card:hover .card-glow { opacity: 1; }

.card-icon { font-size: 3.2rem; margin-bottom: 16px; width: 80px; height: 80px; display: flex; align-items: center; justify-content: center; border-radius: 50%; margin: 0 auto 16px; }
.love-icon { background: linear-gradient(135deg, rgba(255,0,122,0.2), rgba(255,87,34,0.15)); box-shadow: 0 0 30px rgba(255,0,122,0.15); }
.agent-icon { background: linear-gradient(135deg, rgba(0,178,255,0.2), rgba(79,86,255,0.15)); box-shadow: 0 0 30px rgba(0,178,255,0.15); }

.card-title { font-size: 1.3rem; font-weight: 700; color: #fff; margin-bottom: 6px; }
.card-desc { font-size: 0.82rem; color: rgba(255,255,255,0.5); margin-bottom: 16px; }

.card-tags { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; margin-bottom: 22px; }
.card-tags span { font-size: 0.68rem; padding: 3px 10px; border-radius: 20px; background: rgba(255,255,255,0.05); color: rgba(255,255,255,0.45); border: 1px solid rgba(255,255,255,0.06); }

.card-btn {
  background: linear-gradient(90deg, rgba(0,136,255,0.3), rgba(0,178,255,0.3)); color: #fff;
  border: 1px solid rgba(0,240,255,0.25); border-radius: 30px; padding: 10px 28px;
  font-size: 0.85rem; font-weight: 600; transition: all 0.3s; display: inline-block;
}
.card:hover .card-btn { background: linear-gradient(90deg, #0088ff, #00b2ff); box-shadow: 0 0 20px rgba(0,178,255,0.4); }

/* 按钮区 */
.reset-area { margin-top: 30px; z-index: 1; display: flex; gap: 12px; }
.reset-btn { background: transparent; border: 1px solid rgba(255,255,255,0.08); color: rgba(255,255,255,0.2); padding: 8px 20px; border-radius: 8px; font-size: 0.72rem; cursor: pointer; transition: all 0.2s; }
.reset-btn:hover { border-color: rgba(245,108,108,0.3); color: #f56c6c; }
.admin-btn:hover { border-color: rgba(255,190,11,0.3); color: #ffbe0b; }

/* 弹窗遮罩 */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.7); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 100; animation: fadeIn 0.2s ease; }
.modal-box { background: linear-gradient(160deg, #1a1040, #1a0a30); border: 1px solid rgba(255,255,255,0.08); border-radius: 16px; padding: 32px 28px 24px; max-width: 360px; width: 90%; text-align: center; }
.modal-icon { font-size: 2rem; margin-bottom: 10px; }
.modal-title { font-size: 1rem; font-weight: 600; color: #fff; margin-bottom: 16px; }
.modal-body { text-align: left; margin-bottom: 22px; }
.modal-item { font-size: 0.78rem; color: rgba(255,255,255,0.5); padding: 4px 0; }
.modal-actions { display: flex; gap: 10px; }
.modal-btn { flex: 1; padding: 10px 0; border: none; border-radius: 10px; font-size: 0.82rem; font-weight: 600; cursor: pointer; transition: all 0.15s; }
.modal-btn.cancel { background: rgba(255,255,255,0.06); color: rgba(255,255,255,0.5); }
.modal-btn.cancel:hover { background: rgba(255,255,255,0.1); }
.modal-btn.confirm { background: linear-gradient(135deg, #f56c6c, #c0392b); color: #fff; }
.modal-btn.confirm:hover { box-shadow: 0 4px 16px rgba(245,108,108,0.3); }
.modal-btn.confirm.danger-btn { background: linear-gradient(135deg, #e74c3c, #c0392b); }
.modal-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.pwd-input { width: 100%; margin-top: 12px; padding: 10px 14px; border-radius: 10px; border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04); color: #fff; font-size: 0.88rem; outline: none; text-align: center; letter-spacing: 4px; }
.pwd-input:focus { border-color: rgba(245,108,108,0.3); }
.pwd-error { color: #f56c6c; font-size: 0.72rem; margin-top: 6px; }

/* 结果提示 */
.reset-toast { position: fixed; bottom: 40px; left: 50%; transform: translateX(-50%); background: rgba(0,240,255,0.1); border: 1px solid rgba(0,240,255,0.2); color: rgba(0,240,255,0.8); padding: 10px 24px; border-radius: 10px; font-size: 0.82rem; z-index: 200; }
@keyframes fadeIn { from{opacity:0;transform:translateY(10px)} to{opacity:1;transform:translateY(0)} }

.footer { margin-top: auto; padding-top: 48px; text-align: center; z-index: 1; }
.footer-tech { font-size: 0.7rem; color: rgba(255,255,255,0.2); letter-spacing: 1px; margin-bottom: 14px; }
.footer-contact { display: flex; align-items: center; justify-content: center; gap: 10px; flex-wrap: wrap; font-size: 0.74rem; color: rgba(255,255,255,0.3); }
.contact-title { color: rgba(255,255,255,0.22); }
.contact-item { color: rgba(255,255,255,0.38); transition: color 0.2s; }
.contact-item:hover { color: rgba(0,240,255,0.7); }
.contact-divider { color: rgba(255,255,255,0.1); }

@media (max-width: 768px) {
  .home-container { padding: 30px 16px 50px; min-height: auto; overflow-y: auto; }
  .header { margin-bottom: 24px; }
  .title { font-size: 1.5rem; }
  .subtitle { font-size: 0.72rem; letter-spacing: 2px; }
  .cards-container { gap: 14px; margin-bottom: 20px; }
  .card { width: 100%; max-width: 100%; padding: 22px 18px; }
  .card-icon { width: 44px; height: 44px; font-size: 1.6rem; }
  .card-title { font-size: 0.95rem; }
  .card-desc { font-size: 0.7rem; margin-bottom: 8px; }
  .card-tags { gap: 4px; }
  .card-tags span { font-size: 0.6rem; padding: 2px 6px; }
  .card-btn { font-size: 0.7rem; padding: 8px 0; }
  .footer { padding-top: 24px; }
  .footer-contact { font-size: 0.62rem; gap: 4px; }
}
</style>
