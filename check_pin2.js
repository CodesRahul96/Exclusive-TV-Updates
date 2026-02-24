const https = require("https");
const crypto = require("crypto");

const options = {
  hostname: "raw.githubusercontent.com",
  port: 443,
  method: "GET",
};

const req = https.request(options, (res) => {
  const cert = res.socket.getPeerCertificate(true);
  let currentCert = cert;

  console.log("Certificate Chain Pins for raw.githubusercontent.com:");

  while (currentCert) {
    if (currentCert.pubkey) {
      // SPKI is the public key buffer natively in Node's getPeerCertificate output
      const hash = crypto
        .createHash("sha256")
        .update(currentCert.pubkey)
        .digest("base64");
      console.log(`- CN: ${currentCert.subject.CN}`);
      console.log(`  Pin: sha256/${hash}`);
    } else {
      console.log(
        `Could not extract pubkey for CN: ${currentCert.subject?.CN}`,
      );
    }

    if (
      currentCert.issuerCertificate &&
      currentCert.issuerCertificate !== currentCert
    ) {
      currentCert = currentCert.issuerCertificate;
    } else {
      break;
    }
  }
});

req.on("error", (e) => {
  console.error(e);
});
req.end();
