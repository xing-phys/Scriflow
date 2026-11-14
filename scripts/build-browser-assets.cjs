#!/usr/bin/env node
const fs = require('fs'), path = require('path'), crypto = require('crypto'), cp = require('child_process');
const root = path.resolve(__dirname, '..'), source = path.join(root, 'renderer/browser');
const install = path.join(root, '.tools/browser-capture'), output = path.join(root, 'app/build/generated/browser-assets');
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const inputs = ['package.json', 'package-lock.json', 'manifest.json', 'background.js', 'bootstrap.js', 'bootstrap.html', 'content.js', 'capture.js', 'policy.js'];
const version = fs.readFileSync(path.join(root, 'app/version.properties'));
const fingerprint = sha(Buffer.concat(inputs.map(name => fs.readFileSync(path.join(source, name))).concat([fs.readFileSync(__filename), version])));
const stamp = path.join(output, '.stamp');
if (fs.existsSync(stamp) && fs.readFileSync(stamp, 'utf8') === fingerprint) { console.log('Browser capture assets are current.'); process.exit(0); }
fs.mkdirSync(install, { recursive: true });
const lock = sha(fs.readFileSync(path.join(source, 'package-lock.json'))), installed = path.join(install, '.installed-lock');
if (!fs.existsSync(installed) || fs.readFileSync(installed, 'utf8') !== lock) {
  for (const name of ['package.json', 'package-lock.json']) fs.copyFileSync(path.join(source, name), path.join(install, name));
  cp.execFileSync('npm', ['ci', '--ignore-scripts', '--no-audit', '--no-fund'], { cwd: install, stdio: 'inherit' });
  fs.writeFileSync(installed, lock);
}
fs.mkdirSync(path.dirname(output), { recursive: true });
const stage = fs.mkdtempSync(path.join(path.dirname(output), '.browser-')), assets = path.join(stage, 'browser-extension');
fs.mkdirSync(assets);
const modules = path.join(install, 'node_modules'), esbuild = require(path.join(modules, 'esbuild'));
const options = { bundle: true, platform: 'browser', format: 'iife', target: 'firefox140', minify: true, legalComments: 'eof',
  nodePaths: [modules], define: { 'process.env.NODE_ENV': '"production"' } };
try {
  for (const name of ['background', 'bootstrap', 'content'])
    esbuild.buildSync({ ...options, entryPoints: [path.join(source, name + '.js')], outfile: path.join(assets, name + '.js') });
  for (const name of ['manifest.json', 'bootstrap.html']) fs.copyFileSync(path.join(source, name), path.join(assets, name));
  const manifest = JSON.parse(fs.readFileSync(path.join(assets, 'manifest.json')));
  manifest.version = '1.' + /^versionCode=(\d+)$/m.exec(version.toString())[1];
  fs.writeFileSync(path.join(assets, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  const test = path.join(root, 'build/browser-test-assets'); fs.mkdirSync(test, { recursive: true });
  esbuild.buildSync({ ...options, entryPoints: [path.join(source, 'capture.js')], globalName: 'ScriflowCapture', outfile: path.join(test, 'capture.js') });
  esbuild.buildSync({ ...options, entryPoints: [path.join(source, 'policy.js')], format: 'cjs', platform: 'node', outfile: path.join(test, 'policy.cjs') });
  const notices = ['Scriflow browser capture code is covered by the project MIT license.\n'];
  const packages = JSON.parse(fs.readFileSync(path.join(source, 'package-lock.json'))).packages;
  for (const [relative, entry] of Object.entries(packages)) {
    if (!relative || entry.dev) continue;
    const directory = path.join(install, relative), info = JSON.parse(fs.readFileSync(path.join(directory, 'package.json')));
    notices.push(info.name + ' ' + info.version + '\nLicense: ' + info.license + '\n');
    for (const name of fs.readdirSync(directory).filter(name => /^(license|licence|copying|notice)(\.|$)/i.test(name)))
      if (fs.statSync(path.join(directory, name)).isFile()) notices.push(fs.readFileSync(path.join(directory, name), 'utf8'));
    notices.push('\n------------------------------------------------------------\n');
  }
  fs.writeFileSync(path.join(stage, 'browser-NOTICES.txt'), notices.join('\n'));
  const inventory = {};
  for (const name of fs.readdirSync(assets)) inventory['browser-extension/' + name] = sha(fs.readFileSync(path.join(assets, name)));
  inventory['browser-NOTICES.txt'] = sha(fs.readFileSync(path.join(stage, 'browser-NOTICES.txt')));
  fs.writeFileSync(path.join(stage, 'browser-assets.sha256.json'), JSON.stringify(inventory, null, 2) + '\n');
  fs.writeFileSync(path.join(stage, '.stamp'), fingerprint);
  fs.rmSync(output, { recursive: true, force: true }); fs.renameSync(stage, output);
  console.log('Prepared ' + Object.keys(inventory).length + ' pinned browser capture resources.');
} finally { if (fs.existsSync(stage)) fs.rmSync(stage, { recursive: true, force: true }); }
