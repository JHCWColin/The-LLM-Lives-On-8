#!/usr/bin/env bash
# 下载内置模型 SmolLM2-135M-Instruct Q4_K_M（约 101MB）
# 用法: ./download_model.sh
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
DST="$HERE/app/src/main/assets/models"
mkdir -p "$DST"

URL="https://hf-mirror.com/unsloth/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
# 国内直连失败时可改用镜像；二者内容一致
URL_BACKUP="https://huggingface.co/unsloth/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"

echo "下载模型到 $DST ..."
if ! curl -fL --retry 5 --retry-delay 3 -C - -o "$DST/SmolLM2-135M-Instruct-Q4_K_M.gguf" "$URL"; then
    echo "主镜像失败，尝试官方源 ..."
    curl -fL --retry 5 --retry-delay 3 -C - -o "$DST/SmolLM2-135M-Instruct-Q4_K_M.gguf" "$URL_BACKUP"
fi

ls -lh "$DST/SmolLM2-135M-Instruct-Q4_K_M.gguf"
echo "模型下载完成"
