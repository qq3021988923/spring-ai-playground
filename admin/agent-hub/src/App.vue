<script setup>
import { ref } from 'vue'
import ChatRoom from './components/ChatRoom.vue'

const mode = ref('agent')
</script>

<template>
  <div class="app">
    <!-- 顶部导航栏 -->
    <nav class="navbar">
      <span class="logo"> AI Agent Hub</span>
      <div class="tabs">
        <button
          :class="['tab', { active: mode === 'agent' }]"
          @click="mode = 'agent'"
        > Agent 模式</button>
        <button
          :class="['tab', { active: mode === 'love' }]"
          @click="mode = 'love'"
        >💕 恋爱顾问</button>
        <button
          :class="['tab', { active: mode === 'ollama' }]"
          @click="mode = 'ollama'"
        >🦙 Ollama</button>
      </div>
    </nav>

    <!-- 聊天区 -->
    <ChatRoom :mode="mode" />
  </div>
</template>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>

<style scoped>
.app {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  height: 56px;
  background: white;
  border-bottom: 1px solid #eee;
}
.logo {
  font-size: 18px;
  font-weight: 700;
  color: #303030;
}
.tabs {
  display: flex;
  gap: 4px;
  background: #f0f2f5;
  padding: 3px;
  border-radius: 8px;
}
.tab {
  padding: 7px 20px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all .2s;
}
.tab.active {
  background: white;
  color: #1F67FF;
  box-shadow: 0 1px 3px rgba(0,0,0,.08);
}
.tab:hover:not(.active) {
  color: #333;
}
</style>
