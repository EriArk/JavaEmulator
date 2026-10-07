"""Helpers for real release-build screenshots on a local Android emulator."""

import os
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
SERIAL = 'emulator-5580'
OUT = ROOT / 'screenshots/website-beta2'
GAMES = Path(tempfile.gettempdir()) / 'abyssme-website-games'
HANDHELD = 'io.github.eriark.abyssme'
PHONE = HANDHELD + '.phone'
ACTIVITY = 'ru.playsoftware.j2meloader.MainActivity'


def adb(*args, binary=False):
    result = subprocess.run([str(ADB), '-s', SERIAL, *map(str, args)],
                            capture_output=True, check=True, timeout=90)
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace').strip()


def screenshot(name):
    OUT.mkdir(parents=True, exist_ok=True)
    data = adb('exec-out', 'screencap', '-p', binary=True)
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError('Screenshot is not PNG')
    target = OUT / (name + '.png')
    target.write_bytes(data)
    print(target)
    return target


def nodes():
    status = adb('shell', 'uiautomator', 'dump', '/sdcard/abyssme-ui.xml')
    if 'dumped to:' not in status:
        raise RuntimeError('Could not obtain fresh UI hierarchy: ' + status)
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/abyssme-ui.xml')).iter('node'))


def ui():
    for node in nodes():
        if node.get('text') or node.get('clickable') == 'true':
            print(node.get('resource-id', '').split('/')[-1],
                  repr(node.get('text')), node.get('bounds'))


def tap(text=None, resource=None, description=None, hold=False):
    found = [n for n in nodes() if
             (text is not None and n.get('text') == text) or
             (resource is not None and n.get('resource-id', '').endswith('/' + resource)) or
             (description is not None and n.get('content-desc', '').startswith(description))]
    if len(found) != 1:
        raise ValueError(f'Expected one target, found {len(found)}: {text or resource}')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', found[0].get('bounds')))
    x, y = (x1 + x2) // 2, (y1 + y2) // 2
    if hold:
        adb('shell', 'input', 'swipe', x, y, x, y, 850)
    else:
        adb('shell', 'input', 'tap', x, y)


def browse_artwork(filename):
    # Android 15 routes GetContent through Photo Picker before DocumentsUI.
    tap(description='More options')
    choices = nodes()
    browse = next(n.get('text') for n in choices if n.get('text', '').startswith('Browse'))
    tap(text=browse)
    time.sleep(2)
    current = nodes()
    if not any(n.get('resource-id', '').endswith('/breadcrumb_text') and
               n.get('text') == 'Downloads' for n in current):
        tap(description='Show roots')
        tap(text='Downloads')
    current = nodes()
    if any(n.get('resource-id', '').endswith('/sub_menu_list') for n in current):
        tap(resource='sub_menu_list')
    for _ in range(4):
        if any(n.get('text') == filename and
               int(re.findall(r'\d+', n.get('bounds'))[1]) < 1000 for n in nodes()):
            tap(text=filename)
            time.sleep(2)
            return
        adb('shell', 'input', 'swipe', 1000, 950, 1000, 480, 450)
    raise RuntimeError('Artwork file not visible: ' + filename)


def choose_cover(title, filename):
    tap(text=title, hold=True)
    tap(text='Change cover')
    browse_artwork(filename)


def launch(package=HANDHELD):
    print(adb('shell', 'am', 'start', '-n', package + '/' + ACTIVITY))


def import_game(filename, package=HANDHELD):
    source = GAMES / filename
    destination = f'/sdcard/Android/data/{package}/files/demo-games/{source.name}'
    adb('shell', 'mkdir', '-p', destination.rsplit('/', 1)[0])
    adb('push', source, destination)
    print(adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW',
              '-d', 'file://' + destination, '-n', package + '/' + ACTIVITY))


def download_games():
    GAMES.mkdir(parents=True, exist_ok=True)
    sources = {'2048': 'https://github.com/jsmucr/2048-for-J2ME/releases/download/v1.04/2048.jar'}
    for name in ['BilliardBerzerk', 'PubMan', 'SpaceRun', 'CavernsOfFire',
                 'Munchies', 'DungeonsOfHack', 'MatrixMiner', 'Ogrotron']:
        sources[name] = f'https://www.13thmonkey.org/~boris/jgame/JGame/jars/{name}Midlet.jar'
    for name, url in sources.items():
        target = GAMES / (name + '.jar')
        try:
            if not target.exists():
                with urllib.request.urlopen(url, timeout=20) as response:
                    data = response.read()
                target.write_bytes(data)
            with zipfile.ZipFile(target) as archive:
                manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8', errors='replace')
                if 'MIDlet-1:' not in manifest:
                    raise ValueError('Not a MIDlet archive')
            print(name, target.stat().st_size, flush=True)
        except Exception as error:
            print(name, type(error).__name__, str(error), flush=True)


def export_website_assets():
    from PIL import Image, ImageStat

    web = OUT / 'webp'
    web.mkdir(exist_ok=True)
    images = []
    for source in sorted(OUT.glob('[0-9][0-9]-*.png')):
        if source.name.startswith('00-'):
            continue
        with Image.open(source) as picture:
            picture.load()
            if max(ImageStat.Stat(picture.convert('RGB')).stddev) < 5:
                raise ValueError('Screenshot appears blank: ' + source.name)
            target = web / (source.stem + '.webp')
            picture.save(target, 'WEBP', lossless=True, method=6)
            images.append({'png': source.name, 'webp': 'webp/' + target.name,
                           'width': picture.width, 'height': picture.height,
                           'png_bytes': source.stat().st_size,
                           'webp_bytes': target.stat().st_size})
    manifest = {
        'product': 'AbyssME', 'version': '0.1.0-beta.2',
        'captured_at': datetime.now(timezone.utc).isoformat(),
        'environment': 'Android 15 / API 35 x86_64 emulator, not physical-device captures',
        'capture_settings': {
            'handheld': '1920x1080, 240 dpi',
            'phone': '1080x1920, 400 dpi; Phone keylayout chosen through Virtual keyboard > Switch keylayout',
            'gameplay': '2048 played using directional input; Phone capture played by tapping virtual 2/4/6/8 keys'
        },
        'images': images,
        'processing': 'Unretouched adb screencap PNGs; lossless WebP copies, no resizing or compositing.',
        'library': 'Free demonstration games installed separately; not bundled with AbyssME.',
        'installed_games': ['2048', 'Caverns of Fire', 'Dungeons of Hack', 'Matrix Miner',
                            'Munchies', 'Ogrotron', 'Pub Man', 'Space Run'],
        'display_names': 'Spaces added using the built-in Rename action; JARs are unchanged.',
        'artwork': 'Gallery covers selected manually using Change cover. JGame covers are original author screenshots; 2048 uses its bundled icon. Grid/List retain actual MIDlet icons and native fallbacks.',
        'sources': [
            {'title': '2048 for J2ME', 'url': 'https://github.com/jsmucr/2048-for-J2ME'},
            {'title': 'JGame mobile games', 'author': 'Boris van Schooten',
             'url': 'https://www.13thmonkey.org/~boris/jgame/JGame/html/mobile.html'},
            {'title': 'JGame artwork',
             'url': 'https://www.13thmonkey.org/~boris/jgame/JGame/html/games.html'}
        ],
        'release_apks': []
    }
    for variant in ['fdroid', 'phone']:
        apk = ROOT / 'test-builds' / f'AbyssME-0.1.0-beta.2-{variant}-release.apk'
        manifest['release_apks'].append({
            'name': apk.name, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()})
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    archive = OUT.parent / 'AbyssME-beta2-website.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(OUT / 'manifest.json', 'manifest.json')
        for item in images:
            for key in ['png', 'webp']:
                bundle.write(OUT / item[key], item[key])
    print(json.dumps({'archive': str(archive), 'images': images}, indent=2))
