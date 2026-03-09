# 视频数据分析平台（video-analysis-platform）

本项目包含前端、后端、数据库、采集与分析脚本，支持多平台视频数据分析。

## 目录结构
- `frontend/`：Vue3 前端
- `backend/`：Spring Boot + MyBatis 后端
- `database/`：MySQL 初始化脚本
- `spider/`：采集与解析工具
- `analysis/`：分析与报告导出
- `scripts/`：一键初始化、启动、自测脚本
- `docs/`：部署与使用文档

## 登录与账号隔离
- 入口首页：`http://localhost:5173/`
- 默认测试账号：`demo`
- 默认测试密码：`123456`
- 数据隔离方式：按登录账号进行租户隔离（每个账号看到的都是自己的数据空间）

## 一键脚本
- `OneClick_NoCLI_Init_Test.bat`：无命令行 MySQL 的一键初始化 + 自测
- `OneClick_Full_Auto_Launch.bat`：全自动初始化 + 启动前后端 + 打开页面

## 文档
- [部署与使用手册](docs/部署与使用手册.md)
