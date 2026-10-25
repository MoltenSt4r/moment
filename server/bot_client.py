#!/usr/bin/env python3
import asyncio
import base64
import hashlib
import hmac
import json
import logging
import os
import sys
import time
import uuid

import websockets
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger("MomentTestPeer")

RELAY_URL = "ws://127.0.0.1:8765"
HKDF_INFO = b"Moment-ZeroKnowledge-E2EE-v1"

def hmac_sha256(key: bytes, data: bytes) -> bytes:
    return hmac.new(key, data, hashlib.sha256).digest()

def derive_root_key(salt: bytes, raw_shared_secret: bytes) -> bytes:
    prk = hmac_sha256(salt, raw_shared_secret)
    info = HKDF_INFO + b"\x01"
    return hmac_sha256(prk, info)

def derive_message_key(root_key: bytes, step: int) -> bytes:
    step_ctx = f"Moment-Msg-Key-Step-{step}".encode("utf-8")
    return hmac_sha256(root_key, step_ctx)

def compute_sas(local_pub_b64: str, peer_pub_b64: str, salt_b64: str):
    ordered = "|".join(sorted([local_pub_b64, peer_pub_b64]))
    digest = hashlib.sha256(f"{ordered}|{salt_b64}".encode("utf-8")).digest()
    glyphs = ["◈", "✦", "⬡", "◉", "▲", "❖", "⬢", "✧", "⟐", "❂", "⬟", "✹", "⟁", "✪", "⬠", "✺"]
    sas_glyphs = " ".join([glyphs[digest[i] % len(glyphs)] for i in range(4)])
    sas_hex = "·".join(f"{digest[i*2]:02X}{digest[i*2+1]:02X}" for i in range(4))
    return sas_glyphs, sas_hex

class BotPeer:
    def __init__(self):
        self.node_id = f"bot_{uuid.uuid4().hex[:12]}"
        self.priv_key = ec.generate_private_key(ec.SECP256R1())
        pub_der = self.priv_key.public_key().public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        self.pub_b64 = base64.b64encode(pub_der).decode()
        self.salt_bytes = os.urandom(32)
        self.salt_b64 = base64.b64encode(self.salt_bytes).decode()
        self.root_key: bytes = None
        self.room_id: str = None
        self.ratchet_counter = 0

    def establish_session(self, peer_pub_b64: str, salt_b64: str):
        peer_der = base64.b64decode(peer_pub_b64)
        peer_pub = serialization.load_der_public_key(peer_der)
        shared_secret = self.priv_key.exchange(ec.ECDH(), peer_pub)
        salt = base64.b64decode(salt_b64)
        self.root_key = derive_root_key(salt, shared_secret)
        sas_g, sas_h = compute_sas(self.pub_b64, peer_pub_b64, salt_b64)
        return sas_g, sas_h

    def encrypt_payload(self, kind: str, text: str = "", is_typing: bool = False) -> dict:
        self.ratchet_counter += 1
        step = self.ratchet_counter
        msg_key = derive_message_key(self.root_key, step)

        payload_obj = {
            "kind": kind,
            "messageId": f"msg_{uuid.uuid4().hex[:16]}",
            "text": text,
            "isTyping": is_typing,
            "sentAtMs": int(time.time()) * 1000,
            "pad": "X" * 64
        }
        json_bytes = json.dumps(payload_obj).encode("utf-8")

        # 512-byte quantum padding
        target_size = (((len(json_bytes) + 32) + 511) // 512) * 512
        pad_needed = max(16, target_size - len(json_bytes))
        payload_obj["pad"] = base64.b64encode(os.urandom(pad_needed)).decode()[:pad_needed]
        padded_bytes = json.dumps(payload_obj).encode("utf-8")

        iv = os.urandom(12)
        aesgcm = AESGCM(msg_key)
        aad = f"step:{step}".encode("utf-8")
        ciphertext = aesgcm.encrypt(iv, padded_bytes, aad)

        return {
            "roomId": self.room_id,
            "senderUid": self.node_id,
            "ratchetStep": step,
            "iv": base64.b64encode(iv).decode(),
            "ciphertext": base64.b64encode(ciphertext).decode(),
            "ttlSeconds": 0
        }

    def decrypt_envelope(self, env_data: dict) -> dict:
        step = int(env_data.get("ratchetStep", 0))
        iv = base64.b64decode(env_data.get("iv", ""))
        ciphertext = base64.b64decode(env_data.get("ciphertext", ""))
        msg_key = derive_message_key(self.root_key, step)

        aesgcm = AESGCM(msg_key)
        aad = f"step:{step}".encode("utf-8")
        plain_bytes = aesgcm.decrypt(iv, ciphertext, aad)
        return json.loads(plain_bytes.decode("utf-8"))

async def run_bot():
    logger.info("==================================================")
    logger.info("🤖 Moment Zero-Knowledge Test Peer Bot запущен!")
    logger.info("==================================================")

    while True:
        try:
            bot = BotPeer()
            async with websockets.connect(RELAY_URL) as ws:
                logger.info(f"Подключились к {RELAY_URL}. Встаем в очередь поиска...")
                await ws.send(json.dumps({
                    "action": "join_queue",
                    "nodeId": bot.node_id,
                    "pubKey": bot.pub_b64,
                    "salt": bot.salt_b64
                }))

                logger.info("⏳ Ожидаем подключения собеседника (нажми 'Старт' в приложении на телефоне)...")

                async for raw_msg in ws:
                    data = json.loads(raw_msg)
                    msg_type = data.get("type")

                    if msg_type == "queue_joined":
                        logger.info("✅ В очереди поиска. Ждем входящее подключение.")

                    elif msg_type == "matched":
                        bot.room_id = data["roomId"]
                        peer_id = data["peerNodeId"]
                        peer_pub = data["peerPubKey"]
                        salt = data["salt"]

                        sas_g, sas_h = bot.establish_session(peer_pub, salt)
                        logger.info("🎉 ==================================================")
                        logger.info(f"🎉 СОБЕСЕДНИК ПОДКЛЮЧИЛСЯ! Комната: {bot.room_id}")
                        logger.info(f"🔑 SAS-символы:  {sas_g}")
                        logger.info(f"🔢 SAS-код:      {sas_h}")
                        logger.info("   (Сверь эти символы/код в приложении — они должны совпадать!)")
                        logger.info("🎉 ==================================================")

                        # Send HANDSHAKE_SYN
                        syn_env = bot.encrypt_payload(kind="HANDSHAKE_SYN")
                        await ws.send(json.dumps({
                            "action": "send_envelope",
                            "roomId": bot.room_id,
                            "envelope": syn_env
                        }))

                    elif msg_type == "envelope":
                        env = data.get("envelope", {})
                        try:
                            decrypted = bot.decrypt_envelope(env)
                            kind = decrypted.get("kind")
                            text = decrypted.get("text", "")

                            if kind == "HANDSHAKE_SYN":
                                # Reply with ACK
                                ack_env = bot.encrypt_payload(kind="HANDSHAKE_ACK")
                                await ws.send(json.dumps({
                                    "action": "send_envelope",
                                    "roomId": bot.room_id,
                                    "envelope": ack_env
                                }))

                            elif kind == "MESSAGE" and text:
                                logger.info(f"📩 [ОТ ТЕБЯ]: {text}")

                                # Show typing indicator in the app
                                await ws.send(json.dumps({
                                    "action": "typing",
                                    "roomId": bot.room_id,
                                    "isTyping": True
                                }))
                                await asyncio.sleep(1.2)
                                await ws.send(json.dumps({
                                    "action": "typing",
                                    "roomId": bot.room_id,
                                    "isTyping": False
                                }))

                                # Send encrypted reply
                                reply_text = f"Привет! Zero-Knowledge E2EE работает отлично! 🚀 Твоё сообщение доставлено: «{text}»"
                                reply_env = bot.encrypt_payload(kind="MESSAGE", text=reply_text)
                                await ws.send(json.dumps({
                                    "action": "send_envelope",
                                    "roomId": bot.room_id,
                                    "envelope": reply_env
                                }))
                                logger.info(f"📤 [ОТВЕТ БОТА ОТПРАВЛЕН]: {reply_text}")

                        except Exception as e:
                            logger.error(f"Ошибка расшифровки: {e}")

                    elif msg_type == "typing":
                        if data.get("isTyping"):
                            logger.info("✍️ Собеседник печатает...")

                    elif msg_type == "peer_left":
                        logger.warning("👋 Собеседник вышел из комнаты. Переподключаемся в очередь...")
                        break

        except Exception as e:
            logger.error(f"Сбой соединения бота: {e}. Переподключение через 3 сек...")
            await asyncio.sleep(3)

if __name__ == "__main__":
    try:
        asyncio.run(run_bot())
    except KeyboardInterrupt:
        logger.info("Бот остановлен пользователем.")
