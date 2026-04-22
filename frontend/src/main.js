import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { beginNavigation, endNavigation } from "./composables/loadingBar";
import { hasAuthToken, useAuth } from "./composables/auth";
import "./assets/styles.css";

const { currentUser } = useAuth();

const getHomeRouteByRole = () => (currentUser.value?.role === "creator" ? "creator" : "dashboard");

router.beforeEach((to, from, next) => {
  beginNavigation();
  const isLoggedIn = hasAuthToken();
  if (to.meta?.requiresAuth && !isLoggedIn) {
    next({ name: "auth", query: { redirect: to.fullPath } });
    return;
  }
  if (to.meta?.creatorOnly && currentUser.value?.role !== "creator") {
    next({ name: getHomeRouteByRole() });
    return;
  }
  if (to.meta?.guestOnly && isLoggedIn) {
    next({ name: getHomeRouteByRole() });
    return;
  }
  next();
});

router.afterEach(() => {
  endNavigation();
});

router.onError(() => {
  endNavigation();
});

createApp(App).use(router).mount("#app");
