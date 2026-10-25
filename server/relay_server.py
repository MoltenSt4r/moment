#!/usr/bin/env python3
import asyncio
import json
import logging
import time
import uuid
from typing import Dict, List, Any
import websockets
from websockets.protocol import State

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger("MomentRelay")

# Security & Anti-DoS Configuration
MAX_MESSAGE_BYTES = 64 * 1024       # 64 KB per message max
MAX_WAITING_QUEUE = 2000            # Max users in matchmaking queue
MAX_CONNECTIONS_PER_IP = 100        # Prevent single IP socket-exhaustion flood
RATE_LIMIT_MESSAGES_PER_SEC = 25    # Max incoming messages per socket per second

# In-memory State
ip_connections: Dict[str, int] = {}
waiting_queue: List[dict] = []
active_rooms: Dict[str, dict] = {}
ws_to_room: Dict[Any, str] = {}
ws_rate_limit: Dict[Any, list] = {}

def get_remote_ip(ws) -> str:
    headers = getattr(ws, "request_headers", None) or getattr(ws, "request", None)
    if headers and hasattr(headers, "get"):
        forwarded = headers.get("X-Real-IP") or headers.get("X-Forwarded-For")
        if forwarded:
            return forwarded.split(",")[0].strip()
    remote = getattr(ws, "remote_address", None)
    if remote and len(remote) >= 1:
        return str(remote[0])
    return "unknown"

def check_rate_limit(ws) -> bool:
    now = time.time()
    history = ws_rate_limit.get(ws, [])
    history = [t for t in history if now - t < 1.0]
    if len(history) >= RATE_LIMIT_MESSAGES_PER_SEC:
        return False
    history.append(now)
    ws_rate_limit[ws] = history
    return True

async def send_safe(ws, msg_dict: dict):
    try:
        if ws.state == State.OPEN:
            await ws.send(json.dumps(msg_dict))
    except Exception as e:
        logger.debug(f"Failed to send: {e}")

async def handle_leave(ws):
    global waiting_queue
    waiting_queue = [item for item in waiting_queue if item["ws"] != ws]

    room_id = ws_to_room.pop(ws, None)
    if room_id and room_id in active_rooms:
        room = active_rooms.pop(room_id, None)
        if room:
            host_ws = room["host_ws"]
            guest_ws = room["guest_ws"]
            peer_ws = guest_ws if ws == host_ws else host_ws

            ws_to_room.pop(peer_ws, None)
            await send_safe(peer_ws, {"type": "peer_left", "roomId": room_id})

async def handle_join_queue(ws, data: dict):
    await handle_leave(ws)

    node_id = str(data.get("nodeId", ""))[:64]
    pub_key = str(data.get("pubKey", ""))[:512]
    salt = str(data.get("salt", ""))[:128]

    if not node_id or not pub_key:
        await send_safe(ws, {"type": "error", "message": "Invalid parameters"})
        return

    clean = [item for item in waiting_queue if item["ws"].state == State.OPEN and item["nodeId"] != node_id]
    waiting_queue.clear()
    waiting_queue.extend(clean)

    if len(waiting_queue) >= MAX_WAITING_QUEUE:
        await send_safe(ws, {"type": "error", "message": "Queue is currently full, try again later."})
        return

    if waiting_queue:
        host = waiting_queue.pop(0)
        room_id = f"room_{uuid.uuid4().hex[:16]}"

        active_rooms[room_id] = {
            "host_ws": host["ws"],
            "host_node": host["nodeId"],
            "guest_ws": ws,
            "guest_node": node_id
        }
        ws_to_room[host["ws"]] = room_id
        ws_to_room[ws] = room_id

        await send_safe(host["ws"], {
            "type": "matched",
            "role": "host",
            "roomId": room_id,
            "peerNodeId": node_id,
            "peerPubKey": pub_key,
            "salt": host["salt"]
        })

        await send_safe(ws, {
            "type": "matched",
            "role": "guest",
            "roomId": room_id,
            "peerNodeId": host["nodeId"],
            "peerPubKey": host["pubKey"],
            "salt": host["salt"]
        })
    else:
        waiting_queue.append({
            "ws": ws,
            "nodeId": node_id,
            "pubKey": pub_key,
            "salt": salt
        })
        await send_safe(ws, {"type": "queue_joined"})

async def handle_envelope(ws, data: dict):
    room_id = ws_to_room.get(ws) or data.get("roomId")
    if not room_id or room_id not in active_rooms:
        logger.warning(f"Dropping envelope: room {room_id} not found. Active: {list(active_rooms.keys())}")
        return

    room = active_rooms[room_id]
    if ws != room["host_ws"] and ws != room["guest_ws"]:
        return

    logger.info(f"Forwarding envelope in room {room_id}")
    target_ws = room["guest_ws"] if ws == room["host_ws"] else room["host_ws"]
    envelope = data.get("envelope")
    if not isinstance(envelope, dict):
        return

    safe_envelope = {
        "roomId": str(envelope.get("roomId", ""))[:64],
        "senderUid": str(envelope.get("senderUid", ""))[:64],
        "ratchetStep": int(envelope.get("ratchetStep", 0)),
        "iv": str(envelope.get("iv", ""))[:128],
        "ciphertext": str(envelope.get("ciphertext", ""))[:32768],
        "ttlSeconds": int(envelope.get("ttlSeconds", 0))
    }

    await send_safe(target_ws, {
        "type": "envelope",
        "roomId": room_id,
        "envelope": safe_envelope
    })

async def handle_typing(ws, data: dict):
    room_id = ws_to_room.get(ws) or data.get("roomId")
    if not room_id or room_id not in active_rooms:
        return
    room = active_rooms[room_id]
    if ws != room["host_ws"] and ws != room["guest_ws"]:
        return
    target_ws = room["guest_ws"] if ws == room["host_ws"] else room["host_ws"]

    await send_safe(target_ws, {
        "type": "typing",
        "roomId": room_id,
        "isTyping": bool(data.get("isTyping", False))
    })

async def handler(websocket):
    ip = get_remote_ip(websocket)
    current_conns = ip_connections.get(ip, 0)
    if current_conns >= MAX_CONNECTIONS_PER_IP:
        await send_safe(websocket, {"type": "error", "message": "Too many connections from this IP"})
        await websocket.close(code=1008, reason="Connection limit exceeded")
        return

    ip_connections[ip] = current_conns + 1

    try:
        async for raw_message in websocket:
            if len(raw_message) > MAX_MESSAGE_BYTES:
                await send_safe(websocket, {"type": "error", "message": "Payload too large"})
                continue

            if not check_rate_limit(websocket):
                await send_safe(websocket, {"type": "error", "message": "Rate limit exceeded"})
                await asyncio.sleep(0.1)
                continue

            try:
                data = json.loads(raw_message)
            except Exception:
                continue

            action = data.get("action")
            if action == "join_queue":
                await handle_join_queue(websocket, data)
            elif action == "cancel_queue":
                await handle_leave(websocket)
                await send_safe(websocket, {"type": "queue_cancelled"})
            elif action == "send_envelope":
                await handle_envelope(websocket, data)
            elif action == "typing":
                await handle_typing(websocket, data)
            elif action == "leave_room":
                await handle_leave(websocket)
            elif action == "ping":
                await send_safe(websocket, {"type": "pong"})
    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        ws_rate_limit.pop(websocket, None)
        await handle_leave(websocket)
        remaining = ip_connections.get(ip, 1) - 1
        if remaining <= 0:
            ip_connections.pop(ip, None)
        else:
            ip_connections[ip] = remaining

async def main():
    logger.info("Moment Zero-Knowledge Relay running on 0.0.0.0:8765 (Security Hardened)")
    async with websockets.serve(
        handler,
        "0.0.0.0",
        8765,
        ping_interval=25,
        ping_timeout=20,
        max_size=MAX_MESSAGE_BYTES
    ):
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
