# Landing Alarmas ADT · Distribuidor Autorizado

Landing de captación de leads para alarmas ADT (hogar y negocio). HTML/CSS/JS sin dependencias.

## Cambiar tus datos

Todo lo editable está en **`config.js`**: nombre comercial, asesor, teléfono, WhatsApp, email,
precio y meses de la oferta, fecha fin de la oferta, condiciones, datos legales y opiniones.

> Sustituye las opiniones de ejemplo por opiniones reales de tus clientes antes de hacer campañas.

## Formulario

El formulario "Te llamamos gratis" abre WhatsApp con el nombre, teléfono, código postal e interés
del cliente ya escritos, dirigido al número de `whatsapp` de `config.js`.

## Publicación

Se publica automáticamente con GitHub Pages (workflow `.github/workflows/pages.yml`) en cada push.
En GitHub: **Settings → Pages → Source: GitHub Actions**.

## Probar en local

```sh
python -m http.server 8000
```
