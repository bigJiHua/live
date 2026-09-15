package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Category
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanField
import kotlinx.coroutines.launch

private val TYPES = listOf(
    "expense" to "支出", "income" to "收入", "bank" to "银行", "asset" to "资产", "fixed" to "固定资产",
)

@Composable
fun CategoryManageScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val repo = App.of(ctx).graph.category
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf("expense") }
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            cats = when (val r = repo.list(type)) {
                is ApiResult.Ok -> r.data ?: emptyList()
                else -> emptyList()
            }
        }
    }
    LaunchedEffect(type) { reload() }

    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("分类管理", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                Row(Modifier.padding(horizontal = 12.dp)) {
                    TYPES.forEach { (t, label) ->
                        val sel = t == type
                        Box2(
                            selected = sel,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { type = t },
                            label = label,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 12.dp)) {
                    VanButton(text = "＋ 新增分类", type = VanButtonType.Primary, onClick = { editing = null; name = ""; showForm = true })
                }
                Spacer(Modifier.height(8.dp))
            }
            items(cats) { c ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard)
                        .clickable { editing = c; name = c.name; showForm = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(c.name, 15f, FontWeight.Medium, colors.textPrimary, Modifier.weight(1f))
                    FText(c.id, 11f, color = colors.textPlaceholder)
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }

    VanPopup(show = showForm, onDismissRequest = { showForm = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            FText(if (editing == null) "新增分类" else "编辑分类", 16f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            VanField(value = name, onValueChange = { name = it }, label = "名称", placeholder = "分类名称")
            Spacer(Modifier.height(16.dp))
            Row {
                if (editing != null) {
                    VanButton(
                        text = "删除", type = VanButtonType.Danger, onClick = { showDeleteConfirm = true },
                    )
                    Spacer(Modifier.width(12.dp))
                }
                VanButton(
                    text = "保存", type = VanButtonType.Primary, onClick = {
                        if (name.isBlank()) { toast.show("请输入名称"); return@VanButton }
                        scope.launch {
                            val cur = editing
                            val r = if (cur == null) repo.create(name, type) else repo.update(cur.id, name)
                            when (r) {
                                is ApiResult.Ok -> { toast.success("已保存"); showForm = false; reload() }
                                is ApiResult.Fail -> toast.show(r.message)
                                else -> toast.show("保存失败")
                            }
                        }
                    },
                )
            }
        }
    }

    com.live.vant.feedback.VanConfirmDialog(
        show = showDeleteConfirm,
        title = "删除分类",
        message = "确定删除「${editing?.name ?: ""}」？该分类下若已有流水可能无法删除。",
        onConfirm = {
            val e = editing ?: return@VanConfirmDialog
            showDeleteConfirm = false
            scope.launch {
                when (val r = repo.delete(e.id)) {
                    is ApiResult.Ok -> { toast.success("已删除"); showForm = false; reload() }
                    is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                    is ApiResult.Fail -> toast.show(r.message)
                    else -> toast.show("删除失败")
                }
            }
        },
        onCancel = { showDeleteConfirm = false },
        onClose = { showDeleteConfirm = false },
    )
}

@Composable
private fun Box2(selected: Boolean, modifier: Modifier, label: String) {
    val colors = LocalAppColors.current
    androidx.compose.foundation.layout.Box(
        modifier.background(if (selected) colors.primary else colors.bgThird).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { FText(label, 13f, FontWeight.Medium, if (selected) androidx.compose.ui.graphics.Color.White else colors.textSecondary) }
}
