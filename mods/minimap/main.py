"""North-up minimap: terrain colors, player heading and nearby entity markers."""
from barnocraft import Mod
import math
import time

api = Mod()
last_query = -100
snapshot = {}
last_surface = None
markers = []
palette = {'GRASS': '55984f', 'LEAVES': '37753f', 'LOG': '69502f',
           'WATER': '3f83c0', 'SAND': 'd4c28a', 'STONE': '828b90',
           'DEEPSTONE': '505767', 'PLANKS': 'a68450', 'AIR': '243441'}


def draw(surface):
    global last_surface
    last_surface = surface
    pixels = [[palette.get(block, 'bdad92') for block in row]
              for row in reversed(surface['blocks'])]
    size = surface['size']
    player = snapshot.get('player', {})
    def cell(entity):
        return (round((entity['x'] - surface['x']) / surface['step']),
            size - 1 - round((entity['z'] - surface['z']) / surface['step']))
    for entity in snapshot.get('entities', []):
        x, z = cell(entity)
        if 0 <= x < size and 0 <= z < size:
            pixels[z][x] = ('70e6dd' if entity.get('owner') == 'player' else
                            'dce8be' if entity['type'] == 'core:sheep' else 'ed765a')
    for marker in markers:
        x, z = cell(marker)
        if 0 <= x < size and 0 <= z < size:
            pixels[z][x] = 'ffd166'
            if x > 0:
                pixels[z][x-1] = 'a96831'
            if x+1 < size:
                pixels[z][x+1] = 'a96831'
            if z > 0:
                pixels[z-1][x] = 'a96831'
            if z+1 < size:
                pixels[z+1][x] = 'a96831'
    if player:
        x, z = cell(player)
        if 0 <= x < size and 0 <= z < size:
            pixels[z][x] = 'ffffff'
            yaw = player.get('yaw', 0)
            ax, az = x-round(math.sin(yaw)), z-round(math.cos(yaw))
            if 0 <= ax < size and 0 <= az < size:
                pixels[az][ax] = 'fff18c'
    api.show_image('minimap:map', 'N ↑ Мини-карта · ЛКМ метка / ПКМ снять', pixels)


@api.on('load')
def load(_data):
    global markers
    markers = api.state.get('markers', [])


@api.on('ui.click')
def click(event):
    if event.get('id') != 'minimap:map' or last_surface is None:
        return
    step = last_surface['step']
    world_x = last_surface['x'] + event['x'] * step
    world_z = last_surface['z'] + (last_surface['size'] - 1 - event['y']) * step
    if event.get('button') == 'left':
        if len(markers) >= 20:
            return
        markers.append({'x': world_x, 'z': world_z})
    elif event.get('button') == 'right' and markers:
        nearest = min(markers, key=lambda point:
                      (point['x']-world_x)**2 + (point['z']-world_z)**2)
        if ((nearest['x']-world_x)**2 + (nearest['z']-world_z)**2
                <= (step * 2)**2):
            markers.remove(nearest)
        else:
            return
    else:
        return
    api.set_state(markers=markers)


@api.on('tick')
def tick(data):
    global snapshot, last_query
    snapshot = data
    now = time.monotonic()
    if now - last_query >= 1:
        last_query = now
        api.surface(draw, size=25, step=2)


if __name__ == '__main__':
    api.run()
