<template>
  <view class="page">
    <view class="project-head">
      <view class="project-copy">
        <view class="eyebrow">当前拍摄项目</view>
        <view class="name-line" @click="onInfo">
          <text class="project-name">{{ project?.name || '尚未选择项目' }}</text>
          <view v-if="project" class="chevron" :class="{ open: openInfo }"></view>
        </view>
        <view class="meta">{{ projectLine }}</view>
      </view>
      <button class="pill recycle-btn" hover-class="none" @click="onRecycle">项目回收站</button>
    </view>
    <view v-if="archived" class="readonly">已归档，只能查看照片、原图和带水印图片</view>
    <view class="shoot-row">
      <button class="shoot" @click="onShoot">
        <view class="cam">
          <view class="cam-top"></view>
          <view class="cam-body"><view class="cam-lens"></view></view>
        </view>
        <text>立即拍照</text>
      </button>
      <button class="settings" :class="{ open: openSettings }" hover-class="none" @click="onSettings">
        <image class="gear-icon" src="/static/watermark/gear.png" mode="aspectFit" />
        <text>设置</text>
        <view v-if="openSettings" class="chevron up"></view>
      </button>
    </view>

    <view class="tool-row">
      <view class="tool tool-select" @click="onOpenPicker">
        <view class="tool-plus-box">
          <view class="tool-plus-h"></view>
          <view class="tool-plus-v"></view>
        </view>
        <text>选择项目</text>
      </view>
      <view class="tool tool-date" :class="{ active: Boolean(selectedStart) }" @click="onOpenCalendar">
        <view class="tool-cal-box">
          <view class="cal-ring left"></view>
          <view class="cal-ring right"></view>
          <view class="cal-body">
            <view class="cal-bar"></view>
            <view class="cal-dots">
              <view class="cal-dot"></view>
              <view class="cal-dot"></view>
            </view>
          </view>
        </view>
        <text>{{ dateFilterLabel }}</text>
      </view>
      <view class="tool member-tool tool-members">
        <view class="tool-main" @click="onMembers">
          <image class="tool-icon" src="/static/watermark/members.png" mode="aspectFit" />
          <text>项目成员{{ project ? ` (${project.memberCount || 0})` : '' }}</text>
        </view>
        <view class="add-badge" @click.stop="onInvitePlus">
          <view class="add-h"></view>
          <view class="add-v"></view>
        </view>
      </view>
    </view>

    <view v-if="hasInvite && !loggedIn" class="invite-banner" @click="onInvite">有一个项目邀请，登录后查看</view>

    <view class="section-head">
      <view class="section-left">
        <text class="section-title">最近项目照片</text>
        <view v-if="loggedIn && project" class="import-btn" @click="importLocalPhoto">导入</view>
      </view>
      <view v-if="loggedIn && project" class="section-more">
        <text>全部 {{ photoTotal }} 张</text>
        <view class="more-chevron"></view>
      </view>
    </view>

    <view v-if="!loggedIn" class="card empty">
      <view class="empty-desc">登录后显示项目照片。点击上方按钮会弹出和首页相同的登录。</view>
    </view>
    <view v-else-if="loading" class="card">正在加载项目</view>
    <view v-else-if="errorText" class="card">
      <view class="empty-title">加载失败</view>
      <view class="empty-desc">{{ errorText }}</view>
      <button class="primary" @click="loadProjects">重新加载</button>
    </view>
    <view v-else-if="!project" class="card empty">
      <view class="empty-title">还没有项目</view>
      <view class="empty-desc">先新建一个项目，再拍摄照片。</view>
    </view>
    <view v-else-if="photoLoading" class="card">正在加载照片</view>
    <view v-else-if="photoError" class="card">
      <view class="empty-desc">{{ photoError }}</view>
      <button class="primary" @click="loadPhotos">重新加载</button>
    </view>
    <view v-else-if="groups.length">
      <view v-for="group in groups" :key="group.day" class="day-block">
        <view class="day-line">
          <text>{{ group.day }}</text>
          <text
            class="day-count"
            :class="{ expandable: group.items.length > 3 }"
            @click="toggleDayExpand(group)"
          >{{ group.items.length }} 张{{ group.items.length > 3 ? (isDayExpanded(group.day) ? ' ▴' : ' ▾') : '' }}</text>
        </view>
        <view class="grid">
          <view v-for="item in visibleDayItems(group)" :key="item.id" class="tile" @click="openDetail(item)">
            <image class="tile-image" :src="item.displayUrl || item.previewUrl" mode="aspectFill" />
            <view class="tile-caption">
              <text class="tile-note">{{ item.note || '（空记录）' }}</text>
              <text class="tile-time">{{ clockText(item.takenAt) }}</text>
            </view>
          </view>
        </view>
      </view>
      <button v-if="photos.length && photos.length < photoTotal" class="ghost more" :loading="photoLoading" @click="loadPhotos(true)">加载更多</button>
    </view>
    <view v-else-if="selectedStart" class="card empty">
      <view class="empty-desc">所选日期暂无项目照片。</view>
      <button class="ghost more" @click="clearDateFilter">查看全部日期</button>
    </view>

    <view v-if="openCalendar" class="mask" @click="closeCalendar">
      <view class="sheet calendar-sheet" @click.stop>
        <view class="cal-head">
          <text class="sheet-title">按日期筛选</text>
          <view class="cal-head-right">
            <text class="cal-sub">{{ draftRangeLabel }}</text>
            <text v-if="selectedStart || draftStart" class="cal-reset" @click="resetCalendarFilter">重置</text>
          </view>
        </view>
        <view class="cal-nav">
          <view class="cal-nav-btn" @click="shiftMonth(-1)">‹</view>
          <picker mode="date" fields="month" :value="calendarYm" @change="onPickYearMonth">
            <view class="cal-month-btn">
              <text class="cal-month">{{ calendarYear }}年{{ calendarMonth }}月</text>
              <text class="cal-month-arrow">▾</text>
            </view>
          </picker>
          <view class="cal-nav-btn" @click="shiftMonth(1)">›</view>
        </view>
        <view class="cal-week">
          <text v-for="w in weekLabels" :key="w" class="cal-week-cell">{{ w }}</text>
        </view>
        <view class="cal-grid">
          <view
            v-for="cell in calendarCells"
            :key="cell.key"
            class="cal-cell"
            :class="{
              blank: !cell.date,
              selected: cell.date && isRangeEndpoint(cell.date),
              'in-range': cell.date && isInDraftRange(cell.date),
              today: cell.date === todayStr
            }"
            @click="cell.date && pickRangeDate(cell.date)"
          >
            <text v-if="cell.day" class="cal-day">{{ cell.day }}</text>
            <view v-if="cell.date && photoDateSet.has(cell.date)" class="cal-has-photo"></view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="openCreate" class="mask" @click="openCreate = false">
      <view class="sheet" @click.stop>
        <view class="sheet-title">新建项目</view>
        <input v-model="projectName" class="input" maxlength="100" placeholder="项目名称" />
        <button class="primary create-submit" :loading="creating" @click="createProject">创建</button>
      </view>
    </view>

    <view v-if="openInvite" class="mask" @click="openInvite = false">
      <view class="sheet invite-sheet" @click.stop>
        <view class="invite-head">
          <text class="sheet-title">邀请链接与二维码</text>
          <text class="status-pill" :class="invitePreview === 'active' ? 'on' : 'off'">{{ invitePreview === 'active' ? '有效中' : '已失效' }}</text>
          <text class="invite-close" @click="openInvite = false">×</text>
        </view>
        <view class="inviter-row">
          <image class="inviter-avatar" :src="inviterAvatar" mode="aspectFill" />
          <view class="inviter-copy">
            <text class="inviter-name">{{ inviterName }}</text>
            <text class="hint">邀请你加入拍照项目</text>
          </view>
          <text class="hint">{{ roleLabel(project?.role) }}</text>
        </view>
        <view class="invite-project">{{ project?.name }}</view>
        <view class="hint center">有效期至 {{ expiryText }}（7天内有效）</view>
        <view class="qr-box" :class="{ faded: invitePreview !== 'active' }">
          <view class="qr-frame">
            <view class="qr-placeholder"></view>
            <text>二维码生成占位</text>
          </view>
          <text class="hint">扫码后需点击「接受邀请」</text>
        </view>
        <view class="link-row">
          <text class="link-code">{{ inviteScene || '正在生成邀请码' }}</text>
          <button class="copy-btn" hover-class="none" :disabled="!inviteScene" @click="copyScene">复制邀请码</button>
        </view>
        <button v-if="inviteScene" class="primary share-btn" hover-class="none" open-type="share">微信好友分享</button>
        <button v-else class="primary share-btn" hover-class="none" loading disabled>正在生成邀请</button>
        <view class="preview-row">
          <text class="hint">邀请状态预览</text>
          <view class="preview-pills">
            <text :class="{ on: invitePreview === 'active' }" @click="invitePreview = 'active'">有效中</text>
            <text :class="{ on: invitePreview === 'expired' }" @click="invitePreview = 'expired'">已失效</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="inviteDetail" class="mask">
      <view class="sheet">
        <view class="sheet-title">项目邀请</view>
        <view class="empty-desc">{{ inviteDetail.projectName || '项目邀请' }} · {{ inviteDetail.status }}</view>
        <button class="primary" :loading="deciding" @click="decideInvite('accept')">接受</button>
        <button class="ghost" :loading="deciding" @click="decideInvite('reject')">拒绝</button>
      </view>
    </view>

    <view v-if="panelOpen" class="drop-layer" :style="{ top: layerTop + 'px' }">
      <view class="drop-sheet" @click.stop>
        <view v-if="openSettings" class="panel">
          <view v-if="contextPrompt" class="shoot-prompt">拍照前请打开「自动获取时间、地点与天气」</view>
          <view class="panel-label">手机本地相册</view>
          <view class="setting-row">
            <view class="setting-copy">
              <text class="setting-title">自动保存无水印原图到手机相册</text>
            </view>
            <view class="wide-switch" :class="{ on: saveOriginal }" @click="toggleSaveOriginal">
              <view class="wide-switch-knob"></view>
            </view>
          </view>
          <view class="panel-label">水印与环境信息</view>
          <view class="setting-row" @click="pickWatermarkPosition">
            <text class="setting-title">默认水印位置</text>
            <text class="setting-value">{{ positionText[project?.defaultWatermarkPosition] || '左下角' }} ›</text>
          </view>
          <view class="setting-row">
            <view class="setting-copy">
              <text class="setting-title">自动获取时间、地点与天气</text>
            </view>
            <view class="wide-switch" :class="{ on: autoContext }" @click="toggleAutoContext">
              <view class="wide-switch-knob"></view>
            </view>
          </view>
        </view>
        <view v-else-if="openPicker" class="panel">
          <view class="search-row">
            <view class="search-mark"></view>
            <input v-model="projectKeyword" class="search-input" placeholder="搜索项目名称" />
          </view>
          <view v-if="!filteredActive.length" class="picker-empty">没有匹配的项目</view>
          <view v-for="item in filteredActive" :key="item.id" class="picker-row" @click="chooseProject(item)">
            <view class="picker-copy">
              <view class="picker-name">
                <text>{{ item.name }}</text>
                <text v-if="item.id === project?.id" class="current-tag">当前</text>
              </view>
              <text class="hint">{{ roleLabel(item.role) }} · {{ item.photoCount || 0 }} 张照片</text>
            </view>
            <text v-if="item.id === project?.id" class="picker-check">✓</text>
          </view>
          <button class="create-line" hover-class="none" @click="onCreate">+ 新建项目</button>
        </view>
        <view v-else-if="openInviteMenu" class="panel">
          <view class="invite-menu-head">
            <text class="setting-title">邀请加入：{{ project?.name }}</text>
            <text class="role-pill">{{ rolePreview === 'owner' ? '管理员' : '普通成员' }}</text>
          </view>
          <button class="invite-row plain" hover-class="none" open-type="share" @click="ensureInvite">
            <view class="invite-icon green">↗</view>
            <view class="invite-copy">
              <text class="setting-title">微信好友邀请</text>
              <text class="hint">转发邀请卡片，好友需主动点击接受加入</text>
            </view>
            <text class="row-arrow">›</text>
          </button>
          <view class="invite-row">
            <view class="invite-icon violet">链</view>
            <view class="invite-copy">
              <text class="setting-title">复制邀请链接</text>
              <text class="hint">复制关联有效邀请记录的邀请码（7 天内有效）</text>
            </view>
            <text class="copy-link" @click="copyInviteLink">复制</text>
          </view>
          <view class="invite-row" @click="openInviteCard">
            <view class="invite-icon gray">码</view>
            <view class="invite-copy">
              <text class="setting-title">生成邀请二维码</text>
              <text class="hint">进入邀请链接与二维码页，便于现场扫码</text>
            </view>
            <text class="row-arrow">›</text>
          </view>
          <text class="hint invite-note">管理员与普通成员均可邀请；新成员加入后可查看项目照片，仅可编辑自己拍摄的照片。</text>
          <view class="preview-row">
            <text class="hint">身份视角</text>
            <view class="preview-pills">
              <text :class="{ on: rolePreview === 'owner' }" @click="rolePreview = 'owner'">管理员</text>
              <text :class="{ on: rolePreview === 'member' }" @click="rolePreview = 'member'">普通成员</text>
            </view>
          </view>
        </view>
        <view v-else-if="openMemberSheet" class="panel">
          <view class="sheet-title">项目成员</view>
          <view v-for="member in members" :key="member.userId" class="sheet-row">
            <text>{{ member.nickName || '未命名' }} · {{ roleLabel(member.role) }}</text>
            <text v-if="canRename(member)" class="link" @click="renameMember(member)">改名</text>
            <text v-if="project.role === 'owner' && member.role !== 'owner'" class="link" @click="removeMember(member)">移除</text>
            <text v-if="project.role === 'owner' && member.role !== 'owner'" class="link" @click="transfer(member)">转让</text>
          </view>
          <button v-if="project.role !== 'owner'" class="ghost" @click="leaveProject">退出项目</button>
        </view>
        <view v-else-if="openInfo" class="panel">
          <view class="empty-desc info-first">状态：{{ archived ? '已归档' : '进行中' }} · {{ roleLabel(project?.role) }}</view>
          <view class="empty-desc">照片 {{ project?.photoCount || 0 }} 张 · 成员 {{ project?.memberCount || 0 }} 人</view>
          <button v-if="canDeleteProject" class="danger-button" @click="openInfo = false; onDelete()">删除项目</button>
        </view>
        <view v-else-if="openRecycle" class="panel">
          <view class="sheet-title">项目回收站</view>
          <view v-if="!archivedProjects.length" class="empty-desc">回收站是空的。删除的项目会放在这里。</view>
          <view v-for="item in archivedProjects" :key="item.id" class="sheet-row">
            <view class="recycle-copy" @click="viewArchived(item)">
              <text class="recycle-name">{{ item.name }}</text>
              <text class="meta recycle-meta">{{ item.photoCount || 0 }} 张照片 · {{ roleLabel(item.role) }}</text>
            </view>
            <text v-if="item.role === 'owner'" class="danger-link" @click="purgeProject(item)">彻底删除</text>
          </view>
        </view>
      </view>
      <view class="drop-rest" @click="closePanels" @touchmove.stop.prevent></view>
    </view>

    <auth-sheet :visible="authVisible" title="登录后使用水印相机" @close="authVisible = false" @success="onLoginSuccess" />
  </view>
</template>

<script setup>
import { computed, getCurrentInstance, ref } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { clearAccountSession, getUserProfile, isAccountReady, setUserProfile } from '../../utils/auth'
import AuthSheet from '../../components/auth-sheet.vue'
import { toDisplayImageSrc } from '../../utils/http-image'
import { apiJson } from '../../utils/project-api'
import { createClientRequestId, messageFor } from '../../utils/photo-payload'
import { NO_LOCATION, NO_WEATHER, formatClock } from '../../utils/watermark-layout'
import { AUTO_CONTEXT_KEY, INVITE_SCENE_KEY, SAVE_ORIGINAL_KEY, autoContextEnabled, readProjectId, saveOriginalEnabled, writeCapture, writeProjectId } from '../../utils/watermark-session'

const loggedIn = ref(false)
const loading = ref(false)
const creating = ref(false)
const inviting = ref(false)
const deciding = ref(false)
const photoLoading = ref(false)
const errorText = ref('')
const photoError = ref('')
const projects = ref([])
const project = ref(null)
const photos = ref([])
const photoTotal = ref(0)
const photoPage = ref(1)
const members = ref([])
let photoFallbackUsed = false
const openPicker = ref(false)
const openInviteMenu = ref(false)
const openRecycle = ref(false)
const openCreate = ref(false)
const openMemberSheet = ref(false)
const openInvite = ref(false)
const openSettings = ref(false)
const openInfo = ref(false)
const openCalendar = ref(false)
const selectedStart = ref('')
const selectedEnd = ref('')
const draftStart = ref('')
const draftEnd = ref('')
const expandedDays = ref({})
const isDayExpanded = (day) => Boolean(expandedDays.value[day])
const toggleDayExpand = (group) => {
  if (!group || group.items.length <= 3) return
  expandedDays.value = {
    ...expandedDays.value,
    [group.day]: !expandedDays.value[group.day]
  }
}
const visibleDayItems = (group) => (isDayExpanded(group.day) ? group.items : group.items.slice(0, 3))
const nowDate = new Date()
const calendarYear = ref(nowDate.getFullYear())
const calendarMonth = ref(nowDate.getMonth() + 1)
const weekLabels = ['日', '一', '二', '三', '四', '五', '六']
const pad2 = (n) => String(n).padStart(2, '0')
const todayStr = `${nowDate.getFullYear()}-${pad2(nowDate.getMonth() + 1)}-${pad2(nowDate.getDate())}`
const authVisible = ref(false)
const hasInvite = ref(false)
const projectName = ref('')
const projectKeyword = ref('')
const rolePreview = ref('owner')
const inviteScene = ref('')
const inviteProjectId = ref(0)
const inviteExpiresAt = ref('')
const invitePreview = ref('active')
const inviteDetail = ref(null)
const saveOriginal = ref(true)
const autoContext = ref(false)
const contextPrompt = ref(false)
const layerTop = ref(0)
const pageProxy = getCurrentInstance()?.proxy

const archived = computed(() => project.value?.status === 'archived')
const activeProjects = computed(() => projects.value.filter((item) => item.status !== 'archived'))
const archivedProjects = computed(() => projects.value.filter((item) => item.status === 'archived'))
const canDeleteProject = computed(() => loggedIn.value && project.value?.role === 'owner' && !archived.value)
const panelOpen = computed(() => openSettings.value || openPicker.value || openInviteMenu.value || openMemberSheet.value || openInfo.value || openRecycle.value)
const currentUserId = computed(() => Number(getUserProfile()?.userId) || 0)
const filteredActive = computed(() => {
  const keyword = projectKeyword.value.trim().toLowerCase()
  return activeProjects.value.filter((item) => !keyword || String(item.name || '').toLowerCase().includes(keyword))
})
const roleLabel = (role) => role === 'owner' ? '管理员' : '普通成员'
const canRename = (member) => member?.userId === currentUserId.value || project.value?.role === 'owner'
const positionText = { top_left: '左上角', top_right: '右上角', bottom_left: '左下角', bottom_right: '右下角' }
const inviterName = computed(() => {
  const name = getUserProfile()?.nickName
  return name && name !== '微信用户' ? name : '我'
})
const inviterAvatar = computed(() => {
  const avatar = getUserProfile()?.avatarUrl || ''
  return /^https?:\/\//i.test(avatar) ? avatar : '/static/logo.png'
})
const expiryText = computed(() => {
  const parsed = new Date(inviteExpiresAt.value)
  if (!inviteExpiresAt.value || Number.isNaN(parsed.getTime())) return '--'
  const pad = (value) => String(value).padStart(2, '0')
  return `${parsed.getFullYear()}.${pad(parsed.getMonth() + 1)}.${pad(parsed.getDate())} ${pad(parsed.getHours())}:${pad(parsed.getMinutes())}`
})
const projectLine = computed(() => {
  if (!loggedIn.value) return '登录后可以拍摄和查看项目照片'
  if (!project.value) return '还没有可拍摄的项目'
  const watermark = project.value.watermarkEnabled === false
    ? '水印已关闭'
    : `水印已开启（${positionText[project.value.defaultWatermarkPosition] || '左下角'}）`
  return `管理员 ${project.value.ownerNickName || '未命名'} · ${watermark}`
})
const photoDateSet = computed(() => {
  const set = new Set()
  photos.value.forEach((item) => {
    const day = formatClock(item.takenAt).slice(0, 10)
    if (day) set.add(day)
  })
  return set
})
const dateFilterLabel = computed(() => {
  if (!selectedStart.value) return '日期筛选'
  if (!selectedEnd.value || selectedEnd.value === selectedStart.value) return selectedStart.value.slice(5)
  return `${selectedStart.value.slice(5)}~${selectedEnd.value.slice(5)}`
})
const calendarYm = computed(() => `${calendarYear.value}-${pad2(calendarMonth.value)}`)
const draftRangeLabel = computed(() => {
  if (!draftStart.value) return '点选起始日'
  if (!draftEnd.value) return `${draftStart.value}（再点选结束日，点同日即单天）`
  return `${draftStart.value} 至 ${draftEnd.value}`
})
const isRangeEndpoint = (date) => date === draftStart.value || (draftEnd.value && date === draftEnd.value)
const isInDraftRange = (date) => Boolean(draftStart.value && draftEnd.value && date > draftStart.value && date < draftEnd.value)
const calendarCells = computed(() => {
  const year = calendarYear.value
  const month = calendarMonth.value
  const firstDay = new Date(year, month - 1, 1).getDay()
  const totalDays = new Date(year, month, 0).getDate()
  const cells = []
  for (let i = 0; i < firstDay; i += 1) {
    cells.push({ key: `b-${i}`, day: '', date: '' })
  }
  for (let day = 1; day <= totalDays; day += 1) {
    const date = `${year}-${pad2(month)}-${pad2(day)}`
    cells.push({ key: date, day, date })
  }
  return cells
})
const groups = computed(() => {
  const start = selectedStart.value
  const end = selectedEnd.value || selectedStart.value
  const grouped = []
  photos.value.forEach((item) => {
    const day = formatClock(item.takenAt).slice(0, 10) || '未知日期'
    if (start && (day < start || day > end)) return
    let group = grouped.find((entry) => entry.day === day)
    if (!group) {
      group = { day, items: [] }
      grouped.push(group)
    }
    group.items.push(item)
  })
  return grouped
})

const toggleSaveOriginal = () => {
  saveOriginal.value = !saveOriginal.value
  uni.setStorageSync(SAVE_ORIGINAL_KEY, saveOriginal.value ? '1' : '0')
}

const toggleAutoContext = () => {
  autoContext.value = !autoContext.value
  uni.setStorageSync(AUTO_CONTEXT_KEY, autoContext.value ? '1' : '0')
  if (autoContext.value && contextPrompt.value) {
    contextPrompt.value = false
    closePanels()
    openCamera()
  }
}

const requireLogin = () => {
  loggedIn.value = isAccountReady(getUserProfile())
  if (loggedIn.value) return true
  authVisible.value = true
  return false
}

const onLoginSuccess = async () => {
  authVisible.value = false
  loggedIn.value = true
  await loadProjects()
  await openPendingInvite()
}

const closePanels = () => {
  openSettings.value = false
  openPicker.value = false
  openInviteMenu.value = false
  openMemberSheet.value = false
  openInfo.value = false
  openRecycle.value = false
  contextPrompt.value = false
}
const placeLayer = (selector) => new Promise((resolve) => {
  const query = uni.createSelectorQuery()
  if (pageProxy) query.in(pageProxy)
  query.select(selector).boundingClientRect((rect) => {
    layerTop.value = rect && rect.bottom ? rect.bottom : layerTop.value
    resolve()
  }).exec()
})
const onRecycle = async () => {
  if (!requireLogin()) return
  const next = !openRecycle.value
  closePanels()
  if (!next) return
  await placeLayer('.recycle-btn')
  openRecycle.value = true
}
const onShoot = async () => {
  if (!requireLogin()) return
  if (!project.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  if (archived.value) {
    uni.showToast({ title: '项目已归档，只能查看', icon: 'none' })
    return
  }
  if (!autoContext.value) {
    closePanels()
    contextPrompt.value = true
    await placeLayer('.shoot')
    openSettings.value = true
    return
  }
  closePanels()
  openCamera()
}
const onSettings = async () => {
  if (!requireLogin()) return
  const next = !openSettings.value
  closePanels()
  if (!next) return
  await placeLayer('.settings')
  openSettings.value = true
}
const onOpenPicker = async () => {
  if (!requireLogin()) return
  const next = !openPicker.value
  closePanels()
  if (!next) return
  projectKeyword.value = ''
  await placeLayer('.tool-select')
  openPicker.value = true
}
const onCreate = () => {
  if (!requireLogin()) return
  closePanels()
  openCreate.value = true
}
const onMembers = async () => {
  if (!requireLogin()) return
  const next = !openMemberSheet.value
  closePanels()
  if (!next) return
  await placeLayer('.tool-members')
  await openMembers()
}
const onDelete = () => {
  if (!requireLogin() || !canDeleteProject.value || !project.value) return
  const projectId = project.value.id
  uni.showModal({
    title: '删除项目',
    content: '删除后不能再拍摄或邀请，已有照片仍可查看。',
    confirmText: '删除',
    confirmColor: '#C2410C',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await apiJson(`/api/project/${projectId}/archive`, { method: 'POST', data: {} })
        if (project.value?.id === projectId) writeProjectId(0)
        uni.showToast({ title: '项目已删除', icon: 'success' })
        openInfo.value = false
        await loadProjects()
      } catch (error) {
        uni.showToast({ title: messageFor(error, '删除失败'), icon: 'none' })
      }
    }
  })
}
const onInfo = async () => {
  if (!requireLogin() || !project.value) {
    if (loggedIn.value && !project.value) uni.showToast({ title: '请先新建或选择项目', icon: 'none' })
    return
  }
  const next = !openInfo.value
  closePanels()
  if (!next) return
  await placeLayer('.name-line')
  openInfo.value = true
}
const onOpenCalendar = () => {
  if (!requireLogin()) return
  closePanels()
  draftStart.value = selectedStart.value
  draftEnd.value = selectedEnd.value
  if (draftStart.value) {
    const [y, m] = draftStart.value.split('-').map(Number)
    if (y && m) {
      calendarYear.value = y
      calendarMonth.value = m
    }
  }
  openCalendar.value = true
}
const shiftMonth = (delta) => {
  let y = calendarYear.value
  let m = calendarMonth.value + delta
  if (m < 1) { m = 12; y -= 1 }
  if (m > 12) { m = 1; y += 1 }
  calendarYear.value = y
  calendarMonth.value = m
}
const onPickYearMonth = (event) => {
  const value = String(event?.detail?.value || '')
  const [y, m] = value.split('-').map(Number)
  if (y && m) {
    calendarYear.value = y
    calendarMonth.value = m
  }
}
const pickRangeDate = (date) => {
  if (!draftStart.value || draftEnd.value) {
    draftStart.value = date
    draftEnd.value = ''
    selectedStart.value = date
    selectedEnd.value = date
    return
  }
  if (date === draftStart.value) {
    selectedStart.value = date
    selectedEnd.value = date
    openCalendar.value = false
    return
  }
  if (date < draftStart.value) {
    draftEnd.value = draftStart.value
    draftStart.value = date
  } else {
    draftEnd.value = date
  }
  selectedStart.value = draftStart.value
  selectedEnd.value = draftEnd.value
  setTimeout(() => { openCalendar.value = false }, 220)
}
const closeCalendar = () => {
  if (draftStart.value) {
    selectedStart.value = draftStart.value
    selectedEnd.value = draftEnd.value || draftStart.value
  }
  openCalendar.value = false
}
const resetCalendarFilter = () => {
  draftStart.value = ''
  draftEnd.value = ''
  selectedStart.value = ''
  selectedEnd.value = ''
  openCalendar.value = false
}
const clearDateFilter = () => {
  selectedStart.value = ''
  selectedEnd.value = ''
}
const onInvite = () => {
  if (!requireLogin()) return
  openPendingInvite()
}
const clockText = (value) => formatClock(value).slice(11)

const loadProjects = async () => {
  loading.value = true
  errorText.value = ''
  photoFallbackUsed = false
  try {
    const body = await apiJson('/api/project?page=1&pageSize=50')
    projects.value = Array.isArray(body.items) ? body.items : []
    const saved = readProjectId()
    const matched = projects.value.find((item) => item.id === saved)
    const active = projects.value.filter((item) => item.status !== 'archived')
    project.value = matched || active[0] || null
    if (project.value) writeProjectId(project.value.id)
    else writeProjectId(0)
  } catch (error) {
    errorText.value = messageFor(error, '项目加载失败')
    if (error.statusCode === 401) {
      clearAccountSession()
      loggedIn.value = false
      errorText.value = ''
      authVisible.value = true
    }
  } finally {
    loading.value = false
  }
  if (project.value) await loadPhotos()
}

const loadPhotos = async (append = false) => {
  if (!project.value) return
  photoLoading.value = true
  photoError.value = ''
  const page = append ? photoPage.value + 1 : 1
  try {
    const body = await apiJson(`/api/project/${project.value.id}/photos?page=${page}&pageSize=20`)
    const items = await Promise.all((Array.isArray(body.items) ? body.items : []).map(async (item) => ({
      ...item,
      displayUrl: await toDisplayImageSrc(item.previewUrl)
    })))
    photos.value = append ? photos.value.concat(items) : items
    photoPage.value = page
    photoTotal.value = Number(body.total) || photos.value.length
    project.value = { ...project.value, photoCount: photoTotal.value }
  } catch (error) {
    if (!append) photos.value = []
    photoError.value = messageFor(error, '照片加载失败')
    if (error.statusCode === 401) {
      clearAccountSession()
      loggedIn.value = false
      photoError.value = ''
      authVisible.value = true
      return
    }
    if (error.statusCode === 403 && !photoFallbackUsed) {
      const failedId = project.value?.id
      writeProjectId(0)
      const next = projects.value.find((item) => item.id !== failedId)
      project.value = next || null
      if (next) {
        photoFallbackUsed = true
        writeProjectId(next.id)
        await loadPhotos()
      }
    }
  } finally {
    photoLoading.value = false
  }
}

const chooseProject = async (item) => {
  project.value = item
  writeProjectId(item.id)
  openPicker.value = false
  openRecycle.value = false
  photos.value = []
  photoTotal.value = 0
  selectedStart.value = ''
  selectedEnd.value = ''
  expandedDays.value = {}
  await loadPhotos()
}

const viewArchived = (item) => chooseProject(item)

const purgeProject = (item) => {
  if (item?.role !== 'owner') {
    uni.showToast({ title: '只有管理员可以彻底删除', icon: 'none' })
    return
  }
  uni.showModal({
    title: '彻底删除',
    content: '项目和拍摄记录会永久删除，不能恢复。收藏库里的原图仍会保留。',
    confirmText: '彻底删除',
    confirmColor: '#C2410C',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await apiJson(`/api/project/${item.id}/purge`, { method: 'POST', data: {} })
        if (project.value?.id === item.id) writeProjectId(0)
        uni.showToast({ title: '已彻底删除', icon: 'success' })
        await loadProjects()
      } catch (error) {
        uni.showToast({ title: messageFor(error, '删除失败'), icon: 'none' })
      }
    }
  })
}

const createProject = async () => {
  const name = projectName.value.trim()
  if (!name) {
    uni.showToast({ title: '请输入项目名称', icon: 'none' })
    return
  }
  creating.value = true
  try {
    const created = await apiJson('/api/project', { method: 'POST', data: { name, watermarkEnabled: true, defaultWatermarkPosition: 'bottom_left' } })
    projectName.value = ''
    openCreate.value = false
    openPicker.value = false
    writeProjectId(created.id)
    await loadProjects()
  } catch (error) {
    uni.showToast({ title: messageFor(error, '创建失败'), icon: 'none' })
  } finally {
    creating.value = false
  }
}

const openCamera = () => {
  if (!project.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  if (archived.value) {
    uni.showToast({ title: '项目已归档，只能查看', icon: 'none' })
    return
  }
  const name = encodeURIComponent(project.value.name || '')
  uni.navigateTo({ url: `/pages/watermark/camera?projectId=${project.value.id}&projectName=${name}` })
}

const importLocalPhoto = async () => {
  if (!requireLogin()) return
  if (!project.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  if (archived.value) {
    uni.showToast({ title: '项目已归档，只能查看', icon: 'none' })
    return
  }
  try {
    const picked = await new Promise((resolve, reject) => {
      uni.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: ['album'],
        sizeType: ['original'],
        success: resolve,
        fail: reject
      })
    })
    const path = picked?.tempFiles?.[0]?.tempFilePath
    if (!path) return
    let widthPx = 0
    let heightPx = 0
    try {
      const info = await Promise.race([
        new Promise((resolve, reject) => {
          uni.getImageInfo({ src: path, success: resolve, fail: reject })
        }),
        new Promise((resolve) => setTimeout(() => resolve(null), 800))
      ])
      widthPx = info?.width || 0
      heightPx = info?.height || 0
    } catch (error) {
      widthPx = 0
      heightPx = 0
    }
    writeCapture({
      projectId: project.value.id,
      path,
      widthPx,
      heightPx,
      takenAt: new Date().toISOString(),
      clientRequestId: createClientRequestId(),
      watermarkPosition: project.value.defaultWatermarkPosition || 'bottom_left',
      watermarkVisible: true,
      draftNote: '',
      gpsStatus: 'empty',
      previewLocationText: NO_LOCATION,
      previewWeatherText: NO_WEATHER,
      source: 'import'
    })
    uni.navigateTo({ url: `/pages/watermark/preview?projectId=${project.value.id}` })
  } catch (error) {
    if (!/cancel/i.test(String(error?.errMsg || ''))) {
      uni.showToast({ title: '选择照片失败', icon: 'none' })
    }
  }
}

const openDetail = (item) => {
  uni.navigateTo({ url: `/pages/watermark/detail?projectId=${project.value.id}&photoId=${item.id}` })
}

const onInvitePlus = async () => {
  if (!requireLogin()) return
  if (!project.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  if (archived.value) {
    uni.showToast({ title: '项目已归档，不能邀请', icon: 'none' })
    return
  }
  const next = !openInviteMenu.value
  closePanels()
  if (!next) return
  rolePreview.value = project.value.role === 'owner' ? 'owner' : 'member'
  await placeLayer('.add-badge')
  openInviteMenu.value = true
}

const ensureInvite = async () => {
  if (!project.value) return false
  if (inviteScene.value && inviteProjectId.value === project.value.id) return true
  await createInvite()
  return Boolean(inviteScene.value)
}

const copyInviteLink = async () => {
  if (!await ensureInvite()) return
  copyScene()
}

const openInviteCard = async () => {
  openInviteMenu.value = false
  invitePreview.value = 'active'
  openInvite.value = true
  await ensureInvite()
}

const renameMember = (member) => {
  if (!canRename(member)) return
  uni.showModal({
    title: '修改名字',
    editable: true,
    placeholderText: '请输入名字',
    content: member.nickName || '',
    success: async (result) => {
      if (!result.confirm) return
      const nickName = String(result.content || '').trim()
      if (!nickName) {
        uni.showToast({ title: '请输入名字', icon: 'none' })
        return
      }
      try {
        await apiJson(`/api/project/${project.value.id}/members/${member.userId}/name`, { method: 'PATCH', data: { nickName } })
        if (member.userId === currentUserId.value) {
          const profile = getUserProfile() || {}
          setUserProfile({ ...profile, nickName })
        }
        uni.showToast({ title: '名字已更新', icon: 'success' })
        await openMembers()
        await loadProjects()
      } catch (error) {
        uni.showToast({ title: messageFor(error, '修改失败'), icon: 'none' })
      }
    }
  })
}

const pickWatermarkPosition = () => {
  if (!project.value) return
  if (project.value.role !== 'owner') {
    uni.showToast({ title: '只有管理员可以修改', icon: 'none' })
    return
  }
  const codes = ['top_left', 'top_right', 'bottom_left', 'bottom_right']
  uni.showActionSheet({
    itemList: codes.map((code) => positionText[code]),
    success: async (result) => {
      try {
        await apiJson(`/api/project/${project.value.id}`, { method: 'PATCH', data: { defaultWatermarkPosition: codes[result.tapIndex] } })
        await loadProjects()
      } catch (error) {
        uni.showToast({ title: messageFor(error, '修改失败'), icon: 'none' })
      }
    }
  })
}

const openMembers = async () => {
  if (!project.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  try {
    const body = await apiJson(`/api/project/${project.value.id}/members`)
    members.value = Array.isArray(body.items) ? body.items : []
    openMemberSheet.value = true
  } catch (error) {
    uni.showToast({ title: messageFor(error), icon: 'none' })
  }
}

const removeMember = (member) => {
  uni.showModal({
    title: '移除成员',
    content: `移除后，${member.nickName || '该成员'}将不能再查看照片。`,
    success: async (result) => {
      if (!result.confirm) return
      try {
        await apiJson(`/api/project/${project.value.id}/members/${member.userId}/remove`, { method: 'POST', data: {} })
        await openMembers()
      } catch (error) {
        uni.showToast({ title: messageFor(error), icon: 'none' })
      }
    }
  })
}

const transfer = (member) => {
  uni.showModal({
    title: '转让管理员',
    content: `把管理员转给${member.nickName || '该成员'}后，你将成为普通成员。`,
    success: async (result) => {
      if (!result.confirm) return
      try {
        await apiJson(`/api/project/${project.value.id}/owner/transfer`, { method: 'POST', data: { targetUserId: member.userId } })
        await loadProjects()
        openMemberSheet.value = false
      } catch (error) {
        uni.showToast({ title: messageFor(error), icon: 'none' })
      }
    }
  })
}

const leaveProject = async () => {
  try {
    await apiJson(`/api/project/${project.value.id}/members/leave`, { method: 'POST', data: {} })
    writeProjectId(0)
    openMemberSheet.value = false
    await loadProjects()
  } catch (error) {
    uni.showToast({ title: messageFor(error), icon: 'none' })
  }
}

const createInvite = async () => {
  inviting.value = true
  try {
    const body = await apiJson(`/api/project/${project.value.id}/invites`, { method: 'POST', data: {} })
    inviteScene.value = body.scene || ''
    inviteExpiresAt.value = body.expiresAt || ''
    inviteProjectId.value = project.value?.id || 0
  } catch (error) {
    uni.showToast({ title: messageFor(error, '邀请创建失败'), icon: 'none' })
  } finally {
    inviting.value = false
  }
}

const copyScene = () => {
  uni.setClipboardData({ data: inviteScene.value })
}

const tokenFromScene = (scene) => (scene || '').startsWith('pinv_') ? scene.slice(5) : ''

const openPendingInvite = async () => {
  const scene = uni.getStorageSync(INVITE_SCENE_KEY)
  const token = tokenFromScene(scene)
  if (!token || !loggedIn.value) return
  try {
    inviteDetail.value = await apiJson(`/api/project/invites/${token}`)
  } catch (error) {
    uni.removeStorageSync(INVITE_SCENE_KEY)
    uni.showToast({ title: messageFor(error, '邀请无法打开'), icon: 'none' })
    return
  }
  try {
    await apiJson(`/api/project/invites/${token}/message`, { method: 'POST', data: {} })
  } catch (error) {
    // 打开邀请不代表加入。消息记录失败时仍让用户决定接受或拒绝。
  }
}

const decideInvite = async (decision) => {
  const scene = uni.getStorageSync(INVITE_SCENE_KEY)
  const token = tokenFromScene(scene)
  if (!token) return
  deciding.value = true
  try {
    await apiJson(`/api/project/invites/${token}/${decision}`, { method: 'POST', data: {} })
    uni.removeStorageSync(INVITE_SCENE_KEY)
    inviteDetail.value = null
    await loadProjects()
  } catch (error) {
    uni.showToast({ title: messageFor(error), icon: 'none' })
  } finally {
    deciding.value = false
  }
}

onShareAppMessage(() => ({
  title: project.value?.name ? `邀请你加入${project.value.name}` : '邀请你加入拍照项目',
  path: inviteScene.value ? `/pages/index/index?scene=${encodeURIComponent(inviteScene.value)}` : '/pages/watermark/home'
}))

onShow(async () => {
  loggedIn.value = isAccountReady(getUserProfile())
  saveOriginal.value = saveOriginalEnabled()
  autoContext.value = autoContextEnabled()
  hasInvite.value = Boolean(uni.getStorageSync(INVITE_SCENE_KEY))
  if (!loggedIn.value) {
    project.value = null
    photos.value = []
    return
  }
  await loadProjects()
  await openPendingInvite()
})
</script>

<style scoped>
.page { min-height: 100vh; background: #F7F6FB; padding: 28rpx 32rpx 48rpx; box-sizing: border-box; color: #1C1F26; }
.project-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24rpx; }
.project-copy { position: relative; flex: 1; min-width: 0; }
.eyebrow { font-size: 24rpx; line-height: 1.4; color: #A1A8B3; }
.name-line { display: flex; align-items: center; gap: 12rpx; max-width: 100%; margin-top: 10rpx; }
.project-name { min-width: 0; overflow: hidden; font-size: 44rpx; line-height: 1.25; font-weight: 700; color: #1A1D23; white-space: nowrap; text-overflow: ellipsis; }
.chevron { width: 14rpx; height: 14rpx; border-right: 4rpx solid #64748b; border-bottom: 4rpx solid #64748b; transform: rotate(45deg) translateY(-4rpx); flex-shrink: 0; }
.chevron.open { transform: rotate(225deg) translateY(-2rpx); }
.drop-layer { position: fixed; left: 0; right: 0; bottom: 0; z-index: 18; display: flex; flex-direction: column; background: rgba(116, 121, 133, 0.46); }
.drop-sheet { flex: 0 1 auto; width: 100%; max-height: 72%; overflow-y: auto; padding: 28rpx 32rpx 36rpx; background: #F6F3FB; box-sizing: border-box; }
.drop-rest { flex: 1; min-height: 160rpx; }
.dropdown { position: absolute; left: 0; right: 0; top: 92rpx; z-index: 2; max-height: 420rpx; overflow: auto; padding: 8rpx 0; border-radius: 20rpx; background: #fff; box-shadow: 0 12rpx 40rpx rgba(15, 23, 42, 0.12); }
.dropdown-row, .dropdown-empty { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 20rpx 24rpx; font-size: 28rpx; color: #1f2329; }
.dropdown-empty { color: #64748b; }
.dropdown-name { flex: 1; min-width: 0; }
.dropdown-current { color: #7965D8; font-size: 24rpx; }
.meta, .empty-desc, .readonly, .hint { margin-top: 8rpx; font-size: 26rpx; line-height: 1.5; color: #8B93A1; }
.readonly { color: #9a6700; }
.pill, .settings, .shoot, .primary, .ghost, .copy-btn, .share-btn { margin: 0; border-radius: 999rpx; }
.pill, .settings { flex-shrink: 0; display: flex; align-items: center; justify-content: center; gap: 8rpx; background: #fff; color: #4B5563; font-size: 26rpx; }
.pill { height: 64rpx; padding: 0 28rpx; color: #7965D8; font-size: 26rpx; }
.pill::after, .settings::after, .shoot::after, .primary::after, .ghost::after, .danger-button::after, .copy-btn::after, .share-btn::after { border: none; }
.shoot-row { display: flex; gap: 20rpx; margin-top: 40rpx; }
.shoot { flex: 1; display: flex; align-items: center; justify-content: center; gap: 14rpx; height: 96rpx; background: #7965D8; color: #fff; font-size: 32rpx; font-weight: 700; }
.cam { position: relative; width: 36rpx; height: 30rpx; }
.cam-top { position: absolute; left: 8rpx; top: 0; width: 14rpx; height: 8rpx; border: 3rpx solid #fff; border-bottom: 0; border-radius: 4rpx 4rpx 0 0; box-sizing: border-box; }
.cam-body { position: absolute; left: 0; right: 0; bottom: 0; height: 22rpx; display: flex; align-items: center; justify-content: center; border: 3rpx solid #fff; border-radius: 6rpx; box-sizing: border-box; }
.cam-lens { width: 10rpx; height: 10rpx; border: 3rpx solid #fff; border-radius: 50%; }
.settings { width: 188rpx; height: 96rpx; border: 2rpx solid #E6E8EF; color: #4B5563; font-size: 30rpx; background: #fff; }
.gear-icon, .tool-icon { width: 36rpx; height: 36rpx; flex-shrink: 0; }
.tool-row { display: flex; align-items: center; justify-content: space-between; margin-top: 44rpx; color: #3D4654; font-size: 28rpx; }
.tool, .tool-main, .member-tool { display: flex; align-items: center; gap: 10rpx; }
.tool-plus-box, .tool-cal-box { position: relative; width: 36rpx; height: 36rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.tool-plus-h, .tool-plus-v { position: absolute; border-radius: 999rpx; background: #4B5563; }
.tool-plus-h { width: 26rpx; height: 4rpx; }
.tool-plus-v { width: 4rpx; height: 26rpx; }
.tool-date.active { color: #7965D8; font-weight: 600; }
.cal-ring { position: absolute; top: 2rpx; width: 4rpx; height: 8rpx; border-radius: 2rpx; background: #4B5563; }
.cal-ring.left { left: 10rpx; }
.cal-ring.right { right: 10rpx; }
.cal-body { position: relative; width: 30rpx; height: 26rpx; margin-top: 6rpx; border: 3rpx solid #4B5563; border-radius: 6rpx; box-sizing: border-box; }
.cal-bar { width: 100%; height: 6rpx; border-bottom: 2rpx solid #4B5563; box-sizing: border-box; }
.cal-dots { display: flex; justify-content: space-around; align-items: center; padding-top: 4rpx; }
.cal-dot { width: 4rpx; height: 4rpx; border-radius: 50%; background: #4B5563; }
.tool-date.active .cal-ring, .tool-date.active .cal-dot { background: #7965D8; }
.tool-date.active .cal-body, .tool-date.active .cal-bar { border-color: #7965D8; }
.add-badge { position: relative; width: 32rpx; height: 32rpx; border-radius: 50%; background: #7965D8; }
.add-h, .add-v { position: absolute; left: 8rpx; top: 14rpx; width: 16rpx; height: 4rpx; border-radius: 4rpx; background: #fff; }
.add-v { left: 14rpx; top: 8rpx; width: 4rpx; height: 16rpx; }
.calendar-sheet { padding-bottom: calc(32rpx + env(safe-area-inset-bottom)); }
.cal-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; }
.cal-head-right { display: flex; align-items: center; gap: 16rpx; }
.cal-sub { font-size: 22rpx; color: #7965D8; }
.cal-reset { font-size: 24rpx; color: #8B93A1; }
.cal-nav { display: flex; align-items: center; justify-content: space-between; margin-top: 24rpx; padding: 0 16rpx; }
.cal-nav-btn { width: 64rpx; height: 64rpx; border-radius: 50%; background: #F6F3FB; color: #4B5563; font-size: 38rpx; line-height: 60rpx; text-align: center; }
.cal-month-btn { display: flex; align-items: center; gap: 8rpx; padding: 8rpx 20rpx; border-radius: 999rpx; background: #F6F3FB; }
.cal-month { font-size: 30rpx; font-weight: 700; color: #1A1D23; }
.cal-month-arrow { font-size: 24rpx; color: #7965D8; }
.cal-week { display: flex; margin-top: 20rpx; }
.cal-week-cell { flex: 1; text-align: center; font-size: 24rpx; color: #8B93A1; }
.cal-grid { display: flex; flex-wrap: wrap; margin-top: 12rpx; }
.cal-cell { position: relative; width: 14.2857%; height: 80rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; border-radius: 16rpx; box-sizing: border-box; }
.cal-cell.blank { pointer-events: none; }
.cal-cell.today .cal-day { color: #7965D8; font-weight: 700; }
.cal-cell.in-range { background: #EDE9FE; border-radius: 0; }
.cal-cell.in-range .cal-day { color: #5B21B6; font-weight: 600; }
.cal-cell.selected { background: #7965D8; }
.cal-cell.selected .cal-day { color: #fff; font-weight: 700; }
.info-first { margin-top: 0; }
.cal-day { font-size: 28rpx; color: #1A1D23; line-height: 1.2; }
.cal-has-photo { width: 8rpx; height: 8rpx; margin-top: 4rpx; border-radius: 50%; background: #7965D8; }
.cal-cell.selected .cal-has-photo { background: #fff; }
.cal-actions { display: flex; gap: 20rpx; margin-top: 24rpx; }
.cal-btn { flex: 1; height: 80rpx; line-height: 80rpx; font-size: 28rpx; }
.danger-button { margin-top: 20rpx; border-radius: 999rpx; background: #fff; color: #C2410C; }
.danger-button::after { border: none; }
.invite-banner { margin-top: 20rpx; color: #7965D8; font-size: 26rpx; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-top: 48rpx; }
.section-left { display: flex; align-items: center; gap: 14rpx; }
.import-btn { height: 42rpx; padding: 0 18rpx; border-radius: 999rpx; background: #EDE9FE; color: #7965D8; font-size: 22rpx; font-weight: 600; line-height: 42rpx; }
.section-title, .empty-title, .sheet-title { font-size: 36rpx; font-weight: 700; color: #1A1D23; }
.section-more { display: flex; align-items: center; gap: 6rpx; color: #7965D8; font-size: 28rpx; }
.more-chevron { width: 12rpx; height: 12rpx; border-right: 3rpx solid #7965D8; border-bottom: 3rpx solid #7965D8; transform: rotate(-45deg); }
.card { margin-top: 20rpx; padding: 28rpx; border-radius: 28rpx; background: #fff; }
.day-block { margin-top: 24rpx; }
.day-line { display: flex; justify-content: space-between; align-items: center; color: #8B93A1; font-size: 26rpx; }
.day-count.expandable { color: #7965D8; font-weight: 600; padding: 4rpx 0 4rpx 16rpx; }
.invite-sheet { padding-bottom: 40rpx; }
.invite-head, .inviter-row, .link-row, .preview-row { display: flex; align-items: center; gap: 16rpx; }
.invite-head { justify-content: flex-start; }
.invite-head .sheet-title { flex: 0 1 auto; }
.status-pill, .preview-pills text { padding: 6rpx 18rpx; border-radius: 999rpx; font-size: 22rpx; }
.status-pill.on, .preview-pills text.on { background: #EDE9FE; color: #7965D8; }
.status-pill.off, .preview-pills text { background: #F3F4F6; color: #6B7280; }
.invite-close { margin-left: auto; color: #6B7280; font-size: 40rpx; line-height: 1; }
.inviter-row { margin-top: 28rpx; }
.inviter-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #F3F4F6; }
.inviter-copy { flex: 1; min-width: 0; }
.inviter-name, .invite-project { font-size: 32rpx; font-weight: 700; color: #1A1D23; }
.invite-project, .center { margin-top: 28rpx; text-align: center; }
.qr-box { margin-top: 24rpx; padding: 28rpx; border-radius: 24rpx; background: #F7F6FB; text-align: center; }
.qr-box.faded { opacity: 0.45; }
.qr-frame { width: 280rpx; margin: 0 auto; padding: 28rpx 0; border: 2rpx dashed #D5D8E0; border-radius: 20rpx; color: #6B7280; font-size: 24rpx; }
.qr-placeholder { width: 72rpx; height: 72rpx; margin: 0 auto 12rpx; background:
  linear-gradient(#1A1D23, #1A1D23) 0 0 / 18rpx 18rpx no-repeat,
  linear-gradient(#1A1D23, #1A1D23) 100% 0 / 18rpx 18rpx no-repeat,
  linear-gradient(#1A1D23, #1A1D23) 0 100% / 18rpx 18rpx no-repeat,
  linear-gradient(#fff, #fff) 50% 50% / 28rpx 28rpx no-repeat,
  #1A1D23; }
.link-row { margin-top: 24rpx; padding: 8rpx 8rpx 8rpx 24rpx; border-radius: 999rpx; background: #F7F6FB; }
.link-code { flex: 1; min-width: 0; overflow: hidden; color: #4B5563; font-size: 24rpx; white-space: nowrap; text-overflow: ellipsis; }
.copy-btn { height: 64rpx; padding: 0 24rpx; background: #EDE9FE; color: #7965D8; font-size: 24rpx; line-height: 64rpx; }
.share-btn { width: 100%; height: 88rpx; margin-top: 20rpx; font-size: 30rpx; line-height: 88rpx; }
.preview-row { justify-content: space-between; margin-top: 28rpx; }
.preview-pills { display: flex; gap: 12rpx; }
.grid { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 12rpx; }
.tile { position: relative; width: calc((100% - 24rpx) / 3); height: 220rpx; overflow: hidden; border-radius: 16rpx; background: #fff; }
.tile-image { width: 100%; height: 100%; }
.tile-caption { position: absolute; left: 0; right: 0; bottom: 0; padding: 8rpx; background: rgba(15, 23, 42, 0.72); color: #fff; }
.tile-note, .tile-time { display: block; font-size: 20rpx; line-height: 1.4; }
.primary { background: #7965D8; color: #fff; }
.ghost { background: #F3F0FF; color: #7965D8; }
.more { margin-top: 24rpx; }
.switch-row { display: flex; justify-content: space-between; align-items: center; margin-top: 16rpx; font-size: 26rpx; color: #334155; }
.mask { position: fixed; inset: 0; z-index: 20; display: flex; align-items: flex-end; background: rgba(15, 23, 42, 0.45); }
.sheet { width: 100%; max-height: 70vh; overflow: auto; padding: 32rpx; border-radius: 28rpx 28rpx 0 0; background: #fff; box-sizing: border-box; }
.sheet-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 22rpx 0; border-bottom: 1rpx solid #EAE7F2; font-size: 28rpx; }
.recycle-copy { flex: 1; min-width: 0; display: flex; flex-direction: row; align-items: center; gap: 20rpx; }
.recycle-name { flex-shrink: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recycle-meta { flex-shrink: 0; margin-left: 8rpx; white-space: nowrap; }
.danger-link { flex-shrink: 0; color: #C2410C; }
.link { color: #7965D8; margin-left: 16rpx; }
.input, .scene { margin: 20rpx 0; padding: 20rpx; border-radius: 20rpx; background: #F8F7FC; font-size: 28rpx; }
.create-submit { height: 64rpx; margin-top: 8rpx; font-size: 26rpx; line-height: 64rpx; }
.settings.open { border-color: #7965D8; color: #7965D8; }
.chevron.up { width: 12rpx; height: 12rpx; border-right: 3rpx solid #7965D8; border-bottom: 3rpx solid #7965D8; transform: rotate(225deg); }
.panel { padding: 0 0 8rpx; background: #F6F3FB; }
.shoot-prompt { margin-top: 0; padding: 16rpx 20rpx; border-radius: 16rpx; background: #F3F0FF; color: #7965D8; font-size: 26rpx; line-height: 1.5; }
.wide-switch { position: relative; width: 84rpx; height: 44rpx; flex-shrink: 0; border-radius: 999rpx; background: #D8DCE6; transition: background-color 0.2s ease; }
.wide-switch.on { background: #7965D8; }
.wide-switch-knob { position: absolute; left: 4rpx; top: 4rpx; width: 36rpx; height: 36rpx; border-radius: 50%; background: #fff; box-shadow: 0 2rpx 6rpx rgba(15, 23, 42, 0.16); transition: transform 0.2s ease; }
.wide-switch.on .wide-switch-knob { transform: translateX(40rpx); }
.panel-label { margin-top: 20rpx; color: #A1A8B3; font-size: 24rpx; }
.panel > .panel-label:first-child { margin-top: 0; }
.setting-row, .invite-row, .picker-row, .invite-menu-head { display: flex; align-items: center; gap: 16rpx; }
.setting-row { justify-content: space-between; padding: 22rpx 0; border-bottom: 1rpx solid #F1F2F6; }
.setting-copy, .invite-copy, .picker-copy { flex: 1; min-width: 0; }
.setting-title { display: block; color: #1A1D23; font-size: 30rpx; }
.setting-value { color: #8B93A1; font-size: 26rpx; }
.setting-ok { color: #16A34A; font-size: 26rpx; }
.search-row { display: flex; align-items: center; gap: 12rpx; margin-top: 0; padding: 0 20rpx; border-radius: 16rpx; background: #F6F7FB; }
.search-mark { width: 22rpx; height: 22rpx; border: 3rpx solid #A1A8B3; border-radius: 50%; }
.search-input { flex: 1; height: 72rpx; font-size: 28rpx; }
.picker-row { padding: 22rpx 0; border-bottom: 1rpx solid #F1F2F6; }
.picker-name { display: flex; align-items: center; gap: 12rpx; color: #1A1D23; font-size: 32rpx; font-weight: 700; }
.current-tag { padding: 2rpx 12rpx; border-radius: 999rpx; background: #EDE9FE; color: #7965D8; font-size: 22rpx; font-weight: 500; }
.picker-check { color: #7965D8; font-size: 32rpx; }
.picker-empty { padding: 28rpx 0; color: #8B93A1; font-size: 26rpx; }
.create-line { margin-top: 20rpx; border-radius: 999rpx; background: #fff; color: #7965D8; font-size: 30rpx; }
.create-line::after { border: 2rpx solid #E4E0F5; }
.invite-menu-head { justify-content: space-between; padding-top: 0; }
.role-pill { padding: 6rpx 16rpx; border-radius: 999rpx; background: #EDE9FE; color: #7965D8; font-size: 22rpx; }
.invite-row { width: 100%; padding: 20rpx 0; border-bottom: 1rpx solid #F1F2F6; background: transparent; text-align: left; }
.invite-row.plain { margin: 0; line-height: 1.4; }
.invite-row.plain::after { border: none; }
.invite-icon { width: 64rpx; height: 64rpx; border-radius: 50%; color: #fff; font-size: 28rpx; line-height: 64rpx; text-align: center; }
.invite-icon.green { background: #DCFCE7; color: #16A34A; }
.invite-icon.violet { background: #EDE9FE; color: #7965D8; }
.invite-icon.gray { background: #F3F4F6; color: #4B5563; }
.row-arrow, .copy-link { color: #7965D8; font-size: 28rpx; }
.invite-note { display: block; margin-top: 16rpx; }
</style>
