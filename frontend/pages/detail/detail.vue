<template>
  <view class="page">
    <view class="container" v-if="imageInfo">
      <view class="image-wrapper" @click="previewImage">
        <image :src="resolveImageUrl(imageInfo.localUrl)" mode="widthFix" />
      </view>

      <view class="info-card">
        <view class="section-title">收藏信息</view>
        <view class="info-list">
          <view class="info-row">
            <text class="info-label">收藏时间</text>
            <text class="info-value">{{ imageInfo.createTime || '暂无' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">来源</text>
            <text class="info-value">{{ imageInfo.url || '本地上传' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">图片地址</text>
            <text class="info-value">{{ imageInfo.localUrl || '暂无' }}</text>
          </view>
        </view>
      </view>

      <view class="tag-card">
        <view class="section-header">
          <view>
            <view class="section-title">当前标签</view>
            <view class="section-tip">这些标签决定你以后能不能快速找到它。</view>
          </view>
          <view class="header-actions">
            <button size="mini" @click="toggleManage">
              {{ managing ? '完成整理' : '管理标签' }}
            </button>
          </view>
        </view>

        <view class="tag-list" v-if="imageInfo.tags && imageInfo.tags.length">
          <view
            v-for="tag in imageInfo.tags"
            :key="tag"
            class="tag"
            @click="managing ? removeTag(tag) : handleTagClick(tag)"
          >
            {{ tag }}<text v-if="managing"> x</text>
          </view>
        </view>
        <view v-else class="empty-tags">还没有标签，先补 1 到 3 个会更容易回找。</view>
      </view>

      <view v-if="managing" class="manage-box">
        <view class="section-title">补充整理</view>
        <view class="section-tip">再加 1 到 3 个标签，后面会更容易回找。</view>

        <view class="tag-input-row">
          <input
            v-model="tagInput"
            class="tag-input"
            placeholder="输入一个新标签"
            @focus="showTagDropdown = true"
            @blur="hideDropdownLater"
            @input="filterTagOptions"
          />
          <button size="mini" @click="addTag">添加</button>
        </view>

        <view class="subheading">推荐补充</view>
        <view v-if="showTagDropdown && filteredTagOptions.length" class="tag-dropdown">
          <view
            v-for="tag in filteredTagOptions"
            :key="tag"
            class="tag-option"
            @mousedown="selectTagOption(tag)"
            @click="selectTagOption(tag)"
          >
            {{ tag }}
          </view>
        </view>
      </view>

      <view class="danger-box">
        <view>
          <view class="section-title">管理操作</view>
          <view class="section-tip">删除后无法恢复，请谨慎操作。</view>
        </view>
        <button class="header-delete-btn" size="mini" @click="confirmDelete">删除这张收藏</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import baseUrl from '../../utils/base-url'
import resolveImageUrl from '../../utils/image-url'
import { request } from '../../utils/request'

const REFRESH_KEY = 'image_list_should_refresh'
const AVAILABLE_TAGS_KEY = 'collect_available_tags'

const imageId = ref(null)
const imageInfo = ref(null)
const managing = ref(false)
const tagInput = ref('')
const tagOptions = ref([])
const filteredTagOptions = ref([])
const showTagDropdown = ref(false)

const requestDetail = () => {
  if (!imageId.value) {
    return
  }

  uni.showLoading({
    title: '加载中...'
  })

  request({
    url: `${baseUrl}/api/image/${imageId.value}`,
    success(res) {
      if (res.statusCode !== 200 || !res.data) {
        uni.showToast({
          title: '加载失败',
          icon: 'none'
        })
        return
      }

      imageInfo.value = res.data
    },
    fail() {
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

const previewImage = () => {
  if (!imageInfo.value?.localUrl) {
    return
  }

  const imageUrl = resolveImageUrl(imageInfo.value.localUrl)
  uni.previewImage({
    current: imageUrl,
    urls: [imageUrl]
  })
}

const handleTagClick = (tag) => {
  uni.navigateTo({
    url: `/pages/index/index?tag=${tag}`
  })
}

const requestTagOptions = () => {
  request({
    url: `${baseUrl}/api/image/tags`,
    success(res) {
      if (res.statusCode !== 200 || !Array.isArray(res.data)) {
        return
      }

      tagOptions.value = res.data
      filteredTagOptions.value = res.data
      syncAvailableTags(res.data)
    }
  })
}

const toggleManage = () => {
  managing.value = !managing.value
  if (managing.value) {
    requestTagOptions()
  } else {
    showTagDropdown.value = false
  }
}

const updateTags = (nextTags) => {
  uni.showLoading({
    title: '保存中...'
  })

  request({
    url: `${baseUrl}/api/image/${imageId.value}/tags`,
    method: 'POST',
    data: {
      tags: nextTags.join(',')
    },
    success(res) {
      if (res.statusCode !== 200 || res.data !== true) {
        uni.showToast({
          title: '保存失败',
          icon: 'none'
        })
        return
      }

      imageInfo.value.tags = nextTags
      uni.setStorageSync(REFRESH_KEY, 1)
      requestTagOptions()
      uni.showToast({
        title: '已保存',
        icon: 'none'
      })
    },
    fail() {
      uni.showToast({
        title: '保存失败',
        icon: 'none'
      })
    },
    complete() {
      uni.hideLoading()
    }
  })
}

const addTag = () => {
  const value = tagInput.value.trim()
  if (!value || !imageInfo.value) {
    return
  }

  const nextTags = [...new Set([...(imageInfo.value.tags || []), value])]
  updateTags(nextTags)
  tagInput.value = ''
  showTagDropdown.value = false
}

const removeTag = (tag) => {
  if (!imageInfo.value) {
    return
  }

  const nextTags = (imageInfo.value.tags || []).filter((item) => item !== tag)
  updateTags(nextTags)
}

const confirmDelete = () => {
  uni.showModal({
    title: '删除收藏',
    content: '这会在收藏中移除图片。',
    success(res) {
      if (res.confirm) {
        deleteImage()
      }
    }
  })
}

const deleteImage = () => {
  uni.showLoading({
    title: '删除中...'
  })

  request({
    url: `${baseUrl}/api/image/delete/${imageId.value}`,
    method: 'POST',
    success(res) {
      if (res.statusCode !== 200 || res.data !== true) {
        uni.showToast({
          title: '删除失败',
          icon: 'none'
        })
        return
      }

      uni.setStorageSync(REFRESH_KEY, 1)
      requestTagOptions()
      uni.showToast({
        title: '已删除',
        icon: 'none'
      })
      setTimeout(() => {
        uni.navigateBack()
      }, 400)
    },
    fail() {
      uni.showToast({
        title: '删除失败',
        icon: 'none'
      })
    },
    complete() {
      uni.hideLoading()
    }
  })
}

const syncAvailableTags = (tags = []) => {
  uni.setStorageSync(AVAILABLE_TAGS_KEY, [...new Set(tags)].sort())
}

const filterTagOptions = () => {
  const keyword = tagInput.value.trim()
  filteredTagOptions.value = tagOptions.value.filter((tag) => (
    !imageInfo.value?.tags?.includes(tag) &&
    (!keyword || tag.includes(keyword))
  ))
  showTagDropdown.value = true
}

const selectTagOption = (tag) => {
  tagInput.value = tag
  showTagDropdown.value = false
}

const hideDropdownLater = () => {
  setTimeout(() => {
    showTagDropdown.value = false
  }, 150)
}

onLoad((options) => {
  if (options?.id) {
    imageId.value = options.id
    requestDetail()
    requestTagOptions()
  }
})
</script>

<style>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}

.container {
  padding: 24rpx;
}

.image-wrapper,
.info-card,
.tag-card,
.manage-box,
.danger-box {
  border-radius: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06);
}

.image-wrapper {
  padding: 20rpx;
  background: #000;
  overflow: hidden;
}

.info-card,
.tag-card,
.manage-box {
  margin-top: 24rpx;
  padding: 24rpx;
  background: #fff;
}

.danger-box {
  margin-top: 24rpx;
  padding: 24rpx;
  background: #fff6f5;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.section-tip {
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}

.info-list {
  margin-top: 18rpx;
}

.info-row {
  display: flex;
  flex-direction: column;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #eef2f7;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 22rpx;
  color: #86909c;
}

.info-value {
  margin-top: 8rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #1f2329;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16rpx;
}

.tag {
  margin: 0 16rpx 16rpx 0;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: #eef4ff;
  color: #2d6cdf;
  font-size: 26rpx;
}

.empty-tags {
  margin-top: 16rpx;
  color: #666;
  font-size: 26rpx;
}

.tag-input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 20rpx;
}

.tag-input {
  flex: 1;
  min-height: 76rpx;
  padding: 0 20rpx;
  border-radius: 18rpx;
  background: #f7f8fa;
}

.subheading {
  margin-top: 24rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: #1f2329;
}

.tag-dropdown {
  margin-top: 12rpx;
  border-radius: 12rpx;
  background: #f7f8fa;
  overflow: hidden;
}

.tag-option {
  padding: 16rpx 20rpx;
  border-bottom: 1rpx solid #e6e8eb;
  font-size: 26rpx;
  color: #333;
}

.tag-option:last-child {
  border-bottom: none;
}

.header-delete-btn {
  background: #d93025;
  color: #fff;
}

image {
  width: 100%;
  transition: opacity 0.3s ease;
}

button {
  border-radius: 999rpx;
}
</style>
