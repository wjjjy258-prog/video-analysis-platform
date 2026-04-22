<template>
  <div class="auth-landing">
    <div class="danmaku-layer" aria-hidden="true">
      <span
        v-for="item in danmakuItems"
        :key="item.id"
        class="danmaku-item"
        :style="item.style"
      >
        {{ item.text }}
      </span>
    </div>

    <section class="auth-card">
      <div class="avatar">V</div>
      <h1>视频数据分析平台</h1>
      <p class="auth-subtitle">登录后进入你的专属数据空间</p>

      <div class="tab-row">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          <span>账号</span>
          <input
            v-model.trim="form.username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
            maxlength="64"
            required
          />
        </label>

        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            maxlength="128"
            required
          />
        </label>

        <label v-if="mode === 'register'">
          <span>确认密码</span>
          <input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            maxlength="128"
            required
          />
        </label>

        <template v-if="mode === 'register'">
          <div class="role-section">
            <span class="section-label">注册身份</span>
            <div class="role-grid">
              <label class="role-card" :class="{ active: form.role === 'viewer' }">
                <input v-model="form.role" type="radio" value="viewer" />
                <strong>观众</strong>
                <small>范围：全站数据；重点：热门视频、分类统计、互动效率、用户分析。</small>
              </label>
              <label class="role-card" :class="{ active: form.role === 'creator' }">
                <input v-model="form.role" type="radio" value="creator" />
                <strong>内容创作者</strong>
                <small>范围：自己 + 同行样本；重点：作品表现、同类对标、创作建议。</small>
              </label>
            </div>
          </div>

          <div v-if="form.role === 'creator'" class="creator-fields">
            <label>
              <span>创作者名称</span>
              <input
                v-model.trim="form.creatorName"
                type="text"
                placeholder="需与你导入数据中的作者名尽量一致"
                maxlength="100"
                required
              />
            </label>

            <label>
              <span>主运营平台</span>
              <select v-model="form.creatorPlatform">
                <option value="bilibili">哔哩哔哩</option>
                <option value="douyin">抖音</option>
                <option value="kuaishou">快手</option>
                <option value="xiaohongshu">小红书</option>
                <option value="xigua">西瓜视频</option>
                <option value="weibo">微博</option>
                <option value="youtube">YouTube</option>
                <option value="tiktok">TikTok</option>
                <option value="acfun">AcFun</option>
                <option value="unknown">暂未确定</option>
              </select>
            </label>

            <label>
              <span>主打方向</span>
              <input
                v-model.trim="form.creatorFocusCategory"
                type="text"
                placeholder="例如：游戏、知识、影视、音乐"
                maxlength="60"
              />
            </label>
          </div>
        </template>

        <button class="submit-btn" :disabled="submitting" type="submit">
          {{ submitting ? "处理中..." : mode === "login" ? "登录" : "注册并登录" }}
        </button>
      </form>

      <p class="notice" :class="{ error: messageType === 'error' }">{{ message }}</p>
      <p class="default-account">默认测试账号：`demo` / `123456`</p>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { login, register } from "../api/auth";
import { useAuth } from "../composables/auth";
import { notifyError, notifySuccess, notifyWarning } from "../composables/notify";
import { getErrorMessage, getSuccessMessage } from "../utils/feedback";

const mode = ref("login");
const submitting = ref(false);
const message = ref("支持观众与内容创作者双身份注册，登录后进入各自的专属数据空间。");
const messageType = ref("info");
const route = useRoute();
const router = useRouter();
const { setAuth } = useAuth();

const form = reactive({
  username: "",
  password: "",
  confirmPassword: "",
  role: "viewer",
  creatorName: "",
  creatorPlatform: "bilibili",
  creatorFocusCategory: ""
});

const danmakuTexts = [
  "支持哔哩哔哩、抖音、快手、小红书等多平台数据分析",
  "分类统计支持 2D / 3D 图表一键切换",
  "热榜视频洞察：播放、点赞、评论多维排行",
  "互动效率分析：识别高互动低播放的潜力内容",
  "用户画像分析：兴趣广度、互动深度、标签分布",
  "来源追踪：每条视频都可回溯导入路径",
  "平台筛选联动：切换平台后全模块同步分析",
  "每个账号数据独立隔离，互不干扰",
  "文本智能入库支持 Markdown / TXT / CSV / JSON",
  "AI 增强解析：非结构化文本自动抽取结构化字段",
  "标准化入库：不同平台字段自动映射到统一数据库",
  "质量评分分层：GOOD / LOW / REJECTED",
  "低质量拒绝记录：自动给出修复建议",
  "导入反馈可视化：新增、更新、拒绝数量即时提示",
  "来源平台标签清晰展示，避免数据混淆",
  "导入后自动生成行为日志，支持画像建模",
  "趋势与分类模块支持大数据量查询",
  "全自动脚本支持一键初始化、启动与自测",
  "内容创作者中心：聚焦自己的作品表现与增长机会",
  "同行对标分析：比较平均播放、互动效率与优势方向",
  "创作者专属建议：根据自己和同行差距输出运营提示",
  "后端 Spring Boot + MySQL 稳定支撑",
  "前端 Vue + ECharts + Three.js 交互可视化",
  "数据清理功能支持账号级彻底清空",
  "异常数据自动识别，减少脏数据影响",
  "批量 URL 导入模板可自动校验格式",
  "导入失败有明确报错引导和修正建议",
  "支持毕业设计答辩演示全流程闭环",
  "多维指标统一看板，信息表达更清晰",
  "高性能批量入库，兼顾全量数据保留",
  "可扩展平台字段字典，便于后续升级"
];

const danmakuItems = computed(() =>
  danmakuTexts.map((text, index) => {
    const top = 2 + ((index * 3.4) % 94);
    const duration = 8 + (index % 5) * 1.7;
    const delay = -(index * 0.95);
    const size = 20 + (index % 6) * 4;
    const opacity = 0.58 + (index % 4) * 0.09;
    const colors = ["#0b84ff", "#17a673", "#f59f0b", "#7c4dff", "#ef476f", "#0ea5e9"];
    const color = colors[index % colors.length];
    return {
      id: `${index}-${text}`,
      text,
      style: {
        top: `${top}%`,
        animationDuration: `${duration}s`,
        animationDelay: `${delay}s`,
        fontSize: `${size}px`,
        color,
        opacity
      }
    };
  })
);

const setPageMessage = (text, type = "info") => {
  message.value = text;
  messageType.value = type;
};

const submit = async () => {
  if (!form.username || !form.password) {
    const msg = "请先填写账号和密码。";
    setPageMessage(msg, "error");
    notifyWarning(msg, { title: "参数不完整" });
    return;
  }
  if (mode.value === "register" && form.password !== form.confirmPassword) {
    const msg = "两次输入的密码不一致。";
    setPageMessage(msg, "error");
    notifyWarning(msg, { title: "参数校验" });
    return;
  }
  if (mode.value === "register" && form.password.length < 8) {
    const msg = "注册密码至少 8 位。";
    setPageMessage(msg, "error");
    notifyWarning(msg, { title: "参数校验" });
    return;
  }
  if (
    mode.value === "register" &&
    !( /[A-Za-z]/.test(form.password) && /\d/.test(form.password))
  ) {
    const msg = "注册密码需包含字母和数字。";
    setPageMessage(msg, "error");
    notifyWarning(msg, { title: "参数校验" });
    return;
  }
  if (mode.value === "register" && form.role === "creator" && !form.creatorName) {
    const msg = "内容创作者身份需要填写创作者名称。";
    setPageMessage(msg, "error");
    notifyWarning(msg, { title: "参数校验" });
    return;
  }

  submitting.value = true;
  try {
    const payload = {
      username: form.username,
      password: form.password
    };
    const data =
      mode.value === "login"
        ? await login(payload)
        : await register({
          ...payload,
          confirmPassword: form.confirmPassword,
          role: form.role,
          creatorName: form.role === "creator" ? form.creatorName : "",
          creatorPlatform: form.role === "creator" ? form.creatorPlatform : "unknown",
          creatorFocusCategory: form.role === "creator" ? form.creatorFocusCategory : ""
        });

    if (!data?.success || !data?.token) {
      throw new Error(getSuccessMessage(data?.message, "认证失败。"));
    }

    setAuth({
      token: data.token,
      user: data.user
    });

    const targetPath = data?.user?.role === "creator" ? "/creator" : "/dashboard";
    const successMessage = mode.value === "login"
      ? (data?.user?.role === "creator" ? "登录成功，正在进入创作者中心..." : "登录成功，正在进入平台...")
      : (data?.user?.role === "creator" ? "创作者注册成功，正在进入创作者中心..." : "注册成功，正在进入平台...");
    setPageMessage(successMessage, "info");
    notifySuccess(successMessage);

    const redirect = typeof route.query.redirect === "string" && route.query.redirect.trim()
      ? route.query.redirect
      : targetPath;
    await router.replace(redirect);
  } catch (error) {
    const msg = getErrorMessage(error, "请求失败，请稍后再试。");
    setPageMessage(msg, "error");
    notifyError(msg, { title: "认证失败" });
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.auth-landing {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 20%, rgba(9, 132, 255, 0.2), transparent 38%),
    radial-gradient(circle at 82% 80%, rgba(21, 170, 255, 0.2), transparent 40%),
    linear-gradient(135deg, #e7f3ff 0%, #f4f9ff 42%, #eaf5ff 100%);
}

.danmaku-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.danmaku-item {
  position: absolute;
  right: -62vw;
  white-space: nowrap;
  font-weight: 800;
  letter-spacing: 0.2px;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.68), 0 6px 16px rgba(17, 60, 109, 0.12);
  animation-name: danmaku-run;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

@keyframes danmaku-run {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-210vw);
  }
}

.auth-card {
  position: relative;
  z-index: 1;
  width: min(430px, calc(100% - 24px));
  border-radius: 18px;
  padding: 28px 26px 22px;
  background: linear-gradient(180deg, rgba(19, 39, 74, 0.94), rgba(23, 43, 80, 0.95));
  border: 1px solid rgba(86, 140, 217, 0.35);
  box-shadow: 0 24px 54px rgba(10, 34, 77, 0.35);
  color: #e9f2ff;
}

.avatar {
  width: 86px;
  height: 86px;
  border-radius: 50%;
  margin: 0 auto 10px;
  display: grid;
  place-items: center;
  font-size: 36px;
  font-weight: 700;
  color: #ffffff;
  background: radial-gradient(circle at 30% 25%, #73c1ff, #1f93ff 60%, #0f5fc0 100%);
  border: 3px solid rgba(255, 255, 255, 0.72);
}

h1 {
  margin: 0;
  text-align: center;
  font-size: 28px;
  letter-spacing: 0.6px;
}

.auth-subtitle {
  margin: 8px 0 0;
  text-align: center;
  font-size: 14px;
  color: #bdd4ef;
}

.tab-row {
  margin: 18px auto 12px;
  display: inline-grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(158, 192, 235, 0.45);
}

.tab-row button {
  border: none;
  background: rgba(58, 84, 126, 0.6);
  color: #deebff;
  font-size: 15px;
  padding: 10px 12px;
  cursor: pointer;
}

.role-section,
.creator-fields {
  display: grid;
  gap: 10px;
}

.section-label {
  font-size: 13px;
  color: #bdd4ef;
}

.role-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.role-card {
  position: relative;
  display: grid;
  gap: 6px;
  padding: 14px 14px 12px;
  border-radius: 14px;
  border: 1px solid rgba(129, 168, 223, 0.34);
  background: rgba(53, 79, 120, 0.52);
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, background 0.2s ease;
}

.role-card input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.role-card strong {
  font-size: 15px;
  color: #f5f9ff;
}

.role-card small {
  line-height: 1.55;
  color: #c2d8f1;
}

.role-card.active {
  border-color: rgba(100, 189, 255, 0.82);
  background: rgba(40, 111, 195, 0.34);
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(13, 70, 136, 0.22);
}

.tab-row button.active {
  background: linear-gradient(135deg, #1f93ff, #2dadff);
  color: #ffffff;
}

.auth-form {
  display: grid;
  gap: 12px;
}

.auth-form label {
  display: grid;
  gap: 6px;
}

.auth-form span {
  font-size: 13px;
  color: #c6dbf4;
}

.auth-form input {
  width: 100%;
  border: 1px solid rgba(150, 186, 232, 0.35);
  border-radius: 10px;
  padding: 11px 12px;
  font-size: 15px;
  color: #eaf2ff;
  background: rgba(32, 52, 87, 0.78);
  outline: none;
}

.auth-form input:focus {
  border-color: rgba(111, 188, 255, 0.95);
  box-shadow: 0 0 0 3px rgba(31, 147, 255, 0.24);
}

.submit-btn {
  margin-top: 4px;
  border: none;
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 17px;
  color: #ffffff;
  background: linear-gradient(120deg, #147fff, #2ab1ff);
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.notice {
  margin: 12px 0 6px;
  min-height: 20px;
  font-size: 13px;
  color: #8ff7c2;
}

.notice.error {
  color: #ffb8b8;
}

.default-account {
  margin: 0;
  font-size: 12px;
  color: #aac3e4;
}

@media (max-width: 768px) {
  .auth-card {
    padding: 24px 18px 20px;
  }

  h1 {
    font-size: 24px;
  }
}
</style>
