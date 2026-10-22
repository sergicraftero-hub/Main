# Guía del servidor

Esta guía cubre todo lo necesario para abrir el servidor: instalación, ajustes, pack de texturas, jugadores de Bedrock, tienda con Tebex, rangos con LuckPerms, comandos y permisos.

## 1. Requisitos

- **Paper 1.21.4** ([papermc.io](https://papermc.io/downloads/paper)) y **Java 21**.
- Unos **8–12 GB de RAM** para empezar.
  - Cada partida es una copia de la sala, y una sala grande ocupa unos 100.000–190.000 bloques.
  - Con 60 partidas a la vez (el máximo por defecto) va sobrado.
- **Plugins recomendados:**
  - **LuckPerms** para los rangos VIP.
  - **Tebex** para la tienda.
  - **Geyser-Spigot** y **Floodgate** si quieres jugadores de Bedrock y móvil.
  - **ViaVersion**, opcional, para que entren clientes Java más nuevos que 1.21.4.

## 2. Instalación

1. Copia `EscapeRooms-2.0.0.jar` en `plugins/`.
2. Arranca el servidor. Se crean estos mundos y carpetas:
   - `er_lobby`: el lobby. Se construye solo la primera vez.
   - `er_instances`: un mundo vacío donde se pegan las copias de las salas. **Se borra y se recrea en cada arranque**, no construyas nada ahí.
   - `plugins/EscapeRooms/`: `config.yml`, `messages.yml`, `menus.yml`, `progression.yml`, `cosmetics.yml`, `lobby.yml`, `rooms/*.yml`, `templates/*.ert` (los mapas) y `data.db` (los perfiles, en SQLite).
3. Revisa `config.yml` (nombre del servidor, zona horaria, pack de texturas, tienda) y ejecuta `/eradmin reload`.
4. Comprueba que todo está bien con `/eradmin validate`, que revisa cada sala contra su mapa.

### `server.properties` recomendado

```properties
gamemode=adventure
force-gamemode=true
difficulty=peaceful
spawn-monsters=false
spawn-protection=0
allow-nether=false
generate-structures=false
view-distance=8
simulation-distance=6
enable-command-block=false
```

En `bukkit.yml`, pon `allow-end: false`. El mundo principal (`level-name`) no se usa para jugar, porque al entrar todos van al lobby.

### Base de datos

SQLite funciona sin configurar nada. Si más adelante tienes varios servidores o una web con rankings, cambia `database.type` a `mysql` en `config.yml`.

## 3. Paquete de texturas

El plugin lleva su propio pack, con fondos de menú, iconos de objetos y sombreros. Sin el pack todo sigue funcionando: se ven los objetos vanilla y los menús de cofre normales.

**Opción A: el plugin lo sirve él mismo** (por defecto)

```yaml
resource-pack:
  mode: builtin
  port: 8163
  public-host: "play.tuservidor.com"   # tu IP pública o dominio
```

Abre el puerto **8163/TCP** en el firewall y en el panel del host. Si el host no te deja abrir puertos, usa la opción B.

**Opción B: enlace externo** (recomendado para servidores grandes)

1. Sube `plugins/EscapeRooms/escaperooms-pack.zip` a un enlace de descarga directa, como GitHub Releases o un CDN.
2. Configura `mode: url`, `url` y `sha1`. Para calcular el sha1: `sha1sum escaperooms-pack.zip`.

**Tu propio arte:** pon un `custom-pack.zip` en `plugins/EscapeRooms/` y se usará ese en lugar del incluido.
- La forma más fácil es editar las imágenes de `resourcepack/` del código fuente y regenerarlo con `python3 tools/generate_pack.py`.
- Los iconos se enlazan por `custom_model_data`, así que el código no cambia.

`required: true` obliga a aceptar el pack. A los jugadores de Bedrock nunca se les envía.

## 4. Jugadores de Bedrock (Geyser)

1. Instala **Geyser-Spigot** y **Floodgate** en `plugins/`.
2. El plugin los detecta solo.
   - **No les envía el pack de Java**, porque Bedrock no lo entiende y les daría error.
   - Les muestra los menús con objetos vanilla.
3. Todo es jugable con mando o pantalla táctil: menús de cofre, clic derecho en bloques y puzles sin teclas especiales.
   - Los teclados numéricos muestran el número también en la cantidad del objeto, para que en Bedrock se lea bien.
4. En la configuración de Geyser puedes poner `show-cooldown: false` para que no salga el indicador de ataque, que en las salas no sirve.

## 5. Rangos y permisos (LuckPerms)

Crea los grupos y dales estos permisos:

```
/lp creategroup vip
/lp group vip permission set escaperooms.vip true
/lp group vip permission set escaperooms.rank.vip true

/lp creategroup vipplus
/lp group vipplus parent add vip
/lp group vipplus permission set escaperooms.rank.vipplus true
/lp group vipplus permission set escaperooms.vipplus true
/lp group vipplus permission set escaperooms.rooms.all true

/lp creategroup leyenda
/lp group leyenda parent add vipplus
/lp group leyenda permission set escaperooms.rank.leyenda true
/lp group leyenda permission set escaperooms.leyenda true
/lp group leyenda permission set escaperooms.cosmetic.hats.* true
/lp group leyenda permission set escaperooms.fly true
```

| Permiso | Qué da |
|---|---|
| `escaperooms.vip` | doble recompensa diaria (`progression.yml` → `daily.vip-bonus`) y XP ×1,25 |
| `escaperooms.rank.<clave>` | prefijo de chat de `config.yml` → `chat.vip-prefixes` |
| `escaperooms.vipplus` · `escaperooms.leyenda` | XP ×1,5 · ×2 (`progression.yml` → `multipliers`) |
| `escaperooms.rooms.all` | todas las salas de pago |
| `escaperooms.room.<sala>` | una sala de pago concreta |
| `escaperooms.cosmetic.<tipo>.<id>` | un cosmético; `<tipo>` es `titles`, `trails`, `hats` o `victory` (`<tipo>.*` para todos) |
| `escaperooms.fly` | volar en el lobby |
| `escaperooms.admin` | todo lo de administración (incluye los tres siguientes) |
| `escaperooms.build` | editar mapas en creativo |
| `escaperooms.bypass.commands` | usar cualquier comando en partida |
| `escaperooms.bypass.access` | jugar cualquier sala sin nivel ni compra |

> En `progression.yml` las claves de `multipliers` se escriben con guion (`escaperooms-vip`) porque un punto en una clave YAML crearía una sección; el plugin las lee como `escaperooms.vip`.

## 6. Tienda con Tebex

1. Crea una cuenta y una tienda en [tebex.io](https://www.tebex.io/) (tipo «Minecraft: Java Edition»).
2. Instala el plugin **Tebex** en `plugins/` y conéctalo con tu clave secreta: `/tebex secret <clave>`.
3. Pon la URL de tu tienda en `config.yml` → `store.url` y en `menus.yml`, en los botones `url:`.
4. Crea los paquetes. En cada uno, en **Commands**, añade el comando que se ejecuta al pagar, con «Require player to be online» **desactivado**. Los comandos del plugin funcionan aunque el jugador esté desconectado.

| Paquete | Comando en Tebex |
|---|---|
| 500 gemas | `eradmin give {username} gems 500` |
| 1.200 gemas | `eradmin give {username} gems 1200` |
| VIP 30 días | `lp user {username} parent addtemp vip 30d` |
| VIP+ 30 días | `lp user {username} parent addtemp vipplus 30d` |
| Leyenda (permanente) | `lp user {username} parent add leyenda` |
| Sala suelta (p. ej. Expreso) | `eradmin grant {username} room expreso` |
| Pack «todas las salas de pago actuales» | `eradmin grant {username} rooms-all x` |
| Cosmético suelto | `eradmin grant {username} cosmetic hats:crown` |

`rooms-all` da las salas de pago que existen en ese momento. Para incluir también las futuras, da el permiso `escaperooms.rooms.all`; el rango VIP+ ya lo lleva.

Para las devoluciones (comandos «on expiry / chargeback» de Tebex):
- `eradmin take {username} gems 500`
- `eradmin revoke {username} room expreso`
- `lp user {username} parent remove vip`

**Salas de pago.** En el `.yml` de la sala:

```yaml
access:
  level: 10            # nivel mínimo
  rooms: [alquimista]  # salas que hay que haber completado antes
  premium: true        # de pago
  price-gems: 300      # se puede comprar en el juego con gemas...
  price-keys: 0        # ...o con llaves (0 = no se vende por llaves)
  permission: ""       # o con un permiso propio
```

### Normas de Mojang (EULA)

Mojang **no permite vender ventaja de juego**. Por eso el plugin no vende pistas, tiempo extra ni objetos que ayuden a resolver puzles. Sí permite vender:
- **acceso** a contenido (salas premium);
- **cosméticos** (títulos, estelas, sombreros, efectos);
- **comodidad** (más recompensa diaria, más XP).

Antes de abrir, lee las [Minecraft Usage Guidelines](https://www.minecraft.net/es-es/usage-guidelines) y pon en la tienda web que el servidor no está afiliado a Mojang.

Los nombres de las salas son **inspirados**, no marcas registradas. No uses nombres ni logotipos de películas, animes o juegos reales.

## 7. Comandos

**Jugadores**

| Comando | Qué hace |
|---|---|
| `/menu` (`/m`) | menú principal (también con el objeto del inventario) |
| `/salas`, `/jugar <sala>` | lista de salas / jugar una sala |
| `/grupo invitar\|aceptar\|salir\|expulsar\|lista` | grupos de hasta 10 jugadores |
| `/pista` | pista durante la partida (resta estrellas y añade tiempo) |
| `/salir`, `/lobby` | salir de la partida o de la cola / volver al lobby |
| `/perfil`, `/top`, `/diario`, `/misiones`, `/cosmeticos`, `/tienda` | progreso, rankings, recompensas y tienda |

En partida solo se permiten los comandos de `config.yml` → `game.allowed-commands`. Si un mensaje empieza por `!`, se habla con todo el servidor; si no, solo con el equipo.

**Administración** (`/eradmin` o `/era`)

| Comando | Qué hace |
|---|---|
| `reload` | recarga todos los `.yml` |
| `validate [sala]` | comprueba una sala contra su mapa (marcadores, bloques y objetos) |
| `start <sala> [jugador]` · `stop [jugador]` | empezar o parar una partida |
| `solve <puzle\|*>` | resolver un puzle (o todos los activos) en tu partida, para probar |
| `give\|take <jugador> keys\|gems\|xp <n>` | dar o quitar moneda o XP (funciona con jugadores desconectados) |
| `grant\|revoke <jugador> room <sala>` · `cosmetic <tipo:id>` · `rooms-all x` | dar o quitar compras |
| `info <jugador>` | perfil de un jugador |
| `instances` | copias de salas en memoria y su estado |
| `build <mapa>` | regenera un mapa incluido (borra los cambios del editor en ese mapa) |
| `editor <sala\|mapa>` · `editor save` · `editor exit` | editar mapas en creativo (ver [CREAR-SALAS.md](CREAR-SALAS.md)) |
| `marker set\|target\|box\|del\|list` · `pos1` · `pos2` · `capture <id>` | marcadores y captura de mapas nuevos |
| `lobby` | ir al lobby |

## 8. Ajustes útiles

- **Fin de semana de doble XP:** `events.xp-multiplier: 2.0` y `/eradmin reload`.
- **Partidas simultáneas:** `instances.max-per-room` y `instances.max-total`.
- **Copias preparadas:** `instances.warm-per-room`. Así la partida empieza al instante.
- **Velocidad de pegado:** `instances.blocks-per-tick`. Bájalo si notas lag al preparar salas.
- **Reconexión:** `game.reconnect-grace-seconds`. Si alguien se cae, vuelve a su partida.
- **Partidas públicas:** `queue.min-players` y las cuentas atrás.
- **Todos los textos** están en `messages.yml` y los menús en `menus.yml`, con formato [MiniMessage](https://docs.advntr.dev/minimessage/format.html).

## 9. Copias de seguridad

Guarda `plugins/EscapeRooms/`: `data.db`, `templates/` (si editaste mapas) y los `.yml`. Los mundos `er_instances` y `er_lobby` se regeneran solos.
