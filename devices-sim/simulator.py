import json
import random
import signal
import threading
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

import config


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def build_client(device_id: str) -> mqtt.Client:
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"device-{device_id}",
    )
    client.username_pw_set(config.BROKER_USERNAME, config.BROKER_PASSWORD)
    return client


def publish_telemetry(client: mqtt.Client, device_id: str, capability: str, value) -> None:
    payload = {
        "capability": capability,
        "value": value,
        "timestamp": now_iso(),
    }
    client.publish(f"devices/{device_id}/telemetry", json.dumps(payload), qos=1)
    print(f"  -> [{capability}] {value}  (device {device_id[:8]}…)")


class Sensor:
    def __init__(self, name, device_id, capability, lo, hi, step):
        self.name = name
        self.device_id = device_id
        self.capability = capability
        self.lo = lo
        self.hi = hi
        self.step = step
        self.value = round(random.uniform(lo, hi), 1)
        self.client = build_client(device_id)
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name=name, daemon=True)

    def _next_value(self) -> float:
        delta = random.uniform(-self.step, self.step)
        self.value = max(self.lo, min(self.hi, self.value + delta))
        return round(self.value, 1)

    def _run(self):
        while not self._stop.is_set():
            publish_telemetry(self.client, self.device_id, self.capability, self._next_value())
            self._stop.wait(config.SENSOR_PUBLISH_INTERVAL_S)

    def start(self):
        self.client.connect(config.BROKER_HOST, config.BROKER_PORT)
        self.client.loop_start()
        self._thread.start()
        print(f"[{self.name}] sensor arrancado (publica {self.capability} cada "
              f"{config.SENSOR_PUBLISH_INTERVAL_S}s)")

    def stop(self):
        self._stop.set()
        self.client.loop_stop()
        self.client.disconnect()


class Bulb:
    POWER = "POWER_SWITCH"
    BRIGHTNESS = "BRIGHTNESS"

    def __init__(self, device_id):
        self.name = "Bombilla"
        self.device_id = device_id
        self.state = {self.POWER: False, self.BRIGHTNESS: 0}
        self.client = build_client(device_id)
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code != 0:
            print(f"[{self.name}] fallo al conectar: {reason_code}")
            return
        client.subscribe(f"devices/{self.device_id}/commands", qos=1)
        print(f"[{self.name}] actuador conectado y suscrito a comandos "
              f"(device {self.device_id[:8]}…)")

    def _on_message(self, client, userdata, msg):
        try:
            cmd = json.loads(msg.payload)
        except json.JSONDecodeError:
            print(f"[{self.name}] comando ilegible, descartado: {msg.payload!r}")
            return

        capability = cmd.get("capability")
        value = cmd.get("value")

        if capability not in (self.POWER, self.BRIGHTNESS):
            print(f"[{self.name}] capability no soportada en comando: {capability}")
            return

        self.state[capability] = value
        print(f"[{self.name}] comando recibido: {capability}={value} "
              f"-> estado {self.state}")

        publish_telemetry(self.client, self.device_id, capability, value)

    def start(self):
        self.client.connect(config.BROKER_HOST, config.BROKER_PORT)
        self.client.loop_start()

    def stop(self):
        self.client.loop_stop()
        self.client.disconnect()


def build_device(spec):
    kind = spec["type"]
    if kind == "bulb":
        return Bulb(spec["id"])
    if kind == "sensor":
        return Sensor(f"Sensor-{spec['capability']}", spec["id"], spec["capability"],
                      spec["min"], spec["max"], spec["step"])
    raise ValueError(f"tipo de device desconocido en config.DEVICES: {kind!r}")


def main():
    devices = [build_device(spec) for spec in config.DEVICES]

    print(f"Conectando al broker LAN {config.BROKER_HOST}:{config.BROKER_PORT} …")
    for d in devices:
        d.start()

    stop_event = threading.Event()

    def shutdown(signum, frame):
        print("\nParando simulador…")
        stop_event.set()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    print("Simulador en marcha. Ctrl+C para parar.\n")
    stop_event.wait()

    for d in devices:
        d.stop()
    print("Simulador detenido.")


if __name__ == "__main__":
    main()
