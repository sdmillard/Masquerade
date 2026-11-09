const { readFileSync, writeFileSync, existsSync } = require("node:fs");
const { generateKeyPairSync, createPublicKey, sign } = require("node:crypto");

const [command, keyPath, input, output] = process.argv.slice(2);
if (command === "init" && keyPath) {
  if (!existsSync(keyPath)) {
    const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 3072 });
    writeFileSync(keyPath, privateKey.export({ type: "pkcs8", format: "pem" }), { flag: "wx" });
  }
  process.stdout.write(createPublicKey(readFileSync(keyPath))
    .export({ type: "spki", format: "der" }).toString("base64"));
} else if (command === "sign" && keyPath && input && output) {
  const payload = readFileSync(input);
  const signature = sign("sha256", payload, readFileSync(keyPath));
  writeFileSync(output, JSON.stringify({
    payload: payload.toString("base64"),
    signature: signature.toString("base64"),
  }) + "\n");
} else {
  throw new Error("Use init <key.pem> or sign <key.pem> <input.json> <output.json>");
}
