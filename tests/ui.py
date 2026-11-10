# Drives the page in Chromium with a fake Android bridge. Run: python3 tests/ui.py
import json, pathlib
from playwright.sync_api import sync_playwright
FAKE = """
window.calls=[];window.fake={status:'stopped',title:'',source:'',word:0,words:0,error:'',id:'-1',refreshing:false,news:{state:'no_feeds',failed:0}};
window.fs=[];window.st={wpm:20,eff:12,pitch:600,volume:80,mode:0,abbr:1,bed:1,bedDb:-24,auto:1,announce:1,age:0,refresh:30};
window.Android={state:()=>JSON.stringify(fake),settings:()=>JSON.stringify(st),feeds:()=>JSON.stringify(fs),
recent:()=>JSON.stringify([{id:'5',title:'<b>Not markup</b>',source:'HN',heard:false}]),
set:(k,v)=>{calls.push(['set',k,v]);st[k]=v;if(k==='wpm'&&st.eff>v)st.eff=v;},command:c=>calls.push([c]),playStory:i=>calls.push(['story',i]),
addFeed:(u,n)=>{if(!u.startsWith('https://'))return 'The address must start with https://';fs.push({id:'1',url:u,name:'',custom:false,enabled:true,lastOk:0,error:'HTTP 500',count:0});return '';},
editFeed:()=>'',setFeedEnabled:(i,o)=>{calls.push(['en',i,o]);fs[0].enabled=o;},removeFeed:i=>{calls.push(['rm',i]);fs.length=0;},refresh:()=>calls.push(['refresh']),abbrevs:()=>JSON.stringify([{a:'ES',m:'and',l:1},{a:'CQ',m:'calling any station',l:0}])};
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
    g.click('nav button[data-t=settings]'); g.click('.seg[data-k=bed] button[data-v="2"]'); assert calls()[-1] == ['set','bed',2]
    g.evaluate("(()=>{const e=document.getElementById('wpm');e.value=10;e.dispatchEvent(new Event('input'));e.dispatchEvent(new Event('change'))})()")
    assert g.inner_text('#v-eff') == '10 WPM', g.inner_text('#v-eff')
    g.click('.seg[data-k=abbr] button[data-v="2"]'); assert calls()[-1] == ['set','abbr',2]
    g.click('#abl summary'); g.wait_for_timeout(100); assert 'calling any station' in g.inner_text('#abt')
    assert g.evaluate('document.documentElement.scrollWidth') <= 360
    g.screenshot(path='out_test/ui_settings.png', full_page=True)
    assert not errs, errs; print('UI test passed'); b.close()
