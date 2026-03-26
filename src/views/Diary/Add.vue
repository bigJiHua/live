<template>
  <div class="page-diary-add">
    <van-nav-bar title="记心情" left-arrow @click-left="$router.back()">
      <template #right>
        <van-button size="small" type="primary" round @click="handleSave"
          >发布</van-button
        >
      </template>
    </van-nav-bar>

    <div class="editor-container">
      <van-field
        v-model="content"
        rows="10"
        autosize
        type="textarea"
        placeholder="这一刻的想法..."
        :border="false"
        class="diary-input"
      />

      <div class="upload-section">
        <van-uploader
          v-model="fileList"
          multiple
          :max-count="3"
          preview-size="80px"
        />
      </div>

      <van-cell-group inset class="meta-cells">
        <van-cell
          title="今日心情"
          is-link
          :value="mood"
          @click="showMood = true"
          icon="smile-o"
        />
        <van-cell
          title="当前位置"
          is-link
          :value="location"
          icon="location-o"
        />
      </van-cell-group>
    </div>

    <van-action-sheet
      v-model:show="showMood"
      :actions="moodActions"
      @select="onMoodSelect"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'

const router = useRouter()
const content = ref('')
const fileList = ref([])
const mood = ref('开心 😊')
const location = ref('新宿区')
const showMood = ref(false)

const moodActions = [
  { name: '开心 😊' },
  { name: '平静 😐' },
  { name: '难过 😢' },
  { name: '累 😫' },
]

const onMoodSelect = (item) => {
  mood.value = item.name
  showMood.value = false
}

const handleSave = () => {
  if (!content.value) return
  showSuccessToast('已存入日记')
  setTimeout(() => router.back(), 1500)
}
</script>

<style scoped>
.page-diary-add {
  min-height: 100vh;
  background: #fff;
}
.diary-input {
  font-size: 16px;
  padding: 20px;
}
.upload-section {
  padding: 0 20px;
  margin-bottom: 20px;
}
.meta-cells {
  margin-top: 10px;
}
</style>
