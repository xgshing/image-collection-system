<template>
  <view class="page">
    <view class="container">
      <view class="intro-card">
        <view class="section-title">收藏图片</view>
        <view class="section-tip">
          把刚看到的图片收进你的灵感库，顺手打上标签，后面会更好找。
        </view>
      </view>

      <view class="summary-card">
        <view>
          <view class="section-title">本次收藏</view>
          <view class="section-tip">已选 {{ images.length }} 张，当前选中 {{ selectedImages.length }} 张。</view>
        </view>
        <button size="mini" @click="toggleSelectAll">
          {{ allSelected ? '取消全选' : '全选图片' }}
        </button>
      </view>

      <view class="upload-card">
        <view class="add-card" @click="chooseImage">
          <view class="add-symbol">+</view>
          <view class="add-title">添加图片</view>
          <view class="add-text">支持一次选择多张图片，先收进来再慢慢整理。</view>
        </view>
      </view>

      <view class="panel-card">
        <view class="section-title">已选图片</view>
        <view class="section-tip">点按图片可选中，标签会批量应用到当前选中项。</view>

        <view class="grid">
          <view
            v-for="(img, index) in images"
            :key="img.id"
            class="preview-card"
            :class="{ active: img.selected }"
            @click="toggleImageSelection(index)"
          >
            <image :src="img.path" class="preview" />
            <view class="preview-remove" @click.stop="removeImage(index)">x</view>
            <view class="preview-state">
              {{ img.selected ? '已选中' : '点击选中' }}
            </view>
            <view class="preview-tags" v-if="img.tags.length">
              {{ img.tags.join(' · ') }}
            </view>
          </view>
        </view>
      </view>

      <view class="tag-box">
        <view class="section-header">
          <view>
            <view class="section-title">先给它们分个类</view>
            <view class="section-tip">标签不用太多，但最好让你下次能一眼找到。</view>
          </view>
          <button size="mini" @click="toggleManageTags">
            {{ managingTags ? '完成整理' : '管理标签' }}
          </button>
        </view>

        <view class="tag-input-row">
          <input v-model="tagInput" class="tag-input" placeholder="输入标签，例如 UI、海报、插画" />
          <button size="mini" @click="addTag">添加标签</button>
        </view>

        <view class="subheading">推荐标签</view>
        <view class="recommend">
          <view
            v-for="item in availableTags"
            :key="item"
            class="recommend-tag"
          >
            <text @click="applyTag(item)">{{ item }}</text>
            <text
              v-if="managingTags"
              class="tag-remove"
              @click.stop="deleteTag(item)"
            >
              x
            </text>
          </view>
        </view>

        <view class="subheading" v-if="selectedTagSummary.length">本次已添加标签</view>
        <view class="tag-list" v-if="selectedTagSummary.length">
          <view
            class="tag"
            v-for="tag in selectedTagSummary"
            :key="tag"
            @click="removeTagFromSelected(tag)"
          >
            {{ tag }} x
          </view>
        </view>
      </view>

      <button class="submit-button" :disabled="submitting" @click="submit">
        收藏并归档 {{ images.length }} 张图片
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import baseUrl from '../../utils/base-url'

const REFRESH_KEY = 'image_list_should_refresh'
const AVAILABLE_TAGS_KEY = 'collect_available_tags'
const availableTags = ref([])
const images = ref([])
const tagInput = ref('')
const submitting = ref(false)
const managingTags = ref(false)

const selectedImages = computed(() => images.value.filter((item) => item.selected))
const allSelected = computed(() => images.value.length > 0 && selectedImages.value.length === images.value.length)
const selectedTagSummary = computed(() => {
  return [...new Set(selectedImages.value.flatMap((item) => item.tags))].sort()
})

const chooseImage = () => {
  uni.chooseImage({
    count: 9,
    success(res) {
      const nextImages = (res.tempFilePaths || []).map((path) => ({
        id: `${Date.now()}-${Math.random()}`,
        path,
        selected: true,
        tags: []
      }))
      images.value = [...images.value, ...nextImages]
    }
  })
}

const toggleImageSelection = (index) => {
  images.value[index].selected = !images.value[index].selected
}

const toggleSelectAll = () => {
  const nextValue = !allSelected.value
  images.value.forEach((item) => {
    item.selected = nextValue
  })
}

const removeImage = (index) => {
  images.value.splice(index, 1)
}

const addTag = () => {
  const value = tagInput.value.trim()
  if (!value) {
    return
  }

  if (!availableTags.value.includes(value)) {
    availableTags.value.push(value)
    persistAvailableTags()
  }

  applyTag(value)
  tagInput.value = ''
}

const applyTag = (tag) => {
  if (!selectedImages.value.length) {
    uni.showToast({
      title: '请先选择图片',
      icon: 'none'
    })
    return
  }

  selectedImages.value.forEach((image) => {
    if (!image.tags.includes(tag)) {
      image.tags.push(tag)
    }
  })
}

const removeTagFromSelected = (tag) => {
  if (!selectedImages.value.length) {
    uni.showToast({
      title: '请先选择图片',
      icon: 'none'
    })
    return
  }

  selectedImages.value.forEach((image) => {
    image.tags = image.tags.filter((item) => item !== tag)
  })
}

const deleteTag = (tag) => {
  availableTags.value = availableTags.value.filter((item) => item !== tag)
  images.value.forEach((image) => {
    image.tags = image.tags.filter((item) => item !== tag)
  })
  persistAvailableTags()
}

const toggleManageTags = () => {
  managingTags.value = !managingTags.value
}

const persistAvailableTags = () => {
  uni.setStorageSync(AVAILABLE_TAGS_KEY, availableTags.value)
}

const syncAvailableTags = (tags = []) => {
  availableTags.value = [...new Set(tags)].sort()
  persistAvailableTags()
}

const requestAvailableTags = () => {
  uni.request({
    url: `${baseUrl}/api/image/tags`,
    success(res) {
      if (res.statusCode === 200 && Array.isArray(res.data)) {
        syncAvailableTags(res.data)
      }
    }
  })
}

const uploadImage = (image) =>
  new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${baseUrl}/api/image/upload`,
      filePath: image.path,
      name: 'file',
      formData: {
        tags: image.tags.join(',')
      },
      success(res) {
        if (res.statusCode !== 200 || String(res.data).startsWith('error')) {
          reject(new Error('upload failed'))
          return
        }

        resolve(res)
      },
      fail: reject
    })
  })

const submit = async () => {
  if (!images.value.length || submitting.value) {
    if (!images.value.length) {
      uni.showToast({
        title: '请先选择图片',
        icon: 'none'
      })
    }
    return
  }

  submitting.value = true

  try {
    uni.showLoading({
      title: '收藏中...'
    })

    await Promise.all(images.value.map((image) => uploadImage(image)))

    uni.setStorageSync(REFRESH_KEY, 1)
    uni.showToast({ title: '收藏成功' })
    setTimeout(() => {
      uni.navigateBack()
    }, 400)
  } catch (error) {
    uni.showToast({
      title: '收藏失败',
      icon: 'none'
    })
  } finally {
    submitting.value = false
    uni.hideLoading()
  }
}

onLoad(() => {
  const savedTags = uni.getStorageSync(AVAILABLE_TAGS_KEY)
  availableTags.value = Array.isArray(savedTags) && savedTags.length
    ? savedTags
    : []
  requestAvailableTags()
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

.intro-card,
.summary-card,
.upload-card,
.panel-card,
.tag-box {
  margin-bottom: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06);
  background: #fff;
}

.summary-card,
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48rpx 24rpx;
  border: 2rpx dashed #cbd5e1;
  border-radius: 24rpx;
  background: #f8fbff;
}

.add-symbol {
  font-size: 48rpx;
  font-weight: 700;
  color: #2d6cdf;
}

.add-title {
  margin-top: 10rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2329;
}

.add-text {
  margin-top: 12rpx;
  color: #6b7280;
  font-size: 24rpx;
  text-align: center;
}

.grid {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
}

.preview-card {
  position: relative;
  width: 30%;
  margin: 1%;
  padding-bottom: 12rpx;
  border: 2rpx solid transparent;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.06);
  background: #fff;
  overflow: hidden;
}

.preview-card.active {
  border-color: #2d6cdf;
  background: #eef4ff;
}

.preview {
  width: 100%;
  border-radius: 10rpx;
}

.preview-remove {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  line-height: 40rpx;
  text-align: center;
  font-size: 24rpx;
}

.preview-state,
.preview-tags {
  padding: 0 8rpx;
  font-size: 22rpx;
  color: #555;
}

.preview-state {
  margin-top: 8rpx;
}

.preview-tags {
  margin-top: 4rpx;
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

.recommend {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16rpx;
}

.recommend-tag {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin: 0 16rpx 16rpx 0;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #eef4ff;
  color: #2d6cdf;
  font-size: 26rpx;
}

.tag-remove {
  color: #d93025;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16rpx;
}

.tag {
  display: inline-block;
  background: #dfe9ff;
  padding: 6rpx 12rpx;
  margin: 5rpx;
  border-radius: 20rpx;
}

button {
  border-radius: 999rpx;
}

image {
  transition: opacity 0.3s ease;
}

.submit-button {
  margin-top: 28rpx;
  background: #2d6cdf;
  color: #fff;
}
</style>
