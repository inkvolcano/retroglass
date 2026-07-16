#!/usr/bin/env python3
"""Emit a self-contained HTML replica of the redesigned RetroGlass library (console
coverflow) from menu_data.json + consoles.png, so the visual can be perfected without a
device and the exact values ported to the Android MainActivity.

Usage: python scripts/gen_menu_prototype.py menu_data.json out.html
"""
import json, sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
# order like the app: maker, then newest-first
data.sort(key=lambda s: (s["maker"], -s["year"], s["n"]))

# sample games so the list looks populated (PlayStation matches the design mock)
POOL = ["Apex Racer","Dungeon of Echoes","Metal Strike 2","Puzzle Court","Star Fighter Zero",
        "Neon Drift","Crystal Saga","Turbo Blitz","Shadow Keep","Pixel Pilots","Void Runner",
        "Rune Warden","Mega Bounce","Galaxy Siege","Iron Circuit"]
def games_for(i):
    n = 3 + (i % 4)
    return [POOL[(i*3 + j) % len(POOL)] for j in range(n)]

payload = {"systems": data}

html = """<meta charset=utf-8>
<style>
 *{margin:0;padding:0;box-sizing:border-box}
 body{background:#17171b;display:flex;gap:48px;justify-content:center;padding:40px;
   font-family:-apple-system,'Segoe UI',Roboto,sans-serif;min-height:100vh}
 .phone{width:412px;height:892px;background:#0B0B0E;border-radius:38px;overflow:hidden;
   position:relative;box-shadow:0 20px 60px rgba(0,0,0,.6)}
 .cap{position:absolute;top:-26px;left:0;right:0;text-align:center;color:#666;
   font-size:12px;letter-spacing:2px;text-transform:uppercase}
 /* top bar */
 .top{display:flex;align-items:center;padding:14px 14px 8px;position:relative;height:64px}
 .logo{position:absolute;left:0;right:0;top:12px;text-align:center;pointer-events:none}
 .logo .r{font-family:'Courier New',monospace;font-weight:800;color:#EDEDF2;font-size:15px;
   letter-spacing:3px}
 .logo .g{display:inline-block;margin-top:2px;background:#26262c;border:1px solid #3a3a44;
   border-radius:9px;padding:1px 12px;font-size:8px;letter-spacing:3px;color:#EDEDF2}
 .top .sp{flex:1}
 .ic{color:#C7C7D2;font-size:18px;padding:6px 8px;cursor:pointer}
 /* hero */
 .hero{background:#000;padding:6px 0 14px;position:relative}
 .flow{height:210px;display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden}
 .tile{position:absolute;transition:transform .28s cubic-bezier(.22,.61,.36,1),opacity .28s;
   width:230px;height:180px}
 .cimg{width:100%;height:100%;background-image:url('consoles.png');background-repeat:no-repeat;
   background-size:500% 400%;background-position:center}
 .ph{display:flex;align-items:center;justify-content:center;color:#5a5a66;font-size:10px;
   font-weight:700;letter-spacing:1px;text-align:center;
   background:repeating-linear-gradient(45deg,#141419,#141419 7px,#1c1c22 7px,#1c1c22 14px);border-radius:12px}
 .arrow{position:absolute;top:88px;font-size:26px;color:rgba(255,255,255,.55);cursor:pointer;z-index:5;user-select:none}
 .arrow.l{left:8px}.arrow.r{right:8px}
 .title{color:#fff;font-size:26px;font-weight:800;text-align:center;margin-top:2px}
 .meta{color:#9a9aa6;font-size:13px;text-align:center;margin-top:3px}
 .dots{display:flex;gap:5px;justify-content:center;margin-top:10px}
 .dot{width:6px;height:6px;border-radius:6px;background:#3a3a44;transition:all .2s}
 .dot.on{width:18px;background:#2D8CFF}
 /* search + sort */
 .srow{display:flex;gap:10px;padding:12px 14px 8px}
 .search{flex:1;background:#1B1B22;border-radius:14px;padding:11px 14px;color:#8a8a96;font-size:14px;display:flex;align-items:center;gap:8px}
 .sort{background:#1B1B22;border-radius:14px;padding:11px 14px;color:#C7C7D2;font-size:13px;font-weight:700}
 /* games */
 .games{padding:2px 6px}
 .g{display:flex;align-items:center;gap:14px;padding:9px 10px;border-radius:12px}
 .chip{width:46px;height:46px;border-radius:13px;display:flex;align-items:center;justify-content:center;
   color:#fff;font-weight:800;font-size:15px;flex:0 0 auto}
 .gt{flex:1;min-width:0}
 .gn{color:#fff;font-size:16px}
 .gs{color:#8a8a96;font-size:12px;margin-top:1px}
 .play{color:#6a6a76;font-size:15px}
</style>
<div class="phone"><div class="cap">Library</div>
 <div class="top"><div class="logo"><div class="r">RETRO</div><div class="g">GLASS</div></div>
   <div class="sp"></div><div class="ic">&#9881;</div><div class="ic">&#8943;</div></div>
 <div class="hero">
   <div class="flow" id="flow"></div>
   <div class="arrow l" onclick="move(-1)">&#8249;</div>
   <div class="arrow r" onclick="move(1)">&#8250;</div>
   <div class="title" id="title"></div>
   <div class="meta" id="meta"></div>
   <div class="dots" id="dots"></div>
 </div>
 <div class="srow"><div class="search" id="search">&#128269; <span id="sh"></span></div>
   <div class="sort">&#8597; A&#8211;Z</div></div>
 <div class="games" id="games"></div>
</div>
<script>
const DATA = __PAYLOAD__;
const S = DATA.systems;
const POOL = __POOL__;
function gamesFor(i){const n=3+(i%4);const out=[];for(let j=0;j<n;j++)out.push(POOL[(i*3+j)%POOL.length]);return out;}
function initials(name){const stop=new Set(['the','of','and','a','an','de','la','el']);
  const w=name.split(/[\\s:_-]+/).filter(x=>x&&!stop.has(x.toLowerCase()));
  if(!w.length)return name.slice(0,2).toUpperCase();
  if(w.length==1)return w[0].slice(0,2).toUpperCase();
  return (w[0][0]+w[1][0]).toUpperCase();}
function bgpos(img){if(img<0)return null;return (img%5)*25+'% '+(Math.floor(img/5)*100/3).toFixed(3)+'%';}
function lighten(hex){const n=parseInt(hex.slice(1),16);let r=(n>>16)+45,g=((n>>8)&255)+45,b=(n&255)+45;
  r=Math.min(255,r);g=Math.min(255,g);b=Math.min(255,b);return `rgb(${r},${g},${b})`;}
let cur = S.findIndex(s=>s.k=='PSX'); if(cur<0)cur=0;
function tileHTML(s){const p=bgpos(s.img);
  return p? `<div class="cimg" style="background-position:${p}"></div>` :
            `<div class="cimg ph">PHOTO<br>SOON</div>`;}
function render(){
  const flow=document.getElementById('flow');flow.innerHTML='';
  for(let d=-2;d<=2;d++){const i=cur+d;if(i<0||i>=S.length)continue;
    const s=S[i];const el=document.createElement('div');el.className='tile';el.innerHTML=tileHTML(s);
    const scale=d==0?1:(Math.abs(d)==1?0.62:0.42);
    const x=d*118;const op=d==0?1:(Math.abs(d)==1?0.5:0.28);
    el.style.transform=`translateX(${x}px) scale(${scale})`;el.style.opacity=op;el.style.zIndex=10-Math.abs(d);
    el.onclick=()=>{cur=i;render();};flow.appendChild(el);}
  const s=S[cur];
  document.getElementById('title').textContent=s.n;
  const g=gamesFor(cur);
  document.getElementById('meta').textContent=`${s.maker} · ${s.year} · ${g.length} games`;
  document.getElementById('sh').textContent=`Search in ${s.n}...`;
  const dots=document.getElementById('dots');dots.innerHTML='';
  const N=Math.min(S.length,9), start=Math.max(0,Math.min(cur-4,S.length-N));
  for(let k=0;k<N;k++){const dd=document.createElement('div');dd.className='dot'+((start+k)==cur?' on':'');dots.appendChild(dd);}
  const acc=s.accent;const games=document.getElementById('games');games.innerHTML='';
  g.forEach((name,gi)=>{const fav=gi==0;const rec=gi==2;
    const row=document.createElement('div');row.className='g';
    row.innerHTML=`<div class="chip" style="background:linear-gradient(135deg,${lighten(acc)},${acc})">${initials(name)}</div>
      <div class="gt"><div class="gn">${name}</div>${fav?'<div class="gs">★ Favorite · played 2d ago</div>':(rec?'<div class="gs">played 4d ago</div>':'')}</div>
      <div class="play">▶</div>`;
    games.appendChild(row);});
}
function move(d){cur=Math.max(0,Math.min(S.length-1,cur+d));render();}
render();
</script>"""

html = html.replace("__PAYLOAD__", json.dumps(payload)).replace("__POOL__", json.dumps(POOL))
open(sys.argv[2], "w", encoding="utf-8").write(html)
print("wrote", sys.argv[2])
