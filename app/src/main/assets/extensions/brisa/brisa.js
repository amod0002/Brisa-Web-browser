// Puente entre cada página y la app: icono de la web, si se puede leer en modo lectura, y el propio modo lectura.
(function () {
  if (window.top !== window) return;
  const port = browser.runtime.connectNative("brisa");

  function bestIcon() {
    const links = [...document.querySelectorAll("link[rel~='icon'], link[rel='apple-touch-icon'], link[rel='apple-touch-icon-precomposed']")];
    const size = l => parseInt((l.getAttribute("sizes") || "0").split("x")[0]) || (l.rel.includes("apple") ? 180 : 16);
    links.sort((a, b) => size(b) - size(a));
    return links.length ? links[0].href : location.origin + "/favicon.ico";
  }

  // Icono enseguida; la comprobación de modo lectura cuando el móvil esté libre, para no frenar la carga.
  port.postMessage({ type: "page", url: location.href, icon: bestIcon(), readerable: false });
  const check = () => {
    let readerable = false;
    try { readerable = isProbablyReaderable(document); } catch (e) {}
    if (readerable) port.postMessage({ type: "page", url: location.href, icon: bestIcon(), readerable });
  };
  if ("requestIdleCallback" in window) requestIdleCallback(check, { timeout: 3000 }); else setTimeout(check, 1500);

  port.onMessage.addListener(msg => {
    if (msg.action !== "reader") return;
    const art = new Readability(document.cloneNode(true)).parse();
    if (!art) { port.postMessage({ type: "reader", ok: false }); return; }
    const dark = matchMedia("(prefers-color-scheme: dark)").matches;
    const fg = dark ? "#e8e6e1" : "#1d1d1f", bg = dark ? "#1c1c1e" : "#fbfaf7", sub = dark ? "#9a9a9e" : "#6e6e73";
    const html = document.documentElement;
    html.innerHTML = "<head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body></body>";
    const style = document.createElement("style");
    style.textContent = `
      body{margin:0;padding:28px 22px 120px;background:${bg};color:${fg};font:19px/1.65 Georgia,'Noto Serif',serif}
      h1.t{font:600 28px/1.25 system-ui,sans-serif;margin:0 0 8px}
      .m{font:14px system-ui,sans-serif;color:${sub};margin-bottom:26px}
      img,video,figure{max-width:100%;height:auto;border-radius:10px}
      a{color:#0a84ff} pre{white-space:pre-wrap;font-size:15px}
      blockquote{border-left:3px solid ${sub};margin:0;padding-left:14px;color:${sub}}`;
    document.head.appendChild(style);
    const h = document.createElement("h1"); h.className = "t"; h.textContent = art.title || document.title;
    const m = document.createElement("div"); m.className = "m";
    const mins = Math.max(1, Math.round((art.length || 0) / 1100));
    m.textContent = [art.byline, art.siteName, mins + " min de lectura"].filter(Boolean).join(" · ");
    const body = document.createElement("div"); body.innerHTML = art.content;
    document.body.append(h, m, body);
    window.scrollTo(0, 0);
    port.postMessage({ type: "reader", ok: true });
  });
})();
