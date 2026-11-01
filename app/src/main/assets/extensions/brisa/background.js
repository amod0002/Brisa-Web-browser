// Recibe los ajustes de la app, los deja en storage para las páginas y aplica la protección de WebRTC.
const port = browser.runtime.connectNative("brisa");
port.onMessage.addListener(async msg => {
  if (msg.action !== "settings") return;
  await browser.storage.local.set({ cookies: !!msg.cookies });
  try {
    // Sin protección: comportamiento normal. Con protección: WebRTC no revela la IP local ni sale fuera de la VPN.
    await browser.privacy.network.webRTCIPHandlingPolicy.set({
      value: msg.webrtc ? "disable_non_proxied_udp" : "default"
    });
  } catch (e) { /* API no disponible en esta versión */ }
});
