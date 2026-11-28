<template>
  <view class="page">
    <view class="container">
      <view class="intro-card">
        <view class="intro-heading">
          <view class="collect-badge">
            <view class="collect-glyph">
              <view class="collect-sq collect-sq-tl"></view>
              <view class="collect-sq collect-sq-tr"></view>
              <view class="collect-sq collect-sq-bl"></view>
              <view class="collect-plus">
                <view class="collect-plus-h"></view>
                <view class="collect-plus-v"></view>
              </view>
            </view>
          </view>
          <view class="section-title">收藏图片</view>
        </view>
        <view class="section-tip intro-tip">选择图片、添加标签，然后批量保存。</view>
      </view>

      <view class="summary-card">
        <view class="summary-copy">
          <view class="section-title">当前批次</view>
          <view class="section-tip">图片 {{ images.length }} 张 | 已选 {{ selectedImages.length }} 张</view>
        </view>
        <button class="ghost-button" size="mini" @click="toggleSelectAll">
          {{ allSelected ? '取消全选' : '全选图片' }}
        </button>
      </view>

      <view class="upload-card">
        <view class="add-card" @click="chooseImage">
          <view class="add-icon">
            <view class="add-bar add-bar-h"></view>
            <view class="add-bar add-bar-v"></view>
          </view>
          <view class="add-title">添加图片</view>
          <view class="add-text">支持一次选择多张图片，后面再统一整理标签。</view>
        </view>
      </view>

      <view class="panel-card">
        <view class="section-title">已选图片</view>
        <view class="section-tip">点击图片切换选中状态，标签会应用到当前选中的所有图片。</view>
        <view class="grid">
          <view
            v-for="(img, index) in images"
            :key="img.id"
            class="preview-card"
            :class="{ active: img.selected }"
            @click="toggleImageSelection(index)"
          >
            <image :src="img.path" class="preview" mode="aspectFill" />
            <view class="preview-remove" @click.stop="removeImage(index)">×</view>
            <view class="preview-state">{{ img.selected ? '已选中' : '点击选中' }}</view>
            <view v-if="img.tags.length" class="preview-tags">{{ img.tags.join(' / ') }}</view>
          </view>
        </view>
      </view>

      <view class="tag-box">
        <view class="section-header">
          <view class="section-copy">
            <view class="section-title">标签</view>
            <view class="section-tip">可以新建标签，也可以从已有标签中直接选择。</view>
          </view>
          <button class="ghost-button action-button" size="mini" @click="toggleManageTags">
            {{ managingTags ? '完成' : '管理标签' }}
          </button>
        </view>

        <view class="tag-input-row">
          <view class="tag-input-wrap">
            <input
              v-model="tagInput"
              class="tag-input"
              placeholder="输入标签"
              @focus="tagInputFocused = true"
              @blur="handleTagInputBlur"
              @confirm="addTag"
            />
            <view v-if="showTagSuggestions" class="tag-suggestion-panel">
              <view
                v-for="item in filteredTagSuggestions"
                :key="item"
                class="tag-suggestion-item"
                @click="selectSuggestedTag(item)"
              >
                {{ item }}
              </view>
            </view>
          </view>
          <button class="primary-mini-button action-button" size="mini" @click="addTag">添加标签</button>
        </view>

        <view class="subheading">可用标签</view>
        <view class="recommend">
          <view v-for="item in availableTags" :key="item" class="recommend-tag">
            <text class="recommend-label" @click="applyTag(item)">{{ item }}</text>
            <view v-if="managingTags" class="manage-actions">
              <text class="tag-edit" @click.stop="renameTag(item)">编辑</text>
              <text class="tag-remove" @click.stop="deleteTag(item)">删除</text>
            </view>
          </view>
        </view>

        <view v-if="selectedTagSummary.length" class="subheading">已应用标签</view>
        <view v-if="selectedTagSummary.length" class="tag-list">
          <view v-for="tag in selectedTagSummary" :key="tag" class="tag" @click="removeTagFromSelected(tag)">
            {{ tag }} ×
          </view>
        </view>
      </view>

      <button class="submit-button" :disabled="submitting" @click="submit">保存 {{ images.length }} 张图片</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import baseUrl from '../../utils/base-url'
import { request, uploadFile } from '../../utils/request'

const REFRESH_KEY = 'image_list_should_refresh'
const availableTags = ref([])
const images = ref([])
const tagInput = ref('')
const submitting = ref(false)
const managingTags = ref(false)
const tagInputFocused = ref(false)

const selectedImages = computed(() => images.value.filter((item) => item.selected))
const allSelected = computed(() => images.value.length > 0 && selectedImages.value.length === images.value.length)
const selectedTagSummary = computed(() => [...new Set(selectedImages.value.flatMap((item) => item.tags))].sort())
const filteredTagSuggestions = computed(() => {
  if (!tagInputFocused.value) return []
  const keyword = tagInput.value.trim().toLowerCase()
  return availableTags.value.filter((item) => !keyword || item.toLowerCase().includes(keyword)).slice(0, 8)
})
const showTagSuggestions = computed(() => filteredTagSuggestions.value.length > 0)

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

const toggleImageSelection = (index) => { images.value[index].selected = !images.value[index].selected }
const toggleSelectAll = () => {
  const nextValue = !allSelected.value
  images.value.forEach((item) => { item.selected = nextValue })
}
const removeImage = (index) => { images.value.splice(index, 1) }
const syncAvailableTags = (tags = []) => { availableTags.value = [...new Set(tags)].sort() }

const requestAvailableTags = async () => {
  const res = await request({ url: `${baseUrl}/api/image/tags` })
  if (res.statusCode !== 200 || !Array.isArray(res.data)) throw new Error('request tags failed')
  syncAvailableTags(res.data)
}

const addTag = () => {
  const value = tagInput.value.trim()
  if (!value) return
  if (!availableTags.value.includes(value)) availableTags.value = [...availableTags.value, value].sort()
  applyTag(value)
  tagInput.value = ''
}

const selectSuggestedTag = (tag) => {
  applyTag(tag)
  tagInput.value = ''
  tagInputFocused.value = false
}

const applyTag = (tag) => {
  if (!selectedImages.value.length) {
    uni.showToast({ title: '请先选择图片', icon: 'none' })
    return
  }
  selectedImages.value.forEach((image) => {
    if (!image.tags.includes(tag)) image.tags.push(tag)
  })
}

const removeTagFromSelected = (tag) => {
  if (!selectedImages.value.length) {
    uni.showToast({ title: '请先选择图片', icon: 'none' })
    return
  }
  selectedImages.value.forEach((image) => { image.tags = image.tags.filter((item) => item !== tag) })
}

const deleteTag = async (tag) => {
  const confirmRes = await new Promise((resolve) => {
    uni.showModal({
      title: '删除标签',
      content: `删除“${tag}”后，所有已使用该标签的图片都会移除这个标签。是否继续？`,
      success: resolve
    })
  })
  if (!confirmRes.confirm) return
  try {
    uni.showLoading({ title: '删除中...' })
    const res = await request({ url: `${baseUrl}/api/image/tags/delete`, method: 'POST', data: { tag } })
    if (res.statusCode !== 200 || res.data !== true) throw new Error('delete tag failed')
    images.value.forEach((image) => { image.tags = image.tags.filter((item) => item !== tag) })
    await requestAvailableTags()
    uni.setStorageSync(REFRESH_KEY, 1)
    uni.showToast({ title: '标签已删除', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: '删除标签失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

const renameTag = async (tag) => {
  const modalRes = await new Promise((resolve) => {
    uni.showModal({
      title: '修改标签',
      content: `把“${tag}”统一改成新的标签名称`,
      editable: true,
      placeholderText: '输入新的标签名称',
      success: resolve
    })
  })
  if (!modalRes.confirm) return
  const nextTag = (modalRes.content || '').trim()
  if (!nextTag) {
    uni.showToast({ title: '请输入新的标签名称', icon: 'none' })
    return
  }
  if (nextTag === tag) return
  try {
    uni.showLoading({ title: '修改中...' })
    const res = await request({ url: `${baseUrl}/api/image/tags/rename`, method: 'POST', data: { oldTag: tag, newTag: nextTag } })
    if (res.statusCode !== 200 || res.data !== true) throw new Error('rename tag failed')
    images.value.forEach((image) => {
      if (!image.tags.includes(tag)) return
      const nextTags = image.tags.map((item) => (item === tag ? nextTag : item))
      image.tags = [...new Set(nextTags)].sort()
    })
    await requestAvailableTags()
    uni.setStorageSync(REFRESH_KEY, 1)
    uni.showToast({ title: '标签已修改', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: '修改标签失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

const toggleManageTags = () => { managingTags.value = !managingTags.value }
const handleTagInputBlur = () => { setTimeout(() => { tagInputFocused.value = false }, 120) }

const uploadImage = async (image) => {
  const res = await uploadFile({ url: `${baseUrl}/api/image/upload`, filePath: image.path, name: 'file', formData: { tags: image.tags.join(',') } })
  let payload = res.data
  if (typeof payload === 'string') payload = JSON.parse(payload)
  if (res.statusCode !== 200 || !payload || payload.status === 'error') throw new Error('upload failed')
  return payload
}

const submit = async () => {
  if (!images.value.length || submitting.value) {
    if (!images.value.length) uni.showToast({ title: '请先选择图片', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    uni.showLoading({ title: '保存中...' })
    await Promise.all(images.value.map((image) => uploadImage(image)))
    await requestAvailableTags()
    uni.setStorageSync(REFRESH_KEY, 1)
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 400)
  } catch (error) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  } finally {
    submitting.value = false
    uni.hideLoading()
  }
}

onLoad(async (options) => {
  managingTags.value = options?.manageTags === '1'
  try {
    const res = await request({ url: `${baseUrl}/api/image/tags` })
    if (res.statusCode === 401) {
      uni.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/index/index' }) }), 400)
      return
    }
    if (res.statusCode !== 200 || !Array.isArray(res.data)) throw new Error('request tags failed')
    syncAvailableTags(res.data)
  } catch (error) {
    uni.showToast({ title: '加载标签失败', icon: 'none' })
  }
})
</script>

<style>
.page { min-height: 100vh; background: #F8F7FC; }
.container { padding: 24rpx; }
.intro-card, .summary-card, .upload-card, .panel-card, .tag-box { margin-bottom: 24rpx; padding: 24rpx; border-radius: 24rpx; box-shadow: 0 12rpx 30rpx rgba(0,0,0,0.06); background: #fff; }
.summary-card, .section-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20rpx; }
.summary-copy, .section-copy { flex: 1; min-width: 0; }
.intro-heading { display: flex; align-items: center; }
.collect-badge { width: 56rpx; height: 56rpx; margin-right: 16rpx; border-radius: 16rpx; background: #F3F0FF; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.collect-glyph { position: relative; width: 32rpx; height: 32rpx; }
.collect-sq { position: absolute; width: 10rpx; height: 10rpx; border-radius: 3rpx; background: #7965D8; }
.collect-sq-tl { left: 4rpx; top: 4rpx; }
.collect-sq-tr { right: 4rpx; top: 4rpx; }
.collect-sq-bl { left: 4rpx; bottom: 4rpx; }
.collect-plus { position: absolute; right: 4rpx; bottom: 4rpx; width: 10rpx; height: 10rpx; }
.collect-plus-h, .collect-plus-v { position: absolute; left: 50%; top: 50%; background: #7965D8; border-radius: 999rpx; }
.collect-plus-h { width: 10rpx; height: 3rpx; margin-left: -5rpx; margin-top: -1.5rpx; }
.collect-plus-v { width: 3rpx; height: 10rpx; margin-left: -1.5rpx; margin-top: -5rpx; }
.intro-tip { padding-left: 72rpx; }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
.section-tip { margin-top: 8rpx; font-size: 24rpx; line-height: 1.7; color: #6b7280; }
.ghost-button, .primary-mini-button, .action-button { height: 68rpx; min-width: 156rpx; padding: 0 28rpx; line-height: 68rpx; border-radius: 999rpx; font-size: 24rpx; font-weight: 600; }
.ghost-button { background: #F3F0FF; color: #7965D8; }
.primary-mini-button { background: #F3F0FF; color: #7965D8; }
.add-card { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48rpx 24rpx; border: 2rpx dashed #cbd5e1; border-radius: 24rpx; background: #f8fbff; }
.add-icon { position: relative; width: 96rpx; height: 96rpx; border-radius: 50%; background: rgba(219, 234, 254, 0.7); }
.add-bar { position: absolute; left: 50%; top: 50%; background: #7965D8; border-radius: 999rpx; }
.add-bar-h { width: 36rpx; height: 6rpx; margin-left: -18rpx; margin-top: -3rpx; }
.add-bar-v { width: 6rpx; height: 36rpx; margin-left: -3rpx; margin-top: -18rpx; }
.add-title { margin-top: 10rpx; font-size: 30rpx; font-weight: 700; color: #1f2329; }
.add-text { margin-top: 12rpx; color: #6b7280; font-size: 24rpx; text-align: center; }
.grid { display: flex; flex-wrap: wrap; margin-top: 20rpx; }
.preview-card { position: relative; width: 30%; margin: 1%; padding-bottom: 12rpx; border: 2rpx solid transparent; border-radius: 24rpx; box-shadow: 0 12rpx 30rpx rgba(0,0,0,0.06); background: #fff; overflow: hidden; }
.preview-card.active { border-color: #7965D8; background: #F3F0FF; }
.preview { width: 100%; height: 220rpx; }
.preview-remove { position: absolute; top: 8rpx; right: 8rpx; width: 40rpx; height: 40rpx; border-radius: 999rpx; background: rgba(0,0,0,0.6); color: #fff; line-height: 40rpx; text-align: center; font-size: 24rpx; }
.preview-state, .preview-tags { padding: 0 8rpx; font-size: 22rpx; color: #555; }
.preview-state { margin-top: 8rpx; }
.preview-tags { margin-top: 4rpx; }
.tag-input-row { display: flex; align-items: flex-start; gap: 16rpx; margin-top: 20rpx; }
.tag-input-wrap { position: relative; flex: 1; }
.tag-input { width: 100%; min-height: 76rpx; padding: 0 20rpx; border-radius: 18rpx; background: #F8F7FC; }
.tag-suggestion-panel { position: absolute; left: 0; right: 0; top: 88rpx; z-index: 10; padding: 8rpx 0; border-radius: 18rpx; background: #fff; box-shadow: 0 10rpx 30rpx rgba(0,0,0,0.1); }
.tag-suggestion-item { padding: 18rpx 20rpx; font-size: 26rpx; color: #1f2329; }
.subheading { margin-top: 24rpx; font-size: 26rpx; font-weight: 600; color: #1f2329; }
.recommend { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 16rpx; }
.recommend-tag { display: flex; align-items: center; gap: 12rpx; padding: 10rpx 20rpx; border-radius: 999rpx; background: #F3F0FF; color: #7965D8; font-size: 26rpx; }
.recommend-label { max-width: 320rpx; }
.manage-actions { display: flex; align-items: center; gap: 12rpx; }
.tag-edit { color: #7965D8; }
.tag-remove { color: #d93025; }
.tag-list { display: flex; flex-wrap: wrap; margin-top: 16rpx; }
.tag { display: inline-block; background: #EEE9FF; padding: 6rpx 12rpx; margin: 5rpx; border-radius: 20rpx; }
button { border-radius: 999rpx; }
image { transition: opacity 0.3s ease; }
.submit-button { margin-top: 28rpx; background: #7965D8; color: #fff; }
.submit-button:active { background: #6554C0; }
</style>
