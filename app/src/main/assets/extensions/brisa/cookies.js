// Rechaza los avisos de cookies de los gestores más comunes (OneTrust, Didomi, Cookiebot, Quantcast,
// Sourcepoint, Usercentrics, CookieYes, Complianz, iubenda…). Solo pulsa botones de «rechazar»;
// si no encuentra ninguno, no toca nada.
(async function () {
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
  const CONSENT_FRAME = window.top !== window &&
    /privacy-mgmt|sourcepoint|consensu|didomi|cmp|consent|cookie/i.test(location.hostname + location.pathname);
  const AREA = /cookie|consent|gdpr|cmp|privacy|privacidad|didomi|onetrust|sp_message|banner/i;

  function find() {
    for (const s of SELECTORS) {
      const b = document.querySelector(s);
      if (b && b.offsetParent !== null) return b;
    }
    // Botones con texto de rechazo dentro de un aviso de cookies
    for (const b of document.querySelectorAll("button, a[role='button'], [role='button']")) {
      const t = (b.innerText || b.textContent || "").trim();
      if (t.length > 40 || !TEXT.test(t) || b.offsetParent === null) continue;
      let p = b, ok = false;
      for (let i = 0; i < 8 && p; i++, p = p.parentElement) {
        if (AREA.test((p.id || "") + " " + (p.className && p.className.baseVal === undefined ? p.className : ""))) { ok = true; break; }
      }
      if (ok || CONSENT_FRAME) return b;   // en iframes de consentimiento (Sourcepoint…) basta con el texto
    }
    return null;
  }

  let done = false;
  const tryClick = () => {
    if (done) return;
    const b = find();
    if (b) { done = true; b.click(); obs.disconnect(); }
  };
  const obs = new MutationObserver(tryClick);
  obs.observe(document.documentElement, { childList: true, subtree: true });
  tryClick();
  setTimeout(() => obs.disconnect(), 12000);
})();
