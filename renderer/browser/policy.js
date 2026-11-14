// The same boundary protects all network requests, including redirects and subresources.
export function allowedRequest(url, mode, ownOrigin = '') {
  let parsed;
  try { parsed = new URL(url); } catch { return false; }
  if (ownOrigin && parsed.href.startsWith(ownOrigin)) return true;
  if (['data:', 'blob:', 'about:'].includes(parsed.protocol)) return true;
  if (!mode) return false;
  if (mode.offline) return parsed.protocol === 'file:' && parsed.href.split('#')[0] === mode.archive;
  return parsed.protocol === 'https:' || parsed.protocol === 'wss:';
}
