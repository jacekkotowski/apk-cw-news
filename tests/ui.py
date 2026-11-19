import re
# Drives the page in Chromium with a fake Android bridge. Run: python3 tests/ui.py
import json, pathlib
from playwright.sync_api import sync_playwright
FAKE = """
window.calls=[];window.mst={running:true,perm:true,calibrated:false,fist:false,key:true,heard:'CQ DE',sent:'',have0:false,have1:false,have2:false,warming:false,warm:'',pitch:812};window.fake={status:'stopped',title:'',source:'',word:0,words:0,error:'',id:'-1',refreshing:false,news:{state:'no_feeds',failed:0}};
window.fs=[];window.st={wpm:20,eff:12,pitch:600,volume:80,mode:0,abbr:1,band:1,activity:1,bed:1,bedDb:-24,width:1,agc:1,span:40,region:0,auto:1,announce:1,age:0,refresh:30,filter:0,follow:0,sleep:0,mycall:'SP0CWN',myname:'JACEK',myqth:'KRAKOW',myrig:'FT891',myant:'DIPOLE',power:10,sendwpm:15,manners:0,sendwith:0,hint:1,showtext:0,ckind:1,side:0,shift:0,apf:0,nb:0,weather:0,realwx:0};
window.Android={state:()=>JSON.stringify(fake),settings:()=>JSON.stringify(st),feeds:()=>JSON.stringify(fs),
recent:()=>JSON.stringify([{id:'5',title:'<b>Not markup</b>',source:'HN',heard:false}]),
set:(k,v)=>{calls.push(['set',k,v]);st[k]=v;if(k==='wpm'&&st.eff>v)st.eff=v;},command:c=>calls.push([c]),playStory:i=>calls.push(['story',i]),
addFeed:(u,n)=>{if(!u.startsWith('https://'))return 'The address must start with https://';fs.push({id:'1',url:u,name:'',custom:false,enabled:true,lastOk:0,error:'HTTP 500',count:0});return '';},
editFeed:()=>'',setFeedEnabled:(i,o)=>{calls.push(['en',i,o]);fs[0].enabled=o;},removeFeed:i=>{calls.push(['rm',i]);fs.length=0;},refresh:()=>calls.push(['refresh']),regions:()=>JSON.stringify(['Europe','North America, east']),
micPermission:()=>{calls.push(['perm']);return true;},micOn:o=>calls.push(['mic',o]),micRecord:(k,sec)=>{calls.push(['rec',k]);window.mst['have'+({held:0,quiet:1,band:2}[k])]=true;return '';},
micCalibrate:()=>JSON.stringify({ok:true,pitch:812,snr:34,leakOk:false}),fistStart:()=>calls.push(['fist']),fistDone:t=>{calls.push(['fistdone',t]);return 'Your dashes are about 2.4 dots; I will read them that way.';},warmUp:o=>calls.push(['warm',o]),
micState:()=>JSON.stringify(window.mst),notWhatISent:t=>calls.push(['wrong',t]),exportSaved:()=>calls.push(['export']),
liveState:()=>JSON.stringify(window.live||{on:false,canAnswer:true,canCall:true,tx:7015,summary:''}),answer:()=>{calls.push(['answer']);window.live={on:true,call:'DL1ABC',stage:1,done:false,sending:false,khz:7015.12,tx:7015,hint:'Give DL1ABC a report.',suggest:'DL1ABC DE SP0CWN <BT> R <BT> UR RST 579 579 <BT> DL1ABC DE SP0CWN <KN>',lines:['DL1ABC: CQ CQ DE DL1ABC K','YOU: DL1ABC DE SP0CWN K'],partial:'R GM',canAnswer:false,canCall:false};return '';},
callCq:()=>{calls.push(['cq']);return 'Another station is on this frequency. Tune to a clear one.';},sendText:t=>{calls.push(['send',t]);return '';},net:()=>calls.push(['net']),endContact:()=>{calls.push(['end']);window.live={on:false,canAnswer:true,canCall:true,tx:7015,summary:'Contact with DL1ABC. Thank you for the contact.'};},setText:(k,v)=>{calls.push(['text',k,v]);if(k==='mycall'&&!/^[A-Z0-9]{1,3}[0-9][A-Z0-9]{0,3}[A-Z]$/.test(v.toUpperCase()))return 'That is not a callsign. A call looks like SP9ABC.';st[k]=v.toUpperCase();return '';},
log:()=>JSON.stringify([{id:'2',at:1700000000000,call:'DL1XYZ',name:'HANS',qth:'BERLIN',sent:'579',rcvd:'599',khz:7012.3,complete:true,overs:6},{id:'1',at:1699990000000,call:'OH2BH',name:'',qth:'',sent:'',rcvd:'',khz:7031,complete:false,overs:2}]),logSummary:()=>JSON.stringify({contacts:2,countries:2}),
band:()=>JSON.stringify({on:true,dial:window.dialV||7020,rit:window.ritV||0,passLo:-200,passHi:200,low:7000,high:7040,news:7020,contest:'CQ WW CW',sky:'Thunderstorms in the distance: crackling static.',act:[{k:7012.3,l:.8,on:true},{k:7031.1,l:.3,on:false}]}),
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
    g.click('nav button[data-t=band]'); g.evaluate("document.querySelectorAll('#band details').forEach(d=>d.open=true)"); g.wait_for_timeout(700)
    assert '7 020.00' in g.inner_text('#khz'), g.inner_text('#khz')
    assert g.evaluate("(()=>{const c=document.getElementById('wf'),d=c.getContext('2d').getImageData(Math.round(c.width*12.3/40),0,1,1).data;return d[0]+d[1]+d[2]})()") > 300, 'waterfall shows the station'
    assert g.inner_text('#sval').startswith('S')
    g.click('#dnext'); assert calls()[-1] == ['next',1]
    g.click('#cans'); g.wait_for_timeout(100); assert calls()[-1] == ['answer']
    g.wait_for_timeout(700); assert g.is_visible('#clive') and 'DL1ABC' in g.inner_text('#ccall')
    assert 'report' in g.inner_text('#chint') and 'DL1ABC: CQ CQ' in g.inner_text('#ctrans') and 'R GM' not in g.inner_text('#ctrans')
    assert 'above' in g.inner_text('#czero')
    assert 'UR RST 579 579' in g.inner_text('#csugt'); g.click('#cuse'); assert 'UR RST 579 579' in g.input_value('#ctext')
    g.click('#clive [data-f=call]'); assert g.input_value('#ctext') == 'DL1ABC DE SP0CWN SP0CWN K'
    for f in ['rst','name','qth']: g.click('#clive [data-f=%s]' % f)
    g.select_option('#cwx','RAIN'); g.click('#clive [data-f=rig]'); g.click('#clive [data-f=ant]')
    v = g.input_value('#ctext'); assert v == 'UR RST 579 579 <BT> NAME JACEK JACEK <BT> QTH KRAKOW KRAKOW <BT> WX HR RAIN <BT> RIG FT891 PWR 10 W <BT> ANT DIPOLE', v
    g.click('#csend'); assert calls()[-1][0] == 'send' and calls()[-1][1] == 'DL1ABC DE SP0CWN <BT> ' + v + ' <BT> DL1ABC DE SP0CWN <KN>', calls()[-1]
    g.evaluate('document.querySelector(".seg[data-k=showtext] button[data-v=\'2\']").click()'); assert calls()[-1] == ['set','showtext',2]; g.wait_for_timeout(700)
    assert 'R GM' in g.inner_text('#ctrans')
    g.evaluate('document.querySelector(".seg[data-k=showtext] button[data-v=\'1\']").click()'); g.wait_for_timeout(700); assert 'DL1ABC: CQ CQ' not in g.inner_text('#ctrans') and 'YOU:' in g.inner_text('#ctrans')
    g.click('#cclear'); g.select_option('#cwx','RAIN'); g.select_option('#ctemp','12'); assert g.input_value('#ctext') == 'WX HR RAIN TEMP 12 C'
    g.click('#cclear'); g.select_option('#ctemp','-3'); assert g.input_value('#ctext') == 'WX HR TEMP -3 C'; g.click('#cclear')
    g.click('#cnet'); assert calls()[-1] == ['net']
    g.click('#cend'); g.wait_for_timeout(700); assert not g.is_visible('#clive') and 'Thank you' in g.inner_text('#csum')
    g.evaluate("window.live={on:false,canAnswer:false,canCall:false,tx:7015,summary:''}"); g.wait_for_timeout(700); assert g.is_disabled('#cans') and g.is_disabled('#ccq')
    g.evaluate("window.live={on:false,canAnswer:false,canCall:true,tx:7015,summary:''}"); g.wait_for_timeout(700)
    g.click('#ccq'); assert calls()[-1] == ['cq'] and 'clear one' in g.inner_text('#cerr')
    g.evaluate("S.sendwith=1"); g.wait_for_timeout(700)
    assert g.is_visible('#keybox') and 'You sent: CQ DE' in g.inner_text('#ksent') and g.locator('.klight.on').count() == 1
    g.evaluate("fake.status='playing'"); g.click('#ksetup'); assert 'Hold your key' in g.inner_text('#kstep')
    g.wait_for_timeout(2600)
    assert '812 Hz' in g.inner_text('#kres') and 'headphones' in g.inner_text('#kres')
    g.click('#kfist'); g.click('#kgo'); g.click('#kgo'); assert '2.4 dots' in g.inner_text('#kres')
    g.click('#kwarm'); g.evaluate("mst.warming=true;mst.warm='CQ CQ DE SP0CWN SP0CWN K'"); g.wait_for_timeout(700); assert '100%' in g.inner_text('#kres')
    g.click('#kgo'); g.fill('#kmeant','cq de sp0cwn'); g.click('#kwrong'); assert calls()[-1][0] == 'wrong'
    g.evaluate("S.sendwith=0"); g.wait_for_timeout(700)
    g.click('#scale'); assert calls()[-1] == ['set','span',4]; g.click('#scale')
    g.wait_for_timeout(700); assert 'CQ CQ' in g.inner_text('#ctrans')  # one window: the tuned station's text when no contact is running
    g.click('.seg[data-k=band] button[data-v="0"]'); assert calls()[-1] == ['set','band',0]; g.click('.seg[data-k=band] button[data-v="1"]')
    assert 'Thunderstorms' in g.inner_text('#sky')
    assert 'CQ WW CW' in g.inner_text('#contest')
    g.click('nav button[data-t=log]'); g.wait_for_timeout(700)
    assert '2 contacts, 2 countries' in g.inner_text('#lsum'), g.inner_text('#lsum')
    assert 'DL1XYZ' in g.inner_text('#llist') and 'Not finished' in g.inner_text('#llist')
    assert g.locator('#llist .bad').count() == 0
    g.click('nav button[data-t=settings]')
    g.fill('#mycall','hello'); g.dispatch_event('#mycall','change'); assert calls()[-1] == ['text','mycall','hello'] and 'not a callsign' in g.inner_text('#serr')
    g.fill('#mycall','sp9abc'); g.dispatch_event('#mycall','change'); assert calls()[-1] == ['text','mycall','sp9abc'] and g.inner_text('#serr') == ''
    g.fill('#myname','Jan'); g.dispatch_event('#myname','change'); assert calls()[-1] == ['text','myname','Jan']
    g.click('.seg[data-k=power] button[data-v="50"]'); assert calls()[-1] == ['set','power',50]
    g.click('.seg[data-k=manners] button[data-v="2"]'); assert calls()[-1] == ['set','manners',2]
    g.click('.seg[data-k=sendwith] button[data-v="1"]'); assert calls()[-1] == ['set','sendwith',1]
    g.evaluate("(()=>{const e=document.getElementById('sendwpm');e.value=22;e.dispatchEvent(new Event('input'));e.dispatchEvent(new Event('change'))})()"); assert calls()[-1] == ['set','sendwpm',22]
    g.click('.seg[data-k=bed] button[data-v="2"]'); assert calls()[-1] == ['set','bed',2]
    g.click('.seg[data-k=sleep] button[data-v="30"]'); assert calls()[-1] == ['set','sleep',30]
    g.select_option('#region', '1'); assert calls()[-1] == ['set','region',1]
    g.evaluate("(()=>{const e=document.getElementById('wpm');e.value=10;e.dispatchEvent(new Event('input'));e.dispatchEvent(new Event('change'))})()")
    assert g.inner_text('#v-eff') == '10 WPM', g.inner_text('#v-eff')
    g.click('.seg[data-k=abbr] button[data-v="2"]'); assert calls()[-1] == ['set','abbr',2]
    g.click('#abl summary'); g.wait_for_timeout(100); assert 'calling any station' in g.inner_text('#abt')
    assert g.evaluate('document.documentElement.scrollWidth') <= 360
    g.screenshot(path='out_test/ui_settings.png', full_page=True)
    assert not errs, errs; print('UI test passed'); b.close()
