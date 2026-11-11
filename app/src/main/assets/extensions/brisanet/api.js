"use strict";

/* global ExtensionAPI, Services */

// Se ejecuta dentro de Gecko (proceso principal). Manda el mismo aviso que Gecko se manda a sí mismo
// cuando detecta que la red ha cambiado: las conexiones HTTP/2 y keep-alive se comprueban y se tiran
// las que ya no responden, y el DNS cifrado se reconfirma (issue #59).
this.brisaNet = class extends ExtensionAPI {
  getAPI() {
    return {
      brisaNet: {
        async linkChanged() {
          Services.obs.notifyObservers(null, "network:link-status-changed", "changed");
          return true;
        },
      },
    };
  }
};
