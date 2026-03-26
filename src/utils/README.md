// 导入方式 1：从统一导出导入
import { authApi, securityApi, accountApi, userApi } from '@/utils/index'

// 导入方式 2：直接导入具体模块
import { login, getMe } from '@/utils/auth'
import { setPin, verifyPin } from '@/utils/security'
import { getTransactions, createTransaction } from '@/utils/account'
import { updateProfile, changePassword } from '@/utils/user'

// 使用示例
const res = await authApi.login({ email: '...', password: '...' })
await securityApi.setPin({ pin: '123456' })
await accountApi.createTransaction({ amount: 100, type: 'expense' })
await userApi.updateProfile({ username: 'newname' })
