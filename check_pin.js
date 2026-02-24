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

  // We want to pin the root or intermediate, usually people pin the leaf or the first intermediate
  // Let's just print the whole chain's pins
  console.log("Certificate Chain Pins for raw.githubusercontent.com:");

  while (currentCert && currentCert.raw) {
    // Calculate the SHA-256 hash of the Subject Public Key Info (SPKI)
    // Note: Node's getPeerCertificate().pubkey is the SPKI in DER format (usually)
    // Wait, Node.js doesn't expose raw SPKI easily natively without x509 module in older versions.
    // Let's use the raw certificate to get the pubkey.

    try {
      const pubKey = crypto.createPublicKey(currentCert.raw.toString("base64"));
      const spkiDer = pubKey.export({ format: "der", type: "spki" });
      const hash = crypto.createHash("sha256").update(spkiDer).digest("base64");
      console.log(`- CN: ${currentCert.subject.CN}`);
      console.log(`  Pin: sha256/${hash}`);
    } catch (e) {
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
