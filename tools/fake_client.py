# -*- coding: utf-8 -*-
"""3인 관전 테스트용 가짜 참가자.

폰이 2대뿐이라 3인 상황을 만들 수 없어서, PC가 세 번째 사람으로 방에 들어간다.
앱과 같은 프로토콜(줄바꿈으로 구분되는 JSON)을 그대로 쓰고, 죽지 않고 계속 살아있는 척한다.
"""
import json
import socket
import sys
import threading
import time

HOST = sys.argv[1]
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 45124
NAME = sys.argv[3] if len(sys.argv) > 3 else u"PC참가자"
LIMIT_SEC = float(sys.argv[4]) if len(sys.argv) > 4 else 240.0

sock = socket.create_connection((HOST, PORT), 5)
# 연결 타임아웃이 읽기에도 그대로 걸린다. 풀어 주지 않으면 5초 뒤 수신이 끊긴다.
sock.settimeout(None)
stream = sock.makefile('rw', encoding='utf-8', newline='\n')
lock = threading.Lock()


def send(obj):
    with lock:
        stream.write(json.dumps(obj, ensure_ascii=False) + "\n")
        stream.flush()


started = threading.Event()
stop = threading.Event()


def reader():
    try:
        for line in stream:
            line = line.strip()
            if not line:
                continue
            try:
                o = json.loads(line)
            except ValueError:
                continue
            t = o.get("t")
            if t == "welcome":
                print("[pc] welcome id=%s" % o.get("id"), flush=True)
            elif t == "lobby":
                print("[pc] lobby: %s" % ", ".join(p.get("n", "?") for p in o.get("p", [])), flush=True)
            elif t == "start":
                print("[pc] START", flush=True)
                started.set()
            elif t == "gb":
                print("[pc] got %s garbage from %s" % (o.get("n"), o.get("f")), flush=True)
            elif t == "over":
                rows = ["%s. %s (%s)" % (x.get("r"), x.get("n"), x.get("sc")) for x in o.get("s", [])]
                print("[pc] OVER: %s" % " | ".join(rows), flush=True)
                stop.set()
                break
            elif t == "bye":
                print("[pc] BYE: %s" % o.get("r"), flush=True)
                stop.set()
                break
    except Exception as e:
        print("[pc] reader ended: %s" % e, flush=True)
    stop.set()


threading.Thread(target=reader, daemon=True).start()
send({"t": "join", "n": NAME})
print("[pc] joined %s:%d as %s" % (HOST, PORT, NAME), flush=True)

begin = time.time()
tick = 0
while not stop.is_set() and time.time() - begin < LIMIT_SEC:
    if started.is_set():
        # 바닥부터 조금씩 쌓이는 판을 흉내낸다. 관전 화면에서 움직임이 보여야 하므로.
        filled = min(tick // 25, 8)
        cells = ['0'] * 200
        for r in range(20 - filled, 20):
            for c in range(10):
                if (r + c + tick // 12) % 7 != 0:
                    cells[r * 10 + c] = '4'
        send({
            "t": "state",
            "b": "".join(cells),
            "sc": tick * 13,
            "ln": filled,
            "pg": 0,
            "a": True,
        })
        tick += 1
    time.sleep(0.1)

print("[pc] done", flush=True)
try:
    sock.close()
except Exception:
    pass
