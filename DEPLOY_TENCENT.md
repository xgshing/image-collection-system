# 腾讯云部署说明

本文档基于当前项目实际结构，整理后端在腾讯云 Linux 服务器上的部署方式，以及桌面端 / 插件安装包在腾讯云 COS 的发布方式。

## 1. 目标结构

推荐的部署拆分如下：

- 云服务器：
  - `Nginx`
  - `Spring Boot backend`
  - `MySQL`
- 腾讯云 COS：
  - 插件 zip 包
  - 桌面端安装包
  - 图片对象存储

推荐链路：

```text
小程序 / 插件 / 桌面端
  -> https://你的域名/api/...
  -> Nginx
  -> Spring Boot 127.0.0.1:8080
  -> MySQL 127.0.0.1:3306
  -> COS
```

## 2. 服务器环境

推荐：

- Ubuntu 22.04 / 24.04
- OpenJDK 17
- Nginx
- MySQL 8

基础安装：

```bash
sudo apt update
sudo apt install -y openjdk-17-jre nginx mysql-server unzip
```

检查版本：

```bash
java -version
nginx -v
mysql --version
```

## 3. 后端目录约定

推荐目录：

```text
/opt/image-collection/backend
```

推荐文件布局：

```text
/opt/image-collection/backend/
├─current.jar
├─releases/
├─logs/
└─uploads/
```

创建目录：

```bash
sudo mkdir -p /opt/image-collection/backend/releases
sudo mkdir -p /opt/image-collection/backend/logs
sudo mkdir -p /opt/image-collection/backend/uploads
```

## 4. 环境变量文件

创建：

```bash
sudo mkdir -p /etc/image-collection
sudo nano /etc/image-collection/backend.env
```

示例内容：

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/image_collection?useSSL=false&serverTimezone=Asia/Shanghai
DB_USERNAME=your_mysql_user
DB_PASSWORD=your_mysql_password

WECHAT_MINIAPP_APP_ID=your_wechat_app_id
WECHAT_MINIAPP_SECRET=your_wechat_app_secret
WECHAT_MINIAPP_CODE_ENV_VERSION=release

STORAGE_PUBLIC_BASE_URL=https://api.yourdomain.com

COS_SECRET_ID=your_tencent_cloud_secret_id
COS_SECRET_KEY=your_tencent_cloud_secret_key
COS_REGION=ap-guangzhou
COS_BUCKET=your-bucket-1250000000
COS_PUBLIC_URL_PREFIX=https://your-cdn-or-cos-domain
COS_KEY_PREFIX=image-collection
```

说明：

- 生产环境建议使用 `storage.type=cos`
- `STORAGE_PUBLIC_BASE_URL` 要与外部访问地址一致
- `COS_PUBLIC_URL_PREFIX` 建议配置成 CDN 或 COS 公网访问域名

## 5. 构建后端 jar

本地执行：

```powershell
cd "F:\01 Project\001_image-Collection-System\backend"
.\gradlew.bat clean bootJar
```

构建产物：

```text
backend/build/libs/backend-0.0.1-SNAPSHOT.jar
```

## 6. 上传并发布后端

### 6.1 上传到服务器

示例：

```powershell
scp "F:\01 Project\001_image-Collection-System\backend\build\libs\backend-0.0.1-SNAPSHOT.jar" ubuntu@你的服务器IP:~/backend-2026-04-07-01.jar
```

### 6.2 服务器上归档并切换

登录服务器后执行：

```bash
sudo mv ~/backend-2026-04-07-01.jar /opt/image-collection/backend/releases/backend-2026-04-07-01.jar
sudo cp /opt/image-collection/backend/releases/backend-2026-04-07-01.jar /opt/image-collection/backend/current.jar
```

如果你就是覆盖当前版本，也可以直接覆盖：

```bash
sudo cp /opt/image-collection/backend/releases/backend-2026-04-07-01.jar /opt/image-collection/backend/current.jar
```

## 7. systemd 服务

创建服务：

```bash
sudo nano /etc/systemd/system/image-backend.service
```

示例内容：

```ini
[Unit]
Description=Image Collection Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/image-collection/backend
EnvironmentFile=/etc/image-collection/backend.env
ExecStart=/usr/bin/java -jar /opt/image-collection/backend/current.jar --spring.profiles.active=prod
StandardOutput=append:/opt/image-collection/backend/logs/backend.log
StandardError=append:/opt/image-collection/backend/logs/backend-error.log
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启用并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable image-backend.service
sudo systemctl restart image-backend.service
sudo systemctl status image-backend.service --no-pager
```

## 8. Nginx 反向代理

推荐不要让前端直接访问 `8080`，统一通过 `80/443` 入口访问。

创建配置：

```bash
sudo nano /etc/nginx/sites-available/image-api.conf
```

示例：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 50m;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用：

```bash
sudo ln -s /etc/nginx/sites-available/image-api.conf /etc/nginx/sites-enabled/image-api.conf
sudo nginx -t
sudo systemctl reload nginx
```

## 9. 数据库

默认数据库：

```text
image_collection
```

Flyway 初始化文件：

- `backend/src/main/resources/db/migration/V1__init_schema.sql`

关键表：

- `user`
- `user_session`
- `image`
- `tag`
- `image_tag`

检查命令：

```sql
USE image_collection;
SHOW TABLES;
SELECT COUNT(*) FROM image;
SELECT COUNT(*) FROM tag;
SELECT COUNT(*) FROM image_tag;
```

## 10. 日志与排障

当前推荐日志文件：

```text
/opt/image-collection/backend/logs/backend.log
/opt/image-collection/backend/logs/backend-error.log
```

查看日志：

```bash
tail -f /opt/image-collection/backend/logs/backend.log
tail -f /opt/image-collection/backend/logs/backend-error.log
```

查看服务状态：

```bash
sudo systemctl status image-backend.service --no-pager
sudo systemctl status nginx --no-pager
sudo systemctl status mysql --no-pager
```

查看端口：

```bash
sudo ss -lntp | grep -E '80|8080|3306'
```

## 11. COS 发布插件和桌面端

### 11.1 安装 `coscli`

Windows 下下载 `coscli.exe` 后，可直接使用完整路径执行，或加入系统 `PATH`。

检查版本：

```powershell
D:\tools\coscli\coscli.exe --version
```

配置：

```powershell
coscli config
```

### 11.2 上传插件

最新包：

```powershell
coscli cp "F:\01 Project\001_image-Collection-System\dist\yuntuxia-plugin-latest.zip" "cos://your-bucket-1250000000/plugins/yuntuxia/latest/yuntuxia-plugin-latest.zip"
```

版本归档：

```powershell
coscli cp "F:\01 Project\001_image-Collection-System\dist\yuntuxia-plugin-v1.0.0.zip" "cos://your-bucket-1250000000/plugins/yuntuxia/releases/yuntuxia-plugin-v1.0.0.zip"
```

删除旧版本：

```powershell
coscli rm "cos://your-bucket-1250000000/plugins/yuntuxia/releases/yuntuxia-plugin-v1.0.0.zip"
```

### 11.3 上传桌面端

安装包路径：

```text
desktop/release/云图匣桌面端 Setup 1.0.0.exe
```

上传最新包：

```powershell
coscli cp "F:\01 Project\001_image-Collection-System\desktop\release\云图匣桌面端 Setup 1.0.0.exe" "cos://your-bucket-1250000000/desktop/yuntuxia/latest/yuntuxia-desktop-latest.exe"
```

版本归档：

```powershell
coscli cp "F:\01 Project\001_image-Collection-System\desktop\release\云图匣桌面端 Setup 1.0.0.exe" "cos://your-bucket-1250000000/desktop/yuntuxia/releases/yuntuxia-desktop-1.0.0.exe"
```

列出目录：

```powershell
coscli ls "cos://your-bucket-1250000000/desktop/yuntuxia/latest/"
```

### 11.4 小程序下载地址

上传完成后，需要同步更新：

- `frontend/pages/plugin/plugin.vue`
- `frontend/pages/desktop/desktop.vue`

把下载地址替换成真实可访问的 COS / CDN 地址。

## 12. 发布检查清单

### 后端

- 环境变量正确
- MySQL 表完整
- `image-backend.service` 状态正常
- `/api/image/tags`、`/api/image/list` 正常

### 小程序

- `frontend/utils/base-url.js` 指向正确环境
- 插件 / 桌面端扫码登录确认正常
- “插件”与“桌面端”说明页下载地址正确

### 插件

- `plugin/manifest.json` 配置正确
- 登录二维码正常
- 右键收藏可用

### 桌面端

- `desktop` 可正常登录
- 导入本地文件夹正常
- 导出标签到本地正常
- 安装包已上传 COS
