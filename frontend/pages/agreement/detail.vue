<template>
  <view class="page">
    <view class="title">{{ pageTitle }}</view>
    <view class="subtitle">最后更新：2026-03-22</view>
    <view class="content">{{ content }}</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const agreementType = ref('user')

const agreementMap = {
  user: {
    title: '用户协议',
    content: `欢迎使用本小程序。

1. 您在使用本小程序前，应确认具备完全民事行为能力，或已在监护人指导下使用。

2. 您应合法、合规地使用本小程序提供的图片浏览、上传、分类和查看等功能，不得上传、传播违法、侵权、色情、暴力或其他不当内容。

3. 您应确保提交的信息真实、准确、完整，并对您上传或发布的内容承担责任。

4. 未经允许，您不得以任何方式干扰、破坏本小程序的正常运行，不得恶意调用接口、攻击系统或抓取数据。

5. 因您违反法律法规、本协议约定，或侵犯第三方合法权益所造成的损失，由您自行承担。

6. 我们有权根据运营需要对本协议内容进行更新。更新后继续使用本小程序，视为您已接受更新后的协议。`
  },
  privacy: {
    title: '隐私政策',
    content: `欢迎使用本小程序。为保障您的正常使用，我们会在您同意后处理必要信息。

1. 为完成登录展示，我们会在您授权后获取并存储您的头像、昵称等公开资料，仅用于首页展示和基础身份识别。

2. 为完成小程序登录能力，我们会在您同意协议后调用登录接口，获取登录凭证 code，用于后续身份校验或服务接入。

3. 我们不会在未经您同意的情况下，在协议同意前调用登录接口，也不会超范围收集您的个人信息。

4. 您提供的信息仅用于本小程序基础功能实现，不会擅自出售、出租或向无关第三方披露。

5. 如您希望撤回授权，可清除小程序缓存或停止使用本小程序。撤回后，部分功能可能无法继续使用。

6. 如本隐私政策发生更新，我们会通过页面展示等方式提示您重新查看。`
  }
}

const currentAgreement = computed(() => agreementMap[agreementType.value] || agreementMap.user)
const pageTitle = computed(() => currentAgreement.value.title)
const content = computed(() => currentAgreement.value.content)

onLoad((options) => {
  if (options?.type) {
    agreementType.value = options.type
  }

  uni.setNavigationBarTitle({
    title: pageTitle.value
  })
})
</script>

<style>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 48rpx;
  box-sizing: border-box;
  background: #f7f8fa;
}

.title {
  font-size: 40rpx;
  font-weight: 700;
  color: #1f2329;
}

.subtitle {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #86909c;
}

.content {
  margin-top: 28rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06);
  background: #fff;
  font-size: 28rpx;
  line-height: 1.9;
  color: #333;
  white-space: pre-wrap;
}

button {
  border-radius: 999rpx;
}
</style>
