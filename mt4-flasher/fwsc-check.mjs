// SPDX-License-Identifier: GPL-3.0-only
// EXS MT4: generic local FWSC preflight, no pinned release/version/hash.
// Not an authenticity guarantee; users must provide a trusted FM1 MT4 image.
import {productOf, logicalImage} from '../fm1-test-a03/fm1pkg.js';

const FM1_PRODUCT = /^FM-1_9[0-9]{2}$/;
const LOADER = new TextEncoder().encode('FELUCCA-LOADER-1');
export const MAX_BYTES = 4 * 1024 * 1024;
export const MIN_BYTES = 300000;

function containsBytes(haystack, needle) {
  for (let i = 0, n = haystack.length - needle.length; i <= n; i++) {
    if (haystack[i] !== needle[0]) continue;
    let j = 1;
    for (; j < needle.length && haystack[i + j] === needle[j]; ++j) {}
    if (j === needle.length) return true;
  }
  return false;
}
export function inspectFwsc(data, filename) {
  if (!(data instanceof Uint8Array)) throw new Error('Il file deve essere una sequenza di byte.');
  if (!/\.fwsc$/i.test(filename || '')) throw new Error('Scegli un file .fwsc (non ZIP/BIN).');
  if (data.length < MIN_BYTES || data.length > MAX_BYTES)
    throw new Error('Dimensione non compatibile con un firmware MT4/Felucca.');
  const product = productOf(data);
  if (!FM1_PRODUCT.test(product))
    throw new Error('Identità firmware non FM-1 Felucca 9xx: ' + JSON.stringify(product));
  if (!containsBytes(data, LOADER))
    throw new Error('Loader Felucca mancante: non caricare pacchetti non riconosciuti.');
  const image = logicalImage(data);
  if (image.length !== data.length - 20 || image.length < MIN_BYTES - 20)
    throw new Error('Immagine firmware incompleta.');
  return {product, model: 'FM-1', size: data.length, imageSize: image.length};
}
export function deviceModel(identity) {
  return (identity?.model || '').replace(/^ota-/i, '');
}
export function compatibleDevice(identity, candidate) {
  return !!identity && !!candidate && deviceModel(identity) === candidate.model;
}
