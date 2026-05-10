# 練習需求：Task（任務）API

## 背景說明

在現有的 User Service 基礎上，新增任務管理功能。
每個 User 可以擁有多個 Task，練習實作「一對多關聯」的 CRUD API，
並加入 **分頁查詢** 和 **狀態過濾** 兩個新概念。

---

## 資料模型

### Task

| 欄位        | 型別         | 說明                              |
|-------------|--------------|-----------------------------------|
| id          | Long         | 主鍵，自動產生                    |
| title       | String       | 任務標題，必填，最長 100 字元     |
| description | String       | 任務描述，選填，最長 500 字元     |
| status      | TaskStatus   | 任務狀態，預設 `TODO`             |
| userId      | Long         | 所屬使用者 ID（外鍵）             |
| createdAt   | LocalDateTime| 建立時間，自動設定                |
| updatedAt   | LocalDateTime| 更新時間，自動更新                |

### TaskStatus（enum）

```
TODO → IN_PROGRESS → DONE
```

- 狀態只能「往前」流轉，不可倒退（例如 `DONE` 不可改回 `TODO`）

---

## API 規格

### 1. 建立任務
```
POST /api/users/{userId}/tasks
```

**Request Body:**
```json
{
  "title": "完成 Spring Boot 練習",
  "description": "實作 Task API 並撰寫測試"
}
```

**Response: 201 Created**
```json
{
  "id": 1,
  "title": "完成 Spring Boot 練習",
  "description": "實作 Task API 並撰寫測試",
  "status": "TODO",
  "userId": 1,
  "createdAt": "2026-05-10T10:00:00",
  "updatedAt": "2026-05-10T10:00:00"
}
```

**錯誤情境:**
- `userId` 不存在 → `404 Not Found`，訊息：`"User not found with id: {userId}"`

---

### 2. 查詢單一任務
```
GET /api/users/{userId}/tasks/{taskId}
```

**Response: 200 OK**（同上格式）

**錯誤情境:**
- `userId` 不存在 → `404 Not Found`
- `taskId` 不存在，或該 task 不屬於此 `userId` → `404 Not Found`，訊息：`"Task not found with id: {taskId}"`

---

### 3. 查詢使用者的任務列表（分頁 + 狀態過濾）
```
GET /api/users/{userId}/tasks?status=TODO&page=0&size=10
```

**Query Parameters:**
| 參數   | 必填 | 預設值 | 說明                              |
|--------|------|--------|-----------------------------------|
| status | 否   | 無     | 若帶入則過濾指定狀態              |
| page   | 否   | 0      | 頁碼（從 0 開始）                 |
| size   | 否   | 10     | 每頁筆數，最大 50                 |

**Response: 200 OK**
```json
{
  "tasks": [...],
  "totalElements": 25,
  "totalPages": 3,
  "currentPage": 0,
  "size": 10
}
```

---

### 4. 更新任務狀態
```
PATCH /api/users/{userId}/tasks/{taskId}/status
```

**Request Body:**
```json
{
  "status": "IN_PROGRESS"
}
```

**Response: 200 OK**（回傳更新後的 Task）

**錯誤情境:**
- 狀態倒退（例如 `DONE` → `TODO`）→ `400 Bad Request`，訊息：`"Invalid status transition from DONE to TODO"`

---

### 5. 刪除任務
```
DELETE /api/users/{userId}/tasks/{taskId}
```

**Response: 204 No Content**

**規則：** 只有 `TODO` 狀態的任務可以刪除；`IN_PROGRESS` 或 `DONE` 的任務刪除時回傳 `400 Bad Request`，訊息：`"Cannot delete task with status: {status}"`

---

## Validation 規則

- `title`：必填（`@NotBlank`），長度 1–100（`@Size`）
- `description`：選填，長度最長 500（`@Size`）
- `size` query param：最大值 50，超過時視為 50（不拋錯，自動 clamp）

---

## 測試要求

請為以下層級各自撰寫測試：

### Unit Test（`TaskServiceTest`）
- [ ] 建立任務：成功情境
- [ ] 建立任務：userId 不存在，拋出例外
- [ ] 更新狀態：`TODO → IN_PROGRESS` 成功
- [ ] 更新狀態：`DONE → TODO` 拋出例外（狀態倒退）
- [ ] 刪除任務：`TODO` 狀態可刪除
- [ ] 刪除任務：`IN_PROGRESS` 狀態拋出例外

### Controller Test（`TaskControllerTest`，用 `@WebMvcTest`）
- [ ] `POST /api/users/1/tasks`：title 為空 → 400
- [ ] `POST /api/users/1/tasks`：userId 不存在 → 404
- [ ] `GET /api/users/1/tasks`：成功回傳分頁結果
- [ ] `PATCH /api/users/1/tasks/1/status`：狀態倒退 → 400

### Integration Test（`TaskControllerIntegrationTest`，用 `@SpringBootTest`）
- [ ] 完整流程：建立 User → 建立 Task → 更新狀態 → 查詢列表

---

## 建議實作順序

1. `TaskStatus` enum
2. `Task` entity（含 JPA 關聯）
3. `TaskRepository`（含分頁查詢方法）
4. `TaskRequest` / `TaskResponse` / `TaskPageResponse` DTO
5. `TaskService`（含狀態流轉驗證邏輯）
6. `TaskController`
7. 例外類別（`TaskNotFoundException`、`InvalidStatusTransitionException`、`TaskDeletionNotAllowedException`）
8. `GlobalExceptionHandler` 新增對應處理
9. 撰寫測試

---

## 提示

- `@ManyToOne` / `@JoinColumn` 用於 Task 與 User 的關聯
- 分頁查詢使用 Spring Data 的 `Pageable` 和 `Page<T>`
- `updatedAt` 可使用 `@PreUpdate` 自動更新
- 狀態流轉邏輯建議封裝在 `TaskStatus` enum 本身（`canTransitionTo(TaskStatus next)` 方法）
