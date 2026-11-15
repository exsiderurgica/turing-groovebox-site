// SPDX-License-Identifier: GPL-3.0-only
// Software-only tests of the public Chrome flasher. Never touches a device.
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {inspectFwsc,compatibleDevice} from './fwsc-check.mjs';

const SIZE=609912;
function packageFor(product='FM-1_986',loader=true){
  const arr=new Uint8Array(SIZE);
  for(let i=0;i<20;i++)arr[i*0x30+0x2f]=0x7d;
  for(let i=0;i<product.length;i++)arr[i*0x30+0x2f]=(product.charCodeAt(i)+i+1)&255;
  if(loader)arr.set(new TextEncoder().encode('FELUCCA-LOADER-1'),603232);
  return arr;
}
test('P5.8 shape is accepted without an SHA release manifest',()=>{
  const info=inspectFwsc(packageFor(),'EXS_MT4_P58_ENCODER_FIX.fwsc');
  assert.equal(info.product,'FM-1_986');
  assert.equal(info.size,SIZE);
  assert.equal(info.imageSize,SIZE-20);
});
test('subsequent P5.9 filename is accepted unchanged',()=>{
  assert.equal(inspectFwsc(packageFor(),'EXS_MT4_v030_P59.fwsc').product,'FM-1_986');
});
test('only .fwsc files accepted',()=>{
  assert.throws(()=>inspectFwsc(packageFor(),'test.zip'),/\.fwsc/);
});
test('a short or oversized package is rejected',()=>{
  assert.throws(()=>inspectFwsc(new Uint8Array(960),'x.fwsc'),/Dimensione/);
  assert.throws(()=>inspectFwsc(new Uint8Array(5*1024*1024),'x.fwsc'),/Dimensione/);
});
test('wrong model (FM-2) is refused',()=>{
  assert.throws(()=>inspectFwsc(packageFor('FM-2_986'),'x.fwsc'),/non FM-1/);
});
test('missing or altered loader marker is refused',()=>{
  assert.throws(()=>inspectFwsc(packageFor('FM-1_986',false),'x.fwsc'),/Loader/);
});
test('compatible FM-1 user firmware identity, not pinned version',()=>{
  const candidate=inspectFwsc(packageFor(),'test.fwsc');
  assert.equal(compatibleDevice({text:'FM-1_981',model:'FM-1',version:981},candidate),true);
  assert.equal(compatibleDevice({text:'ota-FM-1_981',model:'ota-FM-1',version:981},candidate),true);
  assert.equal(compatibleDevice({text:'FM-2_981',model:'FM-2',version:981},candidate),false);
  assert.equal(compatibleDevice(null,candidate),false);
});
test('site no longer pins P5.1 or release.json',()=>{
  const app=readFileSync(new URL('./app.mjs',import.meta.url),'utf8');
  const html=readFileSync(new URL('./index.html',import.meta.url),'utf8');
  assert.doesNotMatch(app,/fetch\(['\"]\.\/release\.json|9cba756d32b8a679|function loadRelease/);
  assert.doesNotMatch(html,/P5\.1|disabled>\s*<button.*downloadBtn/);
  assert.match(html,/accept="\.fwsc/);
  assert.match(app,/inspectFwsc\(item\.raw,item\.name\)/);
  assert.match(app,/allowUnverifiedFelucca:false/);
});
