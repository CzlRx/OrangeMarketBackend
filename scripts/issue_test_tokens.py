"""Generate long-lived test JWTs and Redis sessions from sql/01_init.sql users."""
from __future__ import annotations

import json
import os
import time
from pathlib import Path

import jwt
import redis

SECRET = "orange-market-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm"
TTL_SECONDS = 365 * 24 * 3600
REDIS_HOST = os.environ.get("REDIS_HOST", "172.29.230.161")
REDIS_PORT = int(os.environ.get("REDIS_PORT") or 6379)
REDIS_PASSWORD = os.environ.get("REDIS_PASSWORD") or None

USERS = [
    {
        "label": "USER",
        "sessionId": "aaaaaaaa-1111-4111-8111-111111111111",
        "id": 10001,
        "phone": "13800138001",
        "nickname": "橙子同学",
        "avatarUrl": "https://i.pravatar.cc/160?img=12",
        "gender": 0,
        "birthday": "1998-08-18",
        "status": "active",
        "role": "USER",
    },
    {
        "label": "ADMIN",
        "sessionId": "bbbbbbbb-2222-4222-8222-222222222222",
        "id": 10004,
        "phone": "19091432641",
        "nickname": "橙子用户2641",
        "avatarUrl": None,
        "gender": 0,
        "birthday": None,
        "status": "active",
        "role": "ADMIN",
    },
]


def json_hash_value(value) -> str:
    return json.dumps(str(value), ensure_ascii=False)


def main() -> None:
    now = int(time.time())
    client = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        password=REDIS_PASSWORD,
        decode_responses=True,
        socket_timeout=5,
    )
    client.ping()

    lines = [
        f"Redis: {REDIS_HOST}:{REDIS_PORT}",
        f"TTL: {TTL_SECONDS} seconds (~365 days)",
        "",
    ]

    for user in USERS:
        token = jwt.encode(
            {
                "sub": user["sessionId"],
                "userId": user["id"],
                "phone": user["phone"],
                "iat": now,
                "exp": now + TTL_SECONDS,
            },
            SECRET,
            algorithm="HS256",
        )
        redis_key = f"auth:login:{user['id']}{user['sessionId']}"
        mapping = {
            "id": json_hash_value(user["id"]),
            "phone": json_hash_value(user["phone"]),
            "nickname": json_hash_value(user["nickname"]),
            "gender": json_hash_value(user["gender"]),
            "status": json_hash_value(user["status"]),
            "role": json_hash_value(user["role"]),
        }
        if user["avatarUrl"]:
            mapping["avatarUrl"] = json_hash_value(user["avatarUrl"])
        if user["birthday"]:
            mapping["birthday"] = json_hash_value(user["birthday"])

        client.delete(redis_key)
        client.hset(redis_key, mapping=mapping)
        client.expire(redis_key, TTL_SECONDS)

        lines.extend(
            [
                f"=== {user['label']} ===",
                f"userId: {user['id']}",
                f"phone: {user['phone']}",
                f"role: {user['role']}",
                f"token: {token}",
                f"ws: ws://localhost:8080/ws/service?token={token}",
                f"Authorization: Bearer {token}",
                "",
            ]
        )

    out = Path(__file__).resolve().parents[1] / ".local-test-tokens.txt"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(out.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
