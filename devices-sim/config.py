import os
import sys
from pathlib import Path

BROKER_HOST = "localhost"
BROKER_PORT = 1884

BROKER_USERNAME = "device-default"


def _read_dotenv(key):
    env_file = Path(__file__).resolve().parent.parent / ".env"
    if not env_file.is_file():
        return None
    for line in env_file.read_text(encoding="utf-8").splitlines():
        name, sep, value = line.strip().partition("=")
        if sep and name.strip() == key:
            return value.strip() or None
    return None


BROKER_PASSWORD = os.environ.get("MQTT_DEVICE_PASSWORD") or _read_dotenv("MQTT_DEVICE_PASSWORD")
if not BROKER_PASSWORD:
    sys.exit("[config] Falta MQTT_DEVICE_PASSWORD: defínela en el .env de la raíz "
             "del repo o como variable de entorno.")

DEVICES = [
    {"type": "bulb", "id": "536d5980-29b5-44a9-867e-ba3de539a4a9"},
    {"type": "sensor", "id": "8678a64b-c36a-4166-9327-a906960c7e2b",
     "capability": "TEMPERATURE", "min": 24.0, "max": 37.0, "step": 0.4},
    {"type": "sensor", "id": "025db3a6-6c38-45da-8215-8e437c1b8b27",
     "capability": "HUMIDITY", "min": 20.0, "max": 60.0, "step": 1.0},
]

SENSOR_PUBLISH_INTERVAL_S = 30
