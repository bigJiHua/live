<template>
  <div class="page limit-manage">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
    <van-tabs v-model:active="activeTab" sticky>
      <!-- 卡片额度 -->
      <van-tab title="卡片额度">
        <div class="section">
          <!-- 共享卡：按共享池分组收纳（父目录 = 银行） -->
          <van-collapse v-model="activeGroupNames" v-if="sharedGroups.length">
            <van-collapse-item
              v-for="g in sharedGroups"
              :key="g.poolId"
              :name="g.poolId"
              :title="`${g.poolName || '共享池'} · ${g.cards.length} 张卡`"
            >
              <van-cell-group :border="false">
                <van-cell
                  v-for="card in g.cards"
                  :key="card.id"
                  :title="cardNameOf(card)"
                  :label="`尾号 ${card.last4_no || '--'}`"
                  clickable
                  @click="openCardEdit(card)"
                >
                  <template #value>
                    <div class="limits">
                      <span>固额 {{ fmt(card.credit_limit) }}</span>
                      <span>临额 {{ fmt(card.temp_limit) }}</span>
                    </div>
                  </template>
                </van-cell>
              </van-cell-group>
            </van-collapse-item>
          </van-collapse>

          <!-- 独立额度：未共享的卡单独一组 -->
          <template v-if="standaloneCards.length">
            <div class="standalone-divider">——— 独立额度 ———</div>
            <van-cell-group inset v-for="card in standaloneCards" :key="card.id" class="card-cell">
              <van-cell
                :title="cardNameOf(card)"
                :label="`尾号 ${card.last4_no || '--'} · 独立额度`"
                @click="openCardEdit(card)"
              >
                <template #value>
                  <div class="limits">
                    <span>固额 {{ fmt(card.credit_limit) }}</span>
                    <span>临额 {{ fmt(card.temp_limit) }}</span>
                  </div>
                </template>
              </van-cell>
            </van-cell-group>
          </template>

          <van-empty v-if="!creditCards.length" description="暂无信用卡" />
        </div>
      </van-tab>

      <!-- 共享额度池 -->
      <van-tab title="共享额度池">
        <div class="section">
          <div class="pool-actions">
            <van-button type="primary" block round @click="openPoolCreate">＋ 新建同银行共享额度池</van-button>
          </div>
          <van-cell-group inset v-for="pool in pools" :key="pool.id" class="pool-cell">
            <van-cell :title="pool.bank_name || '共享池'" :label="`${poolCardCount(pool.id)} 张卡共享${Number(pool.credit_report_merged) ? ' · 信报合一' : ''}`">
              <template #value>
                <div class="limits">
                  <span>共享固额 {{ fmt(pool.total_credit_limit) }}</span>
                  <span>共享临额 {{ fmt(pool.total_temp_limit) }}</span>
                </div>
              </template>
            </van-cell>
            <div class="pool-btns">
              <van-button size="small" plain type="primary" @click="openPoolEdit(pool)">编辑额度</van-button>
              <van-button size="small" plain type="success" @click="openBatchPool(pool)">批量归池</van-button>
              <van-button size="small" plain type="danger" @click="removePool(pool)">删除</van-button>
            </div>
          </van-cell-group>
          <van-empty v-if="!pools.length" description="暂无共享池（同银行多卡可共享一个额度）" />
        </div>
      </van-tab>
    </van-tabs>
    </van-pull-refresh>

    <!-- 卡片额度编辑 -->
    <van-popup v-model:show="cardEditShow" position="bottom" round :style="{ paddingBottom: 'env(safe-area-inset-bottom)' }">
      <div class="edit-pop">
        <div class="edit-title">{{ cardNameOf(editingCard) }} · 额度设置</div>
        <van-field v-model="form.creditLimit" type="number" label="固定额度" placeholder="0.00" />
        <van-field v-model="form.tempLimit" type="number" label="临时额度" placeholder="0.00" />
        <van-field v-model="form.pointsRate" type="number" label="积分倍率" placeholder="1" />
        <van-cell title="归入共享池" :value="form.poolName || '独立（不共享）'" is-link @click="openPoolPicker" />
        <div class="edit-actions">
          <van-button block type="primary" @click="saveCard">保存并同步账单</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 共享池选择 -->
    <van-popup v-model:show="poolPickerShow" position="bottom" round>
      <van-picker
        :columns="poolPickerColumns"
        @confirm="onPoolPick"
        @cancel="poolPickerShow = false"
        :show-toolbar="true"
        title="选择共享额度池"
      />
    </van-popup>

    <!-- 共享池编辑 -->
    <van-popup v-model:show="poolEditShow" position="bottom" round :style="{ paddingBottom: 'env(safe-area-inset-bottom)' }">
      <div class="edit-pop">
        <div class="edit-title">{{ poolForm.id ? '编辑共享池' : '新建共享池' }}</div>
        <van-cell
          v-if="!poolForm.id"
          title="银行名称"
          :value="poolForm.bankName || '请选择'"
          is-link
          @click="openBankPicker"
        />
        <van-field v-else v-model="poolForm.bankName" label="银行名称" placeholder="如 农业银行" />
        <van-field v-model="poolForm.totalCreditLimit" type="number" label="共享固定额度" placeholder="0.00" />
        <van-field v-model="poolForm.totalTempLimit" type="number" label="共享临时额度" placeholder="0.00" />
        <van-cell title="信报合一" label="同一银行多卡共享一个账户，可一次性结清共享额度" center>
          <template #right-icon>
            <van-switch v-model="poolForm.creditReportMerged" size="24" />
          </template>
        </van-cell>
        <div class="edit-actions">
          <van-button block type="primary" @click="savePool">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 银行选择 -->
    <van-popup v-model:show="bankPickerShow" position="bottom" round>
      <van-picker
        :columns="bankPickerColumns"
        @confirm="onBankPick"
        @cancel="bankPickerShow = false"
        :show-toolbar="true"
        title="选择银行"
      />
    </van-popup>

    <!-- 批量归池：仅展示该共享池同银行的信用卡，默认全勾选 -->
    <van-popup v-model:show="batchShow" position="bottom" round :style="{ maxHeight: '80%', paddingBottom: 'env(safe-area-inset-bottom)' }">
      <div class="edit-pop">
        <div class="edit-title">{{ batchPool?.bank_name || '共享池' }} · 批量归池</div>
        <div class="batch-tip">仅限同银行（{{ batchPool?.bank_name || '' }}）的信用卡，不同银行不能交叉归池。已勾选 {{ batchSelected.length }}/{{ batchCards.length }}</div>
        <van-cell-group inset>
          <van-cell
            v-for="card in batchCards"
            :key="card.id"
            :title="cardNameOf(card)"
            clickable
            @click="toggleBatchCard(card.id)"
          >
            <template #right-icon>
              <van-checkbox :model-value="batchSelected.includes(card.id)" shape="round" />
            </template>
          </van-cell>
        </van-cell-group>
        <van-empty v-if="!batchCards.length" description="该银行暂无信用卡" />
        <div class="edit-actions">
          <van-button block type="primary" :disabled="!batchSelected.length" @click="saveBatchPool">归入该共享池（{{ batchSelected.length }}）</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, closeToast } from 'vant'
import {
  getCardList,
  updateCard,
  getCreditPools,
  createCreditPool,
  updateCreditPool,
  deleteCreditPool,
  assignCardPool
} from '@/utils/api/card'

const router = useRouter()
const activeTab = ref(0)
const creditCards = ref([])
const pools = ref([])
const refreshing = ref(false)

const cardEditShow = ref(false)
const poolPickerShow = ref(false)
const poolEditShow = ref(false)
const bankPickerShow = ref(false)
const batchShow = ref(false)
const batchPool = ref(null)
const batchSelected = ref([])

const editingCard = ref(null)
const form = ref({ creditLimit: '0', tempLimit: '0', pointsRate: '1', poolId: '', poolName: '' })
const poolForm = ref({ id: '', bankId: '', bankName: '', totalCreditLimit: '0', totalTempLimit: '0', creditReportMerged: false })

const fmt = (n) => Number(n || 0).toFixed(2)

const poolNameOf = (id) => {
  if (!id) return ''
  const p = pools.value.find((x) => x.id === id)
  return p ? p.bank_name : ''
}

// 每池卡片数：直接从前端 creditCards 实时统计（不依赖后端 card_count 字段），
// 卡归属变更后刷新即可同步，避免后端未重启/无该字段时显示为 0
const poolCardCount = (poolId) => {
  if (!poolId) return 0
  return creditCards.value.filter((c) => c.share_pool_id === poolId).length
}

// 共享卡分组：按 share_pool_id 归组，父目录 = 银行（池）
const sharedGroups = computed(() => {
  const map = new Map()
  creditCards.value.forEach((c) => {
    if (!c.share_pool_id) return
    if (!map.has(c.share_pool_id)) {
      map.set(c.share_pool_id, { poolId: c.share_pool_id, poolName: poolNameOf(c.share_pool_id), cards: [] })
    }
    map.get(c.share_pool_id).cards.push(c)
  })
  return Array.from(map.values())
})

// 独立额度：未共享的卡
const standaloneCards = computed(() => creditCards.value.filter((c) => !c.share_pool_id))

// 折叠面板：默认展开所有共享组
const activeGroupNames = ref([])
watch(sharedGroups, (groups) => {
  activeGroupNames.value = groups.map((g) => g.poolId)
})

// 卡名兜底：优先 alias；为空则用「银行名(尾号)」——不是所有人都有添加 alias 的习惯
const cardNameOf = (card) => {
  if (!card) return '卡片'
  const base = card.alias || card.bank_name || '卡片'
  const last4 = card.last4_no || card.last4No || ''
  return last4 ? `${base}(${last4})` : base
}

// 卡片归池选择：只展示与当前编辑卡同银行的共享池（不同银行不能交叉归池）
const poolPickerColumns = computed(() => {
  const list = [{ text: '独立（不共享）', value: '' }]
  const cardBankId = editingCard.value?.bank_id
  pools.value.forEach((p) => {
    if (cardBankId && p.bank_id && p.bank_id !== cardBankId) return
    list.push({ text: `${p.bank_name}（共 ${fmt(p.total_credit_limit)}）`, value: p.id })
  })
  return list
})

// 批量归池候选卡：仅该共享池同银行的信用卡
const batchCards = computed(() => {
  if (!batchPool.value?.bank_id) return []
  return creditCards.value.filter((c) => c.bank_id === batchPool.value.bank_id)
})

// 银行选项：从信用卡列表按 bank_id 去重得到（唯一标识用 bank_id）
const bankPickerColumns = computed(() => {
  const map = new Map()
  creditCards.value.forEach((c) => {
    if (c.bank_id && !map.has(c.bank_id)) {
      map.set(c.bank_id, c.bank_name || '未命名银行')
    }
  })
  const list = []
  map.forEach((name, id) => list.push({ text: name, value: id }))
  return list
})

// 取某银行下所有卡的固定/临时额度最大值，作为共享池默认额度
function maxLimitsOfBank(bankId) {
  const cards = creditCards.value.filter((c) => c.bank_id === bankId)
  let credit = 0
  let temp = 0
  cards.forEach((c) => {
    credit = Math.max(credit, Number(c.credit_limit || 0))
    temp = Math.max(temp, Number(c.temp_limit || 0))
  })
  return { credit, temp }
}

function openBankPicker() {
  bankPickerShow.value = true
}

function onBankPick({ selectedOptions }) {
  const selected = selectedOptions[0]
  if (selected) {
    poolForm.value.bankId = selected.value
    poolForm.value.bankName = selected.text
    const { credit, temp } = maxLimitsOfBank(selected.value)
    poolForm.value.totalCreditLimit = String(credit)
    poolForm.value.totalTempLimit = String(temp)
  }
  bankPickerShow.value = false
}

async function loadData() {
  // 两个接口独立容错：一个失败不影响另一个的刷新
  try {
    const cRes = await getCardList({ cardType: 'credit' })
    creditCards.value = cRes.data || []
  } catch (e) {
    // 忽略，保持已有列表
  }
  try {
    const pRes = await getCreditPools()
    pools.value = pRes.data || []
  } catch (e) {
    // 忽略，保持已有列表
  }
}

// 下拉刷新
async function onRefresh() {
  try {
    await loadData()
  } finally {
    refreshing.value = false
  }
}

function onBack() {
  router.back()
}

// 批量归池：打开弹窗，默认勾选同银行全部信用卡
function openBatchPool(pool) {
  batchPool.value = pool
  batchSelected.value = batchCards.value.map((c) => c.id)
  batchShow.value = true
}

function toggleBatchCard(cardId) {
  const i = batchSelected.value.indexOf(cardId)
  if (i >= 0) batchSelected.value.splice(i, 1)
  else batchSelected.value.push(cardId)
}

async function saveBatchPool() {
  if (!batchPool.value || !batchSelected.value.length) return
  showLoadingToast({ message: '归池中', forbidClick: true })
  try {
    for (const cardId of batchSelected.value) {
      await assignCardPool(cardId, batchPool.value.id)
    }
    showSuccessToast('已批量归池')
    batchShow.value = false
    await loadData()
  } catch (e) {
    showToast('归池失败')
  } finally {
    closeToast()
  }
}

function openCardEdit(card) {
  editingCard.value = card
  form.value = {
    creditLimit: String(card.credit_limit || 0),
    tempLimit: String(card.temp_limit || 0),
    pointsRate: String(card.points_rate || 1),
    poolId: card.share_pool_id || '',
    poolName: poolNameOf(card.share_pool_id)
  }
  cardEditShow.value = true
}

function openPoolPicker() {
  poolPickerShow.value = true
}

function onPoolPick({ selectedOptions }) {
  const v = selectedOptions[0]?.value ?? ''
  form.value.poolId = v
  form.value.poolName = v ? poolNameOf(v) : '独立（不共享）'
  poolPickerShow.value = false
}

async function saveCard() {
  if (!editingCard.value) return
  const id = editingCard.value.id
  const prevPool = editingCard.value.share_pool_id || ''
  showLoadingToast({ message: '保存中', forbidClick: true })
  try {
    await updateCard(id, {
      creditLimit: Number(form.value.creditLimit || 0),
      tempLimit: Number(form.value.tempLimit || 0),
      pointsRate: Number(form.value.pointsRate || 1)
    })
    if (form.value.poolId !== prevPool) {
      await assignCardPool(id, form.value.poolId)
    }
    showSuccessToast('已保存并同步账单')
    cardEditShow.value = false
    await loadData()
  } catch (e) {
    showToast('保存失败')
  } finally {
    closeToast()
  }
}

function openPoolCreate() {
  poolForm.value = { id: '', bankId: '', bankName: '', totalCreditLimit: '0', totalTempLimit: '0', creditReportMerged: false }
  poolEditShow.value = true
}

function openPoolEdit(pool) {
  poolForm.value = {
    id: pool.id,
    bankId: pool.bank_id || '',
    bankName: pool.bank_name || '',
    totalCreditLimit: String(pool.total_credit_limit || 0),
    totalTempLimit: String(pool.total_temp_limit || 0),
    creditReportMerged: !!Number(pool.credit_report_merged || 0)
  }
  poolEditShow.value = true
}

async function savePool() {
  showLoadingToast({ message: '保存中', forbidClick: true })
  try {
    if (poolForm.value.id) {
      await updateCreditPool(poolForm.value.id, {
        bankId: poolForm.value.bankId,
        bankName: poolForm.value.bankName,
        totalCreditLimit: Number(poolForm.value.totalCreditLimit || 0),
        totalTempLimit: Number(poolForm.value.totalTempLimit || 0),
        creditReportMerged: poolForm.value.creditReportMerged ? 1 : 0
      })
    } else {
      if (!poolForm.value.bankId) {
        showToast('请选择银行')
        closeToast()
        return
      }
      await createCreditPool({
        bankId: poolForm.value.bankId,
        bankName: poolForm.value.bankName,
        totalCreditLimit: Number(poolForm.value.totalCreditLimit || 0),
        totalTempLimit: Number(poolForm.value.totalTempLimit || 0),
        creditReportMerged: poolForm.value.creditReportMerged ? 1 : 0
      })
    }
    showSuccessToast('已保存')
    poolEditShow.value = false
    await loadData()
  } catch (e) {
    showToast('保存失败')
  } finally {
    closeToast()
  }
}

async function removePool(pool) {
  showLoadingToast({ message: '删除中', forbidClick: true })
  try {
    await deleteCreditPool(pool.id)
    showSuccessToast('已删除')
    await loadData()
  } catch (e) {
    showToast('删除失败')
  } finally {
    closeToast()
  }
}

onMounted(loadData)
</script>

<style scoped>
.limit-manage { background: var(--app-bg, #f7f8fa); min-height: 100vh; }
.section { padding: 12px 0; }
.card-cell, .pool-cell { margin-bottom: 10px; }
.limits { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 12px; color: var(--theme-text-secondary, #646566); }
.standalone-divider { text-align: center; color: var(--theme-text-secondary, #646566); font-size: 12px; padding: 8px 0 4px; }
:deep(.van-collapse-item__title) { font-size: 14px; font-weight: 600; color: var(--theme-text-primary, #323233); }
:deep(.van-collapse-item__content) { padding: 0 12px 8px; background: var(--theme-bg-primary, #f7f8fa); }
:deep(.van-collapse-item__content .van-cell-group) { margin-top: 2px; }
:deep(.van-collapse-item__content .van-cell) { padding-left: 16px; background: transparent; }
.pool-actions { padding: 12px; }
.pool-btns { display: flex; gap: 10px; padding: 8px 16px 12px; }
.edit-pop { padding: 20px 16px; }
.edit-title { font-size: 16px; font-weight: 600; margin-bottom: 12px; color: var(--theme-text-primary, #323233); }
.edit-actions { margin-top: 16px; }
</style>
