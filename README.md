# Gym — tracker personal

Web app instalable en iPhone para apuntar **entrenos, peso y dieta**. Sin servidor, sin cuentas:
los datos se guardan solo en tu iPhone.

## Qué hace

- **Entreno**: tus rutinas (Día A/B/C…). Al empezar una, cada ejercicio aparece ya relleno con los kg
  y reps de la última vez; solo vas marcando ✓. Cronómetro de sesión y de descanso, récords,
  historial y gráfica de progreso por ejercicio. Resalta la rutina que te toca hoy.
- **Peso**: un registro al día, gráfica de 90 días con media de 7 días, cambio a 7 y 30 días, peso objetivo.
- **Dieta**: tus comidas habituales como botones de un toque, totales de kcal y proteína frente a tu objetivo,
  "copiar del día anterior" y comidas puntuales.
- **Ajustes**: editar rutinas, comidas habituales, objetivos y copia de seguridad (exportar/importar JSON).

### Formato de las rutinas

Una línea por ejercicio: `nombre | series | nota del entrenador`. Cada serie es `kg x reps @ RIR`
(todo opcional), separadas por comas:

```
Flat barbell BP | 90x3-5@1-2, 85x4-8@1-2, 85x4-8@1-2
Dominadas | AMRAP, AMRAP | Unilateral, agarre neutro
Dominadas asistidas | -30xAMRAP, -30xAMRAP
```

También vale la forma corta `Press banca 4x8 60`. Las rutinas pueden ser de pesas o de cardio.
Si el nombre lleva el día de la semana (`Martes · Empuje`), la app marca la que toca hoy.
El 1RM estimado usa `kg · (1 + 0,033 · (reps + RIR))`.

## Instalar en el iPhone

1. En GitHub: **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. Ve a **Actions → Publicar app → Run workflow** (o haz un push que toque `app/`).
3. Abre la URL publicada (`https://<usuario>.github.io/<repo>/`) en **Safari**.
4. Botón compartir → **Añadir a pantalla de inicio**.

A partir de ahí se abre como una app a pantalla completa y funciona sin conexión.

## Privacidad

La URL es pública, pero la app no tiene datos dentro: todo lo que apuntas vive solo en el almacenamiento
de tu iPhone y nadie más lo ve. Haz **Exportar copia** de vez en cuando (guárdala en Archivos / iCloud Drive),
porque si borras la app de la pantalla de inicio se borran sus datos.

## Desarrollo

Es HTML/CSS/JS sin dependencias ni compilación, en `app/`. Para probar en local:

```sh
cd app && python3 -m http.server 8000
```

Si cambias archivos, sube la versión de `CACHE` en `app/sw.js` para que el iPhone coja la nueva versión.
