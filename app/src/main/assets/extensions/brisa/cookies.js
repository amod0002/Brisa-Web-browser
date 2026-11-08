// Rechaza los avisos de cookies de los gestores más comunes (OneTrust, Didomi, Cookiebot, Quantcast,
// Sourcepoint, Usercentrics, CookieYes, Complianz, iubenda…). Solo pulsa botones de «rechazar»;
// si no encuentra ninguno, no toca nada.
// Ligero (#48): como mucho una búsqueda cada 400 ms, solo 6 s, sin forzar maquetación y, en iframes,
// solo en los de consentimiento.
(async function () {
  const CONSENT_FRAME = window.top !== window &&
    /privacy-mgmt|sourcepoint|consensu|didomi|cmp|consent|cookie/i.test(location.hostname + location.pathname);
  // Iframes normales (anuncios, vídeos…): nada que hacer
  if (window.top !== window && !CONSENT_FRAME) return;
  try {
    const { cookies } = await browser.storage.local.get("cookies");
    if (cookies === false) return;
  } catch (e) {}

  const SELECTORS = [
    "#onetrust-reject-all-handler", ".ot-pc-refuse-all-handler",
    "#didomi-notice-disagree-button", ".didomi-continue-without-agreeing",
    "#CybotCookiebotDialogBodyButtonDecline", "#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll",
    ".qc-cmp2-summary-buttons button[mode='secondary']:last-child",
    "button[title='Rechazar'], button[title='Rechazar todo'], button[title='Reject all'], button[title='Reject']",
    ".sp_choice_type_REJECT_ALL", ".message-component.sp_choice_type_13",
    "[data-testid='uc-deny-all-button']", "#uc-btn-deny-banner",
    ".cky-btn-reject", ".cmplz-deny", ".iubenda-cs-reject-btn", ".osano-cm-denyAll",
    "#truste-consent-required", ".cc-deny", ".cm-btn-decline", "#axeptio_btn_dismiss",
    ".fc-cta-do-not-consent", "#tarteaucitronAllDenied2", ".t-declineButton", "[data-cookiefirst-action='reject']"
  ];
  const TEXT = /^(rechazar( todo| todas| todas las cookies)?|no aceptar|denegar|solo (las )?necesarias|reject( all)?|decline( all)?|refuse( all)?|only necessary|necessary only)$/i;
  // Contenedores que parecen un aviso de cookies (por id o clase)
  const AREA_SEL = ["cookie", "consent", "gdpr", "cmp", "privacy", "privacidad", "didomi", "onetrust", "sp_message", "banner"]
    .map(w => `[id*='${w}' i], [class*='${w}' i]`).join(", ");
  const BUTTONS = "button, a[role='button'], [role='button']";
  const visible = b => b.getClientRects().length > 0;

  function byText(root) {
    for (const b of root.querySelectorAll(BUTTONS)) {
      const t = (b.textContent || "").trim();   // textContent: no obliga a recalcular la maquetación
      if (t.length <= 40 && TEXT.test(t) && visible(b)) return b;
    }
    return null;
  }

  function find() {
    // 1) Selectores conocidos: baratos
    for (const s of SELECTORS) {
      const b = document.querySelector(s);
      if (b && visible(b)) return b;
    }
    // 2) Por texto: en iframes de consentimiento, en todo el documento; si no, solo dentro de lo que parezca un aviso
    if (CONSENT_FRAME) return byText(document);
    const areas = document.querySelectorAll(AREA_SEL);
    for (let i = 0; i < areas.length && i < 20; i++) {
      const b = byText(areas[i]);
      if (b) return b;
    }
    return null;
  }

  let done = false, timer = 0;
  const tryClick = () => {
    timer = 0;
    if (done) return;
    const b = find();
    if (b) { done = true; b.click(); obs.disconnect(); }
  };
  // Agrupa los cambios: como mucho una búsqueda cada 400 ms
  const obs = new MutationObserver(() => { if (!timer && !done) timer = setTimeout(tryClick, 400); });
  obs.observe(document.documentElement, { childList: true, subtree: true });
  tryClick();
  setTimeout(() => { obs.disconnect(); if (timer) clearTimeout(timer); }, 6000);
})();
