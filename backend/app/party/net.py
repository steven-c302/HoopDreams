import os
import socket


def lan_ip() -> str:
    """The Mac's address on the local network (a UDP connect sends no packets)."""
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("10.254.254.254", 1))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def join_url() -> str:
    port = os.environ.get("HOOP_PUBLIC_PORT", "8000")
    return f"http://{lan_ip()}:{port}/play"
