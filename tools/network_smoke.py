#!/usr/bin/env python3
"""Launch two real game windows; verify LAN UI, avatars, building, saving and exit."""
from pathlib import Path
import socket
import subprocess
import time

root = Path(__file__).resolve().parents[1]
with socket.socket() as reservation:
    reservation.bind(('127.0.0.1', 0))
    port = reservation.getsockname()[1]
logs = [root / 'target' / f'network-{role}.log' for role in ('host', 'client')]
processes = []
handles = []
try:
    def launch(role, index):
        handle = logs[index].open('w')
        handles.append(handle)
        process = subprocess.Popen(
            ['java', f'-Dbarnocraft.smokePort={port}', '-jar', 'target/barnocraft.jar', f'--network-smoke-{role}'],
            cwd=root, stdout=handle, stderr=subprocess.STDOUT)
        processes.append(process)
        return process

    host = launch('host', 0)
    deadline = time.monotonic() + 120
    while 'NETWORK_SMOKE_HOST_READY' not in logs[0].read_text(errors='replace'):
        if host.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Host did not open its world; see target/network-host.log')
        time.sleep(.1)
    client = launch('client', 1)
    for process in (client, host):
        if process.wait(timeout=max(1, deadline - time.monotonic())) != 0:
            raise RuntimeError('Game process failed; see target/network-*.log')
    for path, marker in zip(logs, ('NETWORK_SMOKE_HOST_OK', 'NETWORK_SMOKE_CLIENT_OK')):
        if marker not in path.read_text(errors='replace'):
            raise RuntimeError(f'Missing {marker}; see {path}')
    print('NETWORK_SMOKE_OK: two game windows, both characters, shared edits and host save')
finally:
    for process in processes:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
    for handle in handles:
        handle.close()
