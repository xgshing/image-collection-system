<template>
  <view class="page">
    <view class="hero-card">
      <view class="eyebrow">BROWSER EXTENSION</view>
      <view class="hero-title">
        看到喜欢的图片，
        <text class="hero-em">一键收藏</text>
        到云图匣
      </view>
      <view class="hero-text">无需下载图片，无需切换页面，右键即可将网页图片保存到云图匣。</view>

      <view class="browser">
        <view class="browser-bar">
          <view class="window-dot"></view>
          <view class="window-dot window-dot-mid"></view>
          <view class="window-dot window-dot-last"></view>
          <view class="browser-url">https://dribbble.com/shots/interior-inspiration</view>
        </view>
        <view class="browser-stage">
          <image class="stage-image" src="/static/showcase/architecture.jpg" mode="aspectFill" />
          <view class="stage-shade"></view>
          <view class="cursor"></view>
          <view class="context-menu">
            <view class="menu-item">在新标签页中打开图片</view>
            <view class="menu-item">复制图片网址</view>
            <view class="menu-item menu-item-active">
              <view class="menu-mark"></view>
              <text>收藏到云图匣</text>
            </view>
            <view class="menu-item">检查元素</view>
          </view>
        </view>
      </view>
    </view>

    <view class="section-head">
      <view>
        <view class="section-title">核心功能演示</view>
        <view class="section-tip">在网页图片上右键，直接收藏。</view>
      </view>
      <text class="section-badge">极简 · 高效</text>
    </view>

    <view class="card">
      <view class="feature-index">01</view>
      <view class="feature-title">右键快速收藏</view>
      <view class="feature-text">在网页图片上点击鼠标右键，即可选择收藏到云图匣，即刻沉淀灵感。</view>
      <view class="photo-stage">
        <image class="stage-image" src="/static/showcase/vase.jpg" mode="aspectFill" />
        <view class="photo-shade"></view>
        <view class="glass-toast">
          <view class="toast-dot"></view>
          <text>正在收藏至云图匣...</text>
          <text class="toast-ok">成功</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="feature-index">02</view>
      <view class="feature-title">图片添加标签</view>
      <view class="feature-text">收藏图片时，可以添加多个标签，方便后续按标签快速分类检索管理。</view>
      <view class="popup-mock">
        <view class="file-mock">
          <image class="file-thumb" src="/static/showcase/assets.jpg" mode="aspectFill" />
          <view class="file-meta">
            <view class="file-name">灵感海报_v2.png</view>
            <view class="file-size">1920 × 1080 像素</view>
            <view class="save-pill">保存</view>
          </view>
        </view>
      <view class="tag-row">
        <text class="tag-chip">#插画灵感</text>
        <text class="tag-chip">#UI界面</text>
        <text class="tag-chip">#极简设计</text>
        <text class="tag-chip tag-chip-add">+ 自定义</text>
      </view>
      </view>
    </view>

    <view class="card">
      <view class="feature-index">03</view>
      <view class="feature-title">多端自动同步</view>
      <view class="feature-text">收藏成功后，小程序首页会自动刷新显示新图片，无缝连接多端体验。</view>
      <view class="sync-row">
        <view class="sync-pane">
          <view class="sync-label">浏览器插件</view>
          <view class="sync-check"></view>
          <view class="sync-state">收藏已完成</view>
        </view>
        <view class="sync-arrow"></view>
        <view class="sync-pane sync-pane-brand">
          <view class="sync-label">小程序首页</view>
          <view class="sync-state">即刻呈现在库</view>
        </view>
      </view>
    </view>

    <view class="workflow-card">
      <view class="workflow-eyebrow">WORKFLOW</view>
      <view class="workflow-title">直观收藏工作流</view>
      <view class="workflow-text">4步极速流转，高效归拢全网灵感资产</view>
      <view class="workflow-grid">
        <view v-for="item in workflow" :key="item.no" class="workflow-item">
          <view class="workflow-no">{{ item.no }}</view>
          <view class="workflow-name">{{ item.name }}</view>
          <view class="workflow-sub">{{ item.sub }}</view>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="card-head">
        <view>
          <view class="section-title">安装包下载</view>
          <view class="section-tip">建议在电脑浏览器中打开下载地址，再完成安装。</view>
        </view>
        <text class="stable-badge">最新稳定版</text>
      </view>
      <view class="download-box" :class="{ pending: !hasDownloadUrl }">
        <view class="download-label">官方安装包 ZIP 地址</view>
        <view class="download-url">{{ downloadUrlText }}</view>
      </view>
      <button class="copy-button" @click="copyDownloadUrl">复制下载链接</button>
      <view v-if="!hasDownloadUrl" class="inline-tip">当前还没有配置正式下载地址，请先把页面里的 `PLUGIN_DOWNLOAD_URL` 替换成正式安装包链接。</view>

      <view class="install-head">
        <view class="section-title install-title">安装步骤指引</view>
        <view class="browser-switch">
          <view class="browser-tab" :class="{ active: browser === 'chrome' }" @click="browser = 'chrome'">Chrome</view>
          <view class="browser-tab" :class="{ active: browser === 'edge' }" @click="browser = 'edge'">Edge</view>
        </view>
      </view>
      <view v-for="(step, index) in currentSteps" :key="browser + index" class="step-row">
        <view class="step-no">{{ index + 1 }}</view>
        <view class="step-text">{{ step }}</view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">使用说明</view>
      <view v-for="item in usageSteps" :key="item.text" class="usage-item">
        <view class="usage-text">{{ item.text }}</view>
        <view class="usage-note">{{ item.note }}</view>
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
import { computed, ref } from 'vue'

const PLUGIN_DOWNLOAD_URL = "https://static.yuntuxia.top/plugins/yuntuxia/latest/yuntuxia-plugin-latest.zip"

const hasDownloadUrl = Boolean(PLUGIN_DOWNLOAD_URL.trim())
const downloadUrlText = hasDownloadUrl ? PLUGIN_DOWNLOAD_URL : "请配置正式插件下载地址"
const browser = ref('chrome')
const openFaq = ref(0)

const chromeSteps = [
  '在电脑上下载插件安装包，并解压到一个固定文件夹。',
  '打开 Chrome，进入“扩展程序”页面。',
  '打开右上角“开发者模式”。',
  '点击“加载已解压的扩展程序”，选择刚才解压后的插件文件夹。',
  '安装完成后，把插件固定到浏览器工具栏，方便使用。'
]

const edgeSteps = [
  '在电脑上下载插件安装包，并解压到一个固定文件夹。',
  '打开 Edge，进入“扩展”管理页面。',
  '打开“开发人员模式”。',
  '点击“加载解压缩的扩展”，选择解压后的插件文件夹。',
  '安装完成后，建议把插件固定到工具栏。'
]

const currentSteps = computed(() => browser.value === 'edge' ? edgeSteps : chromeSteps)

const workflow = [
  { no: '1', name: '浏览网页', sub: '查找灵感' },
  { no: '2', name: '右键收藏', sub: '一键收纳' },
  { no: '3', name: '添加标签', sub: '按类归集' },
  { no: '4', name: '小程序看', sub: '多端同步' }
]

const usageSteps = [
  { text: '1. 打开浏览器插件，用微信扫码完成登录。', note: '与微信小程序账户无缝打通绑定' },
  { text: '2. 在网页图片上点击右键，选择“收藏图片”。', note: '无干扰沉浸式网页浏览体验' },
  { text: '3. 打开插件弹窗，可补充标签后保存。', note: '多标签归类，方便检索复用' },
  { text: '4. 收藏成功后，小程序首页会自动显示新图片。', note: '多端实时响应，随时随地查看' }
]

const faqs = [
  {
    question: '为什么有些网站图片无法预览？',
    answer: '部分网站有防盗链限制，插件里可能无法直接显示原图预览，但不一定影响收藏。'
  },
  {
    question: '为什么安装后看不到插件？',
    answer: '请确认浏览器已经成功加载插件，并把插件固定到工具栏。'
  },
  {
    question: '插件更新后怎么处理？',
    answer: '重新下载最新安装包，替换原文件后，在浏览器扩展页点击刷新即可。'
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
    data: PLUGIN_DOWNLOAD_URL,
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
.page { min-height: 100vh; padding: 32rpx 32rpx 48rpx; box-sizing: border-box; background: #F8F7FC; }
.hero-card, .card, .workflow-card { margin-bottom: 32rpx; padding: 40rpx; border-radius: 48rpx; border: 1rpx solid rgba(15, 23, 42, 0.08); box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.04); background: #fff; box-sizing: border-box; }
.hero-card { background: linear-gradient(180deg, #F3F0FF 0%, #ffffff 42%); }
.eyebrow, .workflow-eyebrow { font-size: 22rpx; font-weight: 700; letter-spacing: 3rpx; color: #7965D8; }
.hero-title { margin-top: 16rpx; font-size: 44rpx; line-height: 1.35; font-weight: 800; color: #0f172a; }
.hero-em { color: #7965D8; }
.hero-text, .feature-text, .section-tip, .step-text, .usage-note, .faq-answer { font-size: 24rpx; line-height: 1.7; color: #64748b; }
.hero-text { margin-top: 16rpx; }
.browser { margin-top: 32rpx; overflow: hidden; border-radius: 28rpx; border: 1rpx solid rgba(234, 231, 242, 0.9); background: #fff; box-shadow: 0 16rpx 40rpx rgba(15, 23, 42, 0.08); }
.browser-bar { display: flex; align-items: center; gap: 10rpx; padding: 14rpx 16rpx; background: rgba(248, 247, 252, 0.9); }
.window-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #ff5f56; }
.window-dot-mid { background: #ffbd2e; }
.window-dot-last { background: #27c93f; }
.browser-url { flex: 1; min-width: 0; margin-left: 8rpx; padding: 6rpx 14rpx; border-radius: 12rpx; background: rgba(255, 255, 255, 0.9); color: #64748b; font-size: 18rpx; overflow: hidden; white-space: nowrap; }
.browser-stage { position: relative; height: 380rpx; overflow: hidden; background: #0f172a; }
.stage-image { width: 100%; height: 100%; }
.stage-shade { position: absolute; left: 0; top: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.1); }
.cursor { position: absolute; left: 48%; top: 72rpx; width: 0; height: 0; border-top: 8rpx solid transparent; border-bottom: 22rpx solid #0f172a; border-left: 8rpx solid transparent; border-right: 16rpx solid #0f172a; transform: rotate(28deg); }
.context-menu { position: absolute; left: 28rpx; top: 36rpx; width: 320rpx; padding: 8rpx; border-radius: 24rpx; background: rgba(255, 255, 255, 0.96); border: 1rpx solid rgba(234, 231, 242, 0.9); box-shadow: 0 20rpx 40rpx rgba(15, 23, 42, 0.18); }
.menu-item { display: flex; align-items: center; margin: 4rpx; padding: 14rpx 16rpx; border-radius: 14rpx; color: #334155; font-size: 22rpx; }
.menu-item-active { background: #F3F0FF; color: #7965D8; font-weight: 700; }
.menu-mark { width: 12rpx; height: 12rpx; margin-right: 12rpx; border-radius: 50%; background: #7965D8; }
.section-head, .card-head, .install-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.section-head { margin: 8rpx 8rpx 24rpx; }
.section-title { font-size: 32rpx; font-weight: 700; color: #0f172a; }
.section-tip { margin-top: 8rpx; }
.section-badge, .stable-badge, .help-label { flex-shrink: 0; font-size: 22rpx; color: #94a3b8; }
.stable-badge { padding: 6rpx 16rpx; border-radius: 999rpx; background: #ecfdf5; color: #059669; border: 1rpx solid #d1fae5; }
.feature-index { font-size: 22rpx; font-weight: 800; color: #7965D8; }
.feature-title { margin-top: 8rpx; font-size: 32rpx; font-weight: 700; color: #0f172a; }
.feature-text { margin-top: 8rpx; }
.photo-stage, .popup-mock, .sync-pane, .step-row, .usage-item { margin-top: 24rpx; }
.photo-stage { position: relative; height: 256rpx; overflow: hidden; border-radius: 20rpx; background: #0f172a; }
.photo-shade { position: absolute; left: 0; right: 0; bottom: 0; height: 140rpx; background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.45) 100%); }
.glass-toast { position: absolute; left: 20rpx; right: 20rpx; bottom: 20rpx; display: flex; align-items: center; gap: 12rpx; padding: 16rpx 20rpx; border-radius: 20rpx; background: rgba(255, 255, 255, 0.92); color: #334155; font-size: 22rpx; }
.popup-mock { margin-top: 24rpx; padding: 20rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid rgba(234, 231, 242, 0.8); }
.file-mock { display: flex; align-items: stretch; margin-top: 0; padding: 0; background: transparent; border: none; }
.toast-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #7965D8; }
.toast-ok { margin-left: auto; color: #059669; font-weight: 700; }
.file-thumb { width: 168rpx; height: 168rpx; border-radius: 20rpx; background: #EAE7F2; flex-shrink: 0; }
.save-pill { align-self: flex-start; margin-top: 18rpx; padding: 10rpx 28rpx; border-radius: 14rpx; background: #7965D8; color: #fff; font-size: 24rpx; font-weight: 700; }
.sync-check { position: relative; width: 56rpx; height: 56rpx; margin: 12rpx auto; border-radius: 50%; background: #F3F0FF; border: 1rpx solid #EEE9FF; }
.sync-check::after { content: ""; position: absolute; left: 18rpx; top: 12rpx; width: 12rpx; height: 20rpx; border-right: 4rpx solid #7965D8; border-bottom: 4rpx solid #7965D8; transform: rotate(40deg); }
.file-meta { flex: 1; display: flex; flex-direction: column; justify-content: center; margin-left: 20rpx; min-width: 0; }
.file-name { overflow: hidden; font-size: 28rpx; font-weight: 700; color: #0f172a; white-space: nowrap; text-overflow: ellipsis; }
.file-size { margin-top: 8rpx; font-size: 22rpx; color: #94a3b8; }
.tag-row { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 20rpx; }
.tag-chip { padding: 8rpx 18rpx; border-radius: 999rpx; background: #F3F0FF; color: #7965D8; font-size: 22rpx; }
.tag-chip-add { background: #F8F7FC; color: #64748b; border: 1rpx solid #EAE7F2; }
.sync-row { display: flex; align-items: center; gap: 16rpx; margin-top: 24rpx; }
.sync-pane { flex: 1; margin-top: 0; padding: 24rpx 20rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid #EAE7F2; }
.sync-pane-brand { background: #F3F0FF; border-color: #EEE9FF; }
.sync-label { font-size: 22rpx; color: #94a3b8; }
.sync-state { margin-top: 8rpx; font-size: 26rpx; font-weight: 700; color: #0f172a; }
.sync-arrow { width: 16rpx; height: 16rpx; border-top: 4rpx solid #A18BEF; border-right: 4rpx solid #A18BEF; transform: rotate(45deg); }
.workflow-card { background: linear-gradient(135deg, #7965D8 0%, #A18BEF 100%); border: none; }
.workflow-eyebrow, .workflow-title, .workflow-text, .workflow-name { color: #fff; }
.workflow-eyebrow { color: #D4CCF5; }
.workflow-title { margin-top: 8rpx; font-size: 32rpx; font-weight: 700; }
.workflow-text { margin-top: 8rpx; color: #EEE9FF; font-size: 24rpx; }
.workflow-grid { display: flex; gap: 12rpx; margin-top: 28rpx; }
.workflow-item { flex: 1; min-width: 0; padding: 16rpx 8rpx; border-radius: 24rpx; background: rgba(255, 255, 255, 0.12); text-align: center; }
.workflow-no { width: 36rpx; height: 36rpx; margin: 0 auto 8rpx; border-radius: 50%; background: #fff; color: #7965D8; font-size: 20rpx; font-weight: 800; line-height: 36rpx; }
.workflow-name { font-size: 22rpx; font-weight: 700; }
.workflow-sub { margin-top: 4rpx; font-size: 18rpx; color: #D4CCF5; }
.download-box { margin-top: 24rpx; padding: 24rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid #EAE7F2; }
.download-box.pending { background: #EAE7F2; }
.download-label { font-size: 22rpx; font-weight: 700; color: #94a3b8; }
.download-url { margin-top: 10rpx; font-size: 24rpx; line-height: 1.7; color: #334155; word-break: break-all; }
.copy-button { display: flex; align-items: center; justify-content: center; height: 88rpx; margin: 24rpx 0 0; padding: 0; border-radius: 28rpx; background: #7965D8; color: #fff; font-size: 28rpx; font-weight: 700; }
.copy-button:active, .save-pill:active { background: #6554C0; }
.copy-button::after { border: none; }
.inline-tip { margin-top: 16rpx; font-size: 24rpx; line-height: 1.7; color: #d93025; }
.install-head { margin-top: 36rpx; padding-top: 32rpx; border-top: 1rpx solid #EAE7F2; }
.install-title { font-size: 28rpx; }
.browser-switch { display: flex; padding: 4rpx; border-radius: 16rpx; background: #EAE7F2; }
.browser-tab { padding: 10rpx 22rpx; border-radius: 12rpx; color: #64748b; font-size: 24rpx; font-weight: 700; }
.browser-tab.active { background: #fff; color: #7965D8; box-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.06); }
.step-row, .usage-item { display: flex; align-items: flex-start; gap: 16rpx; padding: 20rpx; border-radius: 24rpx; background: #F8F7FC; border: 1rpx solid #EAE7F2; }
.step-no { width: 40rpx; height: 40rpx; border-radius: 50%; background: #EEE9FF; color: #6554C0; font-size: 22rpx; font-weight: 800; line-height: 40rpx; text-align: center; flex-shrink: 0; }
.step-text { flex: 1; color: #475569; }
.usage-item { display: block; }
.usage-text { font-size: 26rpx; font-weight: 700; line-height: 1.6; color: #1e293b; }
.usage-note { margin-top: 6rpx; }
.title-mark { padding-left: 16rpx; border-left: 6rpx solid #7965D8; }
.help-label { color: #94a3b8; font-size: 24rpx; }
.faq-item { margin-top: 20rpx; border: 1rpx solid rgba(234, 231, 242, 0.9); border-radius: 32rpx; overflow: hidden; }
.faq-question { display: flex; align-items: center; gap: 12rpx; padding: 24rpx; background: rgba(248, 247, 252, 0.8); }
.faq-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #7965D8; flex-shrink: 0; }
.faq-qtext { flex: 1; font-size: 26rpx; font-weight: 700; color: #1e293b; }
.faq-chevron { width: 14rpx; height: 14rpx; border-right: 3rpx solid #94a3b8; border-bottom: 3rpx solid #94a3b8; transform: rotate(45deg); }
.faq-chevron.open { transform: rotate(225deg); }
.faq-answer { padding: 20rpx 24rpx 24rpx; background: #fff; border-top: 1rpx solid #EAE7F2; }
</style>
