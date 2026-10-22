# EscapeRooms: servidor de escape rooms para Paper 1.21.4

Plugin completo para montar un servidor dedicado a escape rooms. Incluye:

- **Lobby** con 4 alas temáticas (Cine, Anime, Videojuegos y Originales), NPCs, parkour y rankings.
- **Salas largas** (45–60 min), de **1 a 10 jugadores**. Cada partida juega en su **propia copia** de la sala, así que varios grupos pueden jugar la misma sala a la vez.
- **Motor de puzles configurable en YAML**: 17 tipos de puzle, más acciones, props, recetas, zonas que se desbloquean y línea temporal.
- **Progresión:** niveles 1–100, rangos, estrellas por sala, recompensa diaria con racha, misiones diarias y semanales, logros y cosméticos.
- **Tienda:**
  - *llaves*: se ganan jugando;
  - *gemas*: se compran en Tebex;
  - salas premium, rangos VIP y cosméticos. No se vende ninguna ventaja de juego (EULA).
- **Paquete de texturas** propio, con menús dibujados e iconos. Es compatible con **Geyser/Bedrock**: los jugadores de Bedrock y los que no aceptan el pack ven los objetos vanilla de siempre.
- **Pruebas automáticas:**
  - un simulador recorre cada sala para comprobar que todo puzle se puede resolver y que no se puede saltar ninguna zona;
  - un bot juega las salas enteras en un servidor simulado.

## Contenido de la fase 1

| Sala | Ala | Dificultad | Puzles | Duración |
|---|---|---|---|---|
| Tutorial: La Academia de Escapistas | Originales | Tutorial | 7 | ~10 min |
| La Torre del Alquimista | Originales | Media | 23 | ~50 min |
| El Expreso de Medianoche | Cine | Difícil | 20 | ~55 min |

Las otras 17 salas del plan están en [docs/HOJA-DE-RUTA.md](docs/HOJA-DE-RUTA.md).

## Instalación rápida

1. Instala un servidor **Paper 1.21.4** con **Java 21**.
2. Copia `EscapeRooms-2.0.0.jar` en `plugins/` y arranca el servidor una vez.
3. En `plugins/EscapeRooms/config.yml`, pon tu IP o dominio en `resource-pack.public-host` y abre el puerto `8163`.
4. Opcional: LuckPerms (rangos), Tebex (tienda) y Geyser con Floodgate (Bedrock).

La guía completa está en [docs/SERVIDOR.md](docs/SERVIDOR.md): instalación, `server.properties`, pack de texturas, Geyser, Tebex, LuckPerms, comandos y permisos.

Para crear o modificar salas, consulta [docs/CREAR-SALAS.md](docs/CREAR-SALAS.md): referencia de YAML, tipos de puzle, acciones, props y editor en el juego.

## Compilar

```bash
./gradlew build              # compila, genera el pack de texturas y pasa todos los tests
./gradlew runServer          # arranca un Paper 1.21.4 de pruebas con el plugin
python3 tools/generate_pack.py   # regenera resourcepack/ (iconos y fondos de menú)
```

El jar queda en `build/libs/`.

## Estructura

```
src/main/java/.../escapes/
  build/      generadores de los mapas (lobby, tutorial, alquimista, expreso)
  template/   formato de mapa .ert, editor en el juego
  instance/   copias por partida en el mundo er_instances
  game/       partidas, grupos, colas, props
  puzzle/     motor de puzles y los 17 tipos
  action/     acciones de las salas (abrir puertas, dar objetos, títulos...)
  progress/   niveles, misiones, logros, recompensa diaria
  shop/ cosmetic/ profile/ gui/ lobby/ pack/ hook/
src/main/resources/
  config.yml messages.yml menus.yml progression.yml cosmetics.yml lobby.yml
  rooms/      una sala por fichero .yml
resourcepack/ paquete de texturas (generado por tools/generate_pack.py)
```
