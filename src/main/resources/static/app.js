(() => {
  const proto = location.protocol === "https:" ? "wss" : "ws";
  const ws = new WebSocket(`${proto}://${location.host}/ws`);

  const wsLed = document.getElementById("wsLed");
  const wsLabel = document.getElementById("wsLabel");
  const totalConnEl = document.getElementById("totalConn");
  const activeConnEl = document.getElementById("activeConn");
  const eventsBody = document.getElementById("eventsBody");
  const rowCountEl = document.getElementById("rowCount");
  const filterInput = document.getElementById("filterInput");
  const deviceList = document.getElementById("deviceList");
  const clockEl = document.getElementById("clock");

  let history = [];
  let devices = [];

  function setConnected(connected) {
    wsLed.className = "led " + (connected ? "led-on" : "led-off");
    wsLabel.textContent = connected ? "conectado" : "desconectado";
  }

  function updateClock() {
    clockEl.textContent = new Date().toLocaleTimeString("pt-BR");
  }
  setInterval(updateClock, 1000);
  updateClock();

  function fmtTime(iso) {
    try {
      return new Date(iso).toLocaleTimeString("pt-BR");
    } catch {
      return iso || "-";
    }
  }

  function dataCell(ev) {
    if (ev.type === "data") return ev.text ?? `hex:${ev.hex ?? ""}`;
    if (ev.message) return ev.message;
    if (ev.name) return `dispositivo: ${ev.name}`;
    return "-";
  }

  function renderRows() {
    const filter = filterInput.value.trim();
    const filtered = filter
      ? history.filter((ev) => (ev.ip || "").includes(filter))
      : history;

    eventsBody.innerHTML = filtered
      .slice()
      .reverse()
      .map(
        (ev) => `
        <tr>
          <td>${fmtTime(ev.timestamp)}</td>
          <td><span class="tag tag-${ev.type}">${ev.type}</span></td>
          <td>${ev.ip ?? "-"}</td>
          <td>${ev.port ?? "-"}</td>
          <td>${ev.family ?? "-"}</td>
          <td>${dataCell(ev)}</td>
        </tr>`
      )
      .join("");

    rowCountEl.textContent = `${filtered.length} evento${filtered.length === 1 ? "" : "s"}`;
  }

  function renderDevices() {
    if (!devices.length) {
      deviceList.innerHTML = '<p class="empty">Nenhum dispositivo registrado ainda.</p>';
      return;
    }
    deviceList.innerHTML = devices
      .map((d) => {
        const statusClass =
          d.lastPingBackStatus === "ok"
            ? "led-on"
            : d.lastPingBackStatus === "falha"
            ? "led-off"
            : "led-off";
        return `
        <div class="device-card">
          <div class="name"><span class="led ${statusClass}"></span>${d.name}</div>
          <div class="meta">
            ${d.ip}:${d.replyPort} (${d.family})<br/>
            visto: ${fmtTime(d.lastSeen)}<br/>
            ping: ${d.lastPingBackStatus}
          </div>
          <button data-name="${d.name}">Testar ping</button>
        </div>`;
      })
      .join("");

    deviceList.querySelectorAll("button[data-name]").forEach((btn) => {
      btn.addEventListener("click", () => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: "ping_device", name: btn.dataset.name }));
        }
      });
    });
  }

  ws.addEventListener("open", () => setConnected(true));
  ws.addEventListener("close", () => setConnected(false));
  ws.addEventListener("error", () => setConnected(false));

  ws.addEventListener("message", (msg) => {
    const payload = JSON.parse(msg.data);

    if (payload.type === "snapshot") {
      history = payload.history || [];
      totalConnEl.textContent = payload.totalConnections ?? 0;
      activeConnEl.textContent = payload.activeConnections ?? 0;
      renderRows();
      return;
    }

    if (payload.type === "devices_snapshot") {
      devices = payload.devices || [];
      renderDevices();
      return;
    }

    // Evento individual em tempo real (connect/data/disconnect/error/
    // device_register/api_ping/pingback_ok/pingback_fail)
    history.push(payload);
    if (payload.totalConnections !== undefined) totalConnEl.textContent = payload.totalConnections;
    if (payload.activeConnections !== undefined) activeConnEl.textContent = payload.activeConnections;
    renderRows();
  });

  filterInput.addEventListener("input", renderRows);
})();
