<template>
  <view class="page">
    <view class="hero-card">
      <view class="eyebrow">DESKTOP CLIENT</view>
      <view class="hero-title">桌面端</view>
      <view class="hero-text">
        云图匣桌面端适合批量整理收藏图片，支持将本地文件夹图片批量导入到云图匣，也支持按标签将图片批量导出到电脑本地。
      </view>
    </view>

    <view class="card">
      <view class="section-title">桌面端能做什么</view>
      <view class="bullet-list">
        <view class="bullet-item">批量导入一个或多个本地文件夹图片。</view>
        <view class="bullet-item">默认以文件夹名作为标签名，也可以在导入前修改。</view>
        <view class="bullet-item">按标签批量导出图片到电脑，并自动创建对应文件夹。</view>
        <view class="bullet-item">与小程序使用同一账号体系，扫码即可登录。</view>
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
      <button class="secondary-button" size="mini" @click="copyDownloadUrl">复制下载链接</button>
      <view v-if="!hasDownloadUrl" class="inline-tip">
        当前还没有配置正式下载地址，请先把页面里的 `DESKTOP_DOWNLOAD_URL` 替换成正式安装包链接。
      </view>
    </view>

    <view class="card">
      <view class="section-title">安装步骤</view>
      <view class="step-group">
        <view class="step-item">1. 在电脑上下载桌面端安装包。</view>
        <view class="step-item">2. 双击安装包，按提示完成安装。</view>
        <view class="step-item">3. 安装完成后，打开“云图匣桌面端”。</view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">登录方式</view>
      <view class="step-group">
        <view class="step-item">1. 打开桌面端后，会自动显示登录二维码。</view>
        <view class="step-item">2. 使用微信扫描桌面端二维码。</view>
        <view class="step-item">3. 在小程序中确认登录。</view>
        <view class="step-item">4. 登录成功后，即可开始导入或导出图片。</view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">导入图片</view>
      <view class="step-group">
        <view class="step-item">1. 进入桌面端“导入”页面。</view>
        <view class="step-item">2. 选择一个或多个本地文件夹。</view>
        <view class="step-item">3. 系统默认使用文件夹名作为标签名，可手动修改。</view>
        <view class="step-item">4. 点击“开始导入”，等待任务完成。</view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">导出图片</view>
      <view class="step-group">
        <view class="step-item">1. 进入桌面端“导出”页面。</view>
        <view class="step-item">2. 选择一个或多个标签。</view>
        <view class="step-item">3. 选择本地保存目录。</view>
        <view class="step-item">4. 系统会按标签创建文件夹并导出图片。</view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">常见问题</view>
      <view class="faq-item">
        <view class="faq-question">二维码没有显示怎么办？</view>
        <view class="faq-answer">请检查网络连接，并确认当前桌面端连接的是可用后端服务。</view>
      </view>
      <view class="faq-item">
        <view class="faq-question">上传失败怎么办？</view>
        <view class="faq-answer">请确认图片格式正常，且单张图片大小未超过系统限制。</view>
      </view>
      <view class="faq-item">
        <view class="faq-question">为什么登录失败？</view>
        <view class="faq-answer">请确认当前小程序版本与桌面端连接的是同一套服务环境。</view>
      </view>
    </view>
  </view>
</template>

<script setup>
const DESKTOP_DOWNLOAD_URL = "https://static.yuntuxia.top/desktop/yuntuxia/latest/yuntuxia-desktop-latest.exe"

const hasDownloadUrl = Boolean(DESKTOP_DOWNLOAD_URL.trim())
const downloadUrlText = hasDownloadUrl ? DESKTOP_DOWNLOAD_URL : "请配置正式桌面端下载地址"

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
  min-width: 188rpx;
  height: 68rpx;
  margin-top: 20rpx;
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
