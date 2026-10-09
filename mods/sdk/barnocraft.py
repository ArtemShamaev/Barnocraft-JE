"""Barnocraft Mod API 2. Standard-library-only, asynchronous JSON RPC."""
import json
import sys
import time
import traceback

class Mod:
    def __init__(self):
        self.handlers = {}
        self.pending = {}
        self.sequence = 0
        self.state = {}

    def on(self, event):
        def decorate(fn):
            self.handlers.setdefault(event, []).append(fn)
            return fn
        return decorate

    def call(self, method, callback=None, on_error=None, **params):
        self.sequence += 1
        request_id = self.sequence
        self.pending[request_id] = (callback, time.monotonic(), method, on_error)
        print(json.dumps(dict(id=request_id, method=method, params=params),
                         ensure_ascii=False, separators=(',', ':')), flush=True)
        return request_id

    def set_state(self, callback=None, **values):
        self.state.update(values)
        return self.call('state.set', callback, **self.state)

    def spawn_entity(self, entity_type, x, z, callback=None, on_error=None):
        return self.call('entity.spawn', callback, on_error,
                         type=entity_type, x=x, z=z)

    def set_goal(self, entity_id, x, z, stop=1.0, callback=None):
        return self.call('entity.goal', callback, id=entity_id,
                         x=x, z=z, stop=stop)

    def attack_entity(self, entity_id, target_id, callback=None):
        return self.call('entity.attack', callback, id=entity_id,
                         target=target_id)

    def shoot_entity(self, entity_id, target_id, callback=None):
        return self.call('entity.shoot', callback, id=entity_id,
                         target=target_id)

    def teleport_entity(self, entity_id, x, z, callback=None):
        return self.call('entity.teleport', callback, id=entity_id,
                         x=x, z=z)

    def remove_entity(self, entity_id, callback=None):
        return self.call('entity.remove', callback, id=entity_id)

    def surface(self, callback, size=25, step=2, on_error=None):
        return self.call('world.surface', callback, on_error,
                         size=size, step=step)

    def show_image(self, image_id, title, pixels, callback=None, on_error=None):
        return self.call('ui.image', callback, on_error,
                         id=image_id, title=title, pixels=pixels)

    def remove_image(self, image_id, callback=None):
        return self.call('ui.remove', callback, id=image_id)

    def log(self, text):
        print(text, file=sys.stderr, flush=True)

    def run(self):
        for line in sys.stdin:
            try:
                message = json.loads(line)
                if 'event' in message:
                    if message['event'] == 'load':
                        self.state = message.get('data', {}).get('state', {})
                    for handler in self.handlers.get(message['event'], []):
                        try:
                            handler(message.get('data', {}))
                        except Exception:
                            traceback.print_exc(file=sys.stderr)
                elif 'id' in message:
                    pending = self.pending.pop(message['id'], None)
                    if 'error' in message:
                        if pending and pending[3]:
                            pending[3](message['error'])
                        else:
                            self.log(f"API error: {message['error']}")
                    elif pending and pending[0]:
                        pending[0](message.get('result'))
                now = time.monotonic()
                for key, (_, sent, method, _) in list(self.pending.items()):
                    if now - sent > 10:
                        self.log(f"Request timed out: {method}")
                        del self.pending[key]
            except Exception:
                traceback.print_exc(file=sys.stderr)
