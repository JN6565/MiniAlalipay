#!/usr/bin/env bash
# HTTPS 一键启用脚本（在服务器 /opt/minialalipay 目录下执行）：
# 1. 校验新版 nginx.conf（含 listen 443 ssl）与 docker-compose.yml（含证书卷）已上传，
#    未上传则中止，避免用旧配置重建导致站点不可用；
# 2. 生成 H5 自签证书（幂等：已存在则跳过），SAN 绑定公网 IP，有效期 10 年；
# 3. 重写 .env 的 GATEWAY_CORS_ORIGINS 为包含 https 来源的值（先删旧行再追加，
#    兼容任意旧值格式，避免 sed 模式不匹配导致静默失败）；
# 4. 用一次性容器执行 nginx -t 校验配置与证书无误后，才强制重建 nginx 与 gateway。
#    预检容器必须挂到 compose 网络：nginx 在解析配置阶段就需要解析 proxy_pass 的
#    服务名 gateway，脱离 compose 网络会报 host not found。
set -e
cd /opt/minialalipay

if ! grep -q "listen 443 ssl" nginx/nginx.conf; then
  echo "错误：nginx/nginx.conf 还是旧版（不含 443 站点）。请先在本地执行 build-and-upload.ps1 -SkipBackend -SkipFrontend 上传新配置。"
  exit 1
fi
if ! grep -q "nginx/certs" docker-compose.yml; then
  echo "错误：docker-compose.yml 还是旧版（不含证书卷）。请先上传新配置。"
  exit 1
fi

mkdir -p nginx/certs
if [ -f nginx/certs/h5.crt ] && [ -f nginx/certs/h5.key ]; then
  echo "==> 证书已存在，跳过生成"
else
  echo "==> 生成自签证书（有效期 10 年）"
  openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
    -keyout nginx/certs/h5.key \
    -out nginx/certs/h5.crt \
    -subj "/CN=121.43.51.164" \
    -addext "subjectAltName=IP:121.43.51.164"
  chmod 600 nginx/certs/h5.key
fi

echo "==> 重写 .env 中的 GATEWAY_CORS_ORIGINS"
sed -i '/^GATEWAY_CORS_ORIGINS=/d' .env
echo 'GATEWAY_CORS_ORIGINS=https://121.43.51.164,http://121.43.51.164,http://121.43.51.164:81' >> .env
grep '^GATEWAY_CORS_ORIGINS=' .env

echo "==> 校验 nginx 配置与证书（预检容器挂到 compose 网络以解析服务名 gateway）"
NET=$(docker network ls --format '{{.Name}}' | grep -m1 '^minialalipay' || true)
if [ -z "$NET" ]; then
  echo "错误：找不到 minialalipay 编排网络，请确认 docker compose 已启动过（docker compose ps）。"
  exit 1
fi
docker run --rm --network "$NET" \
  -v "$(pwd)/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v "$(pwd)/nginx/certs:/etc/nginx/certs:ro" \
  -v "$(pwd)/web/h5:/usr/share/nginx/html/h5:ro" \
  -v "$(pwd)/web/admin:/usr/share/nginx/html/admin:ro" \
  nginx:1.27-alpine nginx -t

echo "==> 强制重建 nginx 与 gateway"
docker compose up -d --force-recreate nginx gateway
echo "==> 完成。请确认阿里云安全组已放行 TCP 443，然后用 https://121.43.51.164 访问。"
