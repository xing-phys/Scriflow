/* Local component paths: no CDN or network fallback is used by the app. */
window.MathJax = {
  loader: {
    paths: { mathjax: 'mathjax', fonts: 'fonts' },
    load: ['[tex]/physics', '[tex]/mhchem', '[tex]/mathtools', '[tex]/braket', '[tex]/boldsymbol']
  },
  output: {
    font: 'mathjax-newcm', fontPath: 'fonts/mathjax-newcm',
    displayOverflow: 'linebreak', linebreaks: { inline: true, width: '100%' }
  },
  tex: {
    inlineMath: [['\\(', '\\)']], displayMath: [['\\[', '\\]']],
    packages: { '[-]': ['noundefined'], '[+]': ['physics', 'mhchem', 'mathtools', 'braket', 'boldsymbol'] },
    tags: 'ams', processEscapes: true, maxBuffer: 32768, maxTemplateSubstitutions: 1000
  },
  svg: { fontCache: 'none' },
  // These are static appearances; the native editor retains the readable source.
  // Avoid starting the combined component's speech worker and its external assets.
  options: {
    enableMenu: false, enableEnrichment: false, enableComplexity: false,
    enableSpeech: false, enableBraille: false, enableExplorer: false,
    menuOptions: { settings: { enrich: false, speech: false, braille: false, collapsible: false } }
  }, startup: { typeset: false }
};
