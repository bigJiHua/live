let currentTable = null;
let sqlHistoryList = [];

// 初始化
document.addEventListener('DOMContentLoaded', () => {
  loadTables();
  setupTabs();
  setupTableActions();

  // 从 localStorage 加载历史
  const savedHistory = localStorage.getItem('sqlHistory');
  if (savedHistory) {
    sqlHistoryList = JSON.parse(savedHistory);
    renderHistory();
  }
});

// 设置标签页
function setupTabs() {
  document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

      tab.classList.add('active');
      document.getElementById(tab.dataset.tab + 'Tab').classList.add('active');
    });
  });
}

// 设置表操作按钮
function setupTableActions() {
  const truncateBtn = document.getElementById('truncateTableBtn');
  const dropBtn = document.getElementById('dropTableBtn');

  if (truncateBtn) {
    truncateBtn.addEventListener('click', () => {
      confirmAndTruncateTable();
    });
  }

  if (dropBtn) {
    dropBtn.addEventListener('click', () => {
      confirmAndDropTable();
    });
  }
}

// 加载表列表
async function loadTables() {
  try {
    const response = await fetch('/api/database/tables');
    const data = await response.json();

    const tableList = document.getElementById('tableList');
    tableList.innerHTML = '';

    data.tables.forEach(table => {
      const tableName = Object.values(table)[0];
      const li = document.createElement('li');
      li.className = 'table-item';
      li.textContent = '📄 ' + tableName;
      li.onclick = function() { selectTable(tableName, li); };
      tableList.appendChild(li);
    });
  } catch (error) {
    document.getElementById('tableList').innerHTML =
      '<li class="error">加载失败: ' + error.message + '</li>';
  }
}

// 选择表
async function selectTable(tableName, element) {
  currentTable = tableName;

  // 更新选中状态
  document.querySelectorAll('.table-item').forEach(item => item.classList.remove('active'));
  element.classList.add('active');

  // 在 SQL 输入框中插入表名
  const sqlInput = document.getElementById('sqlInput');
  sqlInput.value = '-- 操作表: ' + tableName + '\nSELECT * FROM ' + tableName + ' LIMIT 100;';

  // 显示表操作区域
  document.getElementById('tableActions').style.display = 'block';
  document.getElementById('currentTableName').textContent = tableName;

  // 加载表结构
  loadTableStructure(tableName);

  // 加载表数据
  loadTableData(tableName);
}

// 加载表结构
async function loadTableStructure(tableName) {
  try {
    const response = await fetch('/api/database/table/' + tableName + '/structure');
    const data = await response.json();

    const content = document.getElementById('structureContent');
    content.innerHTML = '<h3>📋 表: ' + tableName + '</h3>';

    let html = '<div class="table-wrapper"><table class="structure-table">';
    html += '<thead><tr><th>字段名</th><th>类型</th><th>允许NULL</th><th>键</th><th>默认值</th><th>额外</th></tr></thead>';
    html += '<tbody>';

    data.structure.forEach(col => {
      html += '<tr>';
      html += '<td><strong>' + escapeHtml(col.Field) + '</strong></td>';
      html += '<td><code>' + escapeHtml(col.Type) + '</code></td>';
      html += '<td>' + (col.Null === 'YES' ? 'YES' : 'NO') + '</td>';
      html += '<td>' + (col.Key || '-') + '</td>';
      html += '<td>' + (col.Default === null ? 'NULL' : escapeHtml(col.Default)) + '</td>';
      html += '<td>' + (col.Extra || '-') + '</td>';
      html += '</tr>';
    });

    html += '</tbody></table></div>';

    // 获取创建语句
    try {
      const createRes = await fetch('/api/database/table/' + tableName + '/show-create');
      const createData = await createRes.json();
      html += '<h3 style="margin-top: 20px;">📝 创建语句</h3>';
      html += '<div class="code-block"><pre>' + escapeHtml(createData.createStatement) + '</pre></div>';
    } catch (e) {
      console.error('获取创建语句失败:', e);
    }

    content.innerHTML += html;
  } catch (error) {
    document.getElementById('structureContent').innerHTML =
      '<div class="error">加载表结构失败: ' + error.message + '</div>';
  }
}

// 加载表数据
async function loadTableData(tableName, page) {
  if (!page) page = 1;

  try {
    const response = await fetch('/api/database/table/' + tableName + '/data?page=' + page + '&limit=50');
    const data = await response.json();

    const content = document.getElementById('dataContent');
    content.innerHTML = '<h3>📊 表: ' + tableName + ' - 第 ' + page + ' 页</h3>';

    if (data.data.length === 0) {
      content.innerHTML += '<p>表为空</p>';
      return;
    }

    // 获取列名
    const columns = Object.keys(data.data[0]);

    let html = '<div class="table-wrapper"><table>';
    html += '<thead><tr>';

    columns.forEach(col => {
      html += '<th>' + escapeHtml(col) + '</th>';
    });

    html += '</tr></thead><tbody>';

    data.data.forEach(row => {
      html += '<tr>';
      columns.forEach(col => {
        const value = row[col] === null ? '<em style="color: #a0aec0;">NULL</em>' : escapeHtml(String(row[col]));
        html += '<td>' + value + '</td>';
      });
      html += '</tr>';
    });

    html += '</tbody></table></div>';

    // 分页信息
    html += '<p style="margin-top: 10px; color: #718096;">';
    html += '共 ' + data.pagination.total + ' 条记录, ';
    html += '每页 ' + data.pagination.limit + ' 条, ';
    html += '共 ' + data.pagination.totalPages + ' 页';
    html += '</p>';

    content.innerHTML += html;
  } catch (error) {
    document.getElementById('dataContent').innerHTML =
      '<div class="error">加载表数据失败: ' + error.message + '</div>';
  }
}

// 二次确认对话框
function confirmAction(message, callback) {
  const confirmed = confirm(message);
  if (confirmed && callback) {
    callback();
  }
}

// 确认并清空表数据
function confirmAndTruncateTable() {
  if (!currentTable) {
    alert('请先选择一个表');
    return;
  }

  confirmAction(
    '⚠️ 确定要清空表 "' + currentTable + '" 的所有数据吗？\n\n此操作不可恢复！\n\n请再次确认: 是',
    () => {
      confirmAction(
        '🔴 最后确认: 真的要清空表 "' + currentTable + '" 的所有数据吗？',
        () => {
          truncateTable(currentTable);
        }
      );
    }
  );
}

// 确认并删除表
function confirmAndDropTable() {
  if (!currentTable) {
    alert('请先选择一个表');
    return;
  }

  confirmAction(
    '⚠️ 危险操作: 确定要删除表 "' + currentTable + '" 吗？\n\n这将删除整个表及其所有数据！\n此操作不可恢复！\n\n请再次确认: 是',
    () => {
      confirmAction(
        '🔴 最后确认: 真的要删除表 "' + currentTable + '" 吗？\n\n表结构和所有数据都将被永久删除！',
        () => {
          dropTable(currentTable);
        }
      );
    }
  );
}

// 清空表数据
async function truncateTable(tableName) {
  try {
    const response = await fetch('/api/database/table/' + tableName + '/data', {
      method: 'DELETE'
    });

    const result = await response.json();

    if (result.success) {
      alert('✓ 表 "' + tableName + '" 数据已清空');
      // 刷新表数据
      loadTableData(tableName);
    } else {
      alert('✗ 清空失败: ' + result.error);
    }
  } catch (error) {
    alert('✗ 请求失败: ' + error.message);
  }
}

// 删除表
async function dropTable(tableName) {
  try {
    const response = await fetch('/api/database/table/' + tableName, {
      method: 'DELETE'
    });

    const result = await response.json();

    if (result.success) {
      alert('✓ 表 "' + tableName + '" 已删除');
      // 清空选中状态
      currentTable = null;
      document.getElementById('tableActions').style.display = 'none';
      document.getElementById('structureContent').innerHTML = '';
      document.getElementById('dataContent').innerHTML = '';
      document.getElementById('sqlInput').value = '';

      // 刷新表列表
      loadTables();
    } else {
      alert('✗ 删除失败: ' + result.error);
    }
  } catch (error) {
    alert('✗ 请求失败: ' + error.message);
  }
}

// 执行 SQL (带确认)
function executeSQL() {
  const sqlInput = document.getElementById('sqlInput');
  const sql = sqlInput.value.trim();

  if (!sql) {
    alert('请输入 SQL 语句');
    return;
  }

  // 检查是否是危险操作
  const dangerousKeywords = ['DROP', 'DELETE', 'TRUNCATE', 'ALTER'];
  const isDangerous = dangerousKeywords.some(keyword =>
    sql.toUpperCase().includes(keyword)
  );

  if (isDangerous) {
    confirmAction(
      '⚠️ 此 SQL 包含危险操作: ' + sql.substring(0, 100) + (sql.length > 100 ? '...' : '') + '\n\n确定要执行吗？',
      () => {
        executeSQLInternal(sql);
      }
    );
  } else {
    executeSQLInternal(sql);
  }
}

// 内部执行 SQL 函数
async function executeSQLInternal(sql) {
  const resultSection = document.getElementById('resultSection');
  const resultContent = document.getElementById('resultContent');

  resultSection.classList.add('visible');
  resultContent.innerHTML = '<div class="loading">执行中...</div>';

  try {
    const response = await fetch('/api/database/execute', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ sql: sql })
    });

    const result = await response.json();

    // 添加到历史
    addToHistory(sql);

    if (result.success) {
      // 成功
      let html = '<div class="success">✓ 执行成功</div>';

      if (result.data && result.data.length > 0) {
        const columns = Object.keys(result.data[0]);

        html += '<p style="margin: 10px 0;"><strong>返回 ' + result.rowCount + ' 条记录</strong></p>';

        html += '<div class="table-wrapper"><table>';
        html += '<thead><tr>';

        columns.forEach(col => {
          html += '<th>' + escapeHtml(col) + '</th>';
        });

        html += '</tr></thead><tbody>';

        result.data.forEach(row => {
          html += '<tr>';
          columns.forEach(col => {
            const value = row[col] === null ? '<em style="color: #a0aec0;">NULL</em>' : escapeHtml(String(row[col]));
            html += '<td>' + value + '</td>';
          });
          html += '</tr>';
        });

        html += '</tbody></table></div>';
      } else {
        html += '<p>无返回数据 (可能是 INSERT/UPDATE/DELETE 等操作)</p>';
      }

      resultContent.innerHTML = html;

      // 如果是操作了表,刷新表列表
      if (sql.match(/CREATE\s+TABLE/i) || sql.match(/DROP\s+TABLE/i)) {
        setTimeout(loadTables, 500);
      }
    } else {
      // 失败
      let html = '<div class="error">✗ 执行失败</div>';
      html += '<p><strong>错误信息:</strong> ' + escapeHtml(result.error) + '</p>';
      if (result.code) {
        html += '<p><strong>错误代码:</strong> ' + escapeHtml(result.code) + '</p>';
      }
      resultContent.innerHTML = html;
    }
  } catch (error) {
    resultContent.innerHTML = '<div class="error">请求失败: ' + error.message + '</div>';
  }
}

// 清空 SQL
function clearSQL() {
  document.getElementById('sqlInput').value = '';
  document.getElementById('resultSection').classList.remove('visible');
}

// 添加到历史
function addToHistory(sql) {
  sqlHistoryList.unshift(sql);
  if (sqlHistoryList.length > 20) {
    sqlHistoryList.pop();
  }
  localStorage.setItem('sqlHistory', JSON.stringify(sqlHistoryList));
  renderHistory();
}

// 渲染历史
function renderHistory() {
  const historySection = document.getElementById('historySection');
  const sqlHistory = document.getElementById('sqlHistory');

  if (sqlHistoryList.length > 0) {
    historySection.style.display = 'block';
    sqlHistory.innerHTML = sqlHistoryList.map((sql, index) => {
      return '<div class="history-item" onclick="document.getElementById(\'sqlInput\').value = \`' + escapeForJs(sql) + '\`"><pre>' + escapeHtml(sql) + '</pre></div>';
    }).join('');
  }
}

// HTML 转义
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// JavaScript 字符串转义
function escapeForJs(text) {
  return text
    .replace(/\\/g, '\\\\')
    .replace(/`/g, '\\`')
    .replace(/\$/g, '\\$')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r');
}
