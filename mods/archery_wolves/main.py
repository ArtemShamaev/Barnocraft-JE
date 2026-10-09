"""All spawning, targeting and companion behavior is implemented in this mod."""
from barnocraft import Mod
import math
import random
import time

api = Mod()
last_spawn = {'skeleton': -100, 'wolf': -100, 'chicken': -100}
last_ai = -100
owner_target = None
provoked = {}
wander = {}
flee_until = {}
flee_goal = {}
latest_entities = {}


def distance(a, b):
    return math.hypot(a['x'] - b['x'], a['z'] - b['z'])


@api.on('damage')
def damaged(event):
    global owner_target
    source, target = event['source'], event['target']
    if target == 'player':
        owner_target = source
    elif source == 'player':
        owner_target = target
    provoked[target] = source
    if target in latest_entities and latest_entities[target]['type'].endswith(':chicken'):
        flee_until[target] = time.monotonic() + 4
        flee_goal.pop(target, None)


@api.on('entity_tamed')
def tamed(entity):
    provoked.pop(entity['id'], None)
    api.log('Companion tamed: ' + entity['id'])


def goal(entity, target, stop=1.2):
    api.set_goal(entity['id'], target['x'], target['z'], stop)


@api.on('tick')
def tick(data):
    global last_ai, owner_target, latest_entities
    now = time.monotonic()
    if now - last_ai < .2:
        return
    last_ai = now
    player = data['player']
    if player['health'] <= 0:
        return
    entities = {entity['id']: entity for entity in data['entities']}
    latest_entities = entities
    night = data['time'] < .22 or data['time'] >= .72
    for kind, interval, cap in [('wolf', 50, 2), ('skeleton', 60, 2),
                                ('chicken', 35, 4)]:
        count = sum(e['type'] == 'archery_wolves:' + kind and not e.get('owner')
                    for e in entities.values())
        allowed_time = (kind != 'skeleton' or night) and (kind != 'chicken' or not night)
        if count < cap and now-last_spawn[kind] > interval and allowed_time:
            last_spawn[kind] = now
            angle = random.random() * math.tau
            radius = random.uniform(9, 16) if kind == 'wolf' else random.uniform(18, 27)
            api.spawn_entity('archery_wolves:' + kind,
                             player['x'] + math.cos(angle)*radius,
                             player['z'] + math.sin(angle)*radius)
    target = entities.get(owner_target)
    if target is None or target.get('owner') == 'player' or distance(target, player) > 24:
        owner_target = None
        target = None
    for entity in list(entities.values()):
        kind = entity['type']
        if not kind.startswith('archery_wolves:'):
            continue
        if kind.endswith(':chicken'):
            if distance(entity, player) > 60:
                api.remove_entity(entity['id'])
                continue
            source_id = provoked.get(entity['id'])
            source = player if source_id == 'player' else entities.get(source_id)
            if now < flee_until.get(entity['id'], 0):
                target = flee_goal.get(entity['id'])
                if target is None or now-target[0] > 1.2 or distance(entity, target[1]) < .8:
                    if source is None:
                        angle = random.random() * math.tau
                        dx, dz = math.cos(angle), math.sin(angle)
                    else:
                        dx, dz = entity['x']-source['x'], entity['z']-source['z']
                        length = math.hypot(dx, dz) or 1
                        dx, dz = dx/length, dz/length
                    target = (now, {'x': max(2, min(958, entity['x']+dx*7)),
                                    'z': max(2, min(638, entity['z']+dz*7))})
                    flee_goal[entity['id']] = target
                goal(entity, target[1], .4)
                continue
            provoked.pop(entity['id'], None)
            flee_goal.pop(entity['id'], None)
            target = wander.get(entity['id'])
            if target is None or now-target[0] > 7 or distance(entity, target[1]) < .8:
                angle = random.random() * math.tau
                radius = random.uniform(2, 5)
                target = (now, {'x': max(2, min(958, entity['x']+math.cos(angle)*radius)),
                                'z': max(2, min(638, entity['z']+math.sin(angle)*radius))})
                wander[entity['id']] = target
            goal(entity, target[1], .5)
            continue
        if distance(entity, player) > 60 and not entity.get('owner'):
            api.remove_entity(entity['id'])
            continue
        if kind.endswith(':skeleton'):
            goal(entity, player, 8)
            if distance(entity, player) < 18:
                api.shoot_entity(entity['id'], 'player')
        elif kind.endswith(':wolf'):
            victim = None
            if entity.get('owner') == 'player':
                if distance(entity, player) > 28:
                    api.teleport_entity(entity['id'], player['x']+2, player['z']+2)
                    continue
                victim = target
                if not victim:
                    goal(entity, player, 2.2)
            else:
                attacker = provoked.get(entity['id'])
                victim = player if attacker == 'player' else entities.get(attacker)
                if victim is None and night and distance(entity, player) < 12:
                    victim = player
                if victim is None:
                    old = wander.get(entity['id'])
                    if old is None or now-old[0] > 8:
                        old = (now, {'x': max(1, entity['x']+random.uniform(-3, 3)),
                                     'z': max(1, entity['z']+random.uniform(-3, 3))})
                        wander[entity['id']] = old
                    goal(entity, old[1], .4)
            if victim and distance(entity, victim) < 24:
                goal(entity, victim, 1)
                if distance(entity, victim) < 1.7:
                    api.attack_entity(entity['id'], victim['id'])


if __name__ == '__main__':
    api.run()
