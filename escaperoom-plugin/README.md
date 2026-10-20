# EscapeRoom — salas de escape para Paper 1.21.x

Plugin para servidores **Paper 1.21.x (Java 21)** que convierte zonas del mundo en *escape rooms*
jugables y que, con un solo comando, **construye un mapa completo: «La Torre del Alquimista»**,
con cinco estancias decoradas, ocho puzles encadenados, historia, ambiente y ranking.

- Sin dependencias obligatorias. Opcionales: **PlaceholderAPI**, **WorldEdit / FastAsyncWorldEdit**, **Vault**.
- Código en inglés; todos los textos al jugador en `messages_es.yml` con **MiniMessage**.

---

## Instalación

1. Copia `EscapeRoom-1.0.0.jar` en `plugins/` y reinicia el servidor.
2. En el juego (como OP): `/escape build alquimista` donde quieras el patio de entrada
   (o `/escape build alquimista <x> <y> <z>`). La construcción va por tandas y tarda unos segundos.
3. Los jugadores entran con `/escape join alquimista` o haciendo clic en el cartel del patio.

### Compilar

```bash
./gradlew build      # genera build/libs/EscapeRoom-1.0.0.jar y ejecuta los tests
./gradlew runServer  # arranca un Paper 1.21.4 de prueba con el plugin cargado
```

Los tests (`src/test`) construyen la torre en memoria, recargan la sala generada con el motor y
**simulan a un jugador recorriendo el mapa** (andar, subir escaleras, caer, escaleras de mano)
para comprobar que cada puzle es alcanzable y que cada zona sigue cerrada hasta resolver el anterior.

---

## Comandos

| Comando | Descripción | Permiso |
|---|---|---|
| `/escape join <sala>` | Entrar en la cola de una sala | `escaperoom.play` |
| `/escape leave` | Salir de la cola o de la partida | `escaperoom.play` |
| `/escape hint` | Pista progresiva (3 niveles por puzle, cuesta tiempo) | `escaperoom.hint` |
| `/escape list` · `info <sala>` · `top <sala>` · `stats [jugador]` | Información y ranking | `escaperoom.play` |
| `/escape wand` · `pos1` · `pos2` | Seleccionar región (o usar la selección de WorldEdit) | `escaperoom.admin.create` |
| `/escape create <id>` | Crear una sala con la selección | `escaperoom.admin.create` |
| `/escape edit <sala> <propiedad> [valor]` | `spawn`, `exit`, `lobby`, `leaderboard`, `region`, `time-limit`, `min-players`, `max-players`, `countdown`, `hints`, `max-hints`, `hint-penalty`, `night`, `enabled`, `display-name` | `escaperoom.admin.edit` |
| `/escape snapshot <sala>` | Guardar el estado actual (al que se restaura tras cada partida) | `escaperoom.admin.edit` |
| `/escape puzzles <sala>` · `setsign <sala>` | Ver puzles · convertir el cartel que miras en cartel de unión | `escaperoom.admin.edit` |
| `/escape start\|stop\|reset <sala>` | Forzar inicio / parar / restaurar | `escaperoom.admin.control` |
| `/escape delete <sala> confirm` | Borrar sala | `escaperoom.admin.delete` |
| `/escape build alquimista [x y z]` · `demolish alquimista` | Construir / demoler el mapa (restaura el terreno original) | `escaperoom.admin.build` |
| `/escape reload` | Recargar config, mensajes y salas | `escaperoom.admin.reload` |

`escaperoom.admin` incluye todos los permisos; `escaperoom.bypass` ignora la protección de las regiones.
Con `per-room-permissions: true` cada sala exige además `escaperoom.join.<sala>`. Alias: `/er`, `/escaperoom`.
Todo tiene autocompletado con tabulador.

---

## Cómo funciona una partida

- **Cola y lobby**: al unirte vas al lobby; al llegar al mínimo de jugadores empieza una cuenta atrás
  (más corta si la sala se llena). Si hay una partida en curso, entras en la siguiente.
- **Durante la partida**: BossBar con el tiempo, títulos, sonidos, chat sólo de equipo, inventario guardado
  y vaciado, sin hambre ni daño, sin PvP, comandos limitados (`game.allowed-commands`).
- **Solo / equipo**: el modo se decide al empezar (y pasa a solo si el equipo se queda en un jugador).
  Los puzles cooperativos ofrecen alternativa para un jugador (p. ej. el yunque en las placas).
- **Pistas**: `/escape hint` da la siguiente pista del primer puzle activo; restan `hint-penalty` segundos.
- **Victoria**: tiempo final (con penalizaciones), recompensas (comandos y dinero de Vault), posición en el ranking.
  **Derrota**: mensaje y vuelta al lobby.
- **Desconexión, muerte, `/escape leave`, reinicio o caída del servidor**: el inventario se guarda en disco
  (`inventories/<uuid>.yml`) y se devuelve siempre, también en el siguiente inicio de sesión; las salas
  que estaban en partida se restauran solas al arrancar (`dirty-rooms.yml`).
- **Reinicio de la sala**: snapshot de la región aplicado por tandas (sin congelar el servidor), con
  contenido de cofres, atriles y carteles. Si WorldEdit/FAWE está instalado se usan esquemáticas.
- **Protección**: nadie rompe ni coloca bloques salvo lo que permita un puzle; nadie ajeno entra en la región;
  la decoración (marcos, soportes, displays) es intocable; el agua no fluye, el cobre no se oxida, etc.

### Estadísticas y placeholders

SQLite asíncrono (`stats.db`) con caché en memoria. Holograma de ranking (TextDisplay) en la posición
`leaderboard` de cada sala. Placeholders de PlaceholderAPI:

`%escaperoom_wins%`, `%escaperoom_plays%`, `%escaperoom_losses%`, `%escaperoom_best_<sala>%`,
`%escaperoom_best_<sala>_players%`, `%escaperoom_pb_<sala>%`, `%escaperoom_top_<sala>_<n>%` (y `_time`, `_players`),
`%escaperoom_room%`, `%escaperoom_time_left%`, `%escaperoom_hints_used%`, `%escaperoom_status_<sala>%`,
`%escaperoom_queue_<sala>%`, `%escaperoom_playing_<sala>%`.

---

## Crear tu propia sala

1. `/escape wand` y marca dos esquinas (o selecciona con WorldEdit) → `/escape create mi-sala`.
2. Colócate y usa `/escape edit mi-sala spawn|exit|lobby|leaderboard`.
3. Añade puzles en `plugins/EscapeRoom/rooms/mi-sala.yml` (shift + clic derecho con la varita copia las
   coordenadas de un bloque) y `/escape reload`.
4. `/escape edit mi-sala enabled true` (guarda el snapshot inicial). Tras cambiar bloques: `/escape snapshot mi-sala`.

El archivo `rooms/alquimista.yml` que genera el mapa es un ejemplo completo de todo lo que sigue.

### Tipos de puzle

Todos comparten `type`, `name`, `requires` (ids que deben estar resueltos antes), `hints` (lista, de vaga a
explícita) y `on-solve` (lista de acciones).

| `type` | Campos propios | Qué hace |
|---|---|---|
| `levers` | `levers: {"x,y,z": true}`, `lamps: {"x,y,z": ["x,y,z"]}` | Combinación de palancas; cada palanca puede encender lámparas sin cableado |
| `buttons` | `buttons: {"x,y,z": "7"}`, `code: "7125"` o lista, `ordered`, `clear-button` | Teclado (ordenado) o “pulsa los correctos” (sin orden; un fallo reinicia) |
| `key` | `target`, `item`, `consume`, `locked-message` | Cerradura que se abre con un ítem con PDC |
| `plates` | `plates: [...]`, `mode: simultaneous\|sequence`, `solo-alternative`, `solo-item` | Placas a la vez o en orden; en solitario se entrega un peso (yunque) |
| `riddle` | `question`, `answers`, `announce` | Acertijo por chat, sin distinguir mayúsculas, tildes ni signos |
| `hidden-item` | `item`, `container` + `slot` **o** `search-block` | Objeto escondido en un contenedor o al registrar un bloque |
| `placement` | `slots: {"x,y,z": [MATERIAL,...]}`, `ordered`, `reset-to` | Colocar bloques/flores en posiciones concretas (en orden opcional) |
| `cauldron` | `cauldron`, `ingredients: [...]`, `ordered` | Echar ingredientes (clic derecho); un orden erróneo los devuelve |

Nuevos tipos: implementa `Puzzle` (o extiende `AbstractPuzzle`) y llama a `PuzzleRegistry.register("mi-tipo", MiPuzzle::new)`.

### Acciones (`on-solve`, `start-actions`, `triggers`)

Todas aceptan `delay` (ticks).

`message`, `actionbar`, `title`, `sound` (`location` opcional), `particle` (`color`/`size` para `DUST`),
`give` (`item`, `amount`, `to: trigger|all`), `set-blocks` (`from`/`to` o `blocks`, `block`, `physics`),
`open` (puertas/trampillas, ambas mitades), `light` (lámparas), `redstone` (coloca o quita un bloque de
redstone: **pistones y puertas reales**), `slide` (desliza bloques con BlockDisplay animado, `offset`, `via`,
`ticks`), `command` (`as: console|trigger|all`, `%player%`), `narrative` (TextDisplay temporal), `time`
(`seconds` de bonificación o penalización) y `win`.

Otras secciones de la sala: `items` (ítems con nombre, lore, brillo, color, libros escritos), `start-items`,
`setup.lecterns` / `setup.containers` (se rellenan al empezar), `triggers` (acciones una vez por partida al
entrar en una sub-región), `ambient` (partículas y sonidos en bucle), `rewards` y `settings.final-puzzle`.

---

## El mapa: «La Torre del Alquimista»

> Los jugadores son aprendices encerrados en la torre del alquimista Aurelio, desaparecido hace un siglo.
> Deben recrear la Piedra Filosofal antes de que se apague la última vela (30 minutos).

Parcela de 41×45 bloques: patio con lobby (fuente, cartel de unión, holograma de ranking, farolas, árboles)
y una torre de piedra con contrafuertes, molduras, vidrieras, hiedra, balcón y cúpula de cobre oxidado con
rendija de observación. La partida es de noche (tiempo por jugador). Cada estancia muestra un texto
narrativo al entrar; el diario de Aurelio se entrega al empezar.

| # | Estancia | Puzles | Ingrediente |
|---|---|---|---|
| 1 | **La Celda** (sótano de deepslate, cadenas, goteo) | Poema en carteles → 3 de 6 botones camuflados abren la reja · Barril del pasillo | Sal de la Tierra |
| 2 | **La Biblioteca** (estanterías cinceladas, chimenea, galería con escalera de caracol) | Tres tomos en atriles → código de 4 dígitos en el teclado → una estantería se desliza y revela la escalera secreta · Acertijo de la calavera por chat | Mercurio Vivo |
| 3 | **El Invernadero** (cristal, cobre, musgo, azaleas, estanque, raíces, luz verde) | Vitral de 5 colores → flores en 5 macetas en orden · Dos placas a la vez (o yunque en solitario) abren la compuerta | Azufre Dorado |
| 4 | **El Observatorio** (cúpula, telescopio, mapa estelar en marcos) | 9 palancas encienden lámparas que dibujan la constelación del mapa estelar en el techo → se abre una trampilla | Polvo de Estrella |
| 5 | **El Laboratorio** (atanor, alambique, estanterías de pociones, la última vela) | Echar los 4 ingredientes en el caldero en el orden de la receta → Piedra Filosofal, se abre el balcón y **victoria** | — |

<details>
<summary><b>Solución completa (spoilers)</b></summary>

1. **Celda**: botones sobre la piedra musgosa (muro norte), la agrietada (muro oeste) y la cincelada (muro sur).
   La Sal está en el barril inferior de la esquina sureste del pasillo, junto a la escalera.
2. **Biblioteca**: Tomo I → iniciales «SIETE» (7); Tomo II → «doce campanadas» subrayado (12);
   Tomo III → iniciales «CINCO» (5). Código **7125** (C borra). Acertijo: **mercurio** (también azogue, hidrargirio).
3. **Invernadero**: macetas I–V en orden: rojo (amapola/tulipán rojo), amarillo (diente de león), azul
   (aciano/orquídea azul), blanco (lirio de los valles, tulipán blanco, margarita, azur) y rosa (tulipán rosa).
   Luego pisar las dos placas a la vez (en solitario, clic derecho con el yunque en una y pisar la otra).
4. **Observatorio**: sólo cuentan las estrellas fijas (las bolas de fuego son astros errantes).
   Fila norte: izquierda · fila central: centro y derecha · fila sur: izquierda y derecha.
5. **Laboratorio**: Sal de la Tierra → Mercurio Vivo → Azufre Dorado → Polvo de Estrella.
</details>

`/escape demolish alquimista` elimina el mapa, la sala y sus snapshots, y restaura el terreno que había antes.

---

## Configuración (`config.yml`)

Idioma, cuentas atrás, retardo tras la victoria, chat de equipo, comandos permitidos, avisos de tiempo,
interacciones libres (`CHEST`, `BARREL`, `LECTERN`…), bloques por tick de snapshots y construcción,
uso de WorldEdit, tamaño del ranking y radio del ambiente.

## Arquitectura

```
com.sergicraftero.escaperoom
├── EscapeRoomPlugin        arranque y servicios
├── room/                   Room (YAML), RoomManager, ItemSpec, Trigger, AmbientEffect
├── puzzle/                 Puzzle, AbstractPuzzle, PuzzleRegistry, PuzzleState
│   └── types/              levers, buttons, key, plates, riddle, hidden-item, placement, cauldron
├── action/                 RoomAction, ActionList (con delays), Actions (fábrica)
├── game/                   Game (cola → cuenta atrás → partida → reinicio), GameManager, InventoryStore
├── snapshot/               RegionSnapshot (por tandas, a disco), SnapshotService, WorldEditBridge
├── storage/                StatsStorage (SQLite asíncrono + caché)
├── display/                LeaderboardService (TextDisplay), AmbientService
├── listener/               RoomListener (protección y puzles), PlayerListener (ciclo de vida, chat)
├── command/                EscapeCommand (+ tab), Selections
├── hook/                   EscapePlaceholders (PlaceholderAPI), VaultHook (reflexión)
└── build/                  Canvas, Palette, BlockPlacer, Decor, AlchemistTower/Interior/Layout/RoomConfig/Texts
```

### Notas de diseño

- **Estantería secreta**: se desliza con una animación de BlockDisplay y sonido de pistón, y los bloques
  reales quedan movidos; es más fiable que unos pistones reales escondidos en un muro de una sola capa.
  Los pistones/puertas reales están disponibles con la acción `redstone` (la puerta final del balcón la usa).
- Los bloques renombrados entre versiones 1.21.x (p. ej. `chain` → `iron_chain` en 1.21.9) se resuelven solos.
