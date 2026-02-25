# 保护分支设置功能说明

## 功能概述

本功能用于批量设置Git工程的保护分支权限，可以统一管理多个工程的分支保护规则。

## 使用步骤

### 1. 数据库初始化

执行以下SQL脚本添加菜单权限：

```bash
mysql -u<用户名> -p<密码> <数据库名> < src/main/resources/sql/add_protected_branch_menu.sql
```

### 2. 访问功能

1. 启动应用
2. 登录系统
3. 在左侧菜单中找到"Git代码管理" -> "保护分支设置"
4. 进入保护分支设置页面

### 3. 配置保护规则

#### 3.1 分支名
- 输入要设置保护的分支名称
- 例如：`main`、`master`、`develop`、`release`等

#### 3.2 允许合并
选择谁可以合并到此保护分支：
- **Maintainers**（维护者）：只有项目维护者可以合并
- **Developers + Maintainers**（开发者+维护者）：开发者和维护者都可以合并
- **No one**（无人）：禁止任何人合并

#### 3.3 允许推送
选择谁可以推送到此保护分支：
- **Maintainers**（维护者）：只有项目维护者可以推送
- **Developers + Maintainers**（开发者+维护者）：开发者和维护者都可以推送
- **No one**（无人）：禁止任何人推送

#### 3.4 允许强制推送
选择是否允许强制推送（force push）：
- **false**（不允许，默认）：禁止强制推送，保护分支历史
- **true**（允许）：允许强制推送，但可能会覆盖历史记录（不推荐）

⚠️ **建议**：生产环境的主分支（如main、master）应设置为**false**，防止意外覆盖历史记录。

### 4. 选择Git工程

- 勾选需要应用保护规则的Git工程
- 支持全选/取消全选
- 可以同时为多个工程设置相同的保护规则

### 5. 确认修改

点击"确认修改"按钮，系统会：
1. 显示确认对话框，包含所有配置信息
2. 批量为选中的工程设置保护分支
3. 显示每个工程的操作结果

## 权限级别说明

### GitLab权限级别对应关系

| 显示名称 | 权限级别 | 说明 |
|---------|---------|------|
| No one | 0 | 禁止所有人 |
| Developers + Maintainers | 30 | 开发者及以上权限 |
| Maintainers | 40 | 维护者及以上权限 |

### 权限说明

- **Guest** (10)：访客，只能查看
- **Reporter** (20)：报告者，可以查看和创建issue
- **Developer** (30)：开发者，可以推送代码、创建MR
- **Maintainer** (40)：维护者，可以管理分支、合并MR
- **Owner** (50)：所有者，完全控制权限

## 使用场景

### 场景1：保护主分支

**配置：**
- 分支名：`main`
- 允许合并：`Maintainers`
- 允许推送：`No one`
- 允许强制推送：`false`

**效果：**
- 只有维护者可以通过MR合并代码
- 禁止直接推送到main分支
- 禁止强制推送，保护历史记录

---

### 场景2：开发分支

**配置：**
- 分支名：`develop`
- 允许合并：`Developers + Maintainers`
- 允许推送：`Developers + Maintainers`
- 允许强制推送：`false`

**效果：**
- 开发者和维护者都可以合并和推送
- 禁止强制推送

---

### 场景3：发布分支

**配置：**
- 分支名：`release`
- 允许合并：`Maintainers`
- 允许推送：`Maintainers`
- 允许强制推送：`false`

**效果：**
- 只有维护者可以操作发布分支
- 防止误操作影响发布

---

### 场景4：临时分支（不推荐保护）

**配置：**
- 分支名：`feature/*`
- 允许合并：`Developers + Maintainers`
- 允许推送：`Developers + Maintainers`
- 允许强制推送：`true`

**效果：**
- 开发过程中灵活操作
- 允许强制推送修改历史

⚠️ **注意**：特性分支通常不需要设置保护。

## 实现原理

### 技术实现

1. **取消现有保护**（如果存在）
   - API: `DELETE /api/v4/projects/:id/protected_branches/:name`

2. **重新设置保护规则**
   - API: `POST /api/v4/projects/:id/protected_branches`
   - 参数：
     ```json
     {
       "name": "main",
       "push_access_level": 40,
       "merge_access_level": 40,
       "allow_force_push": false
     }
     ```

### 操作流程

```
用户提交
   ↓
验证参数
   ↓
遍历选中的工程
   ↓
对每个工程：
   1. 调用GitLab API取消保护（如果已保护）
   2. 调用GitLab API重新设置保护规则
   ↓
返回操作结果
```

## 注意事项

### 1. 分支必须存在

- 只能保护已存在的分支
- 如果分支不存在，操作会失败
- 建议先创建分支，再设置保护

### 2. 权限要求

- 执行操作的用户必须对目标工程有**Maintainer**或更高权限
- 如果权限不足，操作会失败

### 3. 强制推送的风险

- 启用强制推送会允许覆盖历史记录
- **强烈建议**生产分支设置为`false`
- 只在特殊情况下（如需要修正历史）才临时启用

### 4. 重复执行

- 如果分支已经被保护，会先取消保护再重新设置
- 可以安全地重复执行，更新保护规则

### 5. 批量操作

- 批量操作时，即使部分工程失败，其他工程仍会继续
- 查看结果中会显示每个工程的操作状态

## 常见问题

### Q1：提示"分支不存在"

**原因**：目标工程中不存在指定的分支

**解决方案**：
1. 确认分支名称拼写正确
2. 确认该分支在工程中已创建
3. 可以先使用"分支创建"功能创建分支

### Q2：提示"权限不足"

**原因**：当前GitLab账号对目标工程权限不足

**解决方案**：
1. 检查`application.yml`中配置的GitLab账号
2. 确认该账号对目标工程有Maintainer权限
3. 如需修改，请联系工程管理员授权

### Q3：部分工程成功，部分失败

**原因**：不同工程的分支存在状态或权限不同

**解决方案**：
1. 查看结果详情，了解失败原因
2. 对失败的工程单独处理
3. 可以筛选失败的工程重新执行

### Q4：如何取消分支保护

**方法1**：通过GitLab Web界面
- 访问工程 -> Settings -> Repository -> Protected Branches
- 找到对应分支，点击"Unprotect"

**方法2**：通过本功能
- 设置允许合并和推送为`Developers + Maintainers`
- 这样会放宽保护限制（但不会完全取消）

### Q5：设置后立即生效吗？

**是的**，设置会立即生效：
- 新的保护规则马上应用
- 所有推送和合并请求都会立即受新规则约束

## API参考

### GitLab Protected Branches API

**获取保护分支列表：**
```
GET /api/v4/projects/:id/protected_branches
```

**保护分支：**
```
POST /api/v4/projects/:id/protected_branches
```

**取消保护：**
```
DELETE /api/v4/projects/:id/protected_branches/:name
```

**更新保护规则：**
```
PATCH /api/v4/projects/:id/protected_branches/:name
```

详细文档：https://docs.gitlab.com/ee/api/protected_branches.html

## 最佳实践

### 1. 主分支严格保护

```
main/master:
- 允许合并: Maintainers
- 允许推送: No one
- 允许强制推送: false
```

### 2. 开发分支适度保护

```
develop:
- 允许合并: Developers + Maintainers
- 允许推送: Developers + Maintainers
- 允许强制推送: false
```

### 3. 发布分支严格管理

```
release/*:
- 允许合并: Maintainers
- 允许推送: Maintainers
- 允许强制推送: false
```

### 4. 特性分支不保护

```
feature/*:
不设置保护，允许开发者自由操作
```

### 5. 定期审查

- 定期检查保护分支设置
- 确保符合团队规范
- 及时调整不合理的配置

## 联系支持

如有问题，请查看应用日志或联系技术支持。

