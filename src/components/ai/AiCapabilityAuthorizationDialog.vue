<template>
  <van-popup :show="show" round teleport="body" :close-on-click-overlay="!busy" class="capability-auth" @update:show="!busy && $emit('close')">
    <section role="dialog" aria-modal="true" aria-labelledby="capability-auth-title">
      <div class="heading"><h2 id="capability-auth-title">{{ mode === 'edit' ? '本会话授权' : '允许使用这项能力？' }}</h2><button type="button" :disabled="busy" aria-label="关闭授权" @click="$emit('close')">×</button></div>
      <p v-if="reason">{{ reason }}</p>
      <template v-if="access === 'web_search'">
        <label>允许的能力<input type="text" value="联网搜索（web_search）" disabled /></label>
        <p class="note danger">联网搜索功能收费，具体参照供应商；一般而言该项为单独收费功能。开启后本会话的搜索费用计入当前接入商账户。</p>
      </template>
      <template v-else>
      <label>允许的能力<select v-model="selectedAccess" :disabled="busy || mode !== 'edit'"><option value="read">读取数据并分析</option><option value="operate">准备操作草稿及保存记忆</option></select></label>
      <p class="note">{{ selectedAccess === 'read' ? 'AI 可按需读取各业务的脱敏数据，不生成操作草稿。' : 'AI 可读取数据、准备操作草稿和保存你要求记住的信息；业务入库仍由你核对确认。' }}授权不会限定查询月份，也不会整包发送数据。</p>
      </template>
      <label>有效期<select v-model="lifetime" :disabled="busy"><option value="conversation">当前会话</option><option value="15m">15 分钟</option><option value="1h">1 小时</option><option value="24h">24 小时</option></select></label>
      <p class="note">有效期内不重复询问。切换供应商、撤回或到期后需要重新授权。</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="actions"><button v-if="mode === 'edit'" type="button" :disabled="busy" @click="$emit('revoke')">撤回授权</button><button v-else type="button" :disabled="busy" @click="$emit('reject')">拒绝</button><button type="button" class="primary" :disabled="busy" @click="$emit('save', { access: selectedAccess, lifetime })">{{ busy ? '保存中…' : '同意授权' }}</button></div>
    </section>
  </van-popup>
</template>
<script setup>
import { ref, watch } from 'vue';
const props = defineProps({ show: Boolean, busy: Boolean, error: String, reason: String, access: { type: String, default: 'read' }, mode: String });
defineEmits(['save', 'reject', 'revoke', 'close']);
const lifetime = ref('conversation'), selectedAccess = ref('read');
watch(() => props.show, show => { if (show) { selectedAccess.value = props.access; lifetime.value = 'conversation'; } });
</script>
<style scoped>
.capability-auth{width:min(440px,calc(100vw - 24px));max-height:92dvh;overflow:auto;box-sizing:border-box}section{padding:18px}.heading{display:flex;align-items:center;justify-content:space-between;gap:8px}h2{margin:0;font-size:17px}p,label{font-size:13px;line-height:1.7}label{display:block;margin-top:12px}select{display:block;width:100%;box-sizing:border-box;padding:10px;margin-top:5px;border:1px solid var(--theme-border);border-radius:8px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font:inherit}.note{font-size:11px;color:var(--theme-text-secondary)}.error{color:var(--van-danger-color)}.note.danger{color:var(--van-danger-color);font-weight:600}.actions{display:flex;gap:8px;margin-top:14px}button{padding:10px 12px;border:1px solid var(--theme-border);border-radius:8px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font:inherit;cursor:pointer}.actions button{flex:1;min-height:42px}.primary{background:var(--van-primary-color);color:white;border-color:var(--van-primary-color)}button:disabled{opacity:.5;cursor:default}button:focus-visible,select:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}@media(max-width:600px){section{padding:14px}select{font-size:16px}h2{font-size:15px}}
</style>
