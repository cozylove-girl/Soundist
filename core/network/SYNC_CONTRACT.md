# Soundist 可选云同步契约

## 产品边界

Soundist 以本地数据为主。关闭云同步、未登录或离线时，环境声、专注、记录、习惯和笔记仍须完整可用。

可选云同步需要稳定的设备身份、增量推送与拉取、幂等重放、版本冲突、删除墓碑、行级安全、设备撤销、私有附件存储，以及账号数据导出与删除。本模块定义双向实体同步的传输和编排边界；认证界面、附件上传、数据库桥接与账号管理由相应应用层模块实现。

## 传输协议

- `POST /rest/v1/rpc/sync_push` 接收 `PushRequest`。
- `POST /rest/v1/rpc/sync_pull` 接收 `PullRequest`。
- 每次修改都带有全局唯一且持久保存的 `operationId`，并可携带 `baseRevision`。
- 服务端按用户保存已经处理的操作 ID；重复提交返回 `ALREADY_APPLIED`，不得重复执行。
- 更新依据 `baseRevision` 进行比较并交换。版本不匹配时返回 `CONFLICT` 和服务端规范记录，不得静默覆盖。
- 删除采用带版本的墓碑记录，并参与增量拉取。
- 拉取游标是按用户隔离的不透明水位标记；结果须按 `(serverUpdatedAt, entityType, entityId, revision)` 确定性排序。
- 每行数据归 `auth.uid()` 对应的用户所有。客户端只能携带公开 anon key 与当前用户 JWT，APK 中不得包含 service-role key。

## `core:database` 接入要求

`SyncLocalStore` 须满足以下原子性要求：

1. `pendingMutations` 返回持久化队列记录，其中包含操作 ID、实体 ID 与类型、操作类型、序列化负载、基础版本和客户端时间戳。`SyncQueueEntity` 需要通过非破坏性的 Room migration 补充 `operationId` 与 `baseRevision`。
2. `acknowledge` 只移除服务端已经确认的操作 ID。
3. `recordConflicts` 同时保存本地与远端副本，交由领域层或界面明确解决；不得删除本地修改。
4. `recordRejected` 保存永久拒绝状态和结构化错误，不得进入无限重试。
5. `applyRemotePage(changes, nextCursor)` 在同一个 Room 事务中应用版本单调递增的更新或墓碑，并推进游标。任一实体写入失败时都不得推进游标。
6. 每个已登录用户独立保存游标；切换账号时须原子地清除或切换游标。

## 应用层接入要求

- 提供稳定、仅供应用使用的设备 ID，以及能够实时取得令牌的认证接口。
- 只有在生产级 `SyncLocalStore`、稳定设备 ID 和实时令牌接口均已具备后，才在应用层接入 WorkManager。Worker 将 `Completed` 映射为成功、`Retry` 映射为重试，其余结果映射为失败。
- `Disabled` 与 `Unauthenticated` 是用户可见的未启用状态，不得伪装成同步成功。
- 使用带约束的 WorkManager 周期任务，并在应用回到前台、登录和网络变化时触发同步；离线写入继续保存在本地队列。
- 对外宣称云同步可用之前，须完成服务端 SQL/RPC、RLS 跨用户隔离、令牌刷新、设备撤销及账号数据导出与删除的部署和集成验证。
