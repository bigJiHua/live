# 文件上传 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 上传文件 |
| DELETE | `/:id` | 删除文件 |

---

## 接口详情

### 1. 上传文件

**路径**：`POST /api/v1/upload/`

**请求**：表单数据

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | ✅ | 文件 |
| `bus_type` | string | ❌ | 业务类型：moment/动态图片/product等 |
| `bus_id` | string | ❌ | 关联业务ID |

**响应**：
```json
{
  "status": 200,
  "message": "上传成功",
  "data": {
    "url": "/uploads/xxx.webp"
  }
}
```

---

### 2. 删除文件

**路径**：`DELETE /api/v1/upload/:id`

**说明**：软删除

---

## 字段说明

### sys_attachment 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(255) | 用户ID |
| `bus_id` | varchar(50) | 关联业务ID |
| `bus_type` | varchar(50) | 业务类型 |
| `file_name` | varchar(255) | 原始文件名 |
| `file_path` | varchar(255) | 存储路径 |
| `file_size` | varchar(50) | 文件大小 |
| `file_ext` | varchar(10) | 文件后缀 |
| `create_time` | varchar(20) | 创建时间 |
| `is_deleted` | tinyint | 是否删除 |
