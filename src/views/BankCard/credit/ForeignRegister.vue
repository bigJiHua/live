<template>
  <div class="page foreign-register">
    <div class="tips">
      外币消费入账以银行 App <b>实际结算汇率</b> 为准。登记后请在还款对账时补全实际汇率/人民币，账单才计入。
    </div>

    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="待对账">
        <div class="section">
          <van-cell-group inset v-for="item in pending" :key="item.id" class="reg-cell" @click="openReconcile(item)">
            <van-cell :title="`${item.currency} · ${cardName(item.card_id)}`" :label="`原币 ${fmt(item.foreign_amount)} @ ${fmt(item.registered_rate)}`">
              <template #value>
                <div class="rmb">
                  <span class="est">登记折合 ¥{{ fmt(item.registered_rmb) }}</span>
                  <van-tag type="warning" plain>待对账</van-tag>
                </div>
              </template>
            </van-cell>
          </van-cell-group>
          <van-empty v-if="!pending.length" description="暂无待对账外币消费" />
        </div>
      </van-tab>

      <van-tab title="全部记录">
        <div class="section">
          <van-cell-group inset v-for="item in all" :key="item.id" class="reg-cell">
            <van-cell :title="`${item.currency} · ${cardName(item.card_id)}`" :label="`原币 ${fmt(item.foreign_amount)}`">
              <template #value>
                <div class="rmb">
                  <span v-if="item.status === 'reconciled'" class="done">入账 ¥{{ fmt(item.actual_rmb) }}</span>
                  <span v-else class="est">登记 ¥{{ fmt(item.registered_rmb) }}</span>
                  <van-tag :type="item.status === 'reconciled' ? 'success' : 'warning'" plain>
                    {{ item.status === 'reconciled' ? '已对账' : '待对账' }}
                  </van-tag>
                </div>
              </template>
            </van-cell>
          </van-cell-group>
          <van-empty v-if="!all.length" description="暂无外币登记" />
        </div>
      </van-tab>

      <van-tab title="历史外币流水">
        <div class="section">
          <div class="history-filter">
            <van-dropdown-menu>
              <van-dropdown-item v-model="hisCardId" :options="hisCardOptions" />
              <van-dropdown-item v-model="hisRange" :options="hisRangeOptions" />
            </van-dropdown-menu>
          </div>
          <van-cell-group
            inset
            v-for="item in history"
            :key="item.account_id"
            class="reg-cell"
            :class="{ 'reg-unregistered': !item.reg_status }"
            @click="onHistoryClick(item)"
          >
            <van-cell
              :title="`${item.currency} · ${cardName(item.card_id)}`"
              :label="`${item.trans_date || '--'} · 原币 ${fmt(item.amount)} @ ${fmt(item.exchange_rate)}`"
            >
              <template #value>
                <div class="rmb">
                  <span v-if="item.reg_status === 'reconciled'" class="done">入账 ¥{{ fmt(item.actual_rmb) }}</span>
                  <span v-else-if="item.reg_status === 'pending'" class="est">待对账 ¥{{ fmt(item.registered_rmb) }}</span>
                  <span v-else class="est">未登记</span>
                  <div class="rmb-row">
                    <van-tag
                      :type="item.reg_status === 'reconciled' ? 'success' : item.reg_status === 'pending' ? 'warning' : 'default'"
                      plain
                    >
                      {{ item.reg_status === 'reconciled' ? '已对账' : item.reg_status === 'pending' ? '待对账' : '未登记' }}
                    </van-tag>
                    <van-button v-if="!item.reg_status" size="mini" type="primary" plain @click.stop="doRegister(item)">登记</van-button>
                  </div>
                </div>
              </template>
            </van-cell>
          </van-cell-group>
          <van-empty v-if="!history.length" description="暂无外币消费流水" />
        </div>
      </van-tab>
    </van-tabs>

    <!-- 对账弹窗 -->
    <van-popup v-model:show="reconcileShow" position="bottom" round :style="{ paddingBottom: 'env(safe-area-inset-bottom)' }">
      <div class="edit-pop" v-if="editing">
        <div class="edit-title">{{ editing.currency }} · 实际结算对账</div>
        <div class="reg-meta">
          <span>原币金额：{{ fmt(editing.foreign_amount) }} {{ editing.currency }}</span>
          <span>登记汇率：{{ fmt(editing.registered_rate) }}（每100外币）</span>
        </div>
        <van-field v-model="rec.actualRate" type="number" label="实际汇率" placeholder="每100外币人民币" />
        <van-field v-model="rec.actualRmb" type="number" label="实际人民币" placeholder="留空按汇率自动算" />
        <div class="preview" v-if="previewRmb !== null">
          预计入账：<b>¥{{ fmt(previewRmb) }}</b>
        </div>
        <div class="edit-actions">
          <van-button block type="primary" @click="saveReconcile">确认对账并同步账单</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, closeToast } from 'vant'
import { getCardList, getForeignPending, getForeignList, getForeignHistory, registerForeignAccount, reconcileForeign } from '@/utils/api/card'

const router = useRouter()
const activeTab = ref(0)
const pending = ref([])
const all = ref([])
const cards = ref([])
const reconcileShow = ref(false)
const editing = ref(null)
const rec = ref({ actualRate: '', actualRmb: '' })

// 历史外币流水：按卡片 + 时间范围扫描账本（含未登记）
const history = ref([])
const hisCardId = ref('')
const hisRange = ref('90')

const hisCardOptions = computed(() => [
  { text: '全部卡片', value: '' },
  ...cards.value.map((c) => ({ text: c.alias || c.bank_name || String(c.id).slice(-4), value: c.id }))
])
const hisRangeOptions = [
  { text: '近30天', value: '30' },
  { text: '近90天', value: '90' },
  { text: '近一年', value: '365' },
  { text: '全部', value: '0' }
]

const fmt = (n) => Number(n || 0).toFixed(2)

const cardName = (id) => {
  const c = cards.value.find((x) => x.id === id)
  return c ? (c.alias || c.bank_name) : (id ? String(id).slice(-4) : '')
}

const previewRmb = computed(() => {
  if (!editing.value) return null
  const rate = Number(rec.value.actualRate || 0)
  if (!rate) return null
  const rmb = Number(rec.value.actualRmb || 0)
  if (rmb) return rmb
  // toCNY = 原币 * 汇率 / 100（与后端一致）
  return (Number(editing.value.foreign_amount || 0) * rate) / 100
})

async function loadData() {
  try {
    const [cRes, pRes, aRes] = await Promise.all([
      getCardList({ cardType: 'credit' }),
      getForeignPending(),
      getForeignList()
    ])
    cards.value = cRes.data || []
    pending.value = pRes.data || []
    all.value = aRes.data || []
  } catch (e) {
    showToast('加载失败')
  }
}

function onBack() {
  router.back()
}

function openReconcile(item) {
  editing.value = item
  rec.value = {
    actualRate: String(item.registered_rate || ''),
    actualRmb: ''
  }
  reconcileShow.value = true
}

async function saveReconcile() {
  if (!editing.value) return
  if (!rec.value.actualRate) {
    showToast('请填写实际汇率')
    return
  }
  showLoadingToast({ message: '对账中', forbidClick: true })
  try {
    await reconcileForeign(editing.value.id, {
      actualRate: Number(rec.value.actualRate),
      actualRmb: rec.value.actualRmb ? Number(rec.value.actualRmb) : undefined
    })
    showSuccessToast('已对账，账单已同步')
    reconcileShow.value = false
    await loadData()
  } catch (e) {
    showToast(e.message || '对账失败')
  } finally {
    closeToast()
  }
}

const fmtD = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

// 加载历史外币消费流水（含未登记，按卡/时间范围）
async function loadHistory() {
  try {
    const range = Number(hisRange.value)
    const params = { cardId: hisCardId.value || undefined }
    if (range > 0) {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - range)
      params.startDate = fmtD(start)
      params.endDate = fmtD(end)
    }
    const res = await getForeignHistory(params)
    history.value = res.data || []
  } catch (e) {
    showToast('加载历史流水失败')
  }
}

watch([hisCardId, hisRange], () => loadHistory())

// 补登记未登记的历史外币消费（转 pending 后可对账）
async function doRegister(item) {
  if (!item || !item.account_id) return
  showLoadingToast({ message: '登记中', forbidClick: true })
  try {
    await registerForeignAccount({ accountId: item.account_id })
    showSuccessToast('已登记为待对账')
    await Promise.all([loadData(), loadHistory()])
  } catch (e) {
    showToast(e.message || '登记失败')
  } finally {
    closeToast()
  }
}

// 点击历史流水：待对账 → 打开对账弹窗；未登记 → 补登记
function onHistoryClick(item) {
  if (item.reg_status === 'pending') {
    openReconcile({
      id: item.reg_id,
      card_id: item.card_id,
      currency: item.currency,
      foreign_amount: item.amount,
      registered_rate: item.registered_rate || item.exchange_rate,
      settle_date: item.settle_date || ''
    })
  } else if (!item.reg_status) {
    doRegister(item)
  }
}

onMounted(() => {
  loadData()
  loadHistory()
})
</script>

<style scoped>
.foreign-register { background: var(--app-bg, #f7f8fa); min-height: 100vh; }
.tips { margin: 12px; padding: 10px 12px; font-size: 12px; line-height: 1.6; color: var(--theme-text-secondary, #646566); background: var(--theme-bg-secondary, #fff); border-radius: 8px; }
.section { padding: 4px 0 12px; }
.reg-cell { margin-bottom: 10px; }
.rmb { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.rmb-row { display: flex; align-items: center; gap: 6px; }
.history-filter { padding: 4px 16px 8px; }
:deep(.van-dropdown-menu) {
  --van-dropdown-menu-background: var(--theme-bg-secondary, #fff);
  --van-dropdown-menu-title-text-color: var(--theme-text-primary, #323233);
  --van-dropdown-menu-title-active-text-color: var(--theme-primary, #3a66e0);
  --van-dropdown-menu-option-active-color: var(--theme-primary, #3a66e0);
}
.reg-unregistered { opacity: 0.85; }
.est { font-size: 12px; color: var(--theme-text-secondary, #646566); }
.done { font-size: 12px; color: var(--theme-primary, #3a66e0); }
.edit-pop { padding: 20px 16px; }
.edit-title { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.reg-meta { font-size: 12px; color: var(--theme-text-secondary, #646566); display: flex; flex-direction: column; gap: 2px; margin-bottom: 8px; }
.preview { margin: 12px 0; font-size: 14px; }
.preview b { color: var(--theme-primary, #3a66e0); }
.edit-actions { margin-top: 8px; }
</style>
