# 腾讯云部署说明

## 1. 当前推荐结构

- 对外入口：`http://139.199.19.101/api`
- Nginx：监听 `80`，转发 `/api/` 到 Spring Boot
- Spring Boot：监听 `127.0.0.1:8080`
- MySQL：监听 `127.0.0.1:3306`

当前演示链路：

```text
Browser / Mini Program / Plugin
  -> http://139.199.19.101/api/...
  -> Nginx :80
  -> http://127.0.0.1:8080/api/...
  -> Spring Boot
  -> MySQL 127.0.0.1:3306
```

## 2. 服务器环境

推荐系统：

- Ubuntu 22.04 / 24.04

推荐安装：

```bash
sudo apt update
sudo apt install -y openjdk-17-jre nginx mysql-server unzip
```

## 3. 后端配置

后端代码目录示例：

```text
/opt/image-collection/backend
```

systemd 环境文件：

```bash
sudo mkdir -p /etc/image-collection
sudo nano /etc/image-collection/backend.env
```

写入：

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/image_collection?useSSL=false&serverTimezone=Asia/Shanghai
DB_USERNAME=appuser
DB_PASSWORD=你的数据库密码
```

说明：

- 线上运行时优先确认 jar 内置配置是否正确
- 如果 jar 内置了错误的数据库地址，需要重新打包后再部署

## 4. 构建后端

在本地项目中执行：

```powershell
cd "F:\01 Project\001_image-Collection-System\backend"
.\gradlew.bat clean bootJar
```

构建产物：

```text
backend/build/libs/backend-0.0.1-SNAPSHOT.jar
```

上传到服务器，例如：

```powershell
scp "F:\01 Project\001_image-Collection-System\backend\build\libs\backend-0.0.1-SNAPSHOT.jar" ubuntu@139.199.19.101:/tmp/
```

服务器替换：

```bash
sudo mkdir -p /opt/image-collection/backup
sudo cp /opt/image-collection/backend/build/libs/backend-0.0.1-SNAPSHOT.jar /opt/image-collection/backup/backend-0.0.1-SNAPSHOT-$(date +%F-%H%M%S).jar
sudo cp /tmp/backend-0.0.1-SNAPSHOT.jar /opt/image-collection/backend/build/libs/backend-0.0.1-SNAPSHOT.jar
```

## 5. systemd 服务

新建：

```bash
sudo nano /etc/systemd/system/image-backend.service
```

内容：

```ini
[Unit]
Description=Image Collection Backend
After=network.target

[Service]
User=root
WorkingDirectory=/opt/image-collection/backend
EnvironmentFile=/etc/image-collection/backend.env
ExecStart=/usr/bin/java -jar /opt/image-collection/backend/build/libs/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
Restart=always

[Install]
WantedBy=multi-user.target
```

启用并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable image-backend
sudo systemctl restart image-backend
sudo systemctl status image-backend --no-pager
```

## 6. Nginx 反向代理

当前推荐不要直接让前端请求 `8080`，而是统一走 `80`。

站点配置示例：

```bash
sudo nano /etc/nginx/sites-available/image-api.conf
```

内容：

```nginx
server {
    listen 80;
    server_name 139.199.19.101;

    client_max_body_size 20m;

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

## 7. 数据库

默认数据库：

```text
image_collection
```

当前需要的表：

- `image`
- `tag`
- `image_tag`

可先检查：

```sql
USE image_collection;
SHOW TABLES;
SELECT COUNT(*) FROM image;
SELECT COUNT(*) FROM tag;
SELECT COUNT(*) FROM image_tag;
```

## 8. 小程序和插件配置

当前演示环境统一走：

```text
http://139.199.19.101
```

前端默认地址：

- `frontend/utils/base-url.js`

插件默认地址：

- `plugin/popup.js`

## 9. 验证命令

后端接口：

```bash
curl -i http://127.0.0.1:8080/api/image/tags
curl -i http://127.0.0.1:8080/api/image/list
curl -i http://139.199.19.101/api/image/tags
curl -i http://139.199.19.101/api/image/list
```

服务状态：

```bash
sudo systemctl status image-backend --no-pager
sudo systemctl status nginx --no-pager
sudo systemctl status mysql --no-pager
```

端口监听：

```bash
sudo ss -tlnp | grep -E '80|8080|3306'
```

日志：

```bash
sudo journalctl -u image-backend -n 100 --no-pager
sudo journalctl -u nginx -n 100 --no-pager
```

## 10. 真机预览说明

当前 `http + IP` 方式适合：

- 浏览器演示
- 开发者工具调试
- 插件联调

不适合长期正式使用。

如果要支持微信小程序真机更稳定地访问，建议后续补齐：

- 域名
- HTTPS
- 微信后台合法域名配置
- 大陆正式上线时完成备案
