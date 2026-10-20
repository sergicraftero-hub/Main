(function () {
  var C = window.CONFIG;

  // Textos configurables
  document.querySelectorAll("[data-cfg]").forEach(function (el) {
    var v = C[el.getAttribute("data-cfg")];
    if (v != null) el.textContent = v;
  });
  var meses = ["enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"];
  document.querySelectorAll("[data-month]").forEach(function (el) { el.textContent = meses[new Date().getMonth()]; });

  // Enlaces de contacto
  function waLink(text) { return "https://wa.me/" + C.whatsapp + "?text=" + encodeURIComponent(text); }
  function openWa(text) { window.open(waLink(text), "_blank", "noopener"); }
  document.querySelectorAll(".js-wa").forEach(function (a) {
    a.href = waLink("Hola, me interesa una alarma ADT. ¿Me podéis informar?");
    a.target = "_blank";
    a.rel = "noopener";
  });
  document.querySelectorAll(".js-tel").forEach(function (a) { a.href = "tel:" + C.telefono; });

  function validPhone(v) { return /^(\+?34)?[6789]\d{8}$/.test(v.replace(/[\s.-]/g, "")); }

  // "Llámame gratis"
  document.querySelectorAll(".callme").forEach(function (form) {
    var msg = form.parentNode.querySelector(".form-msg");
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var tel = form.elements.telefono.value.trim();
      if (!validPhone(tel)) {
        msg.textContent = "Introduce un teléfono válido de 9 cifras.";
        msg.className = "form-msg error";
        msg.hidden = false;
        form.elements.telefono.focus();
        return;
      }
      msg.hidden = true;
      openWa("Hola, quiero que me llaméis para informarme de las alarmas ADT.\nTeléfono: " + tel);
    });
  });

  // ============ Calculadora ============
  var Q = {
    tipo:      { phase: 0, q: "¿La alarma es para una vivienda o un negocio?", o: [["Vivienda", "uso"], ["Negocio", "negocio"]] },
    uso:       { phase: 0, q: "¿Es tu vivienda habitual o una segunda vivienda?", o: [["Vivienda habitual", "como"], ["Segunda vivienda", "como"]] },
    como:      { phase: 0, q: "¿Cómo es tu vivienda?", o: [["Piso", "accesos"], ["Piso bajo o ático", "accesos"], ["Chalet o casa", "accesos"]] },
    accesos:   { phase: 1, q: "¿Tiene jardín, terraza o balcón?", o: [["Sí", "cp"], ["No", "cp"]] },
    negocio:   { phase: 0, q: "¿Cómo es tu negocio?", o: [["Local a pie de calle", "cp"], ["Local en centro comercial", "cp"], ["Nave industrial", "cp"], ["Oficina", "cp"]] },
    cp:        { phase: 1, q: "¿Cuál es el código postal?", input: "cp" },
    fin:       { phase: 2, q: "Tu estudio está listo", input: "fin" }
  };
  var order = ["tipo","uso","como","negocio","accesos","cp","fin"];

  var calc = document.getElementById("calc"), qEl = document.getElementById("calc-q"),
      opts = calc.querySelector(".calc-opts"), back = calc.querySelector(".calc-back"),
      bar = calc.querySelector(".calc-bar i"), phases = calc.querySelectorAll("[data-phase]");
  var answers, history, lastFocus;

  function kit(a) {
    if (a.tipo === "Negocio") return { name: "Kit Negocio", desc: "Detectores con cámara, botón de atraco y videovigilancia opcional, adaptado a tu local.", img: "img/camara.webp" };
    var risk = 0;
    if (a.como === "Chalet o casa") risk += 2;
    if (a.como === "Piso bajo o ático") risk += 1;
    if (a.uso === "Segunda vivienda") risk += 2;
    if (a.accesos === "Sí") risk += 1;
    if (risk >= 3) return { name: "Kit Advance", desc: "5 detectores, 3 de ellos con cámara, para cubrir todos los accesos.", img: "img/bodegon.png" };
    if (risk >= 1) return { name: "Kit Standard", desc: "4 detectores, 2 de ellos con cámara, para viviendas con varios accesos.", img: "img/fotodetector.webp" };
    return { name: "Kit Basic", desc: "3 detectores, 1 de ellos con cámara. Ideal para pisos.", img: "img/panel.webp" };
  }

  function render(id) {
    var s = Q[id];
    qEl.textContent = typeof s.q === "function" ? s.q(answers) : s.q;
    opts.innerHTML = "";
    opts.className = "calc-opts";
    back.hidden = history.length === 0;
    phases.forEach(function (p) { p.classList.toggle("on", +p.getAttribute("data-phase") <= s.phase); });
    var idx = order.indexOf(id);
    bar.style.width = Math.max(8, Math.round(idx / (order.length - 1) * 100)) + "%";

    if (s.input === "cp") {
      opts.className = "calc-opts calc-form";
      opts.innerHTML = '<input type="text" inputmode="numeric" maxlength="5" placeholder="Ej. 28001" aria-label="Código postal" value="' + (answers.cp || "") + '">' +
        '<p class="form-msg error" hidden></p><button class="btn btn-primary btn-lg" type="button">Continuar</button>';
      var inp = opts.querySelector("input"), err = opts.querySelector(".form-msg");
      function go() {
        if (!/^\d{5}$/.test(inp.value)) { err.textContent = "Introduce un código postal de 5 cifras."; err.hidden = false; return; }
        answers.cp = inp.value; next(id, "fin");
      }
      opts.querySelector("button").onclick = go;
      inp.onkeydown = function (e) { if (e.key === "Enter") go(); };
      inp.focus();
      return;
    }
    if (s.input === "fin") {
      var k = kit(answers);
      bar.style.width = "100%";
      opts.className = "calc-opts calc-form";
      opts.innerHTML =
        '<div class="result"><img src="' + k.img + '" alt=""><div><small>Te recomendamos</small><strong>' + k.name + '</strong><p>' + k.desc + '</p>' +
        (answers.tipo === "Negocio" ? '<p class="result-price">Precio a medida</p>' : '<p class="result-price">' + C.ofertaPrecio + ' €/mes <span>los ' + C.ofertaMeses + ' primeros meses, después desde ' + C.precioDespues + ' €/mes</span></p>') +
        '</div></div>' +
        '<p class="calc-note">Déjanos tus datos y un asesor te confirma el presupuesto sin compromiso.</p>' +
        '<input type="text" name="nombre" autocomplete="name" placeholder="Nombre">' +
        '<input type="tel" name="telefono" inputmode="tel" autocomplete="tel" placeholder="Teléfono">' +
        '<label class="check"><input type="checkbox" name="priv"> <span>Acepto la <a href="privacidad.html" target="_blank">política de privacidad</a> y que me contactéis.</span></label>' +
        '<p class="form-msg error" hidden></p>' +
        '<button class="btn btn-primary btn-lg" type="button">Quiero mi presupuesto</button>';
      var nom = opts.querySelector("[name=nombre]"), tel = opts.querySelector("[name=telefono]"),
          priv = opts.querySelector("[name=priv]"), e2 = opts.querySelector(".form-msg");
      opts.querySelector("button").onclick = function () {
        var m = "";
        if (!nom.value.trim()) m = "Escribe tu nombre.";
        else if (!validPhone(tel.value)) m = "Introduce un teléfono válido de 9 cifras.";
        else if (!priv.checked) m = "Debes aceptar la política de privacidad.";
        if (m) { e2.textContent = m; e2.hidden = false; return; }
        var lines = ["Hola, he hecho el estudio de seguridad en la web.", "Nombre: " + nom.value.trim(), "Teléfono: " + tel.value.trim(), "Recomendación: " + k.name];
        order.forEach(function (key) { if (answers[key] && Q[key].o) lines.push((typeof Q[key].q === "function" ? Q[key].q(answers) : Q[key].q) + " " + answers[key]); });
        lines.push("C.P.: " + answers.cp);
        openWa(lines.join("\n"));
      };
      nom.focus();
      return;
    }
    var list = typeof s.o === "function" ? s.o(answers) : s.o;
    list.forEach(function (o) {
      var b = document.createElement("button");
      b.type = "button";
      b.className = "opt" + (answers[id] === o[0] ? " sel" : "");
      b.innerHTML = "<span>" + o[0] + '</span><svg><use href="#i-arrow"/></svg>';
      b.onclick = function () { answers[id] = o[0]; next(id, o[1]); };
      opts.appendChild(b);
    });
    var first = opts.querySelector(".opt");
    if (first) first.focus();
  }
  function next(from, to) { history.push(from); render(to); }
  back.onclick = function () { render(history.pop()); };

  function open() {
    lastFocus = document.activeElement;
    answers = {}; history = [];
    calc.hidden = false;
    document.body.classList.add("noscroll");
    render("tipo");
  }
  function close() {
    calc.hidden = true;
    document.body.classList.remove("noscroll");
    if (lastFocus) lastFocus.focus();
  }
  document.querySelectorAll(".js-open-calc").forEach(function (b) { b.addEventListener("click", open); });
  calc.querySelector(".calc-close").onclick = close;
  calc.addEventListener("click", function (e) { if (e.target === calc) close(); });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !calc.hidden) close(); });
})();
