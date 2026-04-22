import { createRouter, createWebHistory } from "vue-router";

const AuthLandingView = () => import("../views/AuthLandingView.vue");
const CreatorStudioView = () => import("../views/CreatorStudioView.vue");
const HomeView = () => import("../views/HomeView.vue");
const HotVideoView = () => import("../views/HotVideoView.vue");
const CategoryView = () => import("../views/CategoryView.vue");
const TrendView = () => import("../views/TrendView.vue");
const UserView = () => import("../views/UserView.vue");
const CrawlerView = () => import("../views/CrawlerView.vue");

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", name: "auth", component: AuthLandingView, meta: { guestOnly: true } },
    { path: "/creator", name: "creator", component: CreatorStudioView, meta: { requiresAuth: true, creatorOnly: true } },
    { path: "/dashboard", name: "dashboard", component: HomeView, meta: { requiresAuth: true } },
    { path: "/hot", name: "hot", component: HotVideoView, meta: { requiresAuth: true } },
    { path: "/category", name: "category", component: CategoryView, meta: { requiresAuth: true } },
    { path: "/trend", name: "trend", component: TrendView, meta: { requiresAuth: true } },
    { path: "/user", name: "user", component: UserView, meta: { requiresAuth: true } },
    { path: "/crawler", name: "crawler", component: CrawlerView, meta: { requiresAuth: true } },
    { path: "/home", redirect: "/dashboard" }
  ]
});

export default router;
