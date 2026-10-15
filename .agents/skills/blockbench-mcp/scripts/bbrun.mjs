// Run a codemode-style function body file with a node `tools` facade (MCP fallback): node bbrun.mjs file.js '[args]'
import * as B from './bb.mjs';
import fs from 'node:fs';
import {execSync} from 'node:child_process';
await B.connect();
const t=new Proxy({},{get:(_,k)=>k==='bash'?(async a=>({output:execSync(a.command,{encoding:'utf8',maxBuffer:1e8})})):k==='write'?(async a=>{fs.writeFileSync(a.path,a.content);return 'ok';}):B.tools[k]});
const fn=await (new (Object.getPrototypeOf(async function(){}).constructor)("tools",fs.readFileSync(process.argv[2],"utf8")))(t);
console.log(await fn(...JSON.parse(process.argv[3]||'[]')));
