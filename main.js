(function () {
  var C = window.CONFIG;

  // Rellenar textos configurables
  document.querySelectorAll("[data-cfg]").forEach(function (el) {
    var v = C[el.getAttribute("data-cfg")];
    if (v != null) el.textContent = v;
  });
  document.querySelectorAll("[data-cfg-initial]").forEach(function (el) {
    el.textContent = (C[el.getAttribute("data-cfg-initial")] || "A").charAt(0);
  });

  // Mes actual
  var meses = ["enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"];
  var now = new Date();
  document.querySelectorAll("[data-month]").forEach(function (el) { el.textContent = meses[now.getMonth()].toUpperCase(); });
  document.querySelectorAll("[data-month-lower]").forEach(function (el) { el.textContent = meses[now.getMonth()]; });

  // Enlaces de teléfono y WhatsApp
  function waLink(text) {
    return "https://wa.me/" + C.whatsapp + "?text=" + encodeURIComponent(text);
  }
  var waDefault = "Hola, me interesa una alarma ADT. ¿Me podéis informar de la oferta?";
  document.querySelectorAll(".js-wa").forEach(function (a) {
    a.href = waLink(waDefault);
    a.target = "_blank";
    a.rel = "noopener";
  });
  document.querySelectorAll(".js-tel").forEach(function (a) { a.href = "tel:" + C.telefono; });

  // Botones de kit preseleccionan el formulario
  var tipo = document.querySelector("select[name=tipo]");
  document.querySelectorAll(".js-kit").forEach(function (a) {
    a.addEventListener("click", function () {
      var k = a.getAttribute("data-kit");
      for (var i = 0; i < tipo.options.length; i++) if (tipo.options[i].text === k) tipo.selectedIndex = i;
    });
  });

  // Cuenta atrás
  var end;
  if (C.ofertaFin) end = new Date(C.ofertaFin + "T23:59:59");
  else end = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);
  function pad(n) { return (n < 10 ? "0" : "") + n; }
  function tick() {
    var d = Math.max(0, end - new Date()) / 1000;
    document.getElementById("cd-d").textContent = pad(Math.floor(d / 86400));
    document.getElementById("cd-h").textContent = pad(Math.floor(d % 86400 / 3600));
    document.getElementById("cd-m").textContent = pad(Math.floor(d % 3600 / 60));
    document.getElementById("cd-s").textContent = pad(Math.floor(d % 60));
  }
  tick();
  setInterval(tick, 1000);

  // Pasos "cómo funciona" animados
  var steps = document.querySelectorAll(".step"), si = 0;
  steps.forEach(function (s, i) { s.addEventListener("mouseenter", function () { si = i; show(); }); });
  function show() { steps.forEach(function (s, i) { s.classList.toggle("active", i === si); }); }
  setInterval(function () { si = (si + 1) % steps.length; show(); }, 3500);

  // Equipo
  var devices = [
    { t: "Panel de control", i: "🎛️", d: "El cerebro del sistema: gestiona todos los detectores y se comunica con la Central Receptora.",
      l: ["Conexión permanente con la Central Receptora ADT", "Comunicación por varias vías con antiinhibición", "Batería de respaldo ante cortes eléctricos"] },
    { t: "Detector con cámara", i: "📷", d: "Detecta movimiento y toma imágenes para que la Central verifique la alarma al instante.",
      l: ["Verificación por imagen de cada salto", "Visión nocturna", "Inalámbrico, sin obras"] },
    { t: "Detector de movimiento", i: "📶", d: "Sensor infrarrojo que detecta presencia en las estancias protegidas.",
      l: ["Opción inmune a mascotas", "Largo alcance", "Batería de larga duración"] },
    { t: "Detector magnético", i: "🚪", d: "Se coloca en puertas y ventanas y avisa en cuanto se abren.",
      l: ["Protección perimetral", "Discreto y compacto", "Ideal para accesos"] },
    { t: "Sirena", i: "📢", d: "Sirena de alta potencia que disuade al intruso desde el primer segundo.",
      l: ["Más de 100 dB", "Protección antisabotaje", "Señal luminosa"] },
    { t: "App y mandos", i: "📱", d: "Conecta y desconecta tu alarma con el mando, el teclado o la App desde cualquier lugar.",
      l: ["Avisos en tiempo real", "Gestión de usuarios", "Historial de eventos"] }
  ];
  var tabs = document.querySelector(".device-tabs");
  devices.forEach(function (dv, i) {
    var b = document.createElement("button");
    b.type = "button";
    b.setAttribute("role", "tab");
    b.innerHTML = "<span>" + dv.i + "</span>" + dv.t;
    b.addEventListener("click", function () { pick(i); });
    tabs.appendChild(b);
  });
  function pick(i) {
    var dv = devices[i];
    tabs.querySelectorAll("button").forEach(function (b, j) { b.classList.toggle("on", i === j); b.setAttribute("aria-selected", i === j); });
    document.getElementById("dv-ico").textContent = dv.i;
    document.getElementById("dv-title").textContent = dv.t;
    document.getElementById("dv-desc").textContent = dv.d;
    document.getElementById("dv-list").innerHTML = dv.l.map(function (x) { return "<li>" + x + "</li>"; }).join("");
  }
  pick(0);

  // Opiniones
  document.getElementById("reviews").innerHTML = C.opiniones.map(function (o) {
    return '<article class="review"><div class="stars">★★★★★</div><p>“' + o.texto + '”</p><strong>' + o.nombre + "</strong><small>" + o.rol + "</small></article>";
  }).join("");

  // Formulario -> WhatsApp
  var form = document.getElementById("lead-form"), err = document.getElementById("form-error");
  form.addEventListener("submit", function (e) {
    e.preventDefault();
    var f = form.elements, msg = "";
    if (!f.nombre.value.trim()) msg = "Escribe tu nombre.";
    else if (!/^[0-9 +]{9,15}$/.test(f.telefono.value.trim())) msg = "Escribe un teléfono válido.";
    else if (!f.privacidad.checked) msg = "Debes aceptar la política de privacidad.";
    if (msg) { err.textContent = msg; err.hidden = false; return; }
    err.hidden = true;
    var text = "Hola, quiero que me llaméis para informarme de las alarmas ADT.\n" +
      "Nombre: " + f.nombre.value.trim() + "\n" +
      "Teléfono: " + f.telefono.value.trim() + "\n" +
      (f.cp.value.trim() ? "C.P.: " + f.cp.value.trim() + "\n" : "") +
      "Interés: " + f.tipo.value;
    window.open(waLink(text), "_blank", "noopener");
  });
})();
