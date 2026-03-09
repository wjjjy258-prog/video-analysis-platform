<template>
  <section class="panel panel-error">
    <div class="panel-error-head">
      <span class="panel-error-dot"></span>
      <h2>{{ title }}</h2>
    </div>
    <p class="panel-error-message">{{ message }}</p>
    <button class="btn-secondary" type="button" @click="$emit('retry')">{{ retryText }}</button>
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: "加载失败"
  },
  message: {
    type: String,
    default: "请求失败，请稍后重试。"
  },
  retryText: {
    type: String,
    default: "重试"
  }
});

defineEmits(["retry"]);
</script>

<style scoped>
.panel-error {
  border-color: #f2c8c8;
  background: linear-gradient(180deg, #fffdfd 0%, #fff7f7 100%);
}

.panel-error-head {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.panel-error-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #dc2626;
  box-shadow: 0 0 0 6px rgba(220, 38, 38, 0.12);
}

.panel-error-message {
  margin: 12px 0 14px;
  color: #7f1d1d;
  line-height: 1.6;
}
</style>
