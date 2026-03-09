# Oracle 免费云发布版部署手册（视频数据分析平台）

本手册按你当前项目技术栈定制：`Vue + Spring Boot + MySQL + Nginx`，部署目标是 **Oracle Cloud Always Free**。

## 1. 部署目标与架构

- 单台 Oracle 免费云主机（Ubuntu）运行：
- `MySQL`：存储数据
- `Spring Boot`：后端 API（`127.0.0.1:8080`）
- `Nginx`：对外站点入口（`80/443`），静态托管前端，并把 `/api/*` 反代到后端
- 前端打包后放在：`/var/www/video-analysis-platform`

请求路径：
- 浏览器访问：`http(s)://你的域名`
- 前端请求 API：`/api/...`
- Nginx 转发后端：`http://127.0.0.1:8080/...`

## 2. Oracle 控制台准备

## 2.1 创建 Always Free 实例

1. 登录 Oracle Cloud 控制台。
2. 创建 Compute 实例，镜像建议选 `Ubuntu 22.04 LTS`。
3. 机型建议：
- 优先：`VM.Standard.A1.Flex`（ARM）
- 备选：`VM.Standard.E2.1.Micro`
4. 勾选公网 IP。
5. 下载并保存 SSH 私钥（`.key`）。

## 2.2 放行入站端口（VCN 安全列表/NSG）

至少放行：
- `22`（SSH）
- `80`（HTTP）
- `443`（HTTPS）

说明：Oracle 网络层不放行的话，实例里服务启动了也无法公网访问。

## 3. 连接服务器并安装基础环境

先 SSH 登录（把路径和 IP 换成你自己的）：

```bash
ssh -i ~/.ssh/oracle.key ubuntu@<PUBLIC_IP>
```

更新系统：

```bash
sudo apt update
sudo apt -y upgrade
```

安装 Java17 / Maven / Nginx / MySQL / Git：

```bash
sudo apt install -y openjdk-17-jdk maven nginx mysql-server git unzip curl
```

安装 Node.js 20（前端构建）：

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

版本检查：

```bash
java -version
mvn -v
node -v
npm -v
mysql --version
nginx -v
```

## 4. 上传项目代码

建议路径：

```bash
mkdir -p ~/apps
cd ~/apps
```

方式 A（推荐）：Git 克隆

```bash
git clone <你的仓库地址> video-analysis-platform
```

方式 B：本机打包上传（SCP）

```bash
scp -i ~/.ssh/oracle.key -r /local/path/video-analysis-platform ubuntu@<PUBLIC_IP>:~/apps/
```

## 5. 初始化 MySQL 数据库

登录 MySQL：

```bash
sudo mysql
```

执行（可直接复制）：

```sql
CREATE DATABASE IF NOT EXISTS video_analysis
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'video_user'@'localhost' IDENTIFIED BY '改成强密码';
GRANT ALL PRIVILEGES ON video_analysis.* TO 'video_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

导入初始化脚本（项目根目录执行）：

```bash
cd ~/apps/video-analysis-platform
mysql -u video_user -p video_analysis < database/init.sql
```

## 6. 部署后端（Spring Boot + systemd）

进入后端并打包：

```bash
cd ~/apps/video-analysis-platform/backend
mvn -DskipTests clean package
```

创建 systemd 服务文件：

```bash
sudo tee /etc/systemd/system/video-analysis-backend.service > /dev/null <<'EOF'
[Unit]
Description=Video Analysis Backend
After=network.target mysql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apps/video-analysis-platform/backend
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/video-analysis-platform/backend/target/video-analysis-backend-1.0.0.jar --server.port=8080
Restart=always
RestartSec=5
Environment=SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/video_analysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false&rewriteBatchedStatements=true&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048
Environment=SPRING_DATASOURCE_USERNAME=video_user
Environment=SPRING_DATASOURCE_PASSWORD=改成强密码
Environment=SPIDER_PYTHON=python3

[Install]
WantedBy=multi-user.target
EOF
```

启动并设置开机自启：

```bash
sudo systemctl daemon-reload
sudo systemctl enable video-analysis-backend
sudo systemctl restart video-analysis-backend
sudo systemctl status video-analysis-backend --no-pager
```

后端健康检查：

```bash
curl http://127.0.0.1:8080/health
```

## 7. 部署前端（Vue 构建产物）

构建前端：

```bash
cd ~/apps/video-analysis-platform/frontend
npm install
```

创建生产环境变量：

```bash
cat > .env.production <<'EOF'
VITE_API_BASE_URL=/api
VITE_API_TIMEOUT_MS=0
EOF
```

打包：

```bash
npm run build
```

发布到 Nginx 目录：

```bash
sudo mkdir -p /var/www/video-analysis-platform
sudo cp -r dist/* /var/www/video-analysis-platform/
```

## 8. 配置 Nginx（SPA + API 反代）

创建 Nginx 站点配置：

```bash
sudo tee /etc/nginx/sites-available/video-analysis-platform > /dev/null <<'EOF'
server {
    listen 80;
    server_name _;

    root /var/www/video-analysis-platform;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF
```

启用站点并重载：

```bash
sudo ln -sf /etc/nginx/sites-available/video-analysis-platform /etc/nginx/sites-enabled/video-analysis-platform
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl restart nginx
```

现在可以访问：

```text
http://<PUBLIC_IP>
```

## 9. HTTPS（可选但推荐）

前提：你有域名，并且 A 记录已解析到 Oracle 实例公网 IP。

安装 Certbot：

```bash
sudo apt install -y certbot python3-certbot-nginx
```

申请证书（替换域名）：

```bash
sudo certbot --nginx -d your-domain.com -d www.your-domain.com
```

自动续期测试：

```bash
sudo certbot renew --dry-run
```

## 10. 发布后验证清单

```bash
# 后端
curl http://127.0.0.1:8080/health

# 前端首页
curl -I http://127.0.0.1

# API 反代
curl http://127.0.0.1/api/health
```

浏览器验证：
- 打开 `http(s)://你的域名`
- 登录 `demo / 123456`
- 查看首页、热门视频、数据管理是否可用

## 11. 后续更新流程（最常用）

每次代码更新后执行：

```bash
cd ~/apps/video-analysis-platform
git pull

cd backend
mvn -DskipTests clean package
sudo systemctl restart video-analysis-backend

cd ../frontend
npm install
npm run build
sudo cp -r dist/* /var/www/video-analysis-platform/
sudo nginx -t && sudo systemctl reload nginx
```

## 12. 常见问题排查

`502 Bad Gateway`：
- 后端没启动，执行：
```bash
sudo systemctl status video-analysis-backend --no-pager
sudo journalctl -u video-analysis-backend -n 200 --no-pager
```

页面能开但数据请求失败：
- 检查 Nginx `/api/` 反代配置是否生效。
- 检查后端数据库连接账号密码是否正确。

公网无法访问：
- Oracle 安全列表没放行 `80/443`。
- Nginx 未启动或配置未生效：`sudo nginx -t`。

## 13. Always Free 注意事项

- 免费实例可能遇到临时容量不足（创建失败可换可用区重试）。
- 长时间闲置有回收风险，建议保留监控和定期访问。
- 本项目建议先用 HTTP 验证功能，再上 HTTPS。

---

## 参考资料

- Oracle Always Free 资源说明：
  https://docs.oracle.com/iaas/Content/FreeTier/resourceref.htm
- Oracle 网络安全规则示例（80/443）：
  https://docs.oracle.com/iaas/Content/GSG/Tasks/loadbalancing.htm
- Certbot 官方：
  https://certbot.eff.org/
