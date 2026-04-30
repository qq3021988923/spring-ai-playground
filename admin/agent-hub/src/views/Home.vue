<script setup>
import { ref } from 'vue'
 

const count = ref(0)
</script>

<template>
  <section id="center">
 计算机应用技术
    <div>
      <h1>桂林电子科技大学</h1>
    </div>
    <button type="button" class="counter" @click="count++">
      Count is {{ count }}
    </button>
  </section>

  <div class="ticks"></div>

  <section id="next-steps">
    <div id="docs">
 
    
      <ul>

        <li>
1111
        </li>
      </ul>
    </div>
    <div id="social">
 
      <ul>
        <li>
2222
        </li>

      </ul>
    </div>
  </section>

  <div class="ticks"></div>
  <section id="spacer"></section>
</template>
