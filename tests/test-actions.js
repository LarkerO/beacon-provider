#!/usr/bin/env node
import net from "node:net";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import process from "node:process";
import { fileURLToPath } from "node:url";
import msgpack from "msgpack-lite";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

loadDotEnv(path.join(__dirname, ".env"));

const HOST = process.env.PROVIDER_HOST || "127.0.0.1";
const PORT = Number(process.env.PROVIDER_PORT || "28545");
const TOKEN = process.env.PROVIDER_TOKEN || "change-me";
const DIMENSION = process.env.PROVIDER_MTR_DIMENSION || "minecraft:overworld";
const REQUEST_TIMEOUT_MS = Number(process.env.REQUEST_TIMEOUT_MS || "15000");
const OUTPUT_DIR = path.resolve(
  process.env.OUTPUT_DIR || path.join(__dirname, "output")
);
const ACTION_NAME = "mtr:get_railway_snapshot";
const CREATE_NETWORK_ACTION = "create:get_network";
const CREATE_REALTIME_ACTION = "create:get_realtime";
const ROUTE_ID = Number(process.env.PROVIDER_MTR_ROUTE_ID || "0");
const STATION_ID = parseEnvLong(process.env.PROVIDER_MTR_STATION_ID);
const STATION_PLATFORM_ID = parseEnvLong(process.env.PROVIDER_MTR_PLATFORM_ID);
const DEPOT_ID = process.env.PROVIDER_MTR_DEPOT_ID
  ? Number(process.env.PROVIDER_MTR_DEPOT_ID)
  : 0;

async function main() {
  console.log(`Connecting to ${HOST}:${PORT} ...`);
  await prepareOutputDir(OUTPUT_DIR);
  const client = new GatewayClient({
    host: HOST,
    port: PORT,
    token: TOKEN,
    timeoutMs: REQUEST_TIMEOUT_MS,
  });
  try {
    await client.connect();
    const ping = await client.request("beacon:ping", { echo: "tests" });
    await writeJson(path.join(OUTPUT_DIR, "beacon_ping.json"), ping);

    const response = await client.request(
      ACTION_NAME,
      DIMENSION ? { dimension: DIMENSION } : {}
    );
    const slug = dimensionToSlug(DIMENSION || "all");
    const responseTarget = path.join(
      OUTPUT_DIR,
      `mtr_railway_snapshot_${slug}.json`
    );
    await writeJson(responseTarget, response);

    const snapshots = Array.isArray(response?.payload?.snapshots)
      ? response.payload.snapshots
      : [];
    if (snapshots.length === 0) {
      console.warn("No snapshots returned from provider.");
    }
    for (const snapshot of snapshots) {
      const dimensionId = snapshot?.dimension || DIMENSION || "unknown";
      const ledger = snapshot?.payload;
      if (!ledger) {
        continue;
      }
      const data = Buffer.from(ledger, "base64");
      const msgpackTarget = path.join(
        OUTPUT_DIR,
        `mtr_railway_snapshot_${dimensionToSlug(dimensionId)}.msgpack`
      );
      await fs.promises.writeFile(msgpackTarget, data);
      try {
        const decoded = msgpack.decode(data);
        const normalized = normalizeForJson(decoded);
        const jsonTarget = path.join(
          OUTPUT_DIR,
          `mtr_railway_snapshot_${dimensionToSlug(dimensionId)}.json`
        );
        await writeJson(jsonTarget, normalized);
      } catch (decodeErr) {
        console.warn(
          `Failed to decode snapshot for ${dimensionId}: ${decodeErr.message}`
        );
      }
    }

    const dimensionSlug = dimensionToSlug(DIMENSION || "all");
    await writeRouteTrainsOutput(client, dimensionSlug);
    await writeStationScheduleOutput(client, dimensionSlug);
    await writeDepotTrainsOutput(client, dimensionSlug);
    await writeCreateNetworkOutput(client);
    await writeCreateRealtimeOutput(client);
    // await writeAllStationSchedulesOutput(client, dimensionSlug);
    console.log(`Done. Files written to ${OUTPUT_DIR}`);
  } catch (err) {
    console.error("Test run failed:", err.message);
    process.exitCode = 1;
  } finally {
    client.close();
  }
}

async function writeRouteTrainsOutput(client, dimensionSlug) {
  const payload = {
    routeId: ROUTE_ID,
  };
  if (DIMENSION) {
    payload.dimension = DIMENSION;
  }
  const response = await client.request("mtr:get_route_trains", payload);
  const slug = `${dimensionSlug}_route_${routeSuffix(ROUTE_ID)}`;
  const target = path.join(OUTPUT_DIR, `mtr_route_trains_${slug}.json`);
  await writeJson(target, response);
}

function routeSuffix(routeId) {
  return routeId > 0 ? String(routeId) : "all";
}

async function writeStationScheduleOutput(client, dimensionSlug) {
  if (STATION_ID == null) {
    console.log("Skipping station schedule (PROVIDER_MTR_STATION_ID not set)");
    return;
  }
  const payload = {
    stationId: STATION_ID,
  };
  if (DIMENSION) {
    payload.dimension = DIMENSION;
  }
  if (STATION_PLATFORM_ID != null) {
    payload.platformId = STATION_PLATFORM_ID;
  }
  const response = await client.request("mtr:get_station_schedule", payload);
  const slug = `${dimensionSlug}_station_${STATION_ID}`;
  const target = path.join(OUTPUT_DIR, `mtr_station_schedule_${slug}.json`);
  await writeJson(target, response);
}

async function writeDepotTrainsOutput(client, dimensionSlug) {
  const payload = {
    depotId: DEPOT_ID,
  };
  if (DIMENSION) {
    payload.dimension = DIMENSION;
  }
  const response = await client.request("mtr:get_depot_trains", payload);
  const suffix = DEPOT_ID > 0 ? `depot_${DEPOT_ID}` : "all";
  const target = path.join(
    OUTPUT_DIR,
    `mtr_depot_trains_${dimensionSlug}_${suffix}.json`
  );
  await writeJson(target, response);
}

async function writeAllStationSchedulesOutput(client, dimensionSlug) {
  const payload = {};
  if (DIMENSION) {
    payload.dimension = DIMENSION;
  }
  const response = await client.request(
    "mtr:get_all_station_schedules",
    payload
  );
  const target = path.join(
    OUTPUT_DIR,
    `mtr_all_station_schedules_${dimensionSlug}.json`
  );
  await writeJson(target, response);
}

async function writeCreateNetworkOutput(client) {
  const basePayload = {
    includePolylines: true,
  };
  const response = await client.request(CREATE_NETWORK_ACTION, basePayload);
  const target = path.join(OUTPUT_DIR, "create_network_all.json");
  await writeJson(target, response);

  const graphs = Array.isArray(response?.payload?.graphs)
    ? response.payload.graphs
    : [];
  for (const graph of graphs) {
    const graphId = graph?.graphId;
    if (!graphId) {
      continue;
    }
    const graphPayload = {
      graphId,
      includePolylines: true,
    };
    const graphResponse = await client.request(
      CREATE_NETWORK_ACTION,
      graphPayload
    );
    const graphTarget = path.join(
      OUTPUT_DIR,
      `create_network_${dimensionToSlug(graphId)}.json`
    );
    await writeJson(graphTarget, graphResponse);
  }
}

async function writeCreateRealtimeOutput(client) {
  const response = await client.request(CREATE_REALTIME_ACTION, {});
  const target = path.join(OUTPUT_DIR, "create_realtime.json");
  await writeJson(target, response);
}
export class GatewayClient {
  constructor({ host, port, token, timeoutMs }) {
    this.host = host;
    this.port = port;
    this.token = token;
    this.timeoutMs = timeoutMs;
    this.pending = new Map();
    this.buffer = Buffer.alloc(0);
    this.clientId = `beacon-tests-${process.pid}`;
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.socket = net.createConnection(
        { host: this.host, port: this.port },
        () => {
          this._sendEnvelope(
            "handshake",
            {
              protocolVersion: 1,
              clientId: this.clientId,
              token: this.token,
              capabilities: ["actions"],
            },
            false
          );
        }
      );

      this.socket.on("data", (chunk) => this._onData(chunk));
      this.socket.on("error", (err) => {
        if (!this.handshakeCompleted && !this.handshakeRejected) {
          reject(err);
          this.handshakeRejected = true;
        } else {
          console.error("Gateway socket error:", err.message);
        }
      });
      this.socket.on("close", () => {
        if (!this.handshakeCompleted && !this.handshakeRejected) {
          reject(new Error("Connection closed before handshake"));
          this.handshakeRejected = true;
        }
        for (const { reject } of this.pending.values()) {
          reject(new Error("Connection closed"));
        }
        this.pending.clear();
      });
      this.socket.setNoDelay(true);

      this.handshakeResolve = (info) => {
        this.handshakeCompleted = true;
        console.log(
          `Handshake OK (connectionId=${info.connectionId}, server=${info.serverName}, version=${info.modVersion})`
        );
        resolve();
      };
      this.handshakeReject = (err) => {
        if (!this.handshakeRejected) {
          this.handshakeRejected = true;
          reject(err);
        }
      };
    });
  }

  close() {
    if (this.socket) {
      this.socket.end();
      this.socket = undefined;
    }
  }

  request(action, payload = {}) {
    if (!this.connectionId) {
      throw new Error("Gateway handshake not completed");
    }
    const requestId = randomRequestId();
    const body = {
      protocolVersion: 1,
      requestId,
      action,
      payload,
    };
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error(`${action} (${requestId}) timed out`));
      }, this.timeoutMs);
      this.pending.set(requestId, {
        resolve: (data) => {
          clearTimeout(timer);
          resolve(data);
        },
        reject: (err) => {
          clearTimeout(timer);
          reject(err);
        },
      });
      this._sendEnvelope("request", body, true);
    });
  }

  _sendEnvelope(type, body, attachConnection = true) {
    const envelope = {
      type,
      timestamp: Date.now(),
      body: body || {},
    };
    if (attachConnection && this.connectionId) {
      envelope.connectionId = this.connectionId;
    }
    const json = JSON.stringify(envelope);
    const frame = Buffer.alloc(4 + Buffer.byteLength(json));
    frame.writeUInt32BE(Buffer.byteLength(json), 0);
    frame.write(json, 4, "utf8");
    this.socket.write(frame);
  }

  _onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 4) {
      const length = this.buffer.readUInt32BE(0);
      if (this.buffer.length < 4 + length) {
        break;
      }
      const frame = this.buffer.slice(4, 4 + length);
      this.buffer = this.buffer.slice(4 + length);
      this._handleFrame(frame);
    }
  }

  _handleFrame(frameBuffer) {
    let envelope;
    try {
      envelope = JSON.parse(frameBuffer.toString("utf8"));
    } catch (err) {
      console.error("Invalid frame JSON:", err.message);
      return;
    }
    const { type, body } = envelope;
    if (type === "handshake_ack") {
      this.connectionId = body?.connectionId;
      this.handshakeCompleted = true;
      this.handshakeResolve?.(body || {});
      return;
    }
    if (type === "error") {
      const message = body?.message || "Unknown gateway error";
      console.warn(`Gateway error: ${body?.errorCode || "ERROR"} - ${message}`);
      if (!this.connectionId) {
        this.handshakeReject?.(new Error(message));
      }
      return;
    }
    if (type === "response") {
      const requestId = body?.requestId;
      if (requestId && this.pending.has(requestId)) {
        const pending = this.pending.get(requestId);
        this.pending.delete(requestId);
        pending.resolve(body);
      } else {
        console.warn(`Received response for unknown requestId: ${requestId}`);
      }
      return;
    }
    if (type === "pong") {
      return;
    }
  }
}

async function prepareOutputDir(dir) {
  if (fs.existsSync(dir)) {
    await fs.promises.rm(dir, { recursive: true, force: true });
  }
  await fs.promises.mkdir(dir, { recursive: true });
}

async function writeJson(target, data) {
  const payload = {
    timestamp: new Date().toISOString(),
    data: data === undefined ? null : data,
  };
  await fs.promises.mkdir(path.dirname(target), { recursive: true });
  await fs.promises.writeFile(
    target,
    JSON.stringify(payload, null, 2) + "\n",
    "utf8"
  );
  console.log(`Wrote ${target}`);
}

function dimensionToSlug(value) {
  return String(value || "unknown").replace(/[^0-9A-Za-z_-]/g, "_");
}

function normalizeForJson(value) {
  if (typeof value === "bigint") {
    return value.toString();
  }
  if (typeof value === "number") {
    return Number.isSafeInteger(value) ? value : value.toString();
  }
  if (value instanceof Map) {
    return {
      __type: "Map",
      entries: Array.from(value.entries()).map(([key, val]) => [
        normalizeForJson(key),
        normalizeForJson(val),
      ]),
    };
  }
  if (Array.isArray(value)) {
    return compactArray(value.map(normalizeForJson));
  }
  if (value && typeof value === "object") {
    if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
      return Buffer.from(value).toString("base64");
    }
    const normalized = {};
    for (const [key, val] of Object.entries(value)) {
      if (key.startsWith("[object ")) {
        continue;
      }
      normalized[key] = normalizeForJson(val);
    }
    return normalized;
  }
  return value;
}

export function loadDotEnv(filePath) {
  if (!fs.existsSync(filePath)) {
    return;
  }
  const lines = fs.readFileSync(filePath, "utf8").split(/\r?\n/);
  for (const line of lines) {
    if (!line || line.startsWith("#")) {
      continue;
    }
    const idx = line.indexOf("=");
    if (idx === -1) {
      continue;
    }
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim();
    if (!key || process.env[key] !== undefined) {
      continue;
    }
    process.env[key] = value;
  }
}

function randomRequestId() {
  const alphabet = "0123456789abcdefghijklmnopqrstuvwxyz";
  const bytes = crypto.randomBytes(12);
  let result = "";
  for (let i = 0; i < 12; i += 1) {
    result += alphabet[bytes[i] % alphabet.length];
  }
  return result;
}

function parseEnvLong(value) {
  if (value == null) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});

function compactArray(items) {
  const result = [];
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i];
    if (
      typeof item === "string" &&
      result.length &&
      typeof result[result.length - 1] === "object" &&
      i + 1 < items.length
    ) {
      const next = items[i + 1];
      result[result.length - 1][item] = next;
      i += 1;
      continue;
    }
    result.push(item);
  }
  return result;
}
