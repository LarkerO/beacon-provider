# Create（机械动力）铁路数据：Beacon Provider 规划文档与实现方式（仅 1.20.1）

> 目标：在 `hydroline-beacon-provider` 中新增 Create Railway 数据通道，满足网站 **实时看列车移动/区段占用** 的需求，同时避免 100 个 Beacon Bukkit 并发请求导致重复查询与超时。
> 
> 核心策略：**实时数据统一查询信道（去重/聚合） + 静态数据缓存 + 周期性导库（SQLite）**。

---

## 1. 约束与背景

- 仅支持 **Minecraft 1.20.1 + Create 6.0.8**。
- Provider 以 **Action** 方式返回 JSON，Beacon Bukkit 作为唯一消费者。
- 网站需实时刷新列车/区段占用，无法完全靠数据库静态数据。
- 需避免高并发情况下反复触发 Create 查询。

---

## 2. 总体架构

### 2.1 数据分层

**A. 实时层（高频、实时）**
- 内容：列车位置/速度、信号区段占用（SignalEdgeGroup）。
- 方式：在 Provider 内部维护 **单一查询信道**（统一调度/缓存），外部请求只读内存快照。
- 更新频率建议：200ms–1000ms（可配置）。

**B. 静态层（低频、可缓存）**
- 内容：轨道网络、轨道几何、站台/信号边界、分段信息等。
- 方式：周期性导出到 SQLite（每 5 分钟 diff），Provider 对外读 DB。

### 2.2 统一查询信道（核心）

为实时数据建立 **单线程/单队列查询**：
- 所有 Action 的实时请求合并到统一调度器。
- 调度器定期从 Create API 取一次快照并更新内存。
- 外部 100 个并发请求只读取同一个快照，避免 100 次 Create 查询。

---

## 3. 数据模型与存储方案

### 3.1 SQLite 静态表（建议）

- `create_graphs(graph_id, checksum, color, bounds, updated_at)`
- `create_nodes(graph_id, node_net_id, dimension, x, y, z, normal_x, normal_y, normal_z)`（可选）
- `create_edges(edge_id, graph_id, node1_net_id, node2_net_id, is_turn, is_portal, length, material_id, updated_at)`
- `create_edge_polyline(edge_id, seq, x, y, z)`（或压缩存储为 blob）
- `create_stations(station_id, graph_id, edge_id, position, name, dimension, x, y, z)`
- `create_signal_boundaries(boundary_id, graph_id, edge_id, position, group_id)`
- `create_edge_segments(segment_id, edge_id, start_pos, end_pos, group_id)`

> 以上仅存“静态网络”，与实时运行状态解耦。

### 3.2 内存实时快照（建议）

- `create_realtime_trains`: trainId、name、speed、status、graphId、position（TravellingPoint）
- `create_realtime_groups`: groupId、occupiedTrains、reservedBoundary、timestamp

**注意**：实时数据不进 SQLite（或只存“最新一份”，避免写放大）。

---

## 4. Action 设计（新增）

为 Create 增加独立 Action（命名待确认）：

- `create:get_network`：返回静态网络（轨道几何、站台、信号边界、分段、portal）
- `create:get_realtime`：返回实时列车 + 区段占用（统一信道快照）

> 与现有 MTR Action 并行实现，避免混用。

---

## 5. 实现流程（规划）

### 5.1 Provider 内部模块划分

1. **CreateQueryGateway（接口）**
   - 类似 `MtrQueryGateway`，提供 Create 数据访问入口。
   - 只在 1.20.1 loader 实现。

2. **CreateRealtimeChannel（统一查询信道）**
   - 定时查询 Create 全局数据（Train + SignalEdgeGroup）。
   - 将结果写入 `AtomicReference<CreateRealtimeSnapshot>`。
   - 处理节流、异常重试、更新时间戳。

3. **CreateStaticSnapshotService（静态导库）**
   - 每 5 分钟 diff：按 `TrackGraph.getChecksum()` 判断变更。
   - 变更则更新 SQLite 中对应 graph/edges/stations 等表。

4. **CreateActionHandlers**
   - `CreateGetNetworkActionHandler`：只读 SQLite。
   - `CreateGetRealtimeActionHandler`：只读内存快照。

### 5.2 数据流

```
Create (server)
  ├─ 实时层 → CreateRealtimeChannel → 内存快照 → Action 返回 JSON
  └─ 静态层 → CreateStaticSnapshotService → SQLite → Action 返回 JSON
```

---

## 6. 并发与性能策略

- Provider 端对外 **只读**：不在请求中触发 Create 读取。
- SQLite 使用 WAL 模式，读写分离。
- Action 返回 JSON 时，静态数据可按 graphId 分页/分片，避免一次性全量返回。
- 实时数据保持轻量，默认只返回“当前可视列车/区段占用”。

---

## 7. 版本限制

- 明确仅支持 **1.20.1**。
- 若 Create 不存在或版本不匹配，Action 返回 `ResultCode.NOT_READY`。

---

## 8. 未来扩展（暂不实现）

- 线路/Route 概念可由 Schedule/Title 推导（非强制）。
- SSE/实时推送可在 Beacon Bukkit 层实现（Provider 仍保持 Action 风格）。

---

## 9. 下一步落地清单

1. 新增 `CreateQueryGateway` 与 `CreateQueryRegistry`（参考 MTR 模式）。
2. 实现 `CreateRealtimeChannel`（定时任务 + 内存快照）。
3. 实现 SQLite 结构与 `CreateStaticSnapshotService`。
4. 新增 `create:get_network` 与 `create:get_realtime` Action。
5. Bukkit 侧按需轮询 `create:get_realtime`（高频）与 `create:get_network`（低频）。

---

## 10. 风险点

- Create API 在 1.20.1 之外版本不兼容：必须严格限制。
- 实时查询若过于频繁会影响主线程：需配置可调间隔。
- 跨维度轨道（portal）需单独处理（`isInterDimensional()`）。
- 曲线轨道 polyline 采样步长需合理。

