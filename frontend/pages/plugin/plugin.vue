<template>
  <view class="page">
    <view class="hero-card">
      <view class="eyebrow">BROWSER EXTENSION</view>
      <view class="hero-title">浏览器插件</view>
      <view class="hero-text">安装插件后，可以在电脑浏览网页时右键图片，一键收藏到云图匣，并自动同步到小程序首页。</view>
    </view>

    <view class="card">
      <view class="section-title">插件能做什么</view>
      <view class="bullet-list">
        <view class="bullet-item">在网页图片上右键，直接收藏。</view>
        <view class="bullet-item">支持为图片添加多个标签。</view>
        <view class="bullet-item">收藏成功后，小程序首页会自动刷新。</view>
        <view class="bullet-item">可与小程序账号同步使用同一套收藏数据。</view>
      </view>
    </view>

    <view class="card">
      <view class="section-header">
        <view>
          <view class="section-title">安装包下载</view>
          <view class="section-tip">建议在电脑浏览器中打开下载地址，再完成安装。</view>
        </view>
      </view>
      <view class="download-box" :class="{ pending: !hasDownloadUrl }">
        <view class="download-label">下载地址</view>
        <view class="download-url">{{ downloadUrlText }}</view>
      </view>
      <button class="secondary-button" size="mini" @click="copyDownloadUrl">复制链接</button>
      <view v-if="!hasDownloadUrl" class="inline-tip">当前还没有配置正式下载地址，请先把页面里的 `PLUGIN_DOWNLOAD_URL` 替换成正式安装包链接。</view>
    </view>

    <view class="card">
      <view class="section-title">安装步骤</view>

      <view class="step-group">
        <view class="step-title">Chrome</view>
        <view class="step-item">1. 在电脑上下载插件安装包，并解压到一个固定文件夹。</view>
        <view class="step-item">2. 打开 Chrome，进入“扩展程序”页面。</view>
        <view class="step-item">3. 打开右上角“开发者模式”。</view>
        <view class="step-item">4. 点击“加载已解压的扩展程序”，选择刚才解压后的插件文件夹。</view>
        <view class="step-item">5. 安装完成后，把插件固定到浏览器工具栏，方便使用。</view>
      </view>

      <view class="step-group">
        <view class="step-title">Edge</view>
        <view class="step-item">1. 在电脑上下载插件安装包，并解压到一个固定文件夹。</view>
        <view class="step-item">2. 打开 Edge，进入“扩展”管理页面。</view>
        <view class="step-item">3. 打开“开发人员模式”。</view>
        <view class="step-item">4. 点击“加载解压缩的扩展”，选择解压后的插件文件夹。</view>
        <view class="step-item">5. 安装完成后，建议把插件固定到工具栏。</view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">使用说明</view>
      <view class="step-item">1. 打开浏览器插件，用微信扫码完成登录。</view>
      <view class="step-item">2. 在网页图片上点击右键，选择“收藏图片”。</view>
      <view class="step-item">3. 打开插件弹窗，可补充标签后保存。</view>
      <view class="step-item">4. 收藏成功后，小程序首页会自动显示新图片。</view>
    </view>

    <view class="card">
      <view class="section-title">常见问题</view>
      <view class="faq-item">
        <view class="faq-question">为什么有些网站图片无法预览？</view>
        <view class="faq-answer">部分网站有防盗链限制，插件里可能无法直接显示原图预览，但不一定影响收藏。</view>
      </view>
      <view class="faq-item">
        <view class="faq-question">为什么安装后看不到插件？</view>
        <view class="faq-answer">请确认浏览器已经成功加载插件，并把插件固定到工具栏。</view>
      </view>
      <view class="faq-item">
        <view class="faq-question">插件更新后怎么处理？</view>
        <view class="faq-answer">重新下载最新安装包，替换原文件后，在浏览器扩展页点击刷新即可。</view>
      </view>
    </view>
  </view>
</template>

<script setup>
const PLUGIN_DOWNLOAD_URL = "https://static.yuntuxia.top/plugins/yuntuxia/latest/yuntuxia-plugin-latest.zip"

const hasDownloadUrl = Boolean(PLUGIN_DOWNLOAD_URL.trim())
const downloadUrlText = hasDownloadUrl ? PLUGIN_DOWNLOAD_URL : "请配置正式插件下载地址"

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
.page {
  min-height: 100vh;
  padding: 24rpx 24rpx 40rpx;
  box-sizing: border-box;
  background: #f7f8fa;
}

.hero-card,
.card {
  margin-bottom: 24rpx;
  padding: 28rpx 24rpx;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06);
  background: #fff;
}

.hero-card {
  background: linear-gradient(135deg, #ffffff 0%, #eef4ff 100%);
}

.eyebrow {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  color: #2d6cdf;
}

.hero-title {
  margin-top: 12rpx;
  font-size: 42rpx;
  font-weight: 700;
  color: #1f2329;
}

.hero-text {
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #4e5969;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2329;
}

.section-tip {
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}

.secondary-button {
  min-width: 156rpx;
  height: 68rpx;
  padding: 0 28rpx;
  line-height: 68rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
  background: #eef4ff;
  color: #2d6cdf;
}

.bullet-list,
.step-group {
  margin-top: 20rpx;
}

.bullet-item,
.step-item,
.faq-answer {
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #4e5969;
}

.download-box {
  margin-top: 20rpx;
  padding: 22rpx 20rpx;
  border-radius: 20rpx;
  background: #eef4ff;
}

.download-box.pending {
  background: #f2f3f5;
}

.download-label {
  font-size: 22rpx;
  color: #86909c;
}

.download-url {
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #1f2329;
  word-break: break-all;
}

.inline-tip {
  margin-top: 14rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #d93025;
}

.step-title,
.faq-question {
  margin-top: 20rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2329;
}

.faq-item + .faq-item {
  margin-top: 20rpx;
}
</style>
