# Python ModAPI

Моды — обычные файлы `*.py` в папке `mods/` рядом с игрой. При открытии мира Barnicraft запускает их через `python3 -u`. Если Python или папка отсутствуют, игра продолжает работать без модов.

Обмен намеренно сделан простым: игра отправляет модулю строки событий в stdin, а модуль печатает команды в stdout. Каждая команда занимает одну строку и выполняется в основном потоке игры.

Событие тика:

```text
tick <x> <y> <z> <time>
```

Доступные команды от мода:

```text
give <item> [count]
setblock <x> <y> <z> <BLOCK>
spawn sheep [count]
kill sheep
keepinventory true|false
time set day|night
```

Пример `mods/welcome.py`:

```python
import sys

done = False
for event in sys.stdin:
    parts = event.split()
    if not parts or parts[0] != "tick":
        continue
    if not done:
        print("give torch 4", flush=True)
        print("setblock 40 20 40 TORCH", flush=True)
        done = True
```

Мод не получает прямой доступ к Java-объектам и файловой системе игры: он может только отправлять перечисленные команды. Это сохраняет простой API и не требует Jython или дополнительных зависимостей Maven.
