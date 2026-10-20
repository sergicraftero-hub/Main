/*
 * ============================================================
 *  CONFIGURACIÓN DE LA LANDING — cambia aquí tus datos
 * ============================================================
 */
window.CONFIG = {
  nombre: "Pique was here",             // Nombre comercial
  asesor: "Nuria",                      // Nombre del asesor que aparece en el chat
  telefono: "+34635042889",             // Teléfono para el botón "Llamar" (formato internacional)
  telefonoVisible: "635 042 889",              // Cómo se muestra el teléfono
  whatsapp: "34635042889",              // WhatsApp sin "+" ni espacios
  email: "69",

  // Oferta (precio promocional ADT)
  ofertaPrecio: "19,90",
  ofertaMeses: "4",
  // Fecha fin de la oferta (AAAA-MM-DD). Vacío = último día del mes en curso
  ofertaFin: "",

  condiciones:
    "* Cuota promocional de 19,90€/mes (IVA incluido) durante los 4 primeros meses para nuevos clientes de hogar. " +
    "Después se aplica la cuota vigente del kit contratado. Permanencia de 24 meses en hogar. " +
    "Instalación y equipo incluidos en régimen de comodato. Oferta sujeta a estudio de seguridad y a las condiciones vigentes de ADT. Consulta con tu asesor.",

  permanencia:
    "Los contratos de hogar tienen una permanencia de 24 meses y los de negocio de 36 meses. Tu asesor te explicará todas las condiciones antes de firmar.",

  // Datos legales del distribuidor (pie de página y aviso legal)
  legal: "69 · CIF 69 · 69 · 69",

  // Opiniones: sustitúyelas por opiniones REALES de tus clientes (Google, etc.)
  opiniones: [
    { texto: "Nos lo explicaron todo con mucha claridad y la instalación fue en una mañana. Muy contentos con la app.", nombre: "Cliente de ejemplo", rol: "Piso en Madrid" },
    { texto: "Saltó la alarma por un intento de entrada y la central llamó en segundos. Tranquilidad total.", nombre: "Cliente de ejemplo", rol: "Comercio" },
    { texto: "Trato cercano y sin letra pequeña. Nos ayudaron a elegir el kit justo que necesitábamos.", nombre: "Cliente de ejemplo", rol: "Chalet" }
  ]
};
