<template>
  <view v-if="visible && showPrivacy" class="modal-mask">
    <view class="modal-card">
      <view class="modal-title">登录前请确认</view>
      <view class="modal-desc">请先阅读并同意以下协议。首页收藏和水印相机使用同一次登录。</view>
      <label class="check-line" @click="toggleAgreement('user')">
        <checkbox :checked="userAgreementChecked" color="#7965D8" />
        <view class="check-text">同意 <text class="link-text" @click.stop="openAgreement('user')">《用户协议》</text></view>
      </label>
      <label class="check-line" @click="toggleAgreement('privacy')">
        <checkbox :checked="privacyPolicyChecked" color="#7965D8" />
        <view class="check-text">同意 <text class="link-text" @click.stop="openAgreement('privacy')">《隐私政策》</text></view>
      </label>
      <view class="modal-actions">
        <button class="ghost-button" @click="emit('close')">取消</button>
        <button class="primary-button" @click="handleAgreeAndContinue">继续</button>
      </view>
    </view>
  </view>

  <view v-if="visible && showForm" class="login-mask">
    <view class="login-card">
      <view class="login-title">{{ title }}</view>
      <view class="login-text">设置头像和昵称后才会完成登录。只选头像或填写昵称，还不能使用收藏和水印相机。</view>
      <view class="avatar-row">
        <image class="avatar-preview" :src="formAvatar" mode="aspectFill" />
        <button class="avatar-picker" hover-class="none" open-type="chooseAvatar" @chooseavatar="handleChooseAvatar">选择头像</button>
      </view>
      <input class="nickname-input" type="nickname" placeholder="请输入昵称" :value="pendingNickname" @input="handleNicknameInput" />
      <button class="primary-button" :loading="authLoading" :disabled="authLoading" @click="handleProfileConfirm">保存并继续</button>
      <button class="ghost-button login-later" @click="emit('close')">先看看</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import baseUrl from '../utils/base-url'
import { uploadFile, request } from '../utils/request'
import { getPrivacyConsent, getUserProfile, setPrivacyConsent, setSessionToken, setUserProfile } from '../utils/auth'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '登录后继续' }
})
const emit = defineEmits(['close', 'success'])

const hasConsented = ref(false)
const userAgreementChecked = ref(false)
const privacyPolicyChecked = ref(false)
const pendingAvatarUrl = ref('')
const pendingNickname = ref('')
const authLoading = ref(false)
const defaultAvatar = '/static/logo.png'
let avatarBusy = false

const showPrivacy = computed(() => props.visible && !hasConsented.value)
const showForm = computed(() => props.visible && hasConsented.value)
const formAvatar = computed(() => pendingAvatarUrl.value || defaultAvatar)

watch(() => props.visible, (visible) => {
  if (!visible) return
  hasConsented.value = getPrivacyConsent()
  const profile = getUserProfile()
  if (!pendingAvatarUrl.value && /^https?:\/\//i.test(profile?.avatarUrl || '')) pendingAvatarUrl.value = profile.avatarUrl
  if (!pendingNickname.value && profile?.nickName && profile.nickName !== '微信用户') pendingNickname.value = profile.nickName
})

const toggleAgreement = (type) => {
  if (type === 'user') userAgreementChecked.value = !userAgreementChecked.value
  else privacyPolicyChecked.value = !privacyPolicyChecked.value
}
const openAgreement = (type) => uni.navigateTo({ url: `/pages/agreement/detail?type=${type}` })

const handleAgreeAndContinue = async () => {
  if (!userAgreementChecked.value || !privacyPolicyChecked.value) {
    uni.showToast({ title: '请先同意两个协议', icon: 'none' })
    return
  }
  try {
    await new Promise((resolve, reject) => {
      if (typeof wx !== 'undefined' && typeof wx.requirePrivacyAuthorize === 'function') {
        wx.requirePrivacyAuthorize({ success: resolve, fail: reject })
        return
      }
      resolve()
    })
  } catch (error) {
    uni.showToast({ title: '需要先完成隐私授权', icon: 'none' })
    return
  }
  setPrivacyConsent(true)
  hasConsented.value = true
}

const handleChooseAvatar = (event) => {
  if (avatarBusy) return
  const next = event?.detail?.avatarUrl || ''
  if (!next) return
  avatarBusy = true
  pendingAvatarUrl.value = next
  setTimeout(() => { avatarBusy = false }, 800)
}

const handleNicknameInput = (event) => { pendingNickname.value = event?.detail?.value?.trim?.() || '' }

const loginErrorText = (error) => {
  const raw = String(error?.errMsg || error?.message || '')
  if (/request:fail|CONNECTION|ECONNREFUSED|timeout/i.test(raw)) return '登录服务没有连上，请确认本机接口已启动'
  return raw || '登录失败'
}

const uploadAvatar = (filePath) => new Promise((resolve, reject) => {
  uploadFile({
    url: `${baseUrl}/api/auth/miniapp/avatar`,
    auth: false,
    filePath,
    name: 'file',
    success(res) {
      let payload = res.data
      if (typeof payload === 'string') {
        try { payload = JSON.parse(payload) } catch (error) { reject(error); return }
      }
      if (res.statusCode !== 200 || !payload?.avatarUrl) {
        reject(new Error(payload?.message || 'avatar upload failed'))
        return
      }
      resolve(payload.avatarUrl)
    },
    fail: reject
  })
})

const handleProfileConfirm = async () => {
  if (authLoading.value) return
  if (!pendingAvatarUrl.value) {
    uni.showToast({ title: '请先选择头像', icon: 'none' })
    return
  }
  if (!pendingNickname.value) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }
  authLoading.value = true
  try {
    const avatarUrl = /^https?:\/\//i.test(pendingAvatarUrl.value) ? pendingAvatarUrl.value : await uploadAvatar(pendingAvatarUrl.value)
    const loginRes = await new Promise((resolve, reject) => uni.login({ provider: 'weixin', success: resolve, fail: reject }))
    if (!loginRes?.code) throw new Error('login code missing')
    const authRes = await request({
      url: `${baseUrl}/api/auth/miniapp/login`,
      method: 'POST',
      auth: false,
      data: { code: loginRes.code, nickName: pendingNickname.value, avatarUrl }
    })
    if (authRes.statusCode !== 200 || !authRes.data?.sessionToken) {
      throw new Error(authRes.data?.message || '登录没有完成')
    }
    setSessionToken(authRes.data.sessionToken)
    setUserProfile({
      userId: authRes.data.userId || null,
      nickName: authRes.data.nickName || pendingNickname.value,
      avatarUrl: authRes.data.avatarUrl || avatarUrl
    })
    setPrivacyConsent(true)
    uni.showToast({ title: '登录成功', icon: 'success' })
    emit('success')
  } catch (error) {
    uni.showToast({ title: loginErrorText(error), icon: 'none' })
  } finally {
    authLoading.value = false
  }
}
</script>

<style scoped>
.modal-mask, .login-mask { position: fixed; inset: 0; z-index: 99; display: flex; align-items: center; justify-content: center; padding: 32rpx; background: rgba(15, 23, 42, 0.45); box-sizing: border-box; }
.login-card, .modal-card { width: 100%; padding: 36rpx 28rpx; border-radius: 24rpx; background: #fff; box-sizing: border-box; }
.modal-title, .login-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.modal-desc, .login-text { margin-top: 16rpx; font-size: 28rpx; line-height: 1.7; color: #4e5969; }
.avatar-row { display: flex; align-items: center; gap: 20rpx; margin-top: 28rpx; }
.avatar-preview { width: 96rpx; height: 96rpx; border-radius: 50%; background: #EEE9FF; }
.avatar-picker { flex: 1; margin: 0; background: #F8F7FC; color: #1f2329; border-radius: 24rpx; }
.avatar-picker::after, .ghost-button::after, .primary-button::after { border: none; }
.nickname-input { margin-top: 20rpx; padding: 22rpx 24rpx; border-radius: 20rpx; background: #F8F7FC; font-size: 28rpx; color: #1f2329; }
.check-line { display: flex; align-items: center; margin-top: 24rpx; }
.check-text { margin-left: 12rpx; font-size: 28rpx; color: #1f2329; }
.link-text { color: #7965D8; }
.modal-actions { display: flex; gap: 20rpx; margin-top: 36rpx; }
.ghost-button, .primary-button { flex: 1; border-radius: 999rpx; }
.login-later { width: 100%; margin-top: 20rpx; }
.ghost-button { background: #f2f3f5; color: #1f2329; }
.primary-button { background: #7965D8; color: #fff; }
</style>
