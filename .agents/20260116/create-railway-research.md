# Create（机械动力）铁路数据接入 Hydroline Beacon Provider：勘察记录与方案（MC 1.20.1 / Create 6.0.8）

> 目标：为 `hydroline-beacon-provider` 在 1.20.1 接入 Create（机械动力）铁路/列车数据提供可落地的技术路线与字段级参考。
>
> 本文基于本仓库内已提供的 jar 进行离线勘察：
>
> - Create 本体：`libs/create/create-1.20.1-6.0.8.jar`
> - 在线地图示例 mod：`libs/create/create-track-map-2.1-1.20.1-forge`（modId: `createtrackmap`）
>
> 结论先行：Create 并非“纯方块自由搭建、没有结构化数据”。Create 在服务端维护并持久化了 **TrackGraph（轨道图）**、**Train（列车）**、**SignalEdgeGroup（信号占用分组）**、**TrackEdgePoint（站台/信号等边上点）** 等结构化对象；因此接入侧应 **优先直接读取 Create 的铁路 SavedData/Manager**，而不是扫描世界方块去“重建节点/边”。

---

## 1. Create 铁路系统的核心结构（从 `javap` 勘察的类/字段/方法）

### 1.1 入口：`RailwaySavedData`（存档级持久化）

类：`com.simibubi.create.content.trains.RailwaySavedData`

可用 public API（`javap -public`）：

- `static RailwaySavedData load(MinecraftServer)`
- `Map<UUID, TrackGraph> getTrackNetworks()`
- `Map<UUID, Train> getTrains()`
- `Map<UUID, SignalEdgeGroup> getSignalBlocks()`

意义：

- **服务端可直接从 server 拿到“全局铁路状态”**（轨道网络、列车、信号占用组）。
- 这就是我们要的“结构化数据来源”，对标 MTR 的 `RailwayData`。

### 1.2 运行态全局管理器：`GlobalRailwayManager`

类：`com.simibubi.create.content.trains.GlobalRailwayManager`

关键 public 字段/方法：

- 字段：
  - `Map<UUID, TrackGraph> trackNetworks`
  - `Map<UUID, SignalEdgeGroup> signalEdgeGroups`
  - `Map<UUID, Train> trains`
  - `int version`
- 方法：
  - `void markTracksDirty()`
  - `void tick(Level)`
  - `TrackGraph getGraph(LevelAccessor, TrackNodeLocation)`
  - `List<TrackGraph> getGraphs(LevelAccessor, TrackNodeLocation)`
  - `GlobalRailwayManager sided(LevelAccessor)`

意义：

- Create 的铁路数据以 **“多个 TrackGraph 网络”** 组织（每个网络一个 UUID）。
- 存在 `version` 之类的变更指示（用于我们做增量同步/缓存失效判断）。
- `sided(LevelAccessor)` 暗示 server/client 侧管理器可能不同，但我们接入 hydroline provider 主要走服务端。

### 1.3 轨道图：`TrackGraph` / `TrackNode` / `TrackEdge`

#### TrackGraph

类：`com.simibubi.create.content.trains.graph.TrackGraph`

核心能力（public）：

- 图标识/颜色：
  - `UUID id`
  - `Color color`
- 节点：
  - `Set<TrackNodeLocation> getNodes()`
  - `TrackNode locateNode(TrackNodeLocation)`
  - `TrackNode locateNode(Level, Vec3)`（空间定位到最近节点）
  - `Map<TrackNode, TrackEdge> getConnectionsFrom(TrackNode)`
- 连边：
  - `TrackEdge getConnection(Couple<TrackNode>)`
  - `void connectNodes(LevelAccessor, DiscoveredLocation, DiscoveredLocation, BezierConnection)`
  - `void disconnectNodes(TrackNode, TrackNode)`
- 持久化：
  - `CompoundTag write(DimensionPalette)`
  - `static TrackGraph read(CompoundTag, DimensionPalette)`
- 校验/边界/脏标记：
  - `int getChecksum()`
  - `void markDirty()`
  - `TrackGraphBounds getBounds(Level)`

#### TrackNodeLocation

类：`com.simibubi.create.content.trains.graph.TrackNodeLocation extends Vec3i`

public 字段/方法：

- 字段：
  - `ResourceKey<Level> dimension`
  - `int yOffsetPixels`
- 方法：
  - `Vec3 getLocation()`（注意：节点位置是 Vec3）
  - `Collection<BlockPos> allAdjacent()`
  - `write/read/send/receive`（NBT/网络）

意义：

- Create 的节点位置是 **“整数格点 + 像素级 y 偏移”**（`yOffsetPixels`），不是 MTR 那种“节点对象来自站台/信号块”。
- 节点天然附带维度（dimension）。

#### TrackNode

类：`com.simibubi.create.content.trains.graph.TrackNode`

public 方法：

- `TrackNodeLocation getLocation()`
- `int getNetId()`
- `Vec3 getNormal()`

#### TrackEdge

类：`com.simibubi.create.content.trains.graph.TrackEdge`

public 字段/方法（节选）：

- 字段：`TrackNode node1`, `TrackNode node2`
- 形态与属性：
  - `TrackMaterial getTrackMaterial()`
  - `boolean isTurn()`（曲线/转弯）
  - `BezierConnection getTurn()`（若 isTurn）
  - `boolean isInterDimensional()`（跨维度连接/门户轨道）
- 运动学/几何：
  - `double getLength()`
  - `Vec3 getPosition(TrackGraph, double t)`（t 通常 0..length 或 0..1 取决于实现；CreateTrackMap 侧会用 length 做归一化）
  - `Vec3 getDirectionAt(double)`
  - `Vec3 getNormal(TrackGraph, double)`
  - `Vec3 getPositionSmoothed(...)` / `getNormalSmoothed(...)`
- 边数据：
  - `EdgeData getEdgeData()`
- 持久化：`write/read`

意义：

- **我们做地图不需要扫描每一个 TrackBlock 方块**：只要把 `TrackGraph` 的 `TrackEdge` 转成 polyline（采样 `getPosition`），就是轨道几何。
- `isInterDimensional()` 是重要的边界条件：需要单独在 Hydroline 的数据模型里表示“门户/跨维度边”。

### 1.4 边上的“点”：站台与信号（`TrackEdgePoint`）

#### TrackEdgePoint（抽象）

类：`com.simibubi.create.content.trains.signal.TrackEdgePoint`

public 字段/方法：

- 字段：
  - `UUID id`
  - `Couple<TrackNodeLocation> edgeLocation`
  - `double position`（点在边上的位置）
- 方法：
  - `UUID getId()`
  - `double getLocationOn(TrackEdge)`
  - `boolean isPrimary(TrackNode)`
  - `read/write`（NBT/网络）

Create 把“站台/信号边界”等都作为 TrackEdgePoint 挂在 EdgeData 里（见下文 `EdgeData.getPoints()`）。

#### 站台：`GlobalStation`

类：`com.simibubi.create.content.trains.station.GlobalStation extends SingleBlockEntityEdgePoint`

public 字段/方法（节选）：

- 字段：`String name`
- 方法：
  - `Train getPresentTrain()`
  - `Train getNearestTrain()`
  - `reserveFor/cancelReservation/trainDeparted`
  - `read/write`

对应实体方块：`com.simibubi.create.content.trains.station.StationBlockEntity`

- `GlobalStation getStation()`
- `boolean updateName(String)`

#### 信号边界：`SignalBoundary`

类：`com.simibubi.create.content.trains.signal.SignalBoundary extends TrackEdgePoint`

public 字段/方法（节选）：

- `UUID getGroup(TrackNode)`（根据方向/端点获取所属 group）
- `SignalState getStateFor(BlockPos)`
- `SignalType getTypeFor(BlockPos)`
- `boolean isForcedRed(...)`
- `read/write`

#### 信号分组：`SignalEdgeGroup`

类：`com.simibubi.create.content.trains.signal.SignalEdgeGroup`

public 字段/方法（节选）：

- 字段：
  - `UUID id`
  - `Set<Train> trains`（占用该 group 的列车集合）
  - `SignalBoundary reserved`
  - `EdgeGroupColor color`
- `boolean isOccupiedUnless(Train)`
- `CompoundTag write()` / `static read(CompoundTag)`

意义：

- Create 把“区段占用”抽象成 group：边上每个区段（受信号边界切分）会映射到某个 groupId。
- Hydroline 做“轨道占用热力/区段状态”时，应使用 groupId + trains/reserved 来表达，而不是自己做碰撞/占用推导。

### 1.5 边数据：`EdgeData`

类：`com.simibubi.create.content.trains.graph.EdgeData`

public 方法（节选）：

- 信号：
  - `boolean hasSignalBoundaries()`
  - `UUID getSingleSignalGroup()`
  - `UUID getGroupAtPosition(TrackGraph, double position)`
  - `UUID getEffectiveEdgeGroupId(TrackGraph)`（CreateTrackMap 用它把整条 edge 归入“effective group”）
- 点（站台/信号等）：
  - `List<TrackEdgePoint> getPoints()`
  - `void addPoint(TrackGraph, TrackEdgePoint)`
  - `void removePoint(TrackGraph, TrackEdgePoint)`
- 交叉口：
  - `List<TrackEdgeIntersection> getIntersections()`
  - `addIntersection/removeIntersection`
- 持久化：`write/read`

意义：

- **线路自由度高 ≠ 无结构**。EdgeData 给了我们“分段/信号边界/站台点/交叉口”这些结构化信息。

### 1.6 轨道方块（rail block）：`TrackBlock` / `TrackBlockEntity` / `BezierConnection`

#### 轨道方块注册项（来自 `AllBlocks`）

类：`com.simibubi.create.AllBlocks`

public 静态字段（节选）：

- `TRACK`（`TrackBlock`）
- `FAKE_TRACK`（`FakeTrackBlock`）
- `RAILWAY_CASING`
- `TRACK_STATION`（`StationBlock`）
- `TRACK_SIGNAL`（`SignalBlock`）
- `TRACK_OBSERVER`（`TrackObserverBlock`）
- `SMALL_BOGEY` / `LARGE_BOGEY`

对应资源路径（从 jar 的 blockstates 文件名可直接推断 registry path）：

- `create:track`
- `create:fake_track`
- `create:track_station`
- `create:track_signal`
- `create:track_observer`
- `create:railway_casing`

#### TrackBlock（方块状态与连接查询）

类：`com.simibubi.create.content.trains.track.TrackBlock`

关键 public 字段/方法：

- BlockState 属性：
  - `EnumProperty<TrackShape> SHAPE`
  - `BooleanProperty HAS_BE`
- 连接关系（用于图构建/邻接查询）：
  - `Collection<TrackNodeLocation.DiscoveredLocation> getConnected(BlockGetter, BlockPos, BlockState, boolean, TrackNodeLocation)`
  - `List<Vec3> getTrackAxes(...)`
  - `Vec3 getCurveStart(...)`
- 图形辅助：
  - `Vec3 getUpNormal(...)`

#### TrackBlockEntity（曲线连接存储）

类：`com.simibubi.create.content.trains.track.TrackBlockEntity`

关键 public 方法：

- `Map<BlockPos, BezierConnection> getConnections()`
- `void addConnection(BezierConnection)`
- `void removeConnection(BlockPos)`
- `void validateConnections()`

意义：

- 曲线轨道（Bezier）并不是“每段都由方块决定”，而是靠 `TrackBlockEntity` 的连接数据描述。
- 但对我们来说：**不需要从方块/BE 重建**，直接读 `TrackGraph`/`TrackEdge` 更稳、更便宜。

---

## 2. 车厢/列车信息（Train / Carriage / TravellingPoint）

### 2.1 列车：`Train`

类：`com.simibubi.create.content.trains.entity.Train`

关键 public 字段（节选）：

- 身份与展示：
  - `UUID id`
  - `Component name`
  - `TrainIconType icon`
  - `int mapColorIndex`
  - `UUID owner`
- 运行态：
  - `double speed`, `double targetSpeed`, `double throttle`
  - `TrainStatus status`
  - `boolean derailed`
- 所属网络：
  - `TrackGraph graph`
- 车厢：
  - `List<Carriage> carriages`
  - `List<Integer> carriageSpacing`
- 站台/线路相关：
  - `UUID currentStation`
  - `Navigation navigation`
  - `ScheduleRuntime runtime`
- 维度定位：
  - `List<ResourceKey<Level>> getPresentDimensions()`
  - `Optional<BlockPos> getPositionInDimension(ResourceKey<Level>)`

建议我们在 Hydroline 输出的“列车状态”至少包含：

- `trainId`、`name`、`speed/targetSpeed`、`status`、`derailed`
- `graphId`（或 networkId）
- `presentDimensions` + 每维度的位置（若可得）
- `carriages` 的数量与每节车厢的 leading/trailing 点（见下文）

### 2.2 车厢：`Carriage`

类：`com.simibubi.create.content.trains.entity.Carriage`

关键 public 字段/方法：

- `int id`
- `Couple<CarriageBogey> bogeys`
- `int bogeySpacing`
- `TravellingPoint getLeadingPoint()`
- `TravellingPoint getTrailingPoint()`
- `Optional<BlockPos> getPositionInDimension(ResourceKey<Level>)`

### 2.3 行进点：`TravellingPoint`

类：`com.simibubi.create.content.trains.entity.TravellingPoint`

public 字段/方法：

- 字段：`TrackNode node1`, `TrackNode node2`, `TrackEdge edge`, `double position`
- `Vec3 getPosition(TrackGraph)`
- `CompoundTag write(...)` / `static read(...)`

意义：

- 对地图/实时定位而言，**TravellingPoint 是比“实体坐标”更稳定的来源**：它由轨道图边+参数位置定义。
- CreateTrackMap 的实现也倾向于把 TravellingPoint 转成（dimension, point）形式输出。

### 2.4 车轮架/转向架：`CarriageBogey` / `BogeyStyle`

类：`com.simibubi.create.content.trains.entity.CarriageBogey`

- `CompoundTag bogeyData`
- `BogeyStyle getStyle()`
- `BogeySize getSize()`
- `boolean isUpsideDown()`

意义：

- Hydroline 若需要“车厢类型/外观”可从 `bogeyData + styleId + size` 组合推断。
- 但注意：Create 的列车是“活动结构（Contraption）”，车厢本体可能是任意方块组合；要拿到“车厢材质/构型”会很重，且可能要求 chunk/entity 已加载（后续需要单独评估需求）。

---

## 3. Create 是否存在“线路信息”？（结论：没有 MTR 那种固定 Route，但有 Schedule）

Create 的铁路系统不会强制用户定义“线路/Route/站点节点图”，但提供了 **列车运行计划（Schedule）**，可作为“线路”的替代结构。

### 3.1 Schedule 与运行态：`ScheduleRuntime`

类：`com.simibubi.create.content.trains.schedule.ScheduleRuntime`

关键字段/方法：

- `Schedule schedule`
- `int currentEntry`
- `String currentTitle`（非常适合当作 Hydroline 的“线路名/服务名”）
- `Schedule getSchedule()`
- `CompoundTag write()` / `void read(CompoundTag)`

### 3.2 Schedule 内容：`Schedule` / `ScheduleEntry` / `DestinationInstruction`

- `Schedule.entries: List<ScheduleEntry>`
- `ScheduleEntry.instruction: ScheduleInstruction`

目的地指令：
类：`com.simibubi.create.content.trains.schedule.destination.DestinationInstruction extends TextScheduleInstruction`

关键方法：

- `String getFilter()` / `String getFilterForRegex()`
- `DiscoveredPath start(ScheduleRuntime, Level)`

意义与建议：

- Create 的“到站”是通过 `DestinationInstruction.filter` 去匹配 `GlobalStation.name`（常见用法是输入站名/正则）。
- 我们可以在 Hydroline 把“线路/route”定义为：
  1. `Train.runtime.currentTitle`（如果用户用 `ChangeTitleInstruction` 设置了标题）
  2. 否则 fallback 到 `Train.name`
  3. 站序列可由 `Schedule.entries` 中的 `DestinationInstruction.filter` 提取（但这是“筛选规则”，不是固定站点引用；需要谨慎解释）

---

## 4. create-track-map（在线地图 mod）的做法：可直接复用的思路

### 4.1 它读取什么？

`littlechasiu.ctm.TrackWatcher`（Kotlin）内有一个静态字段：

- `private static final GlobalRailwayManager RR;`

它在 `update()` 周期性做（从 `javap -c` 可见）：

- 遍历 `RR.trackNetworks.entrySet()`：
  - 收集网络内的 `TrackNode`、`TrackEdge`
  - 对每条 `TrackEdge`：
    - `edge.getEdgeData().getPoints()` 中筛选：
      - `GlobalStation` -> 站
      - `SignalBoundary` -> 信号边界
- 读取 `RR.signalEdgeGroups` 生成 “CreateSignalBlock”（区段/占用单元）
- 遍历所有 graph 的 edges：
  - 若 `edge.isInterDimensional()`，则把它作为 `Portal`（跨维度边）挂到对应 signal block 上
  - 若 `edge` 有 signal boundaries，则按 boundary 位置把 edge 切分成多个 segment
- `RR.trains.values()` 替换到内部 trains 集合

结论：

- 它完全没有扫描世界方块；**只依赖 Create 运行态的结构化对象**。
- 这也验证了我们对接 Hydroline 的首选路线：**读 TrackGraph/Train/EdgeData/SignalEdgeGroup**。

### 4.2 它怎么输出？

`littlechasiu.ctm.Server`（Ktor embedded server）暴露 HTTP + SSE：

- JSON：
  - `/api/config.json`
  - `/api/network`
  - `/api/signals`
  - `/api/blocks`（轨道占用/区段）
  - `/api/trains`
- 实时（后缀 `.rt`，对应 SSE 或类似实时流）：
  - `/api/network.rt`
  - `/api/signals.rt`
  - `/api/blocks.rt`
  - `/api/trains.rt`

默认端口（jar 内默认值）：`3876`

Modrinth 描述（页面文本快照）也明确：

- “A web-based track map ... signals, stations, and trains moving in real time.”
- “Server-side / Singleplayer”
- 支持 `1.20.1`，并提供可重载配置命令 `/ctm reload`

### 4.3 它的“轨道几何”怎么表达？

`littlechasiu.ctm.ExtensionsKt` 提供转换：

- `TrackEdge -> Edge/Portal`
  - `edge.isInterDimensional()` => `Portal(from,to)`
  - 否则：`getPath(edge).getSendable()`
- `getPath(edge)`：
  - `edge.isTurn()` => `BezierCurve.Companion.from(edge.getTurn(), dimensionString)`
  - 否则 => `Line(...)`

它的核心思想：

- 在输出层只区分两类：直线段 vs 曲线段（Bezier），并都能 `divideAt()` 来切分（用于信号边界分段）。

这对 Hydroline 非常重要：

- Hydroline 前端若要渲染轨道，最友好的表示是：**polyline**（采样后点列）。
- 但为了更精确/更小数据量，也可以像 CTM 一样保留“Bezier 参数”（需要额外设计前端）。

---

## 5. Hydroline 侧的推荐接入方案（对比 MTR：结构化读取 + 缓存 + 增量）

### 5.1 方案总览（推荐）

目标：在 Beacon Provider 中增加 “Create Railway Data Provider”，提供与 MTR 类似的查询/推送能力。

**数据源优先级：**

1. 首选：直接读取 Create 的结构化对象（推荐）
   - `RailwaySavedData.load(server)`
   - 或从 `GlobalRailwayManager` 获取（取决于我们当前架构更容易拿到 server 还是 manager）
2. 兜底：扫描世界方块（不推荐，仅作为异常 fallback）
   - 成本极高（chunk/BE 访问），且难以覆盖 Bezier/portal/信号分段等逻辑

**同步方式：**

- 静态/低频：TrackGraph（网络、边几何、站台、信号边界） -> **缓存**（按 graphId + checksum/version）
- 高频/实时：列车位置、区段占用 -> **轻量增量**（tick 或固定间隔）

### 5.2 为什么 Create “自由搭建”仍然适合结构化同步？

Create 内部已经做了我们最不想做的事：

- 根据 `TrackBlock + TrackBlockEntity(BezierConnection)` 维护 **轨道图 TrackGraph**
- 把站台/信号作为 **EdgePoint** 挂到图边上
- 把占用抽象为 **SignalEdgeGroup** 并维护 “哪些 train 占用/预定了哪些 group”

所以对 Hydroline 来说：

- **自由度高** 只是“玩家搭建行为”自由；
- **数据层** 已经被 Create 规范化成图模型；
- 我们只需要把 Create 的图模型映射到 Hydroline 的协议模型。

### 5.3 Hydroline 建模建议（最小可用版本 MVP）

下面给出一个“能做地图+列车实时位置+区段占用”的最小结构（建议作为我们协议的 Create 分支模型）。

#### Network/Graph

- `graphId: UUID`
- `color`（可选）
- `bounds`（可选：用于前端视图自动缩放；Create 有 `TrackGraphBounds`）

#### Node（可选）

如果前端仅渲染轨道 polyline，可不显式输出节点；但用于交互/调试/拓扑分析时很有价值。

- `nodeNetId: int`（`TrackNode.getNetId()`）
- `dimension: string`（`TrackNodeLocation.dimension`）
- `pos: {x,y,z}`（`TrackNodeLocation.getLocation()`）
- `normal: {x,y,z}`（`TrackNode.getNormal()`）

#### Edge（轨道段）

- `edgeId`（Create 没有显式 edgeId；可用 `node1.netId + node2.netId + graphId` 派生，或 hash）
- `graphId`
- `node1NetId`, `node2NetId`
- `dimension`（一般取 node1 的维度；`isInterDimensional` 另处理）
- `materialId`（`TrackEdge.getTrackMaterial()` -> registry id，可能需反射/序列化）
- `isTurn: boolean`
- `isInterDimensional: boolean`
- `length: double`
- `polyline: [{x,y,z}, ...]`（由 `TrackEdge.getPosition(graph, t)` 采样得到；推荐做缓存）
- `segments: [...]`（可选：受信号边界切分后的子段，用于“区段占用”着色）

#### Station（站台点）

来自 `EdgeData.getPoints()` 中的 `GlobalStation`

- `stationId: UUID`（`TrackEdgePoint.id`）
- `name: string`（`GlobalStation.name`）
- `dimension` + `blockPos`（`SingleBlockEntityEdgePoint`）
- `onEdge: {node1NetId,node2NetId,position}`（来自 TrackEdgePoint）

#### SignalBoundary（信号边界点）

来自 `EdgeData.getPoints()` 中的 `SignalBoundary`

- `signalBoundaryId: UUID`
- `onEdge: {node1NetId,node2NetId,position}`
- `groups: {primaryGroupId, secondaryGroupId}`（通过 `getGroup(node)` 获取；需结合 node1/node2 方向）
- `blockEntities`（可选：它持有方块 pos->bool 映射；若要展示多灯位可以输出）
- `state`（可选：`getStateFor(pos)`）

#### SignalEdgeGroup（区段/占用组）

来自 `GlobalRailwayManager.signalEdgeGroups`

- `groupId: UUID`
- `color`
- `occupiedByTrainIds: UUID[]`（由 `SignalEdgeGroup.trains`）
- `reservedBy: ...`（可选：`SignalEdgeGroup.reserved`）

#### Train（列车状态）

来自 `GlobalRailwayManager.trains`

- `trainId: UUID`
- `name: string`
- `graphId: UUID`
- `speed/targetSpeed/throttle/status/derailed`
- `currentStationId: UUID?`
- `lineTitle: string?`（优先 `Train.runtime.currentTitle`）
- `cars: [{carriageId, leading: DimensionLocation?, trailing: DimensionLocation?}, ...]`

`DimensionLocation` 的推荐形态与 CTM 一致：

- `dimension: string`
- `pos: {x,y,z}`（由 `TravellingPoint.getPosition(train.graph)` 得到）

### 5.4 增量与性能策略（大世界必备）

Create 的轨道网络可能非常大，直接每次全量序列化会导致：

- 服务器 tick 抖动（CPU、GC）
- 网络带宽/响应时间不可控

建议策略：

1. TrackGraph 静态几何 **分层缓存**
   - key：`graphId + graphChecksum`（`TrackGraph.getChecksum()`）
   - value：edges 的 polyline（采样后点列）+ stations/signals 列表
2. 实时部分 **小对象高频**
   - trains（位置/速度）与 signalEdgeGroups（occupied/reserved）单独接口
3. 变更触发：
   - 若能拿到 `GlobalRailwayManager.version` 并观测其变化：可用作“轨道结构变化”信号
   - 也可定时重新算 checksum：变化则重建该 graph 的缓存

### 5.5 “线路/Route”在 Hydroline 的实现建议

Create 没有强制线路，但 Hydroline 需要“线路视角”时，可采用以下层级：

1. **用户显式定义**（推荐，最稳定）
   - 鼓励玩家在 Schedule 里用 `ChangeTitleInstruction` 设置 `currentTitle`（作为线路名）
2. **从 Schedule 推导站序**（次推荐）
   - 抽取 `Schedule.entries` 中 `DestinationInstruction.getFilter()` 作为“停靠规则”
   - 注意：filter 可能是正则，不是站点 UUID；展示时应标注“匹配规则”
3. **从列车名/车站名弱推导**（fallback）
   - 例如 `Train.name` 前缀、或自定义命名约定（这部分需要产品/运营规则，不在本文决定）

---

## 6. 风险点与边界条件清单（接入时必须显式处理）

1. 跨维度边（`TrackEdge.isInterDimensional()`）
   - Hydroline 模型里需要“Portal/跨维度连接”的显式表示
2. 曲线边（`isTurn()` + `BezierConnection`）
   - 采样 polyline 时步长要合理（建议按长度自适应，或固定 0.5~1.0 方块）
3. chunk 未加载时的列车/车厢定位
   - 实体坐标可能不可用；优先用 `TravellingPoint.getPosition(graph)`
4. “轨道占用”展示不能靠自己推导
   - 应以 `SignalEdgeGroup` + `EdgeData` 的 groupId 切分为准
5. 大规模网络的序列化与发送
   - 必须缓存与增量，否则对服务器与网络不友好
6. 版本兼容
   - Create 的铁路系统在 1.18.2/1.20.1 的 API 细节可能差异较大；本项目明确只做 1.20.1

---

## 7. 与 `hydroline-beacon-provider` 现有架构的对齐建议（下一步落地）

本仓库已存在 MTR 的网关抽象：`common/src/main/java/com/hydroline/beacon/provider/mtr/MtrQueryGateway.java`

建议新增一个并行接口（名称仅建议）：

- `CreateQueryGateway`（或 `CreateRailwayGateway`）
  - `boolean isReady()`
  - `CreateNetworkSnapshot fetchNetwork()`（静态/低频）
  - `CreateRealtimeSnapshot fetchRealtime()`（列车+占用，高频）

并参考 CTM 的拆分方式：

- `/network`（轨道几何、站台、信号边界、portal）
- `/blocks`（区段/占用组状态）
- `/trains`（列车与车厢实时状态）

实现层面要点：

- Create 是可选依赖：需要在 loader 侧检测 mod 是否加载，并隔离类引用（避免无 Create 时 NoClassDefFoundError）。

---

## 8. 附：本次勘察中确认的关键类索引（便于后续反查）

Create（`create-1.20.1-6.0.8.jar`）：

- 全局/持久化
  - `com.simibubi.create.content.trains.RailwaySavedData`
  - `com.simibubi.create.content.trains.GlobalRailwayManager`
- 图结构
  - `com.simibubi.create.content.trains.graph.TrackGraph`
  - `com.simibubi.create.content.trains.graph.TrackNode`
  - `com.simibubi.create.content.trains.graph.TrackNodeLocation`
  - `com.simibubi.create.content.trains.graph.TrackEdge`
  - `com.simibubi.create.content.trains.graph.EdgeData`
- 点/信号/站台
  - `com.simibubi.create.content.trains.signal.TrackEdgePoint`
  - `com.simibubi.create.content.trains.signal.SignalBoundary`
  - `com.simibubi.create.content.trains.signal.SignalEdgeGroup`
  - `com.simibubi.create.content.trains.station.GlobalStation`
  - `com.simibubi.create.content.trains.station.StationBlockEntity`
- 列车
  - `com.simibubi.create.content.trains.entity.Train`
  - `com.simibubi.create.content.trains.entity.Carriage`
  - `com.simibubi.create.content.trains.entity.TravellingPoint`
  - `com.simibubi.create.content.trains.entity.CarriageBogey`
- 线路/计划
  - `com.simibubi.create.content.trains.schedule.ScheduleRuntime`
  - `com.simibubi.create.content.trains.schedule.Schedule`
  - `com.simibubi.create.content.trains.schedule.ScheduleEntry`
  - `com.simibubi.create.content.trains.schedule.destination.DestinationInstruction`
  - `com.simibubi.create.content.trains.schedule.destination.ChangeTitleInstruction`

CreateTrackMap（`create-track-map-2.1-1.20.1-forge`）：

- `littlechasiu.ctm.TrackWatcher`（核心采集逻辑）
- `littlechasiu.ctm.Server`（HTTP/SSE 输出：`/api/network` 等）
- `littlechasiu.ctm.ExtensionsKt`（Create 对象 -> 可发送模型）
