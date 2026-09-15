package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Budget
import com.live.finance.data.model.FixedAsset

interface FixedAssetRepository {
    suspend fun list(): ApiResult<List<FixedAsset>>
}

class FakeFixedAssetRepository : FixedAssetRepository {
    private val seed = listOf(
        FixedAsset("fa1", "MacBook Pro 14", "数码", buyPrice = 18999.0, nowVal = 12000.0, useYears = 3, buyDate = "2023-05-01"),
        FixedAsset("fa2", "家用轿车", "交通", buyPrice = 150000.0, nowVal = 98000.0, useYears = 8, buyDate = "2020-01-01", status = "using"),
        FixedAsset("fa3", "旧手机 iPhone 11", "数码", buyPrice = 5999.0, nowVal = 0.0, status = "sold"),
    )
    override suspend fun list(): ApiResult<List<FixedAsset>> = ApiResult.Ok(seed, "")
}

class RemoteFixedAssetRepository(private val client: ApiClient) : FixedAssetRepository {
    override suspend fun list(): ApiResult<List<FixedAsset>> =
        client.get("/fixedAsset/list").toResult().map { data ->
            arr(data).map { el -> val o = el.asJsonObject
        FixedAsset(
            id = str(o, "id"), info = str(o, "info"), tag = str(o, "tag"), imgUrl = str(o, "img_url"),
            buyPrice = dbl(o, "buy_price"), nowVal = dbl(o, "now_val"), secondhandPrice = dbl(o, "secondhand_price"),
            useYears = dbl(o, "use_years").toInt(), residualVal = dbl(o, "residual_val"),
            buyDate = str(o, "buy_date").take(10), status = str(o, "status").ifEmpty { "using" },
            monthsUsed = dbl(o, "months_used").toInt(), yearsUsed = dbl(o, "years_used"),
            depreciableAmount = dbl(o, "depreciable_amount"),
        )
            }
        }
}

interface BudgetRepository {
    suspend fun list(): ApiResult<List<Budget>>
}

class FakeBudgetRepository : BudgetRepository {
    private val seed = listOf(
        Budget("b1", "9月餐饮预算", "吃", 2000.0, 1450.0, "monthly"),
        Budget("b2", "9月购物预算", "购", 3000.0, 3200.0, "monthly", isOverBudget = true),
        Budget("b3", "国庆出行", "行", 5000.0, 0.0, "once"),
    )
    override suspend fun list(): ApiResult<List<Budget>> = ApiResult.Ok(seed, "")
}

class RemoteBudgetRepository(private val client: ApiClient) : BudgetRepository {
    override suspend fun list(): ApiResult<List<Budget>> =
        client.get("/budget/list").toResult().map { data ->
            arr(data).map { el -> val o = el.asJsonObject
                Budget(
                    id = str(o, "id"), title = str(o, "title"), budgetType = str(o, "budget_type"),
                    budgetAmount = dbl(o, "budget_amount"), usedAmount = dbl(o, "used_amount"),
                    cycle = str(o, "cycle"), planDate = str(o, "plan_date").take(10),
                    isOverBudget = int01(o, "is_over_budget") == 1,
                )
            }
        }
}

// ---- 共用宽松解析助手（本域专用）----
private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
private fun dbl(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
private fun int01(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt else 0 }.getOrDefault(0)
private fun arr(data: JsonElement?): JsonArray = when (data) {
    is JsonArray -> data
    is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
    else -> JsonArray()
}
