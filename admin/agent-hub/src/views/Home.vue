<template>
  <div class="home-container">
    <div class="bg-circles">
      <div class="circle c1"></div><div class="circle c2"></div><div class="circle c3"></div>
    </div>

    <div class="header">
      <h1 class="title">小杨 AI 智能体平台</h1>
      <p class="subtitle">/ 探索 AI 的无限可能 /</p>
      <div class="line"></div>
    </div>

    <div class="cards-container">
      <div class="card" @click="$router.push('/love-master')">
        <div class="card-glow"></div>
        <div class="card-icon love-icon">💕</div>
        <div class="card-title">AI 恋爱大师</div>
        <div class="card-desc">智能情感顾问，帮你解答恋爱烦恼</div>
        <div class="card-tags">
          <span>RAG 检索增强</span><span>Multi-Query</span><span>8 工具</span><span>流式 SSE</span>
        </div>
        <div class="card-btn">立即体验 →</div>
      </div>

      <div class="card" @click="$router.push('/super-agent')">
        <div class="card-glow"></div>
        <div class="card-icon agent-icon">🤖</div>
        <div class="card-title">AI 超级智能体</div>
        <div class="card-desc">全能型 AI 助手，解决各类专业问题</div>
        <div class="card-tags">
          <span>ReAct 推理</span><span>联网搜索</span><span>文件操作</span><span>流式 SSE</span>
        </div>
        <div class="card-btn">立即体验 →</div>
      </div>
    </div>

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
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: linear-gradient(135deg, #0a0020 0%, #150030 30%, #0d0d30 70%, #050520 100%);
  padding: 70px 20px 30px;
  position: relative;
  overflow: hidden;
}

/* 浮动光圈 */
.bg-circles { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }
.circle { position: absolute; border-radius: 50%; opacity: 0.12; }
.c1 { width: 400px; height: 400px; top: -120px; right: -120px; background: radial-gradient(circle, #9000ff, transparent); animation: drift 18s infinite alternate; }
.c2 { width: 500px; height: 500px; bottom: -180px; left: -180px; background: radial-gradient(circle, #ff00d4, transparent); animation: drift 22s infinite alternate-reverse; }
.c3 { width: 250px; height: 250px; top: 35%; right: 10%; background: radial-gradient(circle, #00f0ff, transparent); animation: drift 14s infinite alternate; }
@keyframes drift { 0% { transform: translate(0,0) rotate(0deg); } 100% { transform: translate(40px,40px) rotate(8deg); } }

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
