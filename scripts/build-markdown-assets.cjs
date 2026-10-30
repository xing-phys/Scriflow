/* Build-only dependencies are locked; the APK uses only these local generated assets. */
const fs = require('fs'), path = require('path'), crypto = require('crypto'), cp = require('child_process');
const root = path.resolve(__dirname, '..'), source = path.join(root, 'renderer/markdown');
const install = path.join(root, '.tools/markdown-renderer'), output = path.join(root, 'app/build/generated/markdown-assets');
const sha = data => crypto.createHash('sha256').update(data).digest('hex');
const inputs = ['package.json', 'package-lock.json', 'fonts.json', 'bootstrap.js', 'index.html', 'renderer.css', 'renderer.js'];
async function main() {
  if (+process.versions.node.split('.')[0] < 20) throw new Error('Markdown assets require Node.js 20 or newer');
  const key = sha(Buffer.concat(inputs.map(name => fs.readFileSync(path.join(source, name)))
    .concat([fs.readFileSync(__filename)])));
  const marker = path.join(output, '.stamp');
  if (fs.existsSync(marker) && fs.readFileSync(marker, 'utf8') === key) { console.log('Markdown assets are current.'); return; }
  fs.mkdirSync(install, {recursive:true});
  const lock = sha(fs.readFileSync(path.join(source, 'package-lock.json'))), lockMarker = path.join(install, '.installed-lock');
  if (!fs.existsSync(lockMarker) || fs.readFileSync(lockMarker, 'utf8') !== lock) {
    for (const name of ['package.json','package-lock.json']) fs.copyFileSync(path.join(source,name),path.join(install,name));
    cp.execFileSync('npm',['ci','--ignore-scripts','--no-audit','--no-fund'],{cwd:install,stdio:'inherit'});
    fs.writeFileSync(lockMarker,lock);
  }
  const modules = path.join(install,'node_modules'), esbuild = require(path.join(modules,'esbuild'));
  const fonts = path.join(install,'fonts'); fs.mkdirSync(fonts,{recursive:true});
  const fontManifest = JSON.parse(fs.readFileSync(path.join(source,'fonts.json'),'utf8'));
  for (const entry of fontManifest) {
    const file = path.join(fonts,entry.name);
    if (!fs.existsSync(file) || sha(fs.readFileSync(file)) !== entry.sha256) {
      console.log('Fetching pinned font resource:',entry.name);
      const response = await fetch(entry.url); if (!response.ok) throw new Error(`Cannot fetch ${entry.name}: ${response.status}`);
      const data = Buffer.from(await response.arrayBuffer());
      if (sha(data) !== entry.sha256) throw new Error(`Font checksum differs: ${entry.name}`);
      fs.writeFileSync(file,data);
    }
  }
  fs.mkdirSync(path.dirname(output),{recursive:true});
  const stage = fs.mkdtempSync(path.join(path.dirname(output),'.markdown-')), assets = path.join(stage,'markdown');
  fs.mkdirSync(assets,{recursive:true});
  const build = {bundle:true,platform:'browser',format:'iife',target:'chrome70',minify:true,legalComments:'eof',logLevel:'warning'};
  try {
    esbuild.buildSync({...build,stdin:{contents:'import "core-js/stable";',resolveDir:install},outfile:path.join(assets,'polyfills.js')});
    // texmath's parser uses our asynchronous MathJax adapter; its default KaTeX is unused.
    const stub = path.join(install,'unused-katex.js'); fs.writeFileSync(stub,'exports.renderToString=function(){throw Error("MathJax engine required")};');
    esbuild.buildSync({...build,entryPoints:[path.join(source,'renderer.js')],nodePaths:[modules],
      alias:{katex:stub},define:{'import.meta.url':'location.href'},outfile:path.join(assets,'renderer.js')});
    for (const name of ['index.html','bootstrap.js','renderer.css']) fs.copyFileSync(path.join(source,name),path.join(assets,name));
    const transform = (from,to) => {
      fs.mkdirSync(path.dirname(to),{recursive:true});
      fs.writeFileSync(to,esbuild.transformSync(fs.readFileSync(from,'utf8'),{target:'chrome70',minify:true,legalComments:'eof'}).code);
    };
    transform(path.join(modules,'mathjax/tex-mml-svg.js'),path.join(assets,'mathjax/tex-mml-svg.js'));
    for (const name of fs.readdirSync(path.join(modules,'mathjax/input/tex/extensions')))
      if (name.endsWith('.js')) transform(path.join(modules,'mathjax/input/tex/extensions',name),path.join(assets,'mathjax/input/tex/extensions',name));
    const mathFonts = path.join(modules,'@mathjax/mathjax-newcm-font');
    transform(path.join(mathFonts,'svg.js'),path.join(assets,'fonts/mathjax-newcm/svg.js'));
    for (const name of fs.readdirSync(path.join(mathFonts,'svg/dynamic')))
      if (name.endsWith('.js')) transform(path.join(mathFonts,'svg/dynamic',name),path.join(assets,'fonts/mathjax-newcm/svg/dynamic',name));
    for (const name of ['mhchem','bbm','bboldx','dsfont']) {
      const folder = `mathjax-${name}-font-extension`;
      transform(path.join(modules,'@mathjax',folder,'svg.js'),path.join(assets,'fonts',folder,'svg.js'));
    }
    transform(path.join(modules,'pdfmake/build/pdfmake.min.js'),path.join(assets,'pdfmake.js'));
    const vfs = require(path.join(modules,'pdfmake/build/vfs_fonts.js'));
    for (const [name,base64] of Object.entries(vfs)) fs.writeFileSync(path.join(assets,'fonts',name),Buffer.from(base64,'base64'));
    for (const name of ['Code-Regular.ttf','Code-Bold.ttf']) vfs[name] = fs.readFileSync(path.join(fonts,name)).toString('base64');
    fs.writeFileSync(path.join(assets,'font-vfs.js'),'window.ScriflowFonts='+JSON.stringify(vfs)+';pdfMake.addVirtualFileSystem(window.ScriflowFonts);\n');
    for (const entry of fontManifest) fs.copyFileSync(path.join(fonts,entry.name),path.join(assets,'fonts',entry.name));
    const notices = [];
    const packages = JSON.parse(fs.readFileSync(path.join(source,'package-lock.json'),'utf8')).packages;
    for (const [relative,entry] of Object.entries(packages)) {
      if (!relative || entry.dev) continue;
      const directory = path.join(install,relative), info = JSON.parse(fs.readFileSync(path.join(directory,'package.json'),'utf8'));
      notices.push(`${info.name} ${info.version}\nLicense: ${info.license || 'see included text'}\n`);
      for (const name of fs.readdirSync(directory).filter(n => /^(licen[sc]e|copying|notice|ofl)(\.|$)/i.test(n)))
        if (fs.statSync(path.join(directory,name)).isFile()) notices.push(fs.readFileSync(path.join(directory,name),'utf8'));
      notices.push('\n------------------------------------------------------------\n');
    }
    for (const entry of fontManifest.filter(f=>f.name.endsWith('.txt'))) notices.push(entry.name+'\n'+fs.readFileSync(path.join(fonts,entry.name),'utf8'));
    notices.push('Roboto font data is distributed with pdfmake under Apache-2.0.\nCopyright 2011 Google Inc.\n');
    fs.writeFileSync(path.join(stage,'markdown-NOTICES.txt'),notices.join('\n'));
    const manifest = {};
    function inventory(directory) { for (const name of fs.readdirSync(directory)) {
      const file = path.join(directory,name); if (fs.statSync(file).isDirectory()) inventory(file);
      else manifest[path.relative(stage,file)] = sha(fs.readFileSync(file));
    }}
    inventory(stage);fs.writeFileSync(path.join(stage,'markdown-assets.sha256.json'),JSON.stringify(manifest,null,2)+'\n');
    fs.writeFileSync(path.join(stage,'.stamp'),key);
    fs.rmSync(output,{recursive:true,force:true});fs.renameSync(stage,output);
    console.log(`Prepared ${Object.keys(manifest).length} pinned, offline Markdown resources.`);
  } finally { if (fs.existsSync(stage)) fs.rmSync(stage,{recursive:true,force:true}); }
}
main().catch(error=>{console.error(error);process.exitCode=1});
