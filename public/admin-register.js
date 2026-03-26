// 检查是否已有管理员
async function checkAdminExists() {
  try {
    const response = await fetch('/api/v1/auth/check-admin');
    const result = await response.json();

    if (result.hasAdmin) {
      // 已有管理员,跳转到登录页或提示
      document.querySelector('.container').innerHTML = `
        <div class="header">
          <h1>⚠️ 无法访问</h1>
          <p>系统已初始化</p>
        </div>
        <div class="content">
          <div class="info-box" style="background: #fed7d7; border-color: #fc8181; color: #c53030;">
            <strong>❌ 系统已存在管理员账号</strong>
            <p style="margin-top: 10px;">管理员注册页面仅在系统首次初始化时可用。</p>
            <p style="margin-top: 10px;">请使用已有账号 <a href="/public/database.html" style="color: #667eea;">登录系统</a></p>
          </div>
        </div>
      `;
      return true;
    }

    return false;
  } catch (error) {
    console.error('检查管理员状态失败:', error);
    // 如果接口不存在或出错,允许访问
    return false;
  }
}

// 表单提交
async function handleSubmit(event) {
  event.preventDefault();

  const username = document.getElementById('username').value.trim();
  const email = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value;
  const confirmPassword = document.getElementById('confirmPassword').value;

  // 表单验证
  if (username.length < 3) {
    showError('用户名至少需要 3 个字符');
    return;
  }

  if (password.length < 6) {
    showError('密码至少需要 6 个字符');
    return;
  }

  if (password !== confirmPassword) {
    showError('两次输入的密码不一致');
    return;
  }

  // 隐藏表单,显示加载
  document.getElementById('registerForm').style.display = 'none';
  document.getElementById('loading').classList.add('show');
  hideError();
  hideSuccess();

  try {
    const response = await fetch('/api/v1/auth/admin-register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username,
        email,
        password
      })
    });

    const result = await response.json();

    document.getElementById('loading').classList.remove('show');

    if (response.ok) {
      // 注册成功
      showSuccess('✓ 管理员账号注册成功！3 秒后跳转到数据库管理页面...');

      // 3秒后跳转
      setTimeout(() => {
        window.location.href = '/public/database.html';
      }, 3000);
    } else {
      // 注册失败
      document.getElementById('registerForm').style.display = 'block';
      showError(result.message || '注册失败,请重试');
    }
  } catch (error) {
    console.error('注册失败:', error);
    document.getElementById('loading').classList.remove('show');
    document.getElementById('registerForm').style.display = 'block';
    showError('网络错误,请检查服务器连接');
  }
}

// 显示错误信息
function showError(message) {
  const errorEl = document.getElementById('errorMessage');
  errorEl.textContent = message;
  errorEl.classList.add('show');
}

// 隐藏错误信息
function hideError() {
  document.getElementById('errorMessage').classList.remove('show');
}

// 显示成功信息
function showSuccess(message) {
  const successEl = document.getElementById('successMessage');
  successEl.textContent = message;
  successEl.classList.add('show');
}

// 隐藏成功信息
function hideSuccess() {
  document.getElementById('successMessage').classList.remove('show');
}

// 初始化
document.addEventListener('DOMContentLoaded', async () => {
  // 检查是否已有管理员
  const hasAdmin = await checkAdminExists();

  if (!hasAdmin) {
    // 绑定表单提交事件
    document.getElementById('registerForm').addEventListener('submit', handleSubmit);

    // 密码确认实时验证
    document.getElementById('confirmPassword').addEventListener('input', function() {
      const password = document.getElementById('password').value;
      const confirmPassword = this.value;

      if (confirmPassword && password !== confirmPassword) {
        this.style.borderColor = '#fc8181';
      } else {
        this.style.borderColor = '#e2e8f0';
      }
    });
  }
});
