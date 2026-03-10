<template>
  <div class="app-layout" :class="{ 'auth-layout': !showShell }">
    <header v-if="showShell" class="top-nav">
      <div class="top-nav-inner">
        <div class="brand">
          <span class="brand-mark">
            <UiIcon name="spark" :size="17" />
          </span>
          <h1>视频数据分析平台</h1>
        </div>

        <nav class="nav-links">
          <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">
            <span class="nav-link-inner">
              <UiIcon :name="item.icon" :size="15" />
              <span>{{ item.label }}</span>
            </span>
          </RouterLink>
        </nav>

        <div class="top-actions">
          <div class="global-platform-controls" :class="{ 'is-hidden': !showPlatformSwitch }">
            <label class="global-platform-switch">
              <span class="label-with-icon">
                <UiIcon name="filter" :size="13" />
                <span>平台</span>
              </span>
              <select v-model="platformModel" :disabled="!showPlatformSwitch">
                <option v-for="item in platformOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <button class="global-refresh-btn btn-with-icon" type="button" :disabled="!showPlatformSwitch" @click="refreshCurrentPage">
              <UiIcon name="refresh" :size="13" />
              <span>刷新</span>
            </button>
          </div>

          <div class="account-box">
            <span class="account-name-wrap">
              <UiIcon name="user" :size="13" />
              <span class="account-name">{{ currentUser?.username || "未登录" }}</span>
            </span>
            <button class="logout-btn btn-with-icon" type="button" @click="handleLogout">
              <UiIcon name="logout" :size="13" />
              <span>退出</span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <main class="page-wrap" :class="{ 'auth-page-wrap': !showShell }">
      <RouterView />
    </main>

    <ToastCenter />
  </div>
</template>

<script setup>
import { computed, onMounted } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { logout, me } from "./api/auth";
import ToastCenter from "./components/ToastCenter.vue";
import UiIcon from "./components/UiIcon.vue";
import { usePlatformFilter } from "./composables/platformFilter";
import { useAuth } from "./composables/auth";

const route = useRoute();
const router = useRouter();
const { selectedPlatform, platformOptions, setPlatform, refreshData } = usePlatformFilter();
const { currentUser, isAuthenticated, clearAuth, updateUser } = useAuth();

const navItems = [
  { to: "/dashboard", label: "首页", icon: "home" },
  { to: "/hot", label: "热门视频", icon: "hot" },
  { to: "/category", label: "分类统计", icon: "category" },
  { to: "/trend", label: "互动效率", icon: "trend" },
  { to: "/user", label: "用户分析", icon: "user" },
  { to: "/crawler", label: "数据管理", icon: "data" }
];

const showShell = computed(() => route.meta?.requiresAuth && isAuthenticated.value);
const showPlatformSwitch = computed(() => !["dashboard", "crawler"].includes(String(route.name)));

const platformModel = computed({
  get: () => selectedPlatform.value,
  set: (value) => {
    setPlatform(value);
    syncPlatformQuery(value);
  }
});

onMounted(async () => {
  if (!isAuthenticated.value) {
    return;
  }
  try {
    const data = await me();
    if (data?.success && data?.user) {
      updateUser(data.user);
    } else {
      clearAuth();
      await router.replace("/");
      return;
    }
  } catch (error) {
    // Keep local login state on transient network/backend errors.
    // Only clear auth when server explicitly says the token is invalid.
    const status = Number(error?.response?.status || 0);
    if (status === 401 || status === 403) {
      clearAuth();
      await router.replace("/");
      return;
    }
  }

  const queryPlatform = route.query.platform;
  if (typeof queryPlatform === "string" && queryPlatform.trim()) {
    setPlatform(queryPlatform);
  }
});

const syncPlatformQuery = (value) => {
  const query = { ...route.query };
  const normalized = String(value ?? "").trim().toLowerCase();
  if (!normalized || normalized === "all") {
    delete query.platform;
  } else {
    query.platform = normalized;
  }
  router.replace({ query });
};

const refreshCurrentPage = () => {
  refreshData();
};

const handleLogout = async () => {
  try {
    await logout();
  } catch {
    // Ignore network failures and clear local state anyway.
  }
  clearAuth();
  await router.replace("/");
};
</script>
