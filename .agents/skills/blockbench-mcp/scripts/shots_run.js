// Review sheet from node (no codemode): node bbrun.mjs shots_run.js '[{"views":["tp_close","front"],"out":"/path.jpg"}]'
const src = (await tools.bash({command: "cat /Users/ajac-zero/Developer/PowerSuits/.agents/skills/blockbench-mcp/scripts/shots.js"})).output;
return async (opts) => { const shots = new Function("tools", "image", src)(tools, () => {}); return await shots(opts); };
