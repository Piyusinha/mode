#!/usr/bin/env python3
"""Phase 1 spike: open Uber by name, start a trip to a destination, read the
suggestions back. Stops there: nothing is tapped that would request a ride.

    MODE_ENGINE_URL=... MODE_ENGINE_TOKEN=... tools/spike_uber.py "India Gate"
"""
import sys
import time

from engine import connect


def wait_for(e, predicate, timeout=10.0, interval=0.3):
    """Poll screen.text until predicate(boxes) is truthy; the boxes, or None."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        boxes = e.text()
        if predicate(boxes):
            return boxes
        time.sleep(interval)
    return None


def main():
    destination = sys.argv[1] if len(sys.argv) > 1 else "India Gate"
    e = connect()
    start = time.time()

    def step(label):
        print(f"[{time.time() - start:5.1f}s] {label}")

    e.op("nav.home")
    step("home")
    app = e.op("apps.launch", name="Uber")
    step(f"launched {app} through Spotlight")

    boxes = wait_for(e, lambda b: any(x["text"] == "Where to?" for x in b), timeout=15)
    if boxes is None:
        sys.exit("Uber did not show 'Where to?'; something else is on screen:\n"
                 + "\n".join(x["text"] for x in e.text()[:30]))
    step("'Where to?' is on screen")
    e.tap_text("Where to?", exact=True)

    def destination_field(b):
        return [x for x in b if x["type"] in ("searchField", "textField") and x["text"].startswith("Destination")]

    boxes = wait_for(e, lambda b: destination_field(b), timeout=8)
    if boxes is None:
        sys.exit("the destination field did not appear")
    step("destination field is focused")
    e.op("input.text", s=destination)
    step(f"typed {destination!r}")

    want = destination.lower()
    boxes = wait_for(e, lambda b: any(x["type"] == "button" and x["text"].lower().startswith(want) for x in b), timeout=8)
    if boxes is None:
        sys.exit("no suggestion matched")
    step("suggestions:")
    for x in boxes:
        if x["type"] == "button" and want.split()[0] in x["text"].lower():
            print(f"         - {x['text']}  @ ({x['x']:.0f},{x['y']:.0f})")


if __name__ == "__main__":
    main()
