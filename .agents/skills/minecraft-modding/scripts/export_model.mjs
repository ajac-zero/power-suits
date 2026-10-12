#!/usr/bin/env node
// Re-exports the model, animations and texture from the project open in Blockbench into the mod.
// Requires Blockbench running with its MCP plugin (http://localhost:3000/bb-mcp).
//
//   MOD_ROOT=~/MyMod MOD_ID=mymod NAME=my_entity node export_model.mjs
// (defaults: mod root = cwd, MOD_ID/NAME from env; Power Armor uses powerarmor / power_armor)
//
// Blockbench's own Bedrock exporter is used, so the files match what "File > Export" would give.
// Animation names are renamed from animation.<anything>.* to animation.<NAME>.*.
import fs from 'node:fs';
import path from 'node:path';

const URL_ = process.env.BB_URL || 'http://localhost:3000/bb-mcp';
const ROOT = path.resolve(process.env.MOD_ROOT || process.cwd());
const MOD_ID = process.env.MOD_ID, NAME = process.env.NAME;
if (!MOD_ID || !NAME) throw new Error('set MOD_ID and NAME (e.g. MOD_ID=powerarmor NAME=power_armor)');
const ASSETS = path.join(ROOT, 'src/main/resources/assets', MOD_ID);

let session = null, nextId = 1;
async function rpc(method, params, notify = false) {
  const headers = {'Content-Type': 'application/json', Accept: 'application/json, text/event-stream'};
  if (session) headers['mcp-session-id'] = session;
  const body = {jsonrpc: '2.0', method, params};
  if (!notify) body.id = nextId++;
  const res = await fetch(URL_, {method: 'POST', headers, body: JSON.stringify(body)});
  session = res.headers.get('mcp-session-id') || session;
  if (notify) return null;
  const text = await res.text();
  const msg = (res.headers.get('content-type') || '').includes('event-stream')
    ? text.split('\n').filter(l => l.startsWith('data:')).map(l => JSON.parse(l.slice(5))).find(m => m.id !== undefined)
    : JSON.parse(text);
  if (msg.error) throw new Error(`${method}: ${JSON.stringify(msg.error)}`);
  return msg.result;
}
// Runs an expression inside Blockbench and returns its (string) result.
async function bb(code) {
  const r = await rpc('tools/call', {name: 'risky_eval', arguments: {code}});
  if (r.isError) throw new Error(r.content[0].text);
  return JSON.parse(r.content[0].text);
}

await rpc('initialize', {protocolVersion: '2025-03-26', capabilities: {}, clientInfo: {name: 'powerarmor-export', version: '1'}});
await rpc('notifications/initialized', {}, true);

const project = JSON.parse(await bb(`JSON.stringify({name: Project.name, format: Format.id, saved: Project.saved})`));
if (project.format !== 'bedrock') throw new Error(`Open project "${project.name}" is ${project.format}, expected bedrock`);
if (!project.saved) process.stderr.write(`note: "${project.name}" has unsaved changes; exporting them anyway\n`);

const geo = JSON.parse(await bb(`JSON.stringify(Codecs.bedrock.compile({raw: true}))`));
geo['minecraft:geometry'][0].description.identifier = `geometry.${NAME}`;

const anims = JSON.parse(await bb(`JSON.stringify(Animator.buildFile())`));
anims.animations = Object.fromEntries(Object.entries(anims.animations)
  .map(([k, v]) => [k.replace(/^animation\.[^.]+\./, `animation.${NAME}.`), v]));

const png = (await bb(`Texture.all[0].getDataURL()`)).split(',')[1];

const write = (rel, data) => {
  const file = path.join(ASSETS, rel);
  fs.mkdirSync(path.dirname(file), {recursive: true});
  fs.writeFileSync(file, data);
  process.stdout.write(`wrote ${path.relative(ROOT, file)} (${data.length} bytes)\n`);
};
write(`geo/entity/${NAME}.geo.json`, JSON.stringify(geo, null, 1));
write(`animations/entity/${NAME}.animation.json`, JSON.stringify(anims));
write(`textures/entity/${NAME}.png`, Buffer.from(png, 'base64'));
process.stdout.write(`animations: ${Object.keys(anims.animations).join(', ')}\n`);
