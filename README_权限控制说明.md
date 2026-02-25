# 菜单权限控制说明

## 权限类型

系统支持两种权限类型：

1. **只读权限（READ_ONLY）**
   - 可以查看数据
   - 可以导出数据
   - **不能**新增、编辑、删除数据
   - **不能**导入数据

2. **读写权限（READ_WRITE）**
   - 可以查看数据
   - 可以导出数据
   - 可以新增、编辑、删除数据
   - 可以导入数据

## 已实现的权限控制

### 1. 主页面（index.html）

#### 菜单显示控制
- 数标管理菜单：根据子菜单权限显示
- Git代码管理菜单：根据子菜单权限显示
- 系统管理菜单：仅超级管理员可见

#### 数据列表操作权限
- **编辑按钮**：需要 `data-list` 的 READ_WRITE 权限
- **删除按钮**：需要 `data-list` 的 READ_WRITE 权限
- **查看功能**：有 `data-list` 权限即可（READ_ONLY 或 READ_WRITE）

### 2. 权限检查方法

```javascript
// 检查是否有菜单权限（任何权限）
hasMenuPermission(menuCode)

// 检查是否有写权限
hasWritePermission(menuCode)
```

## 需要在子页面实现的权限控制

以下页面需要添加权限控制（通过 iframe 加载）：

### 1. Excel导入分析（excel-import.html）
- 菜单代码：`excel-import`
- 需要控制：导入按钮（需要 READ_WRITE 权限）

### 2. Excel导入分析-在途（excel-import-ing.html）
- 菜单代码：`excel-import-ing`
- 需要控制：导入按钮（需要 READ_WRITE 权限）

### 3. Excel导出（excel-export.html）
- 菜单代码：`excel-export`
- 导出功能：有权限即可访问（READ_ONLY 或 READ_WRITE）

### 4. 枚举映射关系维护（enum-mapping.html）
- 菜单代码：`enum-mapping`
- 需要控制：
  - 新增按钮（需要 READ_WRITE 权限）
  - 编辑按钮（需要 READ_WRITE 权限）
  - 删除按钮（需要 READ_WRITE 权限）

### 5. 域清单映射关系维护（domain-mapping.html）
- 菜单代码：`domain-mapping`
- 需要控制：
  - 新增按钮（需要 READ_WRITE 权限）
  - 编辑按钮（需要 READ_WRITE 权限）
  - 删除按钮（需要 READ_WRITE 权限）

### 6. 变更历史（change-history.html）
- 菜单代码：`change-history`
- 仅查看功能，有权限即可访问

## 实现权限控制的步骤

### 在 iframe 子页面中实现权限控制

1. **获取当前用户权限**
```javascript
async checkPermission() {
    try {
        const response = await axios.get('/api/auth/current');
        if (response.data.code === 200) {
            this.currentUser = response.data.data;
            this.isAdmin = response.data.data.isAdmin || false;
            await this.loadMenuPermissions();
        }
    } catch (error) {
        console.error('获取用户信息失败:', error);
    }
},

async loadMenuPermissions() {
    try {
        const response = await axios.get('/api/auth/menu-permissions');
        if (response.data.code === 200) {
            this.menuPermissions = response.data.data || {};
        }
    } catch (error) {
        console.error('加载菜单权限失败:', error);
    }
}
```

2. **添加权限检查方法**
```javascript
hasWritePermission(menuCode) {
    if (this.isAdmin) {
        return true;
    }
    return this.menuPermissions[menuCode] === 'READ_WRITE';
}
```

3. **在按钮上添加 v-if 控制**
```html
<!-- 新增按钮 -->
<button v-if="hasWritePermission('menu-code')" @click="add()">新增</button>

<!-- 编辑按钮 -->
<button v-if="hasWritePermission('menu-code')" @click="edit(item)">编辑</button>

<!-- 删除按钮 -->
<button v-if="hasWritePermission('menu-code')" @click="delete(item)">删除</button>

<!-- 只读提示 -->
<span v-if="!hasWritePermission('menu-code')" style="color: #9ca3af;">仅查看</span>
```

## 权限配置

超级管理员可以在"系统管理 -> 用户管理"中为用户配置菜单权限：

1. 选择用户
2. 点击"权限配置"
3. 勾选菜单并选择权限类型（只读/读写）
4. 保存

## 注意事项

1. 超级管理员（isAdmin=true）拥有所有权限
2. 普通用户只能访问已分配权限的菜单
3. 只读权限用户可以查看和导出，但不能修改数据
4. 读写权限用户可以进行所有操作
5. 未分配权限的菜单对用户不可见

