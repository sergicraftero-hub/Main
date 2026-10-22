# Crear y modificar salas

Cada sala está hecha de dos partes:

- **un mapa** (`plugins/EscapeRooms/templates/<mapa>.ert`): los bloques y los **marcadores** (puntos con nombre, como `@lab.cauldron`);
- **un fichero YAML** (`plugins/EscapeRooms/rooms/<sala>.yml`): la historia, los objetos, los puzles y lo que pasa al resolverlos.

El YAML nunca usa coordenadas fijas, solo marcadores. Así se puede mover o redecorar el mapa sin tocar el YAML.

Después de editar, ejecuta `/eradmin reload` y luego `/eradmin validate <sala>`. Este comando avisa de marcadores que no existen, objetos sin definir, puzles que dependen de puzles inexistentes y bloques que no son del tipo esperado.

> Las salas incluidas (tutorial, alquimista y expreso) se vuelven a copiar si borras su `.yml`. Para quitar una sala, pon `enabled: false`.

## 1. Estructura de una sala

```yaml
name: "<gold><b>La Torre del Alquimista</b>"
wing: originales            # cine | anime | videojuegos | originales (config.yml → wings)
difficulty: media           # facil | media | dificil | experta | tutorial
order: 1                    # orden dentro del ala
template: alquimista        # mapa: templates/alquimista.ert
description: ["<gray>Línea 1", "<gray>Línea 2"]
icon: {material: BREWING_STAND, model: "escape:room_alchemist"}
players: {min: 1, max: 10, recommended: 4}
time-limit: 60              # minutos (también vale "90m" o "1h")
estimated-minutes: 50
access: {level: 1, rooms: [], premium: false, price-gems: 0, price-keys: 0}
atmosphere: {time: 18000, rain: false}   # hora del día (18000 = medianoche)
spawn: "@spawn"
void-y: 0                   # quien caiga por debajo vuelve al último punto de control
stars:
  two:   {time: 45, hints: 4}    # ≤45 min y ≤4 pistas → 2 estrellas
  three: {time: 35, hints: 0}
hints: {max: 12, penalty: 60s}   # cada pista suma 60 s al tiempo
rewards: {xp: 600, keys: 120, xp-per-star: 100, first-time: {xp: 800, keys: 250}}
auto-win: true              # se gana al resolver todos los puzles no opcionales
win-delay: 4s

intro: {interval: 70, lines: ["<gold>Texto de la intro...", "..."]}
items:    {...}   # objetos de la sala
zones:    [...]   # zonas con nombre y bloqueo
props:    [...]   # NPCs, hologramas, cofres, búsquedas...
recipes:  [...]   # combinaciones de objetos
ambience: [...]   # sonidos ambientales
timeline: [...]   # eventos por tiempo
puzzles:  {...}   # los puzles
on-win: [...]     # acciones al ganar
```

Los tiempos se escriben como `40` (ticks), `"3s"`, `"5m"` o `"1h"`. En `time-limit`, un número solo cuenta como minutos.

## 2. Marcadores

| Escribes | Significa |
|---|---|
| `"@lab.cauldron"` | un marcador (un punto, o todos los bloques si es una caja) |
| `"@lab.cauldron+0,1,0"` | el marcador desplazado |
| `"@lamp.*"` | todos los marcadores que empiezan por `lamp.`, en orden natural (lamp.2 antes que lamp.10) |
| `"12,3,40"` | coordenadas del mapa (relativas a su esquina) |
| `"1,2,3:5,6,7"` | una caja |

## 3. Objetos (`items`)

```yaml
items:
  llave_biblioteca:
    material: TRIPWIRE_HOOK
    model: "escape:key_iron"       # icono del pack de texturas (sin pack se ve el material)
    name: "<white>Llave de hierro"
    lore: ["<gray>Abre la biblioteca."]
    glow: true
    tags: [uv]                      # "uv": funciona como linterna ultravioleta
  diario:
    material: WRITTEN_BOOK
    title: "Diario"
    author: "El maestro"
    pages: ["<b>Página 1</b>\n...", "Página 2"]
```

Otras claves: `color` (cuero o pociones, `"#AA0000"`), `texture` (cabezas), `amount` y `hide-tooltip`.

Los objetos de sala no se pueden tirar ni sacar de la partida, y se borran al terminar.

## 4. Puzles

Claves comunes a todos:

```yaml
puzzles:
  p_ejemplo:
    type: keypad
    name: "La caja fuerte"                 # sale al resolverlo y en el cuaderno
    objective: "Abre la caja fuerte"       # objetivo visible mientras está activo
    requires: [p_anterior]                 # se activa cuando estos están resueltos
    hints: ["Pista 1 (suave)", "Pista 2", "Pista 3 (casi la solución)"]
    xp: 30
    hidden: false            # true: no aparece como objetivo (secretos)
    optional: false          # true: no hace falta para ganar
    penalty: 30s             # tiempo extra por fallo (si no, el de la sala)
    locked-message: "..."    # al tocarlo antes de tiempo ("" = nada)
    lock-when-solved: true   # una vez resuelto ya no reacciona
    on-activate: [...]       # acciones (ver sección 7)
    on-solve:    [...]
    on-fail:     [...]
```

**Los 17 tipos:**

| Tipo | Para qué | Claves |
|---|---|---|
| `interact` | clic en un bloque (con objeto o sin él) | `at`, `item`, `consume`, `all`, `wrong-item-message` |
| `keypad` | código en un menú: caja fuerte, candado, ordenador | `at`, `code`, `style: digits\|letters\|dial\|symbols`, `symbols`, `title` |
| `levers` | palancas en una posición | `levers`, `pattern: "10110"`, `lamps` |
| `sequence` | pulsar en orden (botones, estatuas, campanas) | `buttons`, `order: [3,1,4,2]` |
| `simon` | «Simón dice» con luces | `buttons`, `lamps`, `start`, `rounds`, `seed` |
| `plates` | placas de presión a la vez (con pesas para jugar solo) | `plates`, `weight-item`, `hold` |
| `frames` | objetos en marcos, en su sitio y girados | `frames: [{at, item, rotation, start-item, start-rotation, locked-item}]` |
| `deliver` | echar objetos en algo (caldero, altar) | `at`, `items`, `ordered` |
| `answer` | acertijo respondido en el chat | `at`, `question`, `answers`, `contains` |
| `reach` | llegar a un sitio | `region`, `all` |
| `obtain` | tener ciertos objetos | `items`, `count` |
| `gate` | se resuelve solo cuando se cumplen sus `requires` | (une ramas y lanza acciones) |
| `lightsout` | «apaga las luces» con lámparas | `grid` (caja), `scramble`, `reset` |
| `melody` | tocar una melodía | `keys`, `notes`, `melody`, `listen`, `sound` |
| `choice` | elegir en un menú (acusar al culpable, cortar un cable) | `npc` o `at`, `title`, `intro`, `options: [{material, name, lore, correct, reply}]` |
| `memory` | parejas de cartas en un menú | `at`, `pairs`, `icons`, `max-mistakes` |
| `slide` | puzle deslizante 3×3 | `at`, `tiles`, `moves` |

Ejemplos:

```yaml
  p_retratos:
    type: keypad
    style: dial
    at: "@hall.cabinet"
    code: "5319"
    title: "<dark_gray>Vitrina · combinación"
    on-solve:
      - {type: give, item: llave_biblioteca}

  p_tria_prima:
    type: deliver
    at: "@lab.cauldron"
    items: [mercurio, azufre, sal]
    ordered: true             # orden equivocado: el caldero lo devuelve todo

  e_acusar:
    type: choice
    npc: inspector
    title: "¿Quién es el asesino?"
    options:
      - {material: PLAYER_HEAD, name: "La condesa", correct: false, reply: "Tenía coartada..."}
      - {material: PLAYER_HEAD, name: "La doctora", correct: true}
```

**Para que funcione de 1 a 10 jugadores:**
- usa `weight-item` en las placas;
- no exijas `all: true` en `reach` salvo en el final;
- pon varias ramas en paralelo (puzles que solo requieren el mismo anterior) para que los grupos grandes se repartan.

## 5. Props (`props`)

Todos llevan `id`, `type` y `at`. Además pueden llevar:
- `requires: [puzle]`: aparece al resolverlo;
- `until: puzle`: desaparece al resolverlo;
- `manual: true`: solo con las acciones `show`/`hide`.

| Tipo | Qué es | Claves |
|---|---|---|
| `text` | holograma | `text`, `scale`, `billboard`, `offset`, `background`, `shadow` |
| `uv-text` | texto que solo se ve con la linterna UV en la mano | `text`, `radius`, `scale` |
| `npc` | aldeano que habla | `name`, `profession`, `talk: [...]`, `after-solved: {puzzle, talk}`, `voice`, `baby` |
| `frame` | marco decorativo con objeto | `item`, `facing`, `rotation`, `invisible` |
| `item` | objeto flotante | `item`, `scale`, `billboard`, `spin` |
| `container` | rellena un cofre o barril | `items: ["llave", "pesa*2"]`, `slots`, `clear` |
| `search` | buscar en un bloque (cajón, saco, macetas) | `item` o `items`, `message`, `requires-item`, `consume-item`, `need-message`, `empty-message`, `per-player`, `actions` |
| `workbench` | mesa donde se combinan objetos | `recipes: [id]`, `title` |
| `clue` | mensaje al inspeccionar un bloque | `text: [...]`, `sound` |
| `book` | libro en un atril | `item` |
| `sign` | texto de un cartel | `lines: [...]` |
| `teleporter` | pasadizo o ascensor | `at` o `region`, `to`, `whole-team`, `message` |
| `hazard` | zona que te devuelve atrás (lava, abismo, láser) | `region`, `to` (si no, al último punto de control), `message` |
| `particles` | partículas continuas | `particle`, `count`, `interval`, `height`, `spread` |
| `block` | cambia un bloque al aparecer | `block` |

```yaml
props:
  - {id: cofre1, type: container, at: "@r1.chest", items: [llave_tutorial]}
  - {id: barril_uv, type: search, at: "@r4.barrel", item: linterna_uv,
     message: "<light_purple><player> encuentra una linterna ultravioleta."}
  - {id: pista_uv, type: uv-text, at: "@r4.uv", text: "<light_purple>Tres letras.", radius: 6}
  - id: jardinera
    type: npc
    at: "@gh.npc"
    name: "<green>Marta, la jardinera"
    profession: farmer
    talk: ["Frase 1", "Frase 2"]
    after-solved: {puzzle: p_puerta_torre, talk: ["¡Has abierto la torre!"]}
```

## 6. Recetas, zonas, ambiente y línea temporal

```yaml
recipes:      # se usan en un prop workbench
  - {id: garfio, inputs: [cuerda, gancho], output: garfio, message: "<gold><player> fabrica un garfio."}

zones:        # título al entrar, y bloqueo hasta resolver un puzle (no se puede entrar antes)
  - {id: cripta, region: "@zone.crypt", name: "<dark_gray>La cripta", subtitle: "<gray>...", locked-until: p_reloj}

ambience:
  - {sound: ambient.cave, zone: "@zone.crypt", interval: 25s, volume: 0.7}
  - {sound: block.brewing_stand.brew, at: "@lab.cauldron", interval: 8s}

timeline:     # por tiempo jugado (at) o por tiempo restante (remaining)
  - at: 20m
    actions: [{type: message, text: "<gray><i>La torre cruje...</i>"}]
  - remaining: 5m
    unless-solved: p_altar
    actions: [{type: title, subtitle: "<red>¡Se acaba el tiempo!"}]
```

## 7. Acciones

Se usan en `on-solve`, `on-fail`, `on-activate`, `on-win`, `timeline` y en los props `search`. Todas aceptan `delay: 2s`.

| Acción | Claves |
|---|---|
| `message`, `actionbar` | `text` |
| `title` | `title`, `subtitle`, `stay`, `fade-in`, `fade-out` |
| `narrative` | `lines`, `interval`, `style: chat\|title\|actionbar` (frases una tras otra) |
| `sound` | `sound`, `volume`, `pitch`, `at` |
| `particle` | `particle`, `at`, `count`, `offset`, `speed`, `color`, `size` |
| `firework`, `lightning` | `at`, `colors` (el rayo es solo visual, no hace daño) |
| `give`, `take` | `item`, `amount`, `to: trigger\|all` |
| `open`, `close` | `at` (puertas, trampillas, vallas), `quiet` |
| `blocks`, `clear` | `at`, `block`, `interval` (poco a poco), `effect` |
| `light`, `unlight`, `power`, `unpower` | `at` |
| `teleport` | `to`, `who: trigger\|all` |
| `checkpoint` | `at` (donde reaparece quien caiga) |
| `show`, `hide` | `prop` |
| `solve` | `puzzle` |
| `effect` | `effect`, `duration`, `amplifier`, `who` |
| `time` | `seconds`: devuelve tiempo al reloj (negativo = lo quita) |
| `player-time` | `time` (hora del cielo solo para el equipo) |
| `objective` | `text` |
| `command` | `command` (desde la consola; `%player%` = quien lo activó), `for: all` para cada jugador |
| `win`, `lose` | `reason` (clave de messages.yml) |

## 8. Editor de mapas en el juego

Se necesita el permiso `escaperooms.build` y estar en creativo.

1. `/eradmin editor alquimista`: te lleva a una copia privada del mapa, con los marcadores visibles como hologramas.
2. Construye o redecora con normalidad.
3. Marcadores:
   - `/eradmin marker set lab.cauldron`: crea un marcador donde estás. Añade `aqui` para guardar también hacia dónde miras.
   - `/eradmin marker target lab.cauldron`: crea el marcador en el bloque que estás mirando.
   - `/eradmin marker box zone.crypt`: crea una caja con la selección `pos1`/`pos2`.
   - `/eradmin marker del <nombre>` y `/eradmin marker list`.
4. `/eradmin editor save` guarda en `templates/alquimista.ert`. Las partidas nuevas ya usan el mapa nuevo.
5. `/eradmin editor exit` sale del editor.

**Mapa nuevo construido a mano:**
1. Constrúyelo en cualquier mundo.
2. Marca dos esquinas con `/eradmin pos1` y `/eradmin pos2` (te colocas en cada esquina).
3. Guárdalo con `/eradmin capture <id>`.
4. Ábrelo con `/eradmin editor <id>` para poner los marcadores.
5. Crea `rooms/<sala>.yml` con `template: <id>`.

> El mapa debe medir menos que `instances.spacing - 32` (480 bloques por defecto) en X y Z.

`/eradmin build <mapa>` vuelve a generar un mapa incluido desde el código, y **borra los cambios del editor** en ese mapa.

## 9. Probar una sala

- `/eradmin start <sala>`: partida privada al instante.
- `/eradmin solve <puzle>` o `/eradmin solve *`: resuelve puzles para saltar a la parte que quieres probar.
- Antes de abrirla al público, juégala con 2–3 personas que no la conozcan y apunta dónde se atascan. Ahí es donde hace falta una pista mejor.
