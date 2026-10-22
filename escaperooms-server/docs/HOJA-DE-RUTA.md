# Hoja de ruta

## Fase 1: hecha

- **Motor:**
  - 17 tipos de puzle, acciones, props, zonas y línea temporal;
  - copias por partida en paralelo;
  - editor de mapas en el juego.
- **Servidor:**
  - lobby con 4 alas, NPCs, parkour y rankings;
  - grupos, colas públicas y partidas privadas;
  - reconexión a la partida.
- **Progresión:** niveles 1–100, rangos, estrellas, recompensa diaria, misiones diarias y semanales, logros y cosméticos.
- **Tienda:**
  - llaves y gemas;
  - salas premium;
  - comandos para Tebex;
  - permisos para rangos VIP.
- **Paquete de texturas** compatible con Geyser/Bedrock.
- **Salas:** Tutorial, La Torre del Alquimista y El Expreso de Medianoche.
- **Pruebas automáticas:**
  - un simulador recorre cada sala;
  - un bot juega las 3 salas enteras;
  - otros tests comprueban los textos, el pack y que todos los bloques existen en Minecraft.

## Salas pendientes

| # | Sala | Ala | Dificultad prevista | Fase |
|---|---|---|---|---|
| 1 | El Expreso de Medianoche | Cine | Difícil | ✅ 1 |
| 2 | La Mansión Blackwood (terror, apagones) | Cine | Media | 2 |
| 3 | Golpe al Banco Central (láseres, cámara acorazada) | Cine | Difícil | 2 |
| 4 | El Templo de la Serpiente (trampas, persecución) | Cine | Media | 3 |
| 5 | Estación Orbital Nova (oxígeno, criatura) | Cine | Experta | 3 |
| 6 | La Aldea Oculta (ninjas, sellos) | Anime | Fácil | 2 |
| 7 | El Balneario de los Espíritus | Anime | Media | 3 |
| 8 | Hangar Mecha 09 | Anime | Difícil | 3 |
| 9 | La Muralla (ciudad sitiada por titanes) | Anime | Difícil | 4 |
| 10 | Era Taishō: Cazadores de Demonios | Anime | Experta | 4 |
| 11 | Mundo 8-Bit (plataformas retro) | Videojuegos | Fácil | 2 |
| 12 | Neo-Barcelona 2088 (cyberpunk, hackeos) | Videojuegos | Difícil | 3 |
| 13 | La Mazmorra del Rey Liche (RPG) | Videojuegos | Media | 4 |
| 14 | Instalación Umbrella-X (survival horror) | Videojuegos | Experta | 4 |
| 15 | Los Santuarios del Héroe (física) | Videojuegos | Difícil | 4 |
| 16 | La Torre del Alquimista | Originales | Media | ✅ 1 |
| 17 | El Último Faro (tormenta, morse) | Originales | Media | 4 |
| 18 | El Circo de los Espejos (ilusiones) | Originales | Difícil | 5 |
| 19 | La Biblioteca Infinita (lógica pura) | Originales | Experta | 5 |
| 20 | El Juicio Final (se desbloquea con las otras 19) | Originales | Experta | 5 |

Propuesta de salas premium, alrededor de 6: Banco Central, Estación Orbital Nova, Hangar Mecha 09, Era Taishō, Instalación Umbrella-X y El Circo de los Espejos. Las fáciles y medias se quedan gratis para enganchar a los jugadores nuevos.

## Otras tareas por fase

- **Fase 2:**
  - mecánicas nuevas que piden las salas de terror y atraco: apagones, láseres que te devuelven atrás y cámaras de vigilancia;
  - más cosméticos (mascotas);
  - logros por sala.
- **Fase 3:**
  - puzles cooperativos entre salas separadas (uno ve el código y otro lo introduce);
  - espejos y rayos de luz.
- **Fase 4:**
  - pase de temporada mensual (línea gratis y línea de pago, solo cosméticos);
  - rankings semanales con premios.
- **Fase 5:**
  - El Juicio Final;
  - equilibrado de tiempos y recompensas con datos reales;
  - pulido.

## Pendiente de probar en un servidor real

Todo está probado con tests y con un bot en un servidor simulado (MockBukkit). Queda una prueba en un **Paper real** con jugadores. Partes que conviene mirar con más atención:

- Rendimiento al pegar salas con muchos jugadores. Si hay lag, ajusta `instances.blocks-per-tick`.
- Que el pack de texturas se descargue bien desde fuera (`public-host` y el puerto).
- Jugadores de Bedrock con Geyser: los menús y los clics en marcos y NPCs.
- Que los tiempos de cada sala se acerquen a 45–60 minutos con grupos que no la conocen.
