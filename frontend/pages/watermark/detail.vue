<template>
  <view class="page">
    <view v-if="loading" class="status-card">正在加载</view>
    <view v-else-if="errorText" class="status-card">
      <view>{{ errorText }}</view>
      <button class="primary" @click="load">重新加载</button>
    </view>
    <template v-else-if="photo">
      <view class="photo-stage">
        <swiper
          class="photo-swiper"
          :current="currentIndex"
          :duration="220"
          @change="onSwiperChange"
        >
          <swiper-item v-for="(item, index) in photos" :key="item.id" class="photo-slide">
            <image
              v-if="Math.abs(index - currentIndex) <= 2 && (item.displayUrl || item.previewUrl)"
              class="photo"
              :src="item.displayUrl || item.previewUrl"
              mode="aspectFit"
              @click="showWatermarked"
            />
          </swiper-item>
        </swiper>
        <view v-if="photos.length > 1" class="pager-badge">{{ currentIndex + 1 }} / {{ totalCount }}</view>
      </view>
      <view class="card">
        <view class="title">{{ photo.projectNameSnapshot }}</view>
        <view class="meta">拍摄人：{{ photo.photographerNickName || '拍摄人' }}</view>
        <view class="meta">备注内容：{{ photo.note || '（空记录）' }}</view>
        <view class="meta">拍摄时间：{{ clock }}</view>
        <view class="meta">地点：{{ photo.locationText || '未获取到地点' }}</view>
        <view class="meta">天气：{{ weatherDisplay }}</view>
        <view v-if="photo.weatherObservedAt" class="meta">天气观测时间：{{ photo.weatherObservedAt }}</view>
        <view v-if="archived" class="readonly">项目已归档，只能查看</view>
        <view class="actions">
          <button class="ghost" @click="showOriginal">查看原图</button>
          <button class="ghost" @click="showWatermarked">查看带水印图片</button>
        </view>
        <button v-if="canDelete" class="danger-btn" :loading="deleting" @click="confirmDelete">删除照片</button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getUserProfile } from '../../utils/auth'
import { toDisplayImageSrc } from '../../utils/http-image'
import { apiJson, downloadAuthorized } from '../../utils/project-api'
import { messageFor } from '../../utils/photo-payload'
import { formatClock } from '../../utils/watermark-layout'

const PAGE_SIZE = 50
const projectId = ref(0)
const photoId = ref(0)
const project = ref(null)
const photos = ref([])
const currentIndex = ref(0)
const photoPage = ref(1)
const hasMore = ref(false)
const totalPhotos = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const deleting = ref(false)
const errorText = ref('')
const detailFetched = new Set()

const photo = computed(() => photos.value[currentIndex.value] || null)
const totalCount = computed(() => Math.max(photos.value.length, totalPhotos.value || 0))
const archived = computed(() => project.value?.status === 'archived')
const clock = computed(() => formatClock(photo.value?.takenAt))
const weatherDisplay = computed(() => {
  if (photo.value?.weatherStatus === 'ok' && photo.value?.weatherText) {
    return photo.value.temperatureC == null || photo.value.temperatureC === ''
      ? photo.value.weatherText
      : `${photo.value.weatherText} ${photo.value.temperatureC}℃`
  }
  return '未获取到天气'
})
const canDelete = computed(() => {
  if (archived.value || !photo.value) return false
  if (project.value?.role === 'owner') return true
  const profile = getUserProfile()
  return Boolean(profile?.userId && Number(profile.userId) === Number(photo.value.photographerUserId))
})

const ensureDisplayUrls = async (centerIndex) => {
  const start = Math.max(0, centerIndex - 1)
  const end = Math.min(photos.value.length - 1, centerIndex + 1)
  const tasks = []
  for (let i = start; i <= end; i += 1) {
    const item = photos.value[i]
    if (!item || item.displayUrl || !item.previewUrl) continue
    tasks.push((async () => {
      const resolved = await toDisplayImageSrc(item.previewUrl)
      if (resolved) {
        photos.value[i] = { ...photos.value[i], displayUrl: resolved }
      }
    })())
  }
  if (tasks.length) await Promise.all(tasks)
}

const fetchPhotoDetail = async (id) => {
  if (!id || detailFetched.has(id)) return
  try {
    const detail = await apiJson(`/api/project/${projectId.value}/photos/${id}`)
    detailFetched.add(id)
    const idx = photos.value.findIndex((item) => Number(item.id) === Number(id))
    if (idx >= 0) {
      const existing = photos.value[idx]
      const displayUrl = existing.displayUrl || await toDisplayImageSrc(detail.previewUrl) || detail.previewUrl || ''
      photos.value[idx] = { ...existing, ...detail, displayUrl }
    }
  } catch (error) {
    // 列表已有基础信息，详情补拉失败不打断滑动
  }
}

const loadNextPage = async () => {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const nextPage = photoPage.value + 1
    const body = await apiJson(`/api/project/${projectId.value}/photos?page=${nextPage}&pageSize=${PAGE_SIZE}`)
    const incoming = Array.isArray(body.items) ? body.items : []
    const existingIds = new Set(photos.value.map((item) => Number(item.id)))
    const appended = incoming.filter((item) => !existingIds.has(Number(item.id)))
    if (appended.length) {
      photos.value = photos.value.concat(appended)
    }
    photoPage.value = nextPage
    hasMore.value = Boolean(body.hasMore) && incoming.length > 0
    if (typeof body.total === 'number') totalPhotos.value = body.total
  } catch (error) {
    // 忽略翻页错误
  } finally {
    loadingMore.value = false
  }
}

const load = async () => {
  // 详情只展示已经保存的地点和天气，不重新定位，也不调用预览接口。
  loading.value = true
  errorText.value = ''
  detailFetched.clear()
  try {
    const [projectDetail, currentDetail, listBody] = await Promise.all([
      apiJson(`/api/project/${projectId.value}`),
      apiJson(`/api/project/${projectId.value}/photos/${photoId.value}`),
      apiJson(`/api/project/${projectId.value}/photos?page=1&pageSize=${PAGE_SIZE}`).catch(() => null)
    ])
    project.value = projectDetail
    detailFetched.add(Number(currentDetail.id))
    const currentDisplay = await toDisplayImageSrc(currentDetail.previewUrl) || currentDetail.previewUrl || ''
    const mergedCurrent = { ...currentDetail, displayUrl: currentDisplay }

    let items = Array.isArray(listBody?.items) ? listBody.items.slice() : []
    photoPage.value = 1
    hasMore.value = Boolean(listBody?.hasMore)
    totalPhotos.value = typeof listBody?.total === 'number' ? listBody.total : items.length

    let idx = items.findIndex((item) => Number(item.id) === Number(photoId.value))
    // 若目标照片在更后面的分页，继续往后找几页以支持无缝滑动
    let guard = 0
    while (idx < 0 && hasMore.value && guard < 4) {
      guard += 1
      const nextPage = photoPage.value + 1
      const more = await apiJson(`/api/project/${projectId.value}/photos?page=${nextPage}&pageSize=${PAGE_SIZE}`).catch(() => null)
      const incoming = Array.isArray(more?.items) ? more.items : []
      if (!incoming.length) {
        hasMore.value = false
        break
      }
      items = items.concat(incoming)
      photoPage.value = nextPage
      hasMore.value = Boolean(more?.hasMore)
      if (typeof more?.total === 'number') totalPhotos.value = more.total
      idx = items.findIndex((item) => Number(item.id) === Number(photoId.value))
    }

    if (idx >= 0) {
      items[idx] = { ...items[idx], ...mergedCurrent }
      photos.value = items
      currentIndex.value = idx
    } else {
      photos.value = [mergedCurrent, ...items]
      currentIndex.value = 0
    }
    ensureDisplayUrls(currentIndex.value)
  } catch (error) {
    errorText.value = messageFor(error, '照片加载失败')
  } finally {
    loading.value = false
  }
}

const onSwiperChange = (event) => {
  const next = Number(event?.detail?.current)
  if (!Number.isFinite(next) || next < 0 || next >= photos.value.length) return
  currentIndex.value = next
  const active = photos.value[next]
  if (active) {
    photoId.value = Number(active.id)
    ensureDisplayUrls(next)
    fetchPhotoDetail(active.id)
  }
  if (hasMore.value && next >= photos.value.length - 3) {
    loadNextPage()
  }
}

const showOriginal = async () => {
  if (!photo.value) return
  try {
    const body = await apiJson(`/api/project/${projectId.value}/photos/${photo.value.id}/original`)
    uni.previewImage({ urls: [body.url] })
  } catch (error) {
    uni.showToast({ title: messageFor(error), icon: 'none' })
  }
}

const showWatermarked = async () => {
  if (!photo.value) return
  try {
    const path = await downloadAuthorized(`/api/project/${projectId.value}/photos/${photo.value.id}/watermarked`)
    uni.previewImage({ urls: [path] })
  } catch (error) {
    uni.showToast({ title: messageFor(error, '带水印图片生成失败'), icon: 'none' })
  }
}

const confirmDelete = () => {
  if (!canDelete.value || deleting.value || !photo.value) return
  const targetId = photo.value.id
  uni.showModal({
    title: '删除照片',
    content: '确定从当前项目中删除这张照片吗？删除后无法恢复。',
    confirmColor: '#DC2626',
    success: async (res) => {
      if (!res.confirm) return
      deleting.value = true
      try {
        await apiJson(`/api/project/${projectId.value}/photos/${targetId}`, { method: 'DELETE' })
        uni.showToast({ title: '已删除', icon: 'success' })
        const remaining = photos.value.filter((item) => Number(item.id) !== Number(targetId))
        totalPhotos.value = Math.max(0, totalPhotos.value - 1)
        if (!remaining.length) {
          setTimeout(() => {
            uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/watermark/home' }) })
          }, 300)
          return
        }
        const nextIndex = Math.min(currentIndex.value, remaining.length - 1)
        photos.value = remaining
        currentIndex.value = nextIndex
        photoId.value = Number(remaining[nextIndex].id)
        ensureDisplayUrls(nextIndex)
        fetchPhotoDetail(remaining[nextIndex].id)
      } catch (error) {
        uni.showToast({ title: messageFor(error, '删除失败'), icon: 'none' })
      } finally {
        deleting.value = false
      }
    }
  })
}

onLoad((query) => {
  projectId.value = Number(query.projectId) || 0
  photoId.value = Number(query.photoId) || 0
  load()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #F8F7FC;
  overflow: hidden;
  box-sizing: border-box;
}
.status-card {
  margin: 24rpx 32rpx;
  padding: 28rpx;
  border-radius: 28rpx;
  background: #fff;
}
.photo-stage {
  position: relative;
  flex: 1;
  min-height: 240rpx;
  width: 100%;
  background: #111;
  overflow: hidden;
}
.photo-swiper {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
.photo-slide {
  position: relative;
  width: 100%;
  height: 100%;
}
.photo {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
.pager-badge {
  position: absolute;
  right: 24rpx;
  top: 20rpx;
  z-index: 2;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.52);
  color: #fff;
  font-size: 22rpx;
  line-height: 1.4;
}
.card {
  flex-shrink: 0;
  margin: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  padding: 20rpx 24rpx;
  border-radius: 24rpx;
  background: #fff;
  box-sizing: border-box;
}
.title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1A1D23;
  line-height: 1.3;
}
.meta, .readonly {
  margin-top: 6rpx;
  font-size: 24rpx;
  line-height: 1.45;
  color: #64748b;
  word-break: break-all;
}
.readonly {
  color: #9a6700;
}
.actions {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}
.primary, .ghost, .danger-btn {
  border-radius: 999rpx;
  margin: 0;
}
.primary {
  margin-top: 16rpx;
  height: 76rpx;
  line-height: 76rpx;
  font-size: 26rpx;
  background: #7965D8;
  color: #fff;
}
.ghost {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 26rpx;
  background: #F3F0FF;
  color: #7965D8;
}
.danger-btn {
  width: 100%;
  margin-top: 12rpx;
  height: 72rpx;
  line-height: 72rpx;
  background: #FEF2F2;
  color: #DC2626;
  font-size: 26rpx;
}
.primary::after, .ghost::after, .danger-btn::after {
  border: none;
}
</style>
