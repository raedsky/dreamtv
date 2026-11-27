#!/usr/bin/env python3
"""APK Builder: يطبّق إعدادات اللوحة (الاسم، الحزمة، الألوان، الشعار، السيرفر) على المشروع قبل البناء.
الإعدادات تأتي كـ JSON مشفّر base64 في المتغير CONFIG_B64."""
import base64, io, json, os, re, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res')


def die(msg):
    print('ERROR:', msg)
    sys.exit(1)


def read(p):
    with open(os.path.join(ROOT, p), encoding='utf-8') as f:
        return f.read()


def write(p, s):
    with open(os.path.join(ROOT, p), 'w', encoding='utf-8') as f:
        f.write(s)


def xml_escape(s):
    return (s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
             .replace("'", "\\'").replace('"', '\\"'))


def kt_escape(s):
    return s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$')


cfg = json.loads(base64.b64decode(os.environ.get('CONFIG_B64', '')).decode('utf-8'))
print('Config:', {k: v for k, v in cfg.items()})

name = (cfg.get('app_name') or '').strip()
pkg = (cfg.get('package') or '').strip()
vname = (cfg.get('version_name') or '1.0').strip()
vcode = int(cfg.get('version_code') or 1)
accent = (cfg.get('accent') or '').strip()
bg = (cfg.get('bg') or '').strip()
api_url = (cfg.get('api_url') or '').strip()
server = (cfg.get('server') or '').strip()
server_name = (cfg.get('server_name') or '').strip()
logo_url = (cfg.get('logo_url') or '').strip()

if not name or len(name) > 40:
    die('bad app_name')
if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+', pkg):
    die('bad package')
if not re.fullmatch(r'[0-9A-Za-z.\-]{1,20}', vname):
    die('bad version_name')
hexc = re.compile(r'#[0-9A-Fa-f]{6}')

# ---- الحزمة والإصدار ----
g = read('app/build.gradle.kts')
g = re.sub(r'applicationId = "[^"]*"', lambda m: f'applicationId = "{pkg}"', g)
g = re.sub(r'versionCode = \d+', lambda m: f'versionCode = {vcode}', g)
g = re.sub(r'versionName = "[^"]*"', lambda m: f'versionName = "{vname}"', g)
write('app/build.gradle.kts', g)

# ---- اسم التطبيق ----
for p in ('app/src/main/res/values/strings.xml', 'app/src/main/res/values-ar/strings.xml'):
    s = read(p)
    s = re.sub(r'<string name="app_name">.*?</string>', lambda m: f'<string name="app_name">{xml_escape(name)}</string>', s)
    write(p, s)

# ---- الألوان ----
c = read('app/src/main/res/values/colors.xml')
if hexc.fullmatch(accent):
    c = re.sub(r'<color name="accent">#[0-9A-Fa-f]+</color>', lambda m: f'<color name="accent">{accent}</color>', c)
if hexc.fullmatch(bg):
    c = re.sub(r'<color name="bg">#[0-9A-Fa-f]+</color>', lambda m: f'<color name="bg">{bg}</color>', c)
write('app/src/main/res/values/colors.xml', c)

# ---- ربط اللوحة والسيرفر الافتراضي ----
pc_path = 'app/src/main/java/com/mytv/player/PanelConfig.kt'
pc = read(pc_path)
if api_url and re.match(r'https?://', api_url):
    pc = re.sub(r'const val API_URL = "[^"]*"', lambda m: f'const val API_URL = "{kt_escape(api_url)}"', pc)
if server and re.match(r'https?://', server):
    pc = re.sub(r'const val DEFAULT_SERVER = "[^"]*"', lambda m: f'const val DEFAULT_SERVER = "{kt_escape(server.rstrip("/"))}"', pc)
reseller = (cfg.get('reseller') or '').strip()
if re.fullmatch(r'[A-Za-z0-9]{4,16}', reseller):
    pc = re.sub(r'const val RESELLER = "[^"]*"', lambda m: f'const val RESELLER = "{reseller}"', pc)
if server_name:
    pc = re.sub(r'const val DEFAULT_SERVER_NAME = "[^"]*"', lambda m: f'const val DEFAULT_SERVER_NAME = "{kt_escape(server_name)}"', pc)
write(pc_path, pc)

# ---- الشعار (أيقونة + بانر التلفزيون) ----
if logo_url:
    from PIL import Image
    req = urllib.request.Request(logo_url, headers={'User-Agent': 'apk-builder'})
    data = urllib.request.urlopen(req, timeout=60).read()
    if len(data) > 5 * 1024 * 1024:
        die('logo too large')
    img = Image.open(io.BytesIO(data)).convert('RGBA')
    nodpi = os.path.join(RES, 'drawable-nodpi')
    os.makedirs(nodpi, exist_ok=True)

    # أيقونة مربعة 512
    side = max(img.size)
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.paste(img, ((side - img.width) // 2, (side - img.height) // 2), img)
    sq.resize((512, 512), Image.LANCZOS).save(os.path.join(nodpi, 'ic_launcher.png'))
    os.remove(os.path.join(RES, 'drawable/ic_launcher.xml'))

    # بانر 320x180 بلون الخلفية والشعار في المنتصف
    bgc = bg if hexc.fullmatch(bg) else '#0E1525'
    rgb = tuple(int(bgc[i:i + 2], 16) for i in (1, 3, 5))
    banner = Image.new('RGBA', (640, 360), rgb + (255,))
    lg = img.copy()
    lg.thumbnail((520, 280), Image.LANCZOS)
    banner.paste(lg, ((640 - lg.width) // 2, (360 - lg.height) // 2), lg)
    banner.convert('RGB').save(os.path.join(nodpi, 'banner.png'))
    os.remove(os.path.join(RES, 'drawable/banner.xml'))

print('Applied OK')
