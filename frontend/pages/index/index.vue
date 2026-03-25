<template>
  <view class="page">
    <view class="topbar">
      <view>
        <view class="topbar-title">我的灵感库</view>
        <view class="topbar-subtitle">先收藏，后整理，之后总能找回来。</view>
      </view>
    </view>

    <view class="hero-card">
      <view class="hero-copy">
        <view class="hero-eyebrow">COLLECT AND ORGANIZE</view>
        <view class="hero-title">把喜欢的图片，好好收起来</view>
        <view class="hero-text">
          看到喜欢的图先收藏，不用存在手机里，也不用再翻聊天记录和浏览器历史。
        </view>
      </view>

      <view class="hero-user">
        <image class="avatar" :src="displayAvatar" mode="aspectFill" />
        <view class="user-meta">
          <view class="user-name">{{ displayNickname }}</view>
          <view class="user-tip">
            {{ isLoggedIn ? '已完成授权，可以开始整理你的收藏。' : '完成登录后，可同步收藏并整理标签。' }}
          </view>
        </view>
      </view>

      <view class="hero-actions">
        <button class="collect-button" @click="goCollect">立即收藏</button>
        <button v-if="profileCompleted" class="scan-login-button" @click="handlePluginLoginScan">插件扫码同步</button>
      </view>
    </view>

    <view class="stats-card">
      <view class="section-title">你的收藏概览</view>
      <view class="section-tip">今天也把喜欢的内容收进来。</view>
      <view class="stats">
        <view class="stat-item">
          <view class="stat-value">{{ stats.total }}</view>
          <view class="stat-label">已收藏</view>
        </view>
        <view class="stat-item">
          <view class="stat-value">{{ stats.tags }}</view>
          <view class="stat-label">标签数</view>
        </view>
        <view class="stat-item">
          <view class="stat-value">{{ stats.lastUpload }}</view>
          <view class="stat-label">最近新增</view>
        </view>
      </view>
    </view>

    <view v-if="isLoggedIn" class="quick-card">
      <view class="section-title">常用标签</view>
      <view class="section-tip">点一下，直接查看同类图片。</view>
      <input
        v-model="tagKeyword"
        class="tag-search"
        placeholder="搜索标签，例如 UI、海报、插画"
      />
      <view
        class="filter"
        :class="{ collapsed: shouldCollapseTags }"
        :style="filterStyle"
      >
        <view
          v-for="item in filteredTagList"
          :key="item.value"
          class="filter-item"
          :class="{ active: currentTag === item.value }"
          @click="filterByTag(item)"
        >
          {{ item.label }}
        </view>
      </view>
      <view v-if="showTagToggle" class="filter-toggle" @click="toggleTagExpand">
        <text>{{ tagsExpanded ? '收起标签' : '展开全部标签' }}</text>
        <text class="filter-toggle-arrow" :class="{ expanded: tagsExpanded }">⌄</text>
      </view>
    </view>

    <view v-if="isLoggedIn" class="quick-card continue-card">
      <view>
        <view class="section-title">继续整理</view>
        <view class="section-tip">把最近收进来的图片补上标签，后面会更好找。</view>
      </view>
      <button size="mini" class="continue-button" @click="goCollect">去整理</button>
    </view>

    <view v-if="isLoggedIn && pageState === 'error'" class="status-box">
      <view class="status-title">加载失败</view>
      <view class="status-text">没有拿到最新收藏，刷新后再试一次。</view>
      <button size="mini" @click="reloadPage">重新加载</button>
    </view>

    <view v-else-if="isLoggedIn && pageState === 'empty'" class="status-box empty-state">
      <view class="empty-title">先收进第一张喜欢的图片</view>
      <view class="empty-desc">
        以后找灵感时，就不用再翻手机相册、聊天记录和浏览器历史。
      </view>
      <button class="collect-button" @click="goCollect">开始收藏</button>
    </view>

    <view v-else-if="isLoggedIn" class="gallery-section">
      <view class="gallery-header">
        <view class="section-title">最近收藏</view>
        <view class="section-tip">按时间排序，帮你快速回看最近收进来的内容。</view>
      </view>

      <view class="container">
        <view class="column">
          <view
            v-for="item in leftList"
            :key="item.id"
            class="card"
            @click="goDetail(item)"
          >
            <image
              :src="resolveImageUrl(item.localUrl)"
              mode="widthFix"
              @error="handleImageError(item)"
            />
            <view class="card-meta">
              <view v-if="item.tags?.length" class="card-tags">{{ item.tags.join(' · ') }}</view>
              <view class="card-time">{{ formatRecentTime(item.createTime) }}</view>
            </view>
            <view v-if="failedImageIds.includes(item.id)" class="image-error">图片加载失败</view>
          </view>
        </view>

        <view class="column">
          <view
            v-for="item in rightList"
            :key="item.id"
            class="card"
            @click="goDetail(item)"
          >
            <image
              :src="resolveImageUrl(item.localUrl)"
              mode="widthFix"
              @error="handleImageError(item)"
            />
            <view class="card-meta">
              <view v-if="item.tags?.length" class="card-tags">{{ item.tags.join(' · ') }}</view>
              <view class="card-time">{{ formatRecentTime(item.createTime) }}</view>
            </view>
            <view v-if="failedImageIds.includes(item.id)" class="image-error">图片加载失败</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="hasConsented && !profileCompleted" class="login-mask">
      <view class="login-card">
        <view class="login-title">打造你的图片灵感库</view>
        <view class="login-text">
          多端同步、标签整理、一键收藏网页图片，先把喜欢的内容稳稳收起来。
        </view>
        <button class="avatar-picker" open-type="chooseAvatar" @chooseavatar="handleChooseAvatar">
          <image class="avatar-preview" :src="displayAvatar" mode="aspectFill" />
          <text class="avatar-picker-text">选择微信头像</text>
        </button>
        <input
          class="nickname-input"
          type="nickname"
          placeholder="请输入微信昵称"
          :value="pendingNickname"
          @input="handleNicknameInput"
        />
        <button class="primary-button" :loading="authLoading" @click="handleProfileConfirm">
          保存并继续
        </button>
      </view>
    </view>

    <view v-if="showPrivacyModal" class="modal-mask">
      <view class="modal-card">
        <view class="modal-title">欢迎使用本小程序</view>
        <view class="modal-desc">请先阅读并同意以下内容，再继续使用收藏功能。</view>

        <label class="check-line" @click="toggleAgreement('user')">
          <checkbox :checked="userAgreementChecked" color="#2d6cdf" />
          <view class="check-text">
            同意
            <text class="link-text" @click.stop="openAgreement('user')">《用户协议》</text>
          </view>
        </label>

        <label class="check-line" @click="toggleAgreement('privacy')">
          <checkbox :checked="privacyPolicyChecked" color="#2d6cdf" />
          <view class="check-text">
            同意
            <text class="link-text" @click.stop="openAgreement('privacy')">《隐私政策》</text>
          </view>
        </label>

        <view class="modal-actions">
          <button class="ghost-button" @click="handleReject">不同意</button>
          <button class="primary-button" :loading="authLoading" @click="handleAgreeAndContinue">
            同意并继续
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import baseUrl from '../../utils/base-url'
import resolveImageUrl from '../../utils/image-url'
import { request } from '../../utils/request'
import {
  clearSessionToken,
  clearPluginLoginToken,
  getPluginLoginToken,
  getPrivacyConsent,
  getSessionToken,
  getUserProfile,
  setPluginLoginToken,
  setLoginCode,
  setPrivacyConsent,
  setSessionToken,
  setUserProfile
} from '../../utils/auth'

const REFRESH_KEY = 'image_list_should_refresh'

const tagList = ref([{ label: '全部', value: '' }])
const leftList = ref([])
const rightList = ref([])
const failedImageIds = ref([])
const currentTag = ref('')
const pageState = ref('loading')
const hasConsented = ref(false)
const authLoading = ref(false)
const userAgreementChecked = ref(false)
const privacyPolicyChecked = ref(false)
const userProfile = ref(null)
const pendingAvatarUrl = ref('')
const pendingNickname = ref('')
const pendingPluginLoginToken = ref('')
const tagKeyword = ref('')
const tagsExpanded = ref(false)
const tagOverflow = ref(false)
const collapsedTagHeight = ref(0)

const defaultAvatar = '/static/logo.png'
const defaultNickname = '微信用户'

const isLoggedIn = computed(() => Boolean(userProfile.value?.nickName && userProfile.value?.avatarUrl))
const profileCompleted = computed(() => Boolean(
  userProfile.value?.nickName &&
  userProfile.value?.avatarUrl &&
  userProfile.value.nickName !== defaultNickname &&
  userProfile.value.avatarUrl !== defaultAvatar
))
const showPrivacyModal = computed(() => !hasConsented.value)
const displayAvatar = computed(() => pendingAvatarUrl.value || userProfile.value?.avatarUrl || defaultAvatar)
const displayNickname = computed(() => pendingNickname.value || userProfile.value?.nickName || '未登录用户')
const filteredTagList = computed(() => {
  const keyword = tagKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return tagList.value
  }

  return tagList.value.filter((item) => (
    item.value === '' || item.label.toLowerCase().includes(keyword)
  ))
})
const shouldCollapseTags = computed(() => !tagKeyword.value.trim() && !tagsExpanded.value && tagOverflow.value)
const showTagToggle = computed(() => !tagKeyword.value.trim() && tagOverflow.value)
const filterStyle = computed(() => {
  if (!shouldCollapseTags.value || !collapsedTagHeight.value) {
    return {}
  }

  return {
    maxHeight: `${collapsedTagHeight.value}px`
  }
})

const goCollect = () => {
  if (!isLoggedIn.value) {
    uni.showToast({
      title: '请先完成协议同意和登录',
      icon: 'none'
    })
    return
  }

  uni.navigateTo({
    url: '/pages/collect/collect'
  })
}

const stats = ref({
  total: 0,
  tags: 0,
  lastUpload: ''
})

const formatRecentTime = (value) => {
  if (!value) {
    return '暂无'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  const month = `${parsed.getMonth() + 1}`.padStart(2, '0')
  const day = `${parsed.getDate()}`.padStart(2, '0')
  return `${month}-${day}`
}

const splitList = (data = []) => {
  leftList.value = []
  rightList.value = []
  failedImageIds.value = []

  data.forEach((item, index) => {
    if (index % 2 === 0) {
      leftList.value.push(item)
    } else {
      rightList.value.push(item)
    }
  })

  pageState.value = data.length ? 'ready' : 'empty'
}

const requestList = (tagValue = '') => {
  if (!isLoggedIn.value) {
    return
  }

  currentTag.value = tagValue
  pageState.value = 'loading'

  uni.showLoading({
    title: '加载中...'
  })

  request({
    url: `${baseUrl}/api/image/list`,
    data: tagValue ? { tag: tagValue } : {},
    success(res) {
      if (res.statusCode !== 200) {
        splitList([])
        pageState.value = 'error'
        uni.showToast({
          title: `请求失败：${res.statusCode}`,
          icon: 'none'
        })
        return
      }

      const list = Array.isArray(res.data) ? res.data : []
      splitList(list)
      stats.value.total = list.length
      stats.value.lastUpload = formatRecentTime(list[0]?.createTime)
    },
    fail() {
      splitList([])
      pageState.value = 'error'
      uni.showToast({
        title: '加载失败',
        icon: 'none'
      })
    },
    complete() {
      uni.hideLoading()
    }
  })
}

const requestTags = () => {
  if (!isLoggedIn.value) {
    return
  }

  request({
    url: `${baseUrl}/api/image/tags`,
    success(res) {
      if (res.statusCode !== 200 || !Array.isArray(res.data)) {
        return
      }

      stats.value.tags = res.data.length
      tagList.value = [
        { label: '全部', value: '' },
        ...res.data.map((tag) => ({ label: tag, value: tag }))
      ]
      tagsExpanded.value = false

      if (currentTag.value && !tagList.value.some((item) => item.value === currentTag.value)) {
        requestList('')
      }
    }
  })
}

const reloadPage = () => {
  requestTags()
  requestList(currentTag.value)
}

const filterByTag = (tag) => {
  requestList(tag.value)
}

const toggleTagExpand = () => {
  tagsExpanded.value = !tagsExpanded.value
}

const measureTagOverflow = async () => {
  await nextTick()

  if (!isLoggedIn.value || !filteredTagList.value.length) {
    tagOverflow.value = false
    collapsedTagHeight.value = 0
    return
  }

  const query = uni.createSelectorQuery()
  query.selectAll('.filter-item').boundingClientRect()
  query.exec((result) => {
    const rects = Array.isArray(result?.[0]) ? result[0].filter(Boolean) : []
    if (!rects.length) {
      tagOverflow.value = false
      collapsedTagHeight.value = 0
      return
    }

    const firstTop = rects[0].top
    const firstRowRects = rects.filter((item) => Math.abs(item.top - firstTop) < 1)
    const firstRowHeight = firstRowRects.reduce((height, item) => Math.max(height, item.height || 0), rects[0].height || 0)
    const hasMoreThanOneRow = rects.some((item) => Math.abs(item.top - firstTop) > 1)

    collapsedTagHeight.value = firstRowHeight
    tagOverflow.value = hasMoreThanOneRow
  })
}

const handleImageError = (item) => {
  if (!failedImageIds.value.includes(item.id)) {
    failedImageIds.value.push(item.id)
  }
}

const goDetail = (item) => {
  if (!isLoggedIn.value) {
    uni.showToast({
      title: '请先完成登录',
      icon: 'none'
    })
    return
  }

  uni.navigateTo({
    url: `/pages/detail/detail?id=${item.id}`
  })
}

const openAgreement = (type) => {
  uni.navigateTo({
    url: `/pages/agreement/detail?type=${type}`
  })
}

const toggleAgreement = (type) => {
  if (type === 'user') {
    userAgreementChecked.value = !userAgreementChecked.value
    return
  }

  privacyPolicyChecked.value = !privacyPolicyChecked.value
}

const handleReject = () => {
  uni.showModal({
    title: '提示',
    content: '您需要同意《用户协议》和《隐私政策》后才能继续使用本小程序。',
    showCancel: false
  })
}

const loadPageData = () => {
  requestTags()
  requestList(currentTag.value)
}

const requestLoginCode = () => new Promise((resolve, reject) => {
  uni.login({
    provider: 'weixin',
    success: resolve,
    fail: reject
  })
})

const requestWechatPrivacyAuthorize = () => new Promise((resolve, reject) => {
  // #ifdef MP-WEIXIN
  if (typeof wx?.requirePrivacyAuthorize === 'function') {
    wx.requirePrivacyAuthorize({
      success: resolve,
      fail: reject
    })
    return
  }
  // #endif

  resolve()
})

const saveAuthorizedProfile = (profile) => {
  const normalizedProfile = {
    userId: profile?.userId || userProfile.value?.userId || null,
    nickName: profile?.nickName || defaultNickname,
    avatarUrl: profile?.avatarUrl || defaultAvatar
  }

  userProfile.value = normalizedProfile
  setUserProfile(normalizedProfile)
  setPrivacyConsent(true)
  hasConsented.value = true
}

const handleChooseAvatar = (event) => {
  pendingAvatarUrl.value = event?.detail?.avatarUrl || ''
}

const handleNicknameInput = (event) => {
  pendingNickname.value = event?.detail?.value?.trim?.() || ''
}

const runLoginFlow = async () => {
  authLoading.value = true

  try {
    const loginRes = await requestLoginCode()

    if (!loginRes?.code) {
      throw new Error('login code missing')
    }

    setLoginCode(loginRes.code)
    const authRes = await request({
      url: `${baseUrl}/api/auth/miniapp/login`,
      method: 'POST',
      auth: false,
      data: {
        code: loginRes.code,
        nickName: pendingNickname.value,
        avatarUrl: pendingAvatarUrl.value
      }
    })

    if (authRes.statusCode !== 200 || !authRes.data?.sessionToken) {
      throw new Error('session token missing')
    }

    setSessionToken(authRes.data.sessionToken)
    saveAuthorizedProfile({
      userId: authRes.data.userId,
      nickName: authRes.data.nickName || pendingNickname.value,
      avatarUrl: authRes.data.avatarUrl || pendingAvatarUrl.value
    })

    uni.showToast({
      title: '登录成功',
      icon: 'success'
    })

    loadPageData()
  } catch (error) {
    console.error('runLoginFlow failed', error)
    clearSessionToken()
    uni.showToast({
      title: '未完成登录授权',
      icon: 'none'
    })
  } finally {
    authLoading.value = false
  }
}

const handleAgreeAndContinue = async () => {
  if (!hasConsented.value && (!userAgreementChecked.value || !privacyPolicyChecked.value)) {
    uni.showToast({
      title: '请先勾选两个协议',
      icon: 'none'
    })
    return
  }

  try {
    await requestWechatPrivacyAuthorize()
  } catch (error) {
    console.error('requestWechatPrivacyAuthorize failed', error)
    uni.showToast({
      title: '请先完成隐私授权',
      icon: 'none'
    })
    return
  }

  await runLoginFlow()
}

const handleProfileConfirm = async () => {
  if (!pendingAvatarUrl.value) {
    uni.showToast({
      title: '请先选择微信头像',
      icon: 'none'
    })
    return
  }

  if (!pendingNickname.value) {
    uni.showToast({
      title: '请先填写微信昵称',
      icon: 'none'
    })
    return
  }

  await runLoginFlow()
}

const confirmPluginLogin = async (token) => {
  if (!token || !profileCompleted.value) {
    return false
  }

  const confirmRes = await request({
    url: `${baseUrl}/api/plugin/login/confirm`,
    method: 'POST',
    data: {
      token
    }
  })

  if (confirmRes.statusCode !== 200 || confirmRes.data?.status !== 'confirmed') {
    return false
  }

  clearPluginLoginToken()
  pendingPluginLoginToken.value = ''
  uni.showToast({
    title: '插件登录已确认',
    icon: 'success'
  })
  return true
}

const handlePluginLoginScan = async () => {
  if (!profileCompleted.value) {
    uni.showToast({
      title: '请先完成头像昵称设置',
      icon: 'none'
    })
    return
  }

  try {
    const scanRes = await new Promise((resolve, reject) => {
      uni.scanCode({
        onlyFromCamera: true,
        success: resolve,
        fail: reject
      })
    })

    const scanText = scanRes?.result || ''
    const prefix = 'ics-plugin-login:'
    if (!scanText.startsWith(prefix)) {
      uni.showToast({
        title: '不是插件登录二维码',
        icon: 'none'
      })
      return
    }

    const token = scanText.slice(prefix.length)
    const confirmed = await confirmPluginLogin(token)
    if (!confirmed) {
      uni.showToast({
        title: '插件登录确认失败',
        icon: 'none'
      })
      return
    }
  } catch (error) {
    console.error('handlePluginLoginScan failed', error)
    uni.showToast({
      title: '扫码失败',
      icon: 'none'
    })
  }
}

const consumePluginLoginOptions = (options = {}) => {
  const rawScene = options?.scene ? decodeURIComponent(options.scene) : ''
  const token = rawScene || options?.token || ''
  if (!token) {
    return
  }
  pendingPluginLoginToken.value = token
  setPluginLoginToken(token)
}

const tryConfirmPendingPluginLogin = async () => {
  const token = pendingPluginLoginToken.value || getPluginLoginToken()
  if (!token || !profileCompleted.value) {
    return
  }

  const confirmed = await confirmPluginLogin(token)
  if (!confirmed) {
    uni.showToast({
      title: '插件登录确认失败',
      icon: 'none'
    })
  }
}

onLoad((options) => {
  consumePluginLoginOptions(options)
  hasConsented.value = getPrivacyConsent()
  userProfile.value = getUserProfile()
  pendingAvatarUrl.value = userProfile.value?.avatarUrl && userProfile.value.avatarUrl !== defaultAvatar
    ? userProfile.value.avatarUrl
    : ''
  pendingNickname.value = userProfile.value?.nickName && userProfile.value.nickName !== defaultNickname
    ? userProfile.value.nickName
    : ''

  if (hasConsented.value && profileCompleted.value && getSessionToken()) {
    loadPageData()
  }

  tryConfirmPendingPluginLogin()
})

onShow(() => {
  userProfile.value = getUserProfile()
  pendingPluginLoginToken.value = getPluginLoginToken() || pendingPluginLoginToken.value
  pendingAvatarUrl.value = userProfile.value?.avatarUrl && userProfile.value.avatarUrl !== defaultAvatar
    ? userProfile.value.avatarUrl
    : pendingAvatarUrl.value
  pendingNickname.value = userProfile.value?.nickName && userProfile.value.nickName !== defaultNickname
    ? userProfile.value.nickName
    : pendingNickname.value

  if (!profileCompleted.value || !getSessionToken()) {
    return
  }

  if (uni.getStorageSync(REFRESH_KEY)) {
    uni.removeStorageSync(REFRESH_KEY)
    requestTags()
    requestList(currentTag.value)
  }

  tryConfirmPendingPluginLogin()
})

watch(filteredTagList, () => {
  measureTagOverflow()
}, { deep: true })

watch(tagKeyword, () => {
  if (tagKeyword.value.trim()) {
    tagsExpanded.value = true
  } else {
    tagsExpanded.value = false
  }
  measureTagOverflow()
})
</script>

<style>
.page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 32rpx;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 24rpx 12rpx;
}

.topbar-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #1f2329;
}

.topbar-subtitle {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.hero-card,
.stats-card,
.quick-card,
.card,
.login-card,
.modal-card {
  border-radius: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06);
}

.hero-card {
  margin: 24rpx;
  padding: 28rpx;
  background: linear-gradient(135deg, #ffffff 0%, #eef4ff 100%);
}

.hero-copy {
  margin-bottom: 28rpx;
}

.hero-eyebrow {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  color: #2d6cdf;
}

.hero-title {
  margin-top: 12rpx;
  font-size: 44rpx;
  line-height: 1.25;
  font-weight: 700;
  color: #1f2329;
}

.hero-text {
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #4e5969;
}

.hero-user {
  display: flex;
  align-items: center;
}

.avatar {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: #dfe9ff;
}

.user-meta {
  margin-left: 20rpx;
}

.user-name {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2329;
}

.user-tip {
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #4e5969;
}

.hero-actions {
  margin-top: 24rpx;
}

.stats-card,
.quick-card {
  margin: 0 24rpx 24rpx;
  padding: 24rpx;
  background: #fff;
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

.stats {
  display: flex;
  justify-content: space-around;
  margin-top: 20rpx;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2329;
}

.stat-label {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #86909c;
}

.filter {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 20rpx;
  overflow: hidden;
}

.filter.collapsed {
  overflow: hidden;
}

.tag-search {
  margin-top: 18rpx;
  min-height: 76rpx;
  padding: 0 20rpx;
  border-radius: 18rpx;
  background: #f7f8fa;
  font-size: 26rpx;
  color: #1f2329;
}

.filter-item {
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: #f2f3f5;
  font-size: 28rpx;
  color: #4e5969;
}

.filter-item.active {
  background: #dfe9ff;
  color: #2d6cdf;
}

.filter-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #2d6cdf;
}

.filter-toggle-arrow {
  font-size: 24rpx;
  line-height: 1;
  transform: rotate(0deg);
  transition: transform 0.2s ease;
}

.filter-toggle-arrow.expanded {
  transform: rotate(180deg);
}

.continue-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.continue-button {
  background: #eef4ff;
  color: #2d6cdf;
}

.status-box {
  margin: 24rpx;
  padding: 80rpx 24rpx;
  text-align: center;
  color: #666;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06);
  background: #fff;
}

.status-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2329;
}

.status-text {
  margin-top: 12rpx;
  font-size: 26rpx;
}

.empty-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2329;
}

.empty-desc {
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #6b7280;
}

.gallery-section {
  margin-top: 8rpx;
}

.gallery-header {
  padding: 0 24rpx 12rpx;
}

.container {
  display: flex;
  padding: 0 10rpx 20rpx;
  box-sizing: border-box;
}

.column {
  flex: 1;
  margin: 0 10rpx;
}

.card {
  margin-bottom: 20rpx;
  overflow: hidden;
  background: #fff;
}

.card-meta {
  padding: 16rpx 18rpx 20rpx;
}

.card-tags {
  font-size: 22rpx;
  color: #2d6cdf;
}

.card-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #86909c;
}

.image-error {
  margin-top: 12rpx;
  color: #d93025;
  font-size: 24rpx;
  text-align: center;
}

image {
  width: 100%;
  border-radius: 12rpx;
  transition: opacity 0.3s ease;
}

.modal-mask,
.login-mask {
  position: fixed;
  inset: 0;
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
  background: rgba(15, 23, 42, 0.45);
  box-sizing: border-box;
}

.login-card,
.modal-card {
  width: 100%;
  padding: 36rpx 28rpx;
  background: #fff;
  box-sizing: border-box;
}

button {
  border-radius: 999rpx;
}

.modal-title,
.login-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1f2329;
}

.modal-desc,
.login-text {
  margin-top: 16rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: #4e5969;
}

.collect-button {
  margin-top: 24rpx;
  background: #2d6cdf;
  color: #fff;
}

.scan-login-button {
  margin-top: 18rpx;
  background: #f2f3f5;
  color: #1f2329;
}

.avatar-picker {
  margin-top: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  background: #f7f8fa;
  color: #1f2329;
  border-radius: 24rpx;
}

.avatar-preview {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #dfe9ff;
}

.avatar-picker-text {
  font-size: 28rpx;
}

.nickname-input {
  margin-top: 20rpx;
  padding: 22rpx 24rpx;
  border-radius: 20rpx;
  background: #f7f8fa;
  font-size: 28rpx;
  color: #1f2329;
}

.check-line {
  display: flex;
  align-items: center;
  margin-top: 24rpx;
}

.check-text {
  margin-left: 12rpx;
  font-size: 28rpx;
  color: #1f2329;
}

.link-text {
  color: #2d6cdf;
}

.modal-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 36rpx;
}

.ghost-button,
.primary-button {
  flex: 1;
  border-radius: 999rpx;
}

.ghost-button {
  background: #f2f3f5;
  color: #1f2329;
}

.primary-button {
  background: #2d6cdf;
  color: #fff;
}
</style>
