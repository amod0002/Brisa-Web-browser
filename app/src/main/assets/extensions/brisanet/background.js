"use strict";

// Brisa avisa por aquí cuando cambia la red por defecto de Android (p. ej. al quitar la VPN).
const port = browser.runtime.connectNative("brisanet");
port.onMessage.addListener((m) => {
  if (m && m.action === "changed") {
    browser.brisaNet.linkChanged().catch((e) => console.error("brisanet", e));
  }
});
