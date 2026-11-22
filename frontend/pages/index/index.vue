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
        <view class="hero-text">看到喜欢的图先收藏，不用存进相册，也不用再翻聊天记录和浏览器历史。</view>
      </view>

      <view class="hero-user" @click="handleUserAreaClick">
        <image class="avatar" :src="displayAvatar" mode="aspectFill" />
        <view class="user-meta">
          <view class="user-name">{{ displayNickname }}</view>
          <view class="user-tip">
            {{ isLoggedIn ? '资料已准备好，现在可以开始整理你的收藏。' : '完成登录后，可在插件和小程序之间同步收藏。' }}
          </view>
        </view>
      </view>

      <view class="hero-actions">
        <button class="hero-collect-button" @click="goCollect">
          <view class="hero-plus">
            <view class="hero-plus-bar hero-plus-h"></view>
            <view class="hero-plus-bar hero-plus-v"></view>
          </view>
          <text>立即收藏</text>
        </button>
      </view>
    </view>

    <view class="stats-card">
      <view class="stats-head">
        <view>
          <view class="block-title">收藏概览</view>
          <view class="block-tip">这里会展示你最近的收藏情况。</view>
        </view>
        <view class="recent-pill">
          <text>最近新增</text>
          <text v-if="showRecentDate" class="recent-date">{{ stats.lastUpload }}</text>
        </view>
      </view>
      <view class="stats-grid">
        <view class="stat-tile">
          <view class="stat-value">{{ stats.total }}</view>
          <view class="stat-label">
            <view class="mini-image-icon">
              <view class="mini-image-dot"></view>
              <view class="mini-image-hill"></view>
            </view>
            <text>图片数</text>
          </view>
        </view>
        <view class="stat-tile stat-tile-link" @click="goTagManager">
          <view class="stat-value stat-value-brand">{{ stats.tags }}</view>
          <view class="stat-label stat-label-brand">
            <view class="mini-tag-icon"></view>
            <text>标签数</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="isLoggedIn" class="quick-card">
      <view class="block-title">标签筛选</view>
      <view class="block-tip">点击标签，可以快速查看对应分类的图片。</view>
      <view class="tag-search-wrap">
        <view class="tag-search-icon">
          <view class="tag-search-circle"></view>
          <view class="tag-search-handle"></view>
        </view>
        <input v-model="tagKeyword" class="tag-search" placeholder="搜索标签" />
      </view>
      <view class="filter" :class="{ collapsed: shouldCollapseTags }" :style="filterStyle">
        <view
          v-for="item in filteredTagList"
          :key="item.value"
          class="filter-item"
          :class="{ active: currentTag === item.value }"
          @click="filterByTag(item)"
        >
          {{ item.label }}
        </view>
        <text v-if="showEmptyCustomTags" class="tag-empty-hint">暂无自定义标签</text>
      </view>
      <view v-if="showTagToggle" class="filter-toggle" @click="toggleTagExpand">
        <text>{{ tagsExpanded ? '收起标签' : '展开全部' }}</text>
        <text class="filter-toggle-arrow" :class="{ expanded: tagsExpanded }">^</text>
      </view>
    </view>

    <view v-if="isLoggedIn" class="quick-card continue-card">
      <view class="continue-copy">
        <view class="block-title">继续整理</view>
        <view class="block-tip continue-tip">继续添加图片，或者给已有图片补充标签。</view>
      </view>
      <button class="continue-button" @click="goCollect">
        <text>打开收藏页</text>
        <view class="continue-arrow"></view>
      </button>
    </view>

    <view v-if="!isLoggedIn" class="status-box empty-state">
      <view class="empty-title">还没有收藏图片</view>
      <view class="empty-desc">开始收藏后，图片会自动出现在这里。</view>
      <button class="collect-button" @click="handleStartCollect">开始收藏</button>
    </view>

    <view v-if="isLoggedIn && pageState === 'error'" class="status-box">
      <view class="status-title">加载失败</view>
      <view class="status-text">暂时无法获取最新内容，请稍后重试。</view>
      <button size="mini" @click="reloadPage">重新加载</button>
    </view>

    <view v-else-if="isLoggedIn && pageState === 'empty'" class="status-box empty-state">
      <view class="empty-title">还没有收藏图片</view>
      <view class="empty-desc">开始收藏后，图片会自动出现在这里。</view>
      <button class="collect-button" @click="handleStartCollect">开始收藏</button>
    </view>

    <view v-else-if="isLoggedIn" class="gallery-section">
      <view class="gallery-header">
        <view class="section-title">最近收藏</view>
        <view class="section-tip">按最近收藏时间排序。</view>
      </view>

      <view class="container">
        <view class="column">
          <view v-for="item in leftList" :key="item.id" class="card" @click="goDetail(item)">
            <image class="gallery-image" :src="resolveImageUrl(item.localUrl)" mode="widthFix" @error="handleImageError(item)" />
            <view class="card-meta">
              <view v-if="item.tags?.length" class="card-tags">{{ item.tags.join(' / ') }}</view>
              <view class="card-time">{{ formatRecentTime(item.createTime) }}</view>
            </view>
            <view v-if="failedImageIds.includes(item.id)" class="image-error">图片加载失败</view>
          </view>
        </view>

        <view class="column">
          <view v-for="item in rightList" :key="item.id" class="card" @click="goDetail(item)">
            <image class="gallery-image" :src="resolveImageUrl(item.localUrl)" mode="widthFix" @error="handleImageError(item)" />
            <view class="card-meta">
              <view v-if="item.tags?.length" class="card-tags">{{ item.tags.join(' / ') }}</view>
              <view class="card-time">{{ formatRecentTime(item.createTime) }}</view>
            </view>
            <view v-if="failedImageIds.includes(item.id)" class="image-error">图片加载失败</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="showBackTop" class="back-top" @click="scrollToTop">
      <view class="back-top-arrow"></view>
    </view>

    <view v-if="showLoginForm" class="login-mask">
      <view class="login-card">
        <view class="login-title">登录后继续收藏</view>
        <view class="login-text">收藏图片前，请先设置头像和昵称。不登录也可以继续浏览。</view>
        <button class="avatar-picker" open-type="chooseAvatar" @chooseavatar="handleChooseAvatar">
          <image class="avatar-preview" :src="displayAvatar" mode="aspectFill" />
          <text class="avatar-picker-text">选择头像</text>
        </button>
        <input
          class="nickname-input"
          type="nickname"
          placeholder="请输入昵称"
          :value="pendingNickname"
          @input="handleNicknameInput"
        />
        <button class="primary-button" :loading="authLoading" @click="handleProfileConfirm">保存并继续</button>
        <button class="ghost-button login-later" @click="dismissAuthPrompt">先看看</button>
      </view>
    </view>

    <view v-if="showPrivacyModal" class="modal-mask">
      <view class="modal-card">
        <view class="modal-title">收藏前请确认</view>
        <view class="modal-desc">请先阅读并同意以下协议，再继续收藏。不登录也可以先浏览。</view>

        <label class="check-line" @click="toggleAgreement('user')">
          <checkbox :checked="userAgreementChecked" color="#7965D8" />
          <view class="check-text">同意 <text class="link-text" @click.stop="openAgreement('user')">《用户协议》</text></view>
        </label>

        <label class="check-line" @click="toggleAgreement('privacy')">
          <checkbox :checked="privacyPolicyChecked" color="#7965D8" />
          <view class="check-text">同意 <text class="link-text" @click.stop="openAgreement('privacy')">《隐私政策》</text></view>
        </label>

        <view class="modal-actions">
          <button class="ghost-button" @click="handleReject">取消</button>
          <button class="primary-button" :loading="authLoading" @click="handleAgreeAndContinue">继续</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { onHide, onLoad, onPageScroll, onShow } from '@dcloudio/uni-app'
import baseUrl from '../../utils/base-url'
import resolveImageUrl from '../../utils/image-url'
import { request, uploadFile } from '../../utils/request'
import {
  clearPluginLoginToken,
  clearSessionToken,
  getPluginLoginToken,
  getPrivacyConsent,
  getSessionToken,
  getUserProfile,
  setPluginLoginToken,
  setPrivacyConsent,
  setSessionToken,
  setUserProfile
} from '../../utils/auth'

const REFRESH_KEY = 'image_list_should_refresh'
const AUTO_REFRESH_INTERVAL = 8000

const tagList = ref([{ label: '全部', value: '' }])
const leftList = ref([])
const rightList = ref([])
const failedImageIds = ref([])
const currentTag = ref('')
const pageState = ref('loading')
const hasConsented = ref(false)
const authPromptVisible = ref(false)
const pendingAction = ref('')
const authLoading = ref(false)
const userAgreementChecked = ref(false)
const privacyPolicyChecked = ref(false)
const userProfile = ref(null)
const pendingAvatarUrl = ref('')
const pendingNickname = ref('')
const pendingPluginLoginToken = ref('')
const showBackTop = ref(false)
const tagKeyword = ref('')
const tagsExpanded = ref(false)
const tagOverflow = ref(false)
const collapsedTagHeight = ref(0)

const defaultAvatar = '/static/logo.png'
const defaultNickname = '微信用户'
let autoRefreshTimer = null

const isHttpUrl = (value) => /^https?:\/\//i.test((value || '').trim())

const stats = ref({ total: 0, tags: 0, lastUpload: '' })

const isLoggedIn = computed(() => Boolean(userProfile.value?.nickName && userProfile.value?.avatarUrl))
const profileCompleted = computed(() => Boolean(userProfile.value?.nickName && isHttpUrl(userProfile.value?.avatarUrl) && userProfile.value.nickName !== defaultNickname))
const authRequired = computed(() => !(profileCompleted.value && getSessionToken()))
const showPrivacyModal = computed(() => authPromptVisible.value && !hasConsented.value)
const showLoginForm = computed(() => authPromptVisible.value && hasConsented.value && authRequired.value)
const displayAvatar = computed(() => pendingAvatarUrl.value || (isHttpUrl(userProfile.value?.avatarUrl) ? userProfile.value.avatarUrl : defaultAvatar))
const displayNickname = computed(() => pendingNickname.value || userProfile.value?.nickName || '未登录')
const filteredTagList = computed(() => {
  const keyword = tagKeyword.value.trim().toLowerCase()
  if (!keyword) return tagList.value
  return tagList.value.filter((item) => item.value === '' || item.label.toLowerCase().includes(keyword))
})
const shouldCollapseTags = computed(() => !tagKeyword.value.trim() && !tagsExpanded.value && tagOverflow.value)
const showTagToggle = computed(() => !tagKeyword.value.trim() && tagOverflow.value)
const showRecentDate = computed(() => isLoggedIn.value && stats.value.total > 0 && stats.value.lastUpload && stats.value.lastUpload !== '--')
const showEmptyCustomTags = computed(() => tagList.value.length <= 1)
const filterStyle = computed(() => (!shouldCollapseTags.value || !collapsedTagHeight.value ? {} : { maxHeight: `${collapsedTagHeight.value}px` }))

const formatRecentTime = (value) => {
  if (!value) return '--'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  const month = `${parsed.getMonth() + 1}`.padStart(2, '0')
  const day = `${parsed.getDate()}`.padStart(2, '0')
  return `${month}-${day}`
}

const currentItems = () => {
  const items = []
  const count = Math.max(leftList.value.length, rightList.value.length)
  for (let index = 0; index < count; index += 1) {
    if (leftList.value[index]) items.push(leftList.value[index])
    if (rightList.value[index]) items.push(rightList.value[index])
  }
  return items
}

const splitList = (data = [], { preserveImages = false } = {}) => {
  const previous = new Map(currentItems().map((item) => [item.id, item.localUrl]))
  const next = preserveImages
    ? data.map((item) => ({ ...item, localUrl: previous.get(item.id) || item.localUrl }))
    : data
  const left = []
  const right = []
  next.forEach((item, index) => {
    if (index % 2 === 0) left.push(item)
    else right.push(item)
  })
  leftList.value = left
  rightList.value = right
  if (!preserveImages) failedImageIds.value = []
  pageState.value = next.length ? 'ready' : 'empty'
}

const requestList = async (tagValue = '', { silent = false } = {}) => {
  if (!isLoggedIn.value) return
  currentTag.value = tagValue
  if (!silent) {
    pageState.value = 'loading'
    uni.showLoading({ title: '加载中...' })
  }
  try {
    const res = await request({ url: `${baseUrl}/api/image/list`, data: tagValue ? { tag: tagValue } : {} })
    if (res.statusCode !== 200) throw new Error(`request failed: ${res.statusCode}`)
    const list = Array.isArray(res.data) ? res.data : []
    splitList(list, { preserveImages: silent })
    stats.value.total = list.length
    stats.value.lastUpload = formatRecentTime(list[0]?.createTime)
  } catch (error) {
    if (!silent) {
      splitList([])
      pageState.value = 'error'
      uni.showToast({ title: '加载失败', icon: 'none' })
    }
  } finally {
    if (!silent) uni.hideLoading()
  }
}

const requestTags = async () => {
  if (!isLoggedIn.value) return
  try {
    const res = await request({ url: `${baseUrl}/api/image/tags` })
    if (res.statusCode !== 200 || !Array.isArray(res.data)) return
    stats.value.tags = res.data.length
    tagList.value = [{ label: '全部', value: '' }, ...res.data.map((tag) => ({ label: tag, value: tag }))]
    tagsExpanded.value = false
    if (currentTag.value && !tagList.value.some((item) => item.value === currentTag.value)) currentTag.value = ''
    await measureTagOverflow()
  } catch (error) {
    console.error('requestTags failed', error)
  }
}

const loadPageData = async ({ silent = false } = {}) => {
  await requestTags()
  await requestList(currentTag.value, { silent })
}

const reloadPage = () => loadPageData()
const filterByTag = (tag) => requestList(tag.value)
const toggleTagExpand = () => { tagsExpanded.value = !tagsExpanded.value }

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
    collapsedTagHeight.value = firstRowHeight
    tagOverflow.value = rects.some((item) => Math.abs(item.top - firstTop) > 1)
  })
}

const handleImageError = (item) => {
  if (!failedImageIds.value.includes(item.id)) failedImageIds.value.push(item.id)
}

const scrollToTop = () => {
  uni.pageScrollTo({ scrollTop: 0, duration: 300 })
}

onPageScroll((event) => {
  showBackTop.value = event.scrollTop > 480
})

const openAuthPrompt = (action) => {
  pendingAction.value = action
  authPromptVisible.value = true
}

const dismissAuthPrompt = () => {
  authPromptVisible.value = false
  pendingAction.value = ''
}

const continuePendingAction = () => {
  const action = pendingAction.value
  pendingAction.value = ''
  authPromptVisible.value = false
  if (action === 'collect') {
    uni.navigateTo({ url: '/pages/collect/collect' })
    return
  }
  if (action === 'tags') uni.navigateTo({ url: '/pages/collect/collect?manageTags=1' })
}

const handleUserAreaClick = () => {
  if (isLoggedIn.value && !authRequired.value) return
  openAuthPrompt('login')
}

const handleStartCollect = () => {
  goCollect()
}

const goCollect = () => {
  if (!isLoggedIn.value || authRequired.value) {
    openAuthPrompt('collect')
    return
  }
  uni.navigateTo({ url: '/pages/collect/collect' })
}

const goTagManager = () => {
  if (!authRequired.value) {
    uni.navigateTo({ url: '/pages/collect/collect?manageTags=1' })
    return
  }
  openAuthPrompt('tags')
}
const goDetail = (item) => uni.navigateTo({ url: `/pages/detail/detail?id=${item.id}` })
const openAgreement = (type) => uni.navigateTo({ url: `/pages/agreement/detail?type=${type}` })

const toggleAgreement = (type) => {
  if (type === 'user') {
    userAgreementChecked.value = !userAgreementChecked.value
    return
  }
  privacyPolicyChecked.value = !privacyPolicyChecked.value
}

const handleReject = () => {
  dismissAuthPrompt()
}

const requestLoginCode = () => new Promise((resolve, reject) => {
  uni.login({ provider: 'weixin', success: resolve, fail: reject })
})

const requestWechatPrivacyAuthorize = () => new Promise((resolve, reject) => {
  // #ifdef MP-WEIXIN
  if (typeof wx?.requirePrivacyAuthorize === 'function') {
    wx.requirePrivacyAuthorize({ success: resolve, fail: reject })
    return
  }
  // #endif
  resolve()
})

const uploadMiniappAvatar = (filePath) => new Promise((resolve, reject) => {
  uploadFile({
    url: `${baseUrl}/api/auth/miniapp/avatar`,
    auth: false,
    filePath,
    name: 'file',
    success(res) {
      let payload = res.data
      if (typeof payload === 'string') {
        try {
          payload = JSON.parse(payload)
        } catch (error) {
          reject(error)
          return
        }
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

const normalizeProfile = (profile) => {
  const normalizedProfile = {
    userId: profile?.userId || userProfile.value?.userId || null,
    nickName: profile?.nickName || defaultNickname,
    avatarUrl: isHttpUrl(profile?.avatarUrl) ? profile.avatarUrl : defaultAvatar
  }
  userProfile.value = normalizedProfile
  setUserProfile(normalizedProfile)
  setPrivacyConsent(true)
  hasConsented.value = true
}

const handleChooseAvatar = (event) => { pendingAvatarUrl.value = event?.detail?.avatarUrl || '' }
const handleNicknameInput = (event) => { pendingNickname.value = event?.detail?.value?.trim?.() || '' }

const getErrorMessage = (error, fallback = '操作失败') => error?.message || error?.errMsg || error?.data?.message || fallback

const resolveAvatarForLogin = async () => {
  const avatarUrl = pendingAvatarUrl.value.trim()
  if (!avatarUrl) return ''
  if (/^https?:\/\//i.test(avatarUrl)) return avatarUrl
  return uploadMiniappAvatar(avatarUrl)
}

const runLoginFlow = async () => {
  authLoading.value = true
  try {
    const uploadedAvatarUrl = await resolveAvatarForLogin()
    const loginRes = await requestLoginCode()
    if (!loginRes?.code) throw new Error('login code missing')
    const authRes = await request({
      url: `${baseUrl}/api/auth/miniapp/login`,
      method: 'POST',
      auth: false,
      data: { code: loginRes.code, nickName: pendingNickname.value, avatarUrl: uploadedAvatarUrl }
    })
    if (authRes.statusCode !== 200) throw new Error(authRes.data?.message || `login failed: ${authRes.statusCode}`)
    if (!authRes.data?.sessionToken) throw new Error(authRes.data?.message || 'session token missing')
    setSessionToken(authRes.data.sessionToken)
    normalizeProfile({
      userId: authRes.data.userId,
      nickName: authRes.data.nickName || pendingNickname.value,
      avatarUrl: authRes.data.avatarUrl || uploadedAvatarUrl
    })
    uni.showToast({ title: '登录成功', icon: 'success' })
    await loadPageData()
    await tryConfirmPendingPluginLogin()
    continuePendingAction()
  } catch (error) {
    console.error('runLoginFlow failed', error)
    clearSessionToken()
    uni.showToast({ title: getErrorMessage(error), icon: 'none' })
  } finally {
    authLoading.value = false
  }
}

const handleAgreeAndContinue = async () => {
  if (!hasConsented.value && (!userAgreementChecked.value || !privacyPolicyChecked.value)) {
    uni.showToast({ title: '请先同意两个协议', icon: 'none' })
    return
  }
  try {
    await requestWechatPrivacyAuthorize()
  } catch (error) {
    uni.showToast({ title: '需要先完成隐私授权', icon: 'none' })
    return
  }
  setPrivacyConsent(true)
  hasConsented.value = true
}

const handleProfileConfirm = async () => {
  if (!pendingAvatarUrl.value) {
    uni.showToast({ title: '请先选择头像', icon: 'none' })
    return
  }
  if (!pendingNickname.value) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }
  await runLoginFlow()
}

const confirmPluginLogin = async (token) => {
  if (!token || !profileCompleted.value) return false
  const confirmRes = await request({ url: `${baseUrl}/api/plugin/login/confirm`, method: 'POST', data: { token } })
  if (confirmRes.statusCode !== 200 || confirmRes.data?.status !== 'confirmed') return false
  clearPluginLoginToken()
  pendingPluginLoginToken.value = ''
  uni.showToast({ title: '桌面端已成功登录', icon: 'success' })
  return true
}

const consumePluginLoginOptions = (options = {}) => {
  const rawScene = options?.scene ? decodeURIComponent(options.scene) : ''
  const token = rawScene || options?.token || ''
  if (!token) return
  pendingPluginLoginToken.value = token
  setPluginLoginToken(token)
}

const tryConfirmPendingPluginLogin = async () => {
  const token = pendingPluginLoginToken.value || getPluginLoginToken()
  if (!token || !profileCompleted.value) return
  const confirmed = await confirmPluginLogin(token)
  if (!confirmed) uni.showToast({ title: '插件登录确认失败', icon: 'none' })
}

const refreshStoredProfile = async () => {
  if (!getSessionToken()) return
  try {
    const res = await request({ url: `${baseUrl}/api/auth/me` })
    if (res.statusCode !== 200 || !isHttpUrl(res.data?.avatarUrl)) return
    const nextProfile = {
      userId: res.data.userId || userProfile.value?.userId || null,
      nickName: res.data.nickName || userProfile.value?.nickName || defaultNickname,
      avatarUrl: res.data.avatarUrl
    }
    userProfile.value = nextProfile
    setUserProfile(nextProfile)
  } catch (error) {
    console.error('refreshStoredProfile failed', error)
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (!profileCompleted.value || !getSessionToken()) return
  autoRefreshTimer = setInterval(() => { loadPageData({ silent: true }) }, AUTO_REFRESH_INTERVAL)
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

onLoad((options) => {
  consumePluginLoginOptions(options)
  hasConsented.value = getPrivacyConsent()
  userProfile.value = getUserProfile()
  pendingAvatarUrl.value = isHttpUrl(userProfile.value?.avatarUrl) ? userProfile.value.avatarUrl : ''
  pendingNickname.value = userProfile.value?.nickName && userProfile.value.nickName !== defaultNickname ? userProfile.value.nickName : ''
  if (hasConsented.value && profileCompleted.value && getSessionToken()) loadPageData({ silent: true })
  const openedForLogin = Boolean(options?.scene || options?.token)
  if (openedForLogin && authRequired.value) openAuthPrompt('plugin')
  tryConfirmPendingPluginLogin()
})

onShow(async () => {
  userProfile.value = getUserProfile()
  pendingPluginLoginToken.value = getPluginLoginToken() || pendingPluginLoginToken.value
  if (getSessionToken()) await refreshStoredProfile()
  pendingAvatarUrl.value = isHttpUrl(userProfile.value?.avatarUrl) ? userProfile.value.avatarUrl : pendingAvatarUrl.value
  pendingNickname.value = userProfile.value?.nickName && userProfile.value.nickName !== defaultNickname ? userProfile.value.nickName : pendingNickname.value
  if (!profileCompleted.value || !getSessionToken()) {
    stopAutoRefresh()
    return
  }
  if (uni.getStorageSync(REFRESH_KEY)) uni.removeStorageSync(REFRESH_KEY)
  loadPageData({ silent: true })
  startAutoRefresh()
  tryConfirmPendingPluginLogin()
})

onHide(() => { stopAutoRefresh() })
watch(filteredTagList, () => { measureTagOverflow() }, { deep: true })
watch(tagKeyword, () => {
  tagsExpanded.value = Boolean(tagKeyword.value.trim())
  measureTagOverflow()
})
</script>

<style>
.page { min-height: 100vh; background: #F8F7FC; padding-bottom: 32rpx; }
.topbar { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 32rpx 0; }
.topbar-title { font-size: 48rpx; font-weight: 700; color: #0f172a; letter-spacing: -0.5rpx; }
.topbar-subtitle { margin-top: 8rpx; font-size: 26rpx; color: #64748b; }
.card, .login-card, .modal-card { border-radius: 24rpx; box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06); }
.hero-card, .stats-card, .quick-card { border-radius: 48rpx; border: 1rpx solid rgba(15, 23, 42, 0.08); box-shadow: 0 8rpx 40rpx rgba(15, 23, 42, 0.04); }
.hero-card { margin: 16rpx 32rpx; padding: 40rpx; background: linear-gradient(180deg, #F3F0FF 0%, #ffffff 58%, #ffffff 100%); }
.hero-copy { margin-bottom: 8rpx; }
.hero-eyebrow { font-size: 22rpx; font-weight: 400; letter-spacing: 2rpx; color: #7965D8; }
.hero-title { margin-top: 8rpx; font-size: 38rpx; line-height: 1.35; font-weight: 700; color: #0f172a; }
.hero-text { margin-top: 8rpx; font-size: 26rpx; line-height: 1.7; color: #64748b; }
.hero-user { display: flex; align-items: center; margin-top: 16rpx; padding: 24rpx 24rpx 24rpx 0; border-radius: 32rpx; background: rgba(248, 247, 252, 0.8); border: 1rpx solid #EAE7F2; }
.avatar { width: 88rpx; height: 88rpx; border-radius: 50%; background: #EEE9FF; border: 4rpx solid #ffffff; box-shadow: 0 4rpx 12rpx rgba(15, 23, 42, 0.06); }
.user-meta { flex: 1; min-width: 0; margin-left: 28rpx; }
.user-name { font-size: 28rpx; font-weight: 600; color: #0f172a; }
.user-tip { margin-top: 6rpx; font-size: 20rpx; line-height: 1.5; color: #64748b; white-space: nowrap; }
.hero-actions { margin-top: 16rpx; }
.hero-collect-button { display: flex; align-items: center; justify-content: center; gap: 12rpx; height: 96rpx; margin: 0; padding: 0; overflow: hidden; border-radius: 999rpx; background: linear-gradient(90deg, #7965D8 0%, #A18BEF 100%); color: #ffffff; font-size: 30rpx; box-shadow: 0 20rpx 40rpx rgba(121, 101, 216, 0.28); }
.hero-collect-button::after { border: none; }
.hero-plus { position: relative; width: 28rpx; height: 28rpx; }
.hero-plus-bar { position: absolute; left: 50%; top: 50%; background: #ffffff; border-radius: 999rpx; }
.hero-plus-h { width: 28rpx; height: 4rpx; margin-left: -14rpx; margin-top: -2rpx; }
.hero-plus-v { width: 4rpx; height: 28rpx; margin-left: -2rpx; margin-top: -14rpx; }
.stats-card, .quick-card { margin: 0 32rpx 16rpx; padding: 24rpx 40rpx; background: #ffffff; }
.stats-card { padding: 20rpx 32rpx 16rpx; }
.stats-head, .continue-card { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.block-title { font-size: 32rpx; font-weight: 700; color: #0f172a; }
.block-tip { margin-top: 6rpx; font-size: 24rpx; line-height: 1.5; color: #94a3b8; }
.recent-pill { display: flex; align-items: center; gap: 8rpx; flex-shrink: 0; padding: 8rpx 20rpx; border-radius: 999rpx; background: #F8F7FC; border: 1rpx solid #EAE7F2; color: #94a3b8; font-size: 22rpx; }
.recent-date { color: #475569; }
.stats-grid { display: flex; gap: 24rpx; margin-top: 12rpx; }
.stat-tile { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 12rpx; border-radius: 32rpx; background: rgba(248, 247, 252, 0.7); border: 1rpx solid rgba(234, 231, 242, 0.8); }
.stat-tile-link { background: rgba(243, 240, 255, 0.7); border-color: rgba(212, 204, 245, 0.8); }
.stat-value { font-size: 60rpx; font-weight: 800; color: #0f172a; line-height: 1.1; }
.stat-value-brand { color: #7965D8; }
.stat-label, .stat-label-brand { display: flex; align-items: center; gap: 8rpx; margin-top: 8rpx; font-size: 24rpx; color: #64748b; }
.stat-label-brand { color: rgba(29, 78, 216, 0.8); }
.mini-image-icon { position: relative; width: 28rpx; height: 22rpx; border: 2rpx solid #94a3b8; border-radius: 4rpx; }
.mini-image-dot { position: absolute; top: 3rpx; right: 3rpx; width: 5rpx; height: 5rpx; border-radius: 50%; background: #94a3b8; }
.mini-image-hill { position: absolute; left: 3rpx; bottom: 2rpx; width: 14rpx; height: 7rpx; border-top: 2rpx solid #94a3b8; border-radius: 8rpx 8rpx 0 0; }
.mini-tag-icon { width: 22rpx; height: 16rpx; border: 2rpx solid #A18BEF; border-radius: 3rpx 8rpx 8rpx 3rpx; }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
.section-tip { margin-top: 8rpx; font-size: 24rpx; line-height: 1.7; color: #6b7280; }
.tag-search-wrap { position: relative; margin-top: 12rpx; }
.tag-search-icon { position: absolute; left: 24rpx; top: 50%; width: 28rpx; height: 28rpx; margin-top: -14rpx; z-index: 1; }
.tag-search-circle { position: absolute; left: 0; top: 2rpx; width: 16rpx; height: 16rpx; border: 3rpx solid #94a3b8; border-radius: 50%; }
.tag-search-handle { position: absolute; left: 16rpx; top: 16rpx; width: 10rpx; height: 3rpx; background: #94a3b8; border-radius: 999rpx; transform: rotate(45deg); }
.tag-search { min-height: 64rpx; padding: 0 24rpx 0 64rpx; border-radius: 24rpx; border: 1rpx solid rgba(234, 231, 242, 0.9); background: #F8F7FC; font-size: 24rpx; color: #1e293b; }
.filter { display: flex; flex-wrap: wrap; align-items: center; gap: 16rpx; margin-top: 8rpx; overflow: hidden; }
.filter.collapsed { overflow: hidden; }
.filter-item { padding: 8rpx 28rpx; border-radius: 999rpx; border: 1rpx solid #EAE7F2; background: #ffffff; font-size: 24rpx; font-weight: 600; color: #475569; }
.filter-item.active { background: #F3F0FF; color: #7965D8; border-color: #D4CCF5; }
.tag-empty-hint { font-size: 22rpx; font-style: italic; color: #94a3b8; }
.filter-toggle { display: flex; align-items: center; justify-content: center; gap: 8rpx; margin-top: 8rpx; font-size: 24rpx; color: #7965D8; }
.filter-toggle-arrow { font-size: 24rpx; line-height: 1; transform: rotate(0deg); transition: transform 0.2s ease; }
.filter-toggle-arrow.expanded { transform: rotate(180deg); }
.continue-card { padding: 24rpx; }
.continue-copy { flex: 1; min-width: 0; margin-right: 12rpx; }
.continue-tip { white-space: nowrap; }
.continue-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; flex-shrink: 0; height: 64rpx; margin: 0 0 0 auto; padding: 0 20rpx; line-height: 64rpx; border-radius: 999rpx; border: 1rpx solid rgba(212, 204, 245, 0.95); background: rgba(243, 240, 255, 0.9); color: #7965D8; font-size: 24rpx; font-weight: 600; }
.continue-button::after { border: none; }
.continue-arrow { width: 12rpx; height: 12rpx; border-top: 3rpx solid #7965D8; border-right: 3rpx solid #7965D8; transform: rotate(45deg); }
.status-box { margin: 0 32rpx 16rpx; padding: 80rpx 24rpx; text-align: center; color: #666; border-radius: 24rpx; box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06); background: #fff; }
.status-title { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.status-text { margin-top: 12rpx; font-size: 26rpx; }
.empty-title { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.empty-desc { margin-top: 16rpx; font-size: 26rpx; line-height: 1.8; color: #6b7280; }
.gallery-section { margin-top: 0; }
.back-top { position: fixed; right: 32rpx; bottom: 32rpx; z-index: 20; display: flex; align-items: center; justify-content: center; width: 84rpx; height: 84rpx; border-radius: 50%; background: rgba(255, 255, 255, 0.58); border: 1rpx solid rgba(255, 255, 255, 0.72); box-shadow: 0 10rpx 28rpx rgba(41, 38, 59, 0.12); backdrop-filter: blur(18rpx); -webkit-backdrop-filter: blur(18rpx); }
.back-top-arrow { width: 18rpx; height: 18rpx; margin-top: 8rpx; border-top: 4rpx solid rgba(121, 101, 216, 0.92); border-left: 4rpx solid rgba(121, 101, 216, 0.92); transform: rotate(45deg); }
.gallery-header { padding: 0 24rpx 12rpx; }
.container { display: flex; padding: 0 10rpx 20rpx; box-sizing: border-box; }
.column { flex: 1; margin: 0 10rpx; }
.card { margin-bottom: 20rpx; background: #fff; border-radius: 24rpx; }
.card-meta { padding: 16rpx 18rpx 20rpx; }
.card-tags { font-size: 22rpx; color: #7965D8; }
.card-time { margin-top: 8rpx; font-size: 22rpx; color: #86909c; }
.image-error { margin-top: 12rpx; color: #d93025; font-size: 24rpx; text-align: center; }
image { width: 100%; border-radius: 12rpx; }
.gallery-image { display: block; width: 100%; border-radius: 24rpx 24rpx 0 0; }
.modal-mask, .login-mask { position: fixed; inset: 0; z-index: 99; display: flex; align-items: center; justify-content: center; padding: 32rpx; background: rgba(15, 23, 42, 0.45); box-sizing: border-box; }
.login-card, .modal-card { width: 100%; padding: 36rpx 28rpx; background: #fff; box-sizing: border-box; }
button { border-radius: 999rpx; }
.hero-collect-button { border-radius: 999rpx; }
.modal-title, .login-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.modal-desc, .login-text { margin-top: 16rpx; font-size: 28rpx; line-height: 1.7; color: #4e5969; }
.collect-button { margin-top: 24rpx; background: #7965D8; color: #fff; }
.avatar-picker { margin-top: 28rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; background: #F8F7FC; color: #1f2329; border-radius: 24rpx; }
.avatar-preview { width: 96rpx; height: 96rpx; border-radius: 50%; background: #EEE9FF; }
.avatar-picker-text { font-size: 28rpx; }
.nickname-input { margin-top: 20rpx; padding: 22rpx 24rpx; border-radius: 20rpx; background: #F8F7FC; font-size: 28rpx; color: #1f2329; }
.check-line { display: flex; align-items: center; margin-top: 24rpx; }
.check-text { margin-left: 12rpx; font-size: 28rpx; color: #1f2329; }
.link-text { color: #7965D8; }
.modal-actions { display: flex; gap: 20rpx; margin-top: 36rpx; }
.ghost-button, .primary-button { flex: 1; border-radius: 999rpx; }
.login-later { width: 100%; margin-top: 20rpx; }
.ghost-button { background: #f2f3f5; color: #1f2329; }
.primary-button { background: #7965D8; color: #fff; }
.hero-collect-button:active, .collect-button:active, .primary-button:active { background: #6554C0; }
</style>
