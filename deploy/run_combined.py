"""Run the backend and its local condition interpreter as one service."""

import os
import signal
import subprocess
import sys
import time


def main():
    children = []
    stopping = False

    def stop(_signum, _frame):
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    environment = os.environ.copy()
    if environment.get("PORT") and not environment.get("SERVER_PORT"):
        environment["SERVER_PORT"] = environment["PORT"]

    try:
        children.append(subprocess.Popen([
            sys.executable, "-m", "uvicorn", "app:app",
            "--host", "127.0.0.1", "--port", "8001",
        ], cwd="/app/constraints", env=environment))
        children.append(subprocess.Popen(["java", "-jar", "/app/app.jar"], env=environment))
        while not stopping:
            for child in children:
                status = child.poll()
                if status is not None:
                    print(f"Child process {child.pid} exited with status {status}", flush=True)
                    return status if status > 0 else 1
            time.sleep(0.5)
        return 0
    finally:
        for child in children:
            if child.poll() is None:
                child.terminate()
        deadline = time.monotonic() + 10
        for child in children:
            try:
                child.wait(timeout=max(0, deadline - time.monotonic()))
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()


if __name__ == "__main__":
    sys.exit(main())
