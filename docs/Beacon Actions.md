# Beacon Actions

Beacon Provider 只保留最小的 `action` 集合，专注于提供当前 Minecraft 世界的 MTR + Create 数据快照：静态结构数据由 Bukkit 端缓存或由 Provider 侧 SQLite 缓存，Provider 只需要返回动态的状态，供前端/Beacon 进行进一步的处理。

## 1. 可用 Action 一览

| Action 名称 | 说明 | 请求 `payload` | 响应结构 |
|-------------|------|----------------|----------|
| `beacon:ping` | 验证 Gateway 通信，并测量往返延迟。 | 可选：`echo` (`string`) | `echo`（原样返回）、`receivedAt`（服务器时间，ms）、`latencyMs`（处理耗时） |
| `mtr:get_railway_snapshot` | 返回一个或多个维度当前的 `RailwayData` 快照（MessagePack 格式 + Base64 编码），仅支持读操作。 | 可选：`dimension`（如 `minecraft:overworld`），不传则返回所有缓存的维度。 | `format: "messagepack"`，`snapshots` （数组，每项包含 `dimension`, `timestamp`, `length`, `payloadChunks`（chunked Base64）） |
| `mtr:get_route_trains` | 返回指定维度/线路上正在运行的列车列表及对应的轨道 ID（`railId`）。 | 可选：`dimension`、`routeId`（默认 `0` 表示所有线路）。 | `timestamp`、可选的 `dimension`/`routeId`、`trains`（含 `trainUuid`、`trainId`、`routeId`、`railId`、`currentStationId`、`nextStationId`、`progress`、`segmentCategory`、`delayMillis` 等） |
| `mtr:get_station_schedule` | 查询某个车站（可选站台）的时刻表，按平台返回即将到达的列车记录（附带线路名称、可跨维度 aggregation）。 | 必需：`stationId`；可选：`dimension`、`platformId`。 | `timestamp`、`stationId`、可选 `dimension`、`timetables`（每项含 `dimension`、`platforms`：`platformId`、`platformName`，`entries` 包含 `routeId`、`routeName`、`route`（线路标签，如 `G23 To`）、`color`（十进制）、`destination`、`circular`、`arrivalMillis`、`trainCars`、`currentStationIndex`、`delayMillis`（若有）） |
| `mtr:get_all_station_schedules` | 扫描所有维度的 station/platform，返回每个平台的时刻表。 | 可选：`dimension`（默认遍历所有已注册维度）。 | `timestamp`、`dimensions`（每项含 `dimension`、`stations`，站点带 `stationId`/`stationName` 和 `platforms`→`platformId`/`platformName` 与 `entries`，内容同 `mtr:get_station_schedule`） |
| `create:get_network` | 返回 Create 轨道网络的静态结构数据（SQLite 缓存），包含轨道节点、边、站台/信号边界/分段等。 | 可选：`graphId`（不传则返回所有网络）；可选：`includePolylines`（默认 `true`）。 | `timestamp`、`graphs`、`nodes`、`edges`、`edgePolylines`（可选）、`stations`、`signalBoundaries`、`edgeSegments` |
| `create:get_realtime` | 返回 Create 实时列车/区段占用信息（统一查询信道内存快照）。 | 无。 | `timestamp`、`trains`、`groups` |

## 2. 新 Action 的响应说明

- `snapshots`：数组，每个元素对应一个维度的 `RailwayData`。  
  - `dimension`: 维度标识（`ResourceLocation` 字符串）。  
  - `timestamp`: 服务端序列化时的毫秒时间戳。  
  - `length`: 解码后的原始 MessagePack 字节数。  
  - `payloadChunks`: 包含 chunked Base64 字符串的对象，字段包括 `encoding`（当前固定 `base64`）、`decodedLength`、`chunkCount`、`chunkSize`、以及 `chunks` 数组；客户端需按 `chunks` 里 `index` 升序拼接每项的 `data` 值，再执行 Base64 解码与 MessagePack 解析。  
- `format`（根级）：目前固定为 `"messagepack"`，用于说明 `payloadChunks` 中的数据编码格式。

调用方只需按 `payloadChunks.chunks` 里的 `index` 升序拼接每项 `data`（形成完整的 Base64 字符串）、再交给 MessagePack 解析器，即可得到与 `world/mtr` 存储结构等价的 `stations`、`platforms`、`routes`、`depots`、`rails`、`signalBlocks`、`sidings` 等集合，用作进一步的 Leaflet 可视化或数据对比。

### 2.1 站点/平台时刻表的 `entries`

- 每条 `entry` 对应一趟即将到达该平台的列车，它包含 `routeId`、`routeName`，以及 MTR 返回的 `route` 字段（示例 `G23 To`）、`color`（十进制表示）、`destination`、`circular`、`arrivalMillis`、`trainCars`、`currentStationIndex` 以及（如有）`delayMillis`。
- `platforms` 及其 `entries` 均保持 MTR 原始顺序，可直接用于按顺序渲染，或者再按 `arrivalMillis` 重新排序显示。

### 2.2 站点时刻表请求限流

- `mtr:get_station_schedule` 与 `mtr:get_all_station_schedules` 目前共用一个串行请求队列，Beacon Provider 会在后台只运行一个线程，每次处理之间至少等待 `beacon.scheduleRateLimitMs` 毫秒（默认 400ms），队列最大等待请求数为 64。
- 请求在队列耗尽或等待超时（默认 `beacon.scheduleRequestTimeoutMs=30000`）时会立刻返回 `ResultCode.BUSY`，客户端应当捕捉这一状态并适当退避再重试。通过 `-Dbeacon.scheduleRateLimitMs=500` 或 `-Dbeacon.scheduleRequestTimeoutMs=60000` 等方式可以调节节流器。
- 该机制避免在 MTR 主线程读取 `StationTimetable` 时出现并发修改带来的 `IndexOutOfBoundsException`，也保障了 `platforms`/`entries` 读取到的结构尽可能稳定。

## 3. Create 动作说明（1.20.1）

### 3.1 `create:get_network`

**用途**：获取 Create 轨道网络的静态结构数据（Provider 侧缓存到 SQLite），可用于地图渲染/站台与信号边界展示。  
**注意**：此 action 只读取缓存，不会触发 Create 的实时查询。

请求 `payload`：

```json
{
  "graphId": "可选，字符串；为空则返回全部网络",
  "includePolylines": true
}
```

响应 `payload`：

- `timestamp`：毫秒时间戳。
- `graphs`：轨道网络列表，每项：
  - `graphId`：Create TrackGraph UUID。
  - `checksum`：TrackGraph 校验值（用于变化检测）。
  - `color`：Create 轨道网络颜色（整数）。
  - `updatedAt`：写入缓存的时间戳。
- `nodes`：轨道节点列表，每项：
  - `graphId`、`netId`、`dimension`、`x/y/z`、`normal`（三元数组）、`yOffsetPixels`。
- `edges`：轨道边列表，每项：
  - `edgeId`（Provider 生成的稳定 ID）、`graphId`、`node1NetId`、`node2NetId`、`isTurn`、`isPortal`、`length`、`materialId`（可能为空）。
- `edgePolylines`：仅当 `includePolylines=true` 返回，按 edge 分组的采样点：
  - `edgeId`、`points`（数组，成员为 `[x,y,z]`）。
- `stations`：站台点列表（Create GlobalStation）：
  - `stationId`、`graphId`、`edgeId`、`position`、`name`、`dimension`、`x/y/z`。
- `signalBoundaries`：信号边界点列表（Create SignalBoundary）：
  - `boundaryId`、`graphId`、`edgeId`、`position`、`groupIdPrimary`、`groupIdSecondary`、`dimension`、`x/y/z`。
- `edgeSegments`：信号分段列表（按边上信号切分）：
  - `segmentId`、`edgeId`、`startPos`、`endPos`、`groupId`（用于区段占用映射）。

### 3.2 `create:get_realtime`

**用途**：获取 Create 列车实时位置与信号区段占用（内存快照），适合高频轮询。  
**注意**：Provider 内部会对所有请求使用统一查询信道，避免 100 个请求触发 100 次 Create 查询。

响应 `payload`：

- `timestamp`：快照采集时间戳。
- `trains`：列车状态列表，每项：
  - `trainId`、`name`、`iconId`、`mapColorIndex`、`status`、`speed`、`targetSpeed`、`throttle`、`derailed`。
  - `graphId`（所属网络）、`currentStationId`（若有）。
  - `scheduleTitle`、`scheduleEntry`、`scheduleState`、`schedulePaused`、`scheduleCompleted`、`scheduleAuto`（若运行了 Create Schedule）。
  - `positions`：列车在各维度的坐标列表（`dimension`、`x/y/z`）。
  - `carriages`：车厢列表：
    - `id`、`bogeySpacing`。
    - `leading`/`trailing`：`edgeId`、`node1NetId`、`node2NetId`、`position`、`dimension`、`x/y/z`（基于 TravellingPoint）。
    - `leadingBogey`/`trailingBogey`：`styleId`、`size`、`upsideDown`。
- `groups`：信号区段占用列表（Create SignalEdgeGroup）：
  - `groupId`、`color`、`reservedBoundaryId`、`trainIds`（占用该区段的列车 UUID 列表）。

### 3.3 缓存与实时策略

- 静态数据：写入 `config/beacon-provider/cache.db`，默认每 5 分钟按 `TrackGraph.checksum` 进行 diff 更新。
- 实时数据：默认 500ms 刷新一次内存快照，所有 `create:get_realtime` 请求读取同一份快照。
- 若服务器未加载 Create 或未就绪，Create action 会返回 `ResultCode.NOT_READY`。

## 4. 扩展与注意事项

- provider 仍保留 `PingAction` 用于连接检测，所有 MTR 逻辑通过 `MtrQueryGateway` 的快照缓存（`MtrSnapshotCache`）读取，防止在主线程上频繁重新构建 `RailwayData`。  
- 如果需要覆盖维度筛选或补充额外的 `payload` 字段，可以在 Bukkit 端负责格式化，Provider 只负责将 `RailwayData` 原封不动地序列化为 MessagePack 并返回。  
- 对于大文件/高频请求场景，建议在客户端缓存解码后的快照，并结合 `timestamp` 判断是否需要重新请求。  
