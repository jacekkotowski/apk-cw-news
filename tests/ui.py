# Drives the page in Chromium with a fake Android bridge. Run: python3 tests/ui.py
import json, pathlib
from playwright.sync_api import sync_playwright
FAKE = """
window.calls=[];window.fake={status:'stopped',title:'',source:'',word:0,words:0,error:'',id:'-1',refreshing:false,news:{state:'no_feeds',failed:0}};
window.fs=[];window.st={wpm:20,eff:12,pitch:600,volume:80,mode:0,abbr:1,band:1,activity:1,bed:1,bedDb:-24,width:1,agc:1,span:40,region:0,auto:1,announce:1,age:0,refresh:30,follow:0,sleep:0,side:0};
window.Android={state:()=>JSON.stringify(fake),settings:()=>JSON.stringify(st),feeds:()=>JSON.stringify(fs),
recent:()=>JSON.stringify([{id:'5',title:'<b>Not markup</b>',source:'HN',heard:false}]),
set:(k,v)=>{calls.push(['set',k,v]);st[k]=v;if(k==='wpm'&&st.eff>v)st.eff=v;},command:c=>calls.push([c]),playStory:i=>calls.push(['story',i]),
addFeed:(u,n)=>{if(!u.startsWith('https://'))return 'The address must start with https://';fs.push({id:'1',url:u,name:'',custom:false,enabled:true,lastOk:0,error:'HTTP 500',count:0});return '';},
editFeed:()=>'',setFeedEnabled:(i,o)=>{calls.push(['en',i,o]);fs[0].enabled=o;},removeFeed:i=>{calls.push(['rm',i]);fs.length=0;},refresh:()=>calls.push(['refresh']),regions:()=>JSON.stringify(['Europe','North America, east']),
band:()=>JSON.stringify({on:true,dial:window.dialV||7020,rit:window.ritV||0,passLo:-200,passHi:200,low:7000,high:7040,news:7020,contest:'CQ WW CW',act:[{k:7012.3,l:.8,on:true},{k:7031.1,l:.3,on:false}]}),
panel:()=>JSON.stringify({s:0.3,n:1,w:[[7012.3,.8,1],[7031.1,.3,0]]}),setRit:v=>{window.ritV=v;calls.push(['rit',v]);},tick:()=>calls.push(['tick']),bandCopy:()=>'DL1ABC: CQ CQ',setDial:v=>{window.dialV=v;calls.push(['dial',v]);},nextSignal:d=>{calls.push(['next',d]);return 7012.3;},abbrevs:()=>JSON.stringify([{a:'ES',m:'and',l:1},{a:'CQ',m:'calling any station',l:0}])};
"""
page_url = pathlib.Path('app/assets/index.html').resolve().as_uri()
with sync_playwright() as p:
    b = p.chromium.launch(); g = b.new_page(viewport={'width': 360, 'height': 740}); errs = []
    g.on('pageerror', lambda e: errs.append(str(e))); g.add_init_script(FAKE); g.goto(page_url); g.wait_for_timeout(200)
    calls = lambda: g.evaluate('calls')
    assert 'No feeds yet' in g.inner_text('#msg')
    for s in ['#toggle','#next','#prev','#stop','#replay','#refresh']: g.click(s)
    assert [c[0] for c in calls()] == ['toggle','next','prev','stop','replay','refresh'], calls()
    assert g.locator('.story b').count() == 0 and '<b>' in g.inner_text('.story')
    g.click('.story'); assert calls()[-1] == ['story','5']
    g.evaluate("fake.status='playing';fake.title='Hello';fake.source='HN';fake.word=1;fake.words=4;fake.news={state:'all_heard',failed:0}"); g.wait_for_timeout(700)
    assert g.inner_text('#head') == 'Hello' and 'All stories heard' in g.inner_text('#msg')
    g.screenshot(path='out_test/ui_listen.png')
    g.click('nav button[data-t=feeds]'); g.fill('#url','http://x'); g.click('#add'); assert 'https' in g.inner_text('#ferr')
    g.fill('#url','https://x.example/rss'); g.click('#add'); g.wait_for_timeout(100); assert 'HTTP 500' in g.inner_text('#flist')
    assert g.locator('#cat details').count() >= 6
    g.click('#cat summary >> nth=0'); g.click('#cat .ci .btn >> nth=0'); g.wait_for_timeout(700)
    g.click('text=Disable'); assert calls()[-1] == ['en','1',False]
    g.wait_for_timeout(100); g.click('text=Remove'); g.click('text=Tap again'); assert calls()[-1] == ['rm','1']
    g.click('nav button[data-t=band]'); g.wait_for_timeout(700)
    assert '7 020.00' in g.inner_text('#khz'), g.inner_text('#khz')
    assert g.evaluate("(()=>{const c=document.getElementById('wf'),d=c.getContext('2d').getImageData(Math.round(c.width*12.3/40),0,1,1).data;return d[0]+d[1]+d[2]})()") > 300, 'waterfall shows the station'
    assert g.inner_text('#sval').startswith('S')
    g.evaluate("(()=>{const e=document.getElementById('rit');e.value=-80;e.dispatchEvent(new Event('input'))})()"); assert calls()[-1] == ['rit',-80]
    g.click('#rit0'); assert calls()[-1] == ['rit',0]
    g.click('#span'); assert calls()[-1] == ['set','span',4]; g.click('#span')
    g.click('#dnext'); assert calls()[-1] == ['next',1]
    g.click('#reveal'); g.wait_for_timeout(700); assert 'CQ CQ' in g.inner_text('#copy')
    g.click('#dplus'); assert calls()[-1][0] == 'dial'
    g.click('#dplus10'); assert abs(calls()[-1][1] - calls()[-2][1] - 0.01) < 1e-6, calls()[-2:]
    g.click('.seg[data-k=width] button[data-v="2"]'); assert calls()[-1] == ['set','width',2]
    g.click('.seg[data-k=side] button[data-v="1"]'); assert calls()[-1] == ['set','side',1]
    g.click('.seg[data-k=agc] button[data-v="0"]'); assert calls()[-1] == ['set','agc',0]
    g.click('.seg[data-k=follow] button[data-v="1"]'); assert calls()[-1] == ['set','follow',1]
    assert 'CQ WW CW' in g.inner_text('#contest')
    g.click('nav button[data-t=settings]'); g.click('.seg[data-k=bed] button[data-v="2"]'); assert calls()[-1] == ['set','bed',2]
    g.click('.seg[data-k=sleep] button[data-v="30"]'); assert calls()[-1] == ['set','sleep',30]
    g.select_option('#region', '1'); assert calls()[-1] == ['set','region',1]
    g.evaluate("(()=>{const e=document.getElementById('wpm');e.value=10;e.dispatchEvent(new Event('input'));e.dispatchEvent(new Event('change'))})()")
    assert g.inner_text('#v-eff') == '10 WPM', g.inner_text('#v-eff')
    g.click('.seg[data-k=abbr] button[data-v="2"]'); assert calls()[-1] == ['set','abbr',2]
    g.click('#abl summary'); g.wait_for_timeout(100); assert 'calling any station' in g.inner_text('#abt')
    assert g.evaluate('document.documentElement.scrollWidth') <= 360
    g.screenshot(path='out_test/ui_settings.png', full_page=True)
    assert not errs, errs; print('UI test passed'); b.close()
