#!/usr/bin/env node
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const root = path.resolve(__dirname, '..'), source = path.join(root, 'renderer/ebooks');
const output = path.join(root, 'app/build/generated/ebook-assets');
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const upstream = JSON.parse(fs.readFileSync(path.join(source, 'upstream.json')));
for (const [name, expected] of Object.entries(upstream.files))
  if (sha(fs.readFileSync(path.join(source, name))) !== expected) throw Error('Ebook dependency checksum changed: ' + name);
const walk = directory => fs.readdirSync(directory).flatMap(name => {
  const file = path.join(directory, name); return fs.statSync(file).isDirectory() ? walk(file) : [file];
});
const files = walk(source).sort(), version = fs.readFileSync(path.join(root, 'app/version.properties'));
const fingerprint = sha(Buffer.concat(files.map(file => fs.readFileSync(file)).concat([version, fs.readFileSync(__filename)])));
if (fs.existsSync(path.join(output, '.stamp')) && fs.readFileSync(path.join(output, '.stamp'), 'utf8') === fingerprint) {
  console.log('Offline ebook assets are current.'); process.exit(0);
}
fs.mkdirSync(path.dirname(output), { recursive: true });
const stage = fs.mkdtempSync(path.join(path.dirname(output), '.ebooks-')), extension = path.join(stage, 'ebook-extension');
try {
  fs.cpSync(source, extension, { recursive: true });
  const manifest = JSON.parse(fs.readFileSync(path.join(extension, 'manifest.json')));
  manifest.version = '1.' + /^versionCode=(\d+)$/m.exec(version.toString())[1];
  fs.writeFileSync(path.join(extension, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  const inventory = Object.fromEntries(walk(extension).map(file => [path.relative(stage, file), sha(fs.readFileSync(file))]));
  fs.writeFileSync(path.join(stage, 'ebook-assets.sha256.json'), JSON.stringify(inventory, null, 2) + '\n');
  fs.writeFileSync(path.join(stage, '.stamp'), fingerprint);
  fs.rmSync(output, { recursive: true, force: true }); fs.renameSync(stage, output);
  console.log('Prepared ' + Object.keys(inventory).length + ' offline ebook resources.');
} finally { if (fs.existsSync(stage)) fs.rmSync(stage, { recursive: true, force: true }); }
