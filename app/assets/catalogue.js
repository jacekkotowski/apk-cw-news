// Built-in feeds: [name, address, note]. The name ends with the short station name that is sent in Morse.
// Checked 2026-11-10: each address returned a valid feed with stories. Groups are rough labels, not ratings.
const CATALOGUE = [
 {g: 'Ham radio and CW', f: [
  ['ARRL News (ARRL)', 'https://www.arrl.org/news/rss', 'The US national association'],
  ['Amateur Radio Newsline (ARNL)', 'https://www.arnewsline.org/news?format=rss', 'Weekly bulletin, worldwide'],
  ['RSGB News (RSGB)', 'https://rsgb.org/main/feed/', 'UK society'],
  ['QRPer (QRPER)', 'https://qrper.com/feed/', 'Low power, kits and portable CW'],
  ['SWLing Post (SWL)', 'https://swling.com/blog/feed/', 'Shortwave listening'],
  ['AMSAT News (AMSAT)', 'https://www.amsat.org/feed/', 'Amateur satellites'],
  ['DX World (DXW)', 'https://www.dx-world.net/feed/', 'Long-distance news'],
  ['AmateurRadio.com (AMRADIO)', 'https://www.amateurradio.com/feed/', 'Community blog posts'],
  ['Hackaday (HAD)', 'https://hackaday.com/blog/feed/', 'Hardware hacks and radios']]},
 {g: 'World news, plain reporting', f: [
  ['Deutsche Welle (DW)', 'https://rss.dw.com/rdf/rss-en-all', 'German public broadcaster, English'],
  ['France 24 (F24)', 'https://www.france24.com/en/rss', 'French public broadcaster, English'],
  ['Euronews (EURONEWS)', 'https://www.euronews.com/rss', 'European news'],
  ['Christian Science Monitor (CSM)', 'https://rss.csmonitor.com/feeds/all', 'Calm, explanatory style'],
  ['The Hill (HILL)', 'https://thehill.com/feed/', 'US politics, both parties']]},
 {g: 'Centre-right and independent', f: [
  ['The Dispatch (DISPATCH)', 'https://thedispatch.com/feed/', 'Fact-based centre-right'],
  ['Reason (REASON)', 'https://reason.com/latest/feed/', 'Libertarian'],
  ['The Free Press (FP)', 'https://www.thefp.com/feed', 'Independent, heterodox'],
  ['UnHerd (UNHERD)', 'https://unherd.com/feed/', 'Contrarian essays'],
  ['National Review (NR)', 'https://www.nationalreview.com/feed/', 'Conservative, mostly opinion']]},
 {g: 'Both sides at once', f: [
  ['Tangle (TANGLE)', 'https://www.readtangle.com/rss/', 'Each story with the left and the right view'],
  ['RealClearPolitics (RCP)', 'https://www.realclearpolitics.com/index.xml', 'Links to opinion from across the spectrum']]},
 {g: 'Poland', f: [
  ['Notes from Poland (NFP)', 'https://notesfrompoland.com/feed/', 'Polish news in English'],
  ['Rzeczpospolita (RP)', 'https://www.rp.pl/rss_main', 'In Polish, accents are dropped']]},
 {g: 'Science and technology', f: [
  ['Ars Technica (ARS)', 'https://feeds.arstechnica.com/arstechnica/index', 'Tech and science'],
  ['Quanta (QUANTA)', 'https://www.quantamagazine.org/feed/', 'Maths and physics, in depth'],
  ['Phys.org (PHYS)', 'https://phys.org/rss-feed/', 'Research news'],
  ['ScienceDaily (SCIDAILY)', 'https://www.sciencedaily.com/rss/top/science.xml', 'Top science stories'],
  ['Nature (NATURE)', 'https://www.nature.com/nature.rss', 'Journal papers'],
  ['NASA (NASA)', 'https://www.nasa.gov/feed/', 'Space agency'],
  ['IEEE Spectrum (IEEE)', 'https://spectrum.ieee.org/feeds/feed.rss', 'Electronics and engineering'],
  ['MIT Technology Review (MITTR)', 'https://www.technologyreview.com/feed/', 'Technology'],
  ['New Scientist (NEWSCI)', 'https://www.newscientist.com/feed/home/', 'General science'],
  ['Hacker News (HN)', 'https://news.ycombinator.com/rss', 'Programmers and startups']]},
 {g: 'Psychology and mind', f: [
  ['PsyPost (PSYPOST)', 'https://www.psypost.org/feed/', 'Psychology research news'],
  ['ScienceDaily Mind (SDMIND)', 'https://www.sciencedaily.com/rss/mind_brain.xml', 'Brain and behaviour'],
  ['Aeon (AEON)', 'https://aeon.co/feed.rss', 'Long essays on mind and ideas']]}
];
