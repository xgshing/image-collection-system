<template>
  <view class="page">
    <view class="hero-card">
      <view class="eyebrow">DESKTOP CLIENT</view>
      <view class="hero-title">
        电脑图片，
        <text class="hero-em">也能</text>
        轻松批量管理
      </view>
      <view class="hero-text">支持本地图片批量导入云图匣，也支持按标签批量导出到电脑，让海量图片整理更加专业高效。</view>

      <view class="window-mock">
        <view class="window-bar">
          <view class="window-dot"></view>
          <view class="window-dot window-dot-mid"></view>
          <view class="window-dot window-dot-last"></view>
          <text class="window-name">云图匣 桌面端</text>
        </view>
        <view class="window-body">
          <view class="folder-col">
            <view class="folder-label">本地目录</view>
            <view v-for="(name, index) in folders" :key="name" class="folder-item" :class="{ 'folder-item-active': index === 0 }">
              <view class="folder-icon"></view>
              <text class="folder-name">{{ name }}</text>
            </view>
          </view>
          <view class="file-col">
            <view class="file-col-head">
              <text class="file-title">UI设计灵感</text>
              <text class="file-count">已勾选 128 张</text>
            </view>
            <view class="asset-stage">
              <image class="asset-image" src="/static/showcase/moodboard.jpg" mode="aspectFit" />
              <view class="asset-shade"></view>
              <view class="asset-top">
                <text class="asset-chip">4K 无损画质</text>
                <view class="asset-check"></view>
              </view>
              <view class="bind-note">标签「UI设计」已绑定</view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="section-head">
      <view>
        <view class="section-title">核心功能演示</view>
        <view class="section-tip">批量导入一个或多个本地文件夹图片。</view>
      </view>
      <text class="section-badge">专业 · 批量</text>
    </view>

    <view class="card">
      <view class="feature-index">01</view>
      <view class="feature-title">批量导入</view>
      <view class="feature-text">默认以文件夹名作为标签名，也可以在导入前修改。</view>
      <view class="import-stage">
        <image class="stage-image" src="/static/showcase/assets.jpg" mode="aspectFill" />
        <view class="import-shade">
          <text class="import-chip">architecture / 3D / UI</text>
          <text class="import-state">多图极速校验中</text>
        </view>
      </view>
      <view class="progress-mock">
        <view class="progress-head">
          <view class="folder-icon"></view>
          <text>灵感集文件夹</text>
          <text class="progress-state">智能打标中</text>
        </view>
        <view class="progress-track"><view class="progress-fill"></view></view>
        <view class="progress-meta">36 / 120 张</view>
      </view>
      <view v-for="(step, index) in importSteps" :key="step" class="step-row">
        <view class="step-no">{{ index + 1 }}</view>
        <view class="step-text">{{ step }}</view>
      </view>
    </view>

    <view class="card">
      <view class="feature-index">02</view>
      <view class="feature-title">批量导出</view>
      <view class="feature-text">按标签批量导出图片到电脑，并自动创建对应文件夹。</view>
      <view class="export-board">
        <view class="export-head">
          <text>已选择云图匣标签</text>
          <text class="export-ready">就绪导出</text>
        </view>
        <view class="export-grid">
          <view class="export-pane">
            <view class="pane-label">已选标签 (2个)</view>
            <view class="tag-line"><text>#3D渲染</text><text class="tag-count">45张</text></view>
            <view class="tag-line tag-line-indigo"><text>#插画素材</text><text class="tag-count">68张</text></view>
          </view>
          <view class="export-pane">
            <view class="pane-label">电脑本地生成目录</view>
            <view class="folder-line"><view class="folder-icon"></view><text>3D渲染/</text><text class="archived">已归档</text></view>
            <view class="folder-line"><view class="folder-icon"></view><text>插画素材/</text><text class="archived">已归档</text></view>
          </view>
        </view>
      </view>
      <view v-for="(step, index) in exportSteps" :key="step" class="step-row">
        <view class="step-no">{{ index + 1 }}</view>
        <view class="step-text">{{ step }}</view>
      </view>
    </view>

    <view class="workflow-card">
      <view class="workflow-eyebrow">WORKFLOW</view>
      <view class="workflow-title">高效闭环工作流</view>
      <view class="workflow-text">微信小程序与电脑桌面端无缝互通，同一账号，资产随时随地随取随用。</view>
      <view class="workflow-grid">
        <view v-for="(item, index) in workflow" :key="item.name" class="workflow-wrap">
          <view class="workflow-item">
            <view class="workflow-no">{{ index + 1 }}</view>
            <view class="workflow-name">{{ item.name }}</view>
            <view class="workflow-sub">{{ item.sub }}</view>
          </view>
          <view v-if="index < workflow.length - 1" class="workflow-link"></view>
        </view>
      </view>
      <view class="workflow-note">支持按标签批量反向导出到电脑本地</view>
    </view>

    <view class="card">
      <view class="card-head">
        <view>
          <view class="section-title">桌面端安装包下载</view>
          <view class="section-tip">建议在电脑浏览器中打开下载地址，再完成安装。</view>
        </view>
        <text class="stable-badge">Windows 官方稳定版</text>
      </view>
      <view class="download-box" :class="{ pending: !hasDownloadUrl }">
        <view class="download-label">官方安装包 EXE 地址</view>
        <view class="download-url">{{ downloadUrlText }}</view>
      </view>
      <button class="copy-button" @click="copyDownloadUrl">复制下载链接</button>
      <view v-if="!hasDownloadUrl" class="inline-tip">当前还没有配置正式下载地址，请先把页面里的 `DESKTOP_DOWNLOAD_URL` 替换成正式安装包链接。</view>
    </view>

    <view class="block-title">安装步骤指引</view>
    <view v-for="(step, index) in installSteps" :key="step" class="plain-step">
      <view class="step-no">{{ index + 1 }}</view>
      <view class="step-text">{{ step }}</view>
    </view>

    <view class="card login-card">
      <view class="section-title title-mark">扫码无缝登录</view>
      <view class="qr-row">
        <view class="qr-box">
          <text class="qr-role">电脑桌面端</text>
          <view class="qr-grid">
            <view class="qr-cell"></view>
            <view class="qr-cell"></view>
            <view class="qr-cell"></view>
            <view class="qr-cell"></view>
          </view>
          <text>登录二维码</text>
        </view>
        <view class="qr-bridge">
          <view class="qr-bridge-arrow"></view>
          <text>扫码确认</text>
        </view>
        <view class="qr-side">
          <text class="qr-role">手机小程序</text>
          <view class="qr-side-title">微信扫一扫</view>
          <view class="qr-side-text">在小程序中确认登录</view>
        </view>
      </view>
      <view v-for="(step, index) in loginSteps" :key="step" class="step-row">
        <view class="step-no">{{ index + 1 }}</view>
        <view class="step-text">{{ step }}</view>
      </view>
    </view>

    <view class="card">
      <view class="card-head">
        <view class="section-title title-mark">常见问题</view>
        <text class="help-label">帮助中心</text>
      </view>
      <view v-for="(item, index) in faqs" :key="item.question" class="faq-item">
        <view class="faq-question" @click="toggleFaq(index)">
          <view class="faq-dot"></view>
          <text class="faq-qtext">{{ item.question }}</text>
          <view class="faq-chevron" :class="{ open: openFaq === index }"></view>
        </view>
        <view v-if="openFaq === index" class="faq-answer">{{ item.answer }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const DESKTOP_DOWNLOAD_URL = "https://static.yuntuxia.top/desktop/yuntuxia/latest/yuntuxia-desktop-latest.exe"

const hasDownloadUrl = Boolean(DESKTOP_DOWNLOAD_URL.trim())
const downloadUrlText = hasDownloadUrl ? DESKTOP_DOWNLOAD_URL : "请配置正式桌面端下载地址"
const openFaq = ref(0)

const folders = ['UI设计灵感', '摄影素材库', '3D建模参考']

const importSteps = [
  '进入桌面端“导入”页面。',
  '选择一个或多个本地文件夹。',
  '系统默认使用文件夹名作为标签名，可手动修改。',
  '点击“开始导入”，等待任务完成。'
]

const exportSteps = [
  '进入桌面端“导出”页面。',
  '选择一个或多个标签。',
  '选择本地保存目录。',
  '系统会按标签创建文件夹并导出图片。'
]

const installSteps = [
  '在电脑上下载桌面端安装包。',
  '双击安装包，按提示完成安装。',
  '安装完成后，打开“云图匣桌面端”。'
]

const loginSteps = [
  '打开桌面端后，会自动显示登录二维码。',
  '使用微信扫描桌面端二维码。',
  '在小程序中确认登录。',
  '登录成功后，即可开始导入或导出图片。'
]

const workflow = [
  { name: '电脑本地', sub: '海量收集' },
  { name: '批量导入', sub: '智能标签' },
  { name: '云端互通', sub: '手机查阅' }
]

const faqs = [
  {
    question: '二维码没有显示怎么办？',
    answer: '请检查网络连接，并确认当前桌面端连接的是可用后端服务。'
  },
  {
    question: '上传失败怎么办？',
    answer: '请确认图片格式正常，且单张图片大小未超过系统限制。'
  },
  {
    question: '为什么登录失败？',
    answer: '请确认当前小程序版本与桌面端连接的是同一套服务环境。'
  }
]

const toggleFaq = (index) => {
  openFaq.value = openFaq.value === index ? -1 : index
}

const copyDownloadUrl = () => {
  if (!hasDownloadUrl) {
    uni.showToast({
      title: "请先配置下载地址",
      icon: "none"
    })
    return
  }

  uni.setClipboardData({
    data: DESKTOP_DOWNLOAD_URL,
    success() {
      uni.showToast({
        title: "下载链接已复制",
        icon: "success"
      })
    }
  })
}
</script>

<style>
.page { min-height: 100vh; padding: 16rpx 32rpx 32rpx; box-sizing: border-box; background: #F8F7FC; }
.hero-card, .card { margin-bottom: 16rpx; padding: 24rpx 40rpx; border-radius: 48rpx; border: 1rpx solid rgba(15, 23, 42, 0.08); box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.04); background: #fff; box-sizing: border-box; }
.hero-card { background: linear-gradient(180deg, #F3F0FF 0%, #ffffff 46%); }
.eyebrow, .workflow-eyebrow { font-size: 22rpx; font-weight: 700; letter-spacing: 3rpx; color: #7965D8; }
.hero-title { margin-top: 8rpx; font-size: 44rpx; line-height: 1.35; font-weight: 800; color: #0f172a; }
.hero-em { color: #7965D8; }
.hero-text, .feature-text, .section-tip, .step-text, .faq-answer, .qr-side-text { font-size: 24rpx; line-height: 1.7; color: #64748b; }
.hero-text { margin-top: 8rpx; }
.window-mock { margin: 8rpx -28rpx 0; overflow: hidden; border-radius: 28rpx; border: 1rpx solid #EAE7F2; background: #fff; }
.window-bar { display: flex; align-items: center; gap: 10rpx; padding: 16rpx 20rpx; background: #F8F7FC; }
.window-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #f87171; }
.window-dot-mid { background: #fbbf24; }
.window-dot-last { background: #34d399; }
.window-name { margin-left: 8rpx; font-size: 22rpx; color: #64748b; }
.window-body { display: flex; align-items: stretch; }
.folder-col { width: 26%; flex: 0 0 26%; padding: 16rpx 10rpx; background: #F8F7FC; box-sizing: border-box; }
.file-col { width: 74%; flex: 0 0 74%; min-width: 0; display: flex; flex-direction: column; padding: 16rpx 12rpx 12rpx; box-sizing: border-box; }
.asset-stage { position: relative; height: 0; margin-top: 8rpx; padding-bottom: 55.814%; overflow: hidden; border-radius: 18rpx; background: #F8F7FC; }
.asset-image { position: absolute; left: 0; top: 0; width: 100%; height: 100%; }
.stage-image { width: 100%; height: 100%; }
.asset-shade { position: absolute; left: 0; right: 0; bottom: 0; height: 72rpx; background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.28) 100%); }
.asset-top { position: absolute; left: 12rpx; right: 12rpx; top: 12rpx; display: flex; justify-content: space-between; align-items: center; }
.asset-chip { padding: 2rpx 10rpx; border-radius: 8rpx; background: rgba(15, 23, 42, 0.7); color: #fff; font-size: 16rpx; }
.asset-check { position: relative; width: 32rpx; height: 32rpx; border-radius: 50%; background: #7965D8; }
.asset-check::after { content: ""; position: absolute; left: 11rpx; top: 6rpx; width: 8rpx; height: 14rpx; border-right: 3rpx solid #fff; border-bottom: 3rpx solid #fff; transform: rotate(40deg); }
.asset-stage .bind-note { position: absolute; left: 12rpx; bottom: 12rpx; margin-top: 0; color: #fff; background: rgba(15, 23, 42, 0.45); }
.import-stage { position: relative; height: 220rpx; margin-top: 8rpx; overflow: hidden; border-radius: 18rpx; background: #0f172a; }
.import-shade { position: absolute; left: 0; right: 0; bottom: 0; display: flex; align-items: center; justify-content: space-between; gap: 12rpx; height: 56rpx; padding: 0 12rpx; background: rgba(15, 23, 42, 0.45); }
.import-chip, .import-state { position: static; max-width: 58%; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; padding: 2rpx 10rpx; border-radius: 8rpx; background: rgba(15, 23, 42, 0.78); font-size: 16rpx; }
.import-chip { color: rgba(255, 255, 255, 0.92); }
.import-state { max-width: 38%; color: #34d399; }
.folder-line text, .tag-line text { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.export-board { margin-top: 8rpx; padding: 16rpx 20rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid rgba(234, 231, 242, 0.8); }
.export-head { display: flex; justify-content: space-between; color: #64748b; font-size: 20rpx; }
.export-ready { color: #059669; font-weight: 700; }
.tag-line, .folder-line { display: flex; align-items: center; gap: 8rpx; margin-top: 10rpx; padding: 10rpx 12rpx; border-radius: 12rpx; background: #F3F0FF; color: #7965D8; font-size: 20rpx; font-weight: 700; }
.tag-line-indigo { background: #EEE9FF; color: #6554C0; }
.tag-count { margin-left: auto; font-size: 18rpx; font-weight: 600; opacity: 0.75; }
.archived { margin-left: auto; color: #059669; font-size: 16rpx; font-weight: 700; }
.folder-label, .pane-label, .file-col-head { font-size: 22rpx; color: #64748b; }
.folder-label { font-weight: 700; }
.folder-item, .file-item { display: flex; align-items: center; gap: 8rpx; margin-top: 14rpx; font-size: 20rpx; color: #334155; }
.folder-item-active { margin-top: 16rpx; padding: 8rpx 6rpx; border-radius: 12rpx; background: #fff; color: #7965D8; font-weight: 700; }
.folder-name { flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.folder-icon { width: 28rpx; height: 20rpx; border-radius: 4rpx; background: #A18BEF; box-shadow: 0 -6rpx 0 #D4CCF5; flex-shrink: 0; }
.folder-item-active .folder-icon { background: #7965D8; box-shadow: 0 -6rpx 0 #D4CCF5; }
.file-col-head { display: flex; align-items: center; justify-content: space-between; gap: 8rpx; }
.file-title { flex: 1; min-width: 0; overflow: hidden; color: #0f172a; font-size: 26rpx; font-weight: 700; white-space: nowrap; text-overflow: ellipsis; }
.file-count { flex-shrink: 0; padding: 4rpx 12rpx; border-radius: 999rpx; background: #F3F0FF; color: #7965D8; font-size: 20rpx; font-weight: 700; }
.file-check { width: 22rpx; height: 22rpx; border-radius: 6rpx; background: #7965D8; }
.bind-note { margin-top: 8rpx; font-size: 20rpx; color: #7965D8; }
.section-head, .card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.section-head { margin: 0 8rpx 8rpx; }
.section-title, .block-title { font-size: 32rpx; font-weight: 700; color: #0f172a; }
.section-tip { margin-top: 4rpx; }
.section-badge { flex-shrink: 0; font-size: 22rpx; color: #94a3b8; }
.stable-badge { flex-shrink: 0; max-width: 180rpx; padding: 6rpx 14rpx; border-radius: 999rpx; background: #F3F0FF; color: #7965D8; font-size: 20rpx; text-align: center; }
.feature-index { font-size: 22rpx; font-weight: 800; color: #7965D8; }
.feature-title { margin-top: 4rpx; font-size: 32rpx; font-weight: 700; color: #0f172a; }
.feature-text { margin-top: 4rpx; }
.progress-mock { margin-top: 8rpx; padding: 16rpx 24rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid #EAE7F2; }
.progress-head { display: flex; align-items: center; gap: 12rpx; font-size: 24rpx; color: #0f172a; }
.progress-state { margin-left: auto; color: #7965D8; font-size: 22rpx; }
.progress-track { height: 12rpx; margin-top: 10rpx; border-radius: 999rpx; background: #EAE7F2; overflow: hidden; }
.progress-fill { width: 46%; height: 100%; border-radius: 999rpx; background: linear-gradient(90deg, #7965D8, #A18BEF); }
.progress-meta { margin-top: 8rpx; font-size: 22rpx; color: #94a3b8; }
.step-row, .plain-step { display: flex; align-items: flex-start; gap: 16rpx; margin-top: 8rpx; padding: 14rpx 20rpx; border-radius: 24rpx; background: #fff; border: 1rpx solid rgba(234, 231, 242, 0.9); }
.plain-step { margin-bottom: 8rpx; }
.step-no { width: 40rpx; height: 40rpx; border-radius: 50%; background: #EEE9FF; color: #7965D8; font-size: 22rpx; font-weight: 800; line-height: 40rpx; text-align: center; flex-shrink: 0; }
.step-text { flex: 1; color: #475569; }
.export-grid { display: flex; gap: 12rpx; margin-top: 8rpx; }
.export-pane { flex: 1; min-width: 0; padding: 16rpx; border-radius: 20rpx; background: #fff; border: 1rpx solid #EAE7F2; }
.tag-chip { display: inline-block; margin-top: 12rpx; margin-right: 8rpx; padding: 8rpx 14rpx; border-radius: 999rpx; background: #F3F0FF; color: #7965D8; font-size: 20rpx; }
.workflow-card { margin-bottom: 16rpx; padding: 24rpx 40rpx; border-radius: 48rpx; background: linear-gradient(135deg, #7965D8 0%, #A18BEF 100%); box-shadow: 0 16rpx 40rpx rgba(121, 101, 216, 0.18); }
.workflow-eyebrow { color: #D4CCF5; }
.workflow-title { margin-top: 4rpx; font-size: 32rpx; font-weight: 700; color: #fff; }
.workflow-text, .workflow-note { margin-top: 4rpx; font-size: 24rpx; color: #EEE9FF; }
.workflow-grid { display: flex; align-items: center; margin-top: 8rpx; }
.workflow-wrap { display: flex; align-items: center; flex: 1; min-width: 0; }
.workflow-item { flex: 1; padding: 16rpx 8rpx; border-radius: 24rpx; background: rgba(255, 255, 255, 0.12); text-align: center; }
.workflow-no { width: 36rpx; height: 36rpx; margin: 0 auto 8rpx; border-radius: 50%; background: #fff; color: #7965D8; font-size: 20rpx; font-weight: 800; line-height: 36rpx; }
.workflow-name { font-size: 22rpx; font-weight: 700; color: #fff; }
.workflow-sub { margin-top: 4rpx; font-size: 18rpx; color: #D4CCF5; }
.workflow-link { width: 16rpx; height: 16rpx; margin: 0 4rpx; border-top: 4rpx solid #A18BEF; border-right: 4rpx solid #A18BEF; transform: rotate(45deg); flex-shrink: 0; }
.workflow-note { margin-top: 8rpx; }
.download-box { margin-top: 8rpx; padding: 16rpx 24rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid #EAE7F2; }
.download-box.pending { background: #EAE7F2; }
.download-label { font-size: 22rpx; font-weight: 700; color: #94a3b8; }
.download-url { margin-top: 6rpx; font-size: 24rpx; line-height: 1.7; color: #334155; word-break: break-all; }
.copy-button { display: flex; align-items: center; justify-content: center; height: 88rpx; margin: 8rpx 0 0; padding: 0; border-radius: 28rpx; background: #7965D8; color: #fff; font-size: 28rpx; font-weight: 700; }
.copy-button:active { background: #6554C0; }
.copy-button::after { border: none; }
.inline-tip { margin-top: 8rpx; font-size: 24rpx; line-height: 1.7; color: #d93025; }
.block-title { margin: 4rpx 8rpx 8rpx; }
.login-card { margin-top: 0; }
.title-mark { padding-left: 16rpx; border-left: 6rpx solid #7965D8; }
.qr-row { display: flex; align-items: center; gap: 16rpx; margin-top: 8rpx; padding: 16rpx 24rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid #EAE7F2; }
.qr-box { width: 196rpx; padding: 16rpx 12rpx; border-radius: 20rpx; background: #fff; border: 1rpx solid #EAE7F2; text-align: center; font-size: 20rpx; color: #64748b; box-sizing: border-box; flex-shrink: 0; }
.qr-role { display: block; margin-bottom: 6rpx; color: #7965D8; font-size: 20rpx; font-weight: 700; }
.qr-grid { display: flex; flex-wrap: wrap; width: 96rpx; margin: 0 auto 10rpx; }
.qr-cell { width: 40rpx; height: 40rpx; margin: 4rpx; border: 4rpx solid #7965D8; border-radius: 6rpx; box-sizing: border-box; }
.qr-bridge { display: flex; flex-direction: column; align-items: center; gap: 8rpx; color: #64748b; font-size: 18rpx; flex-shrink: 0; }
.qr-bridge-arrow { width: 14rpx; height: 14rpx; border-top: 4rpx solid #A18BEF; border-right: 4rpx solid #A18BEF; transform: rotate(45deg); }
.qr-side { flex: 1; min-width: 0; }
.qr-side .qr-role { margin-bottom: 10rpx; }
.qr-side-title { font-size: 28rpx; font-weight: 700; color: #0f172a; }
.help-label { color: #94a3b8; font-size: 24rpx; }
.faq-item { margin-top: 12rpx; border: 1rpx solid rgba(234, 231, 242, 0.9); border-radius: 28rpx; overflow: hidden; }
.faq-question { display: flex; align-items: center; gap: 12rpx; padding: 16rpx 24rpx; background: rgba(248, 247, 252, 0.8); }
.faq-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #7965D8; flex-shrink: 0; }
.faq-qtext { flex: 1; font-size: 26rpx; font-weight: 700; color: #1e293b; }
.faq-chevron { width: 14rpx; height: 14rpx; border-right: 3rpx solid #94a3b8; border-bottom: 3rpx solid #94a3b8; transform: rotate(45deg); }
.faq-chevron.open { transform: rotate(225deg); }
.faq-answer { padding: 12rpx 24rpx 16rpx; background: #fff; border-top: 1rpx solid #EAE7F2; }
</style>
