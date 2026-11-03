#!/usr/bin/env python3
"""Prepare credentials locally; use --apply explicitly to provision an installed app over ADB."""
import argparse
import datetime
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = 'nl.giejay.android.tv.immich'
PREFS = '/data/user/0/' + PACKAGE + '/shared_prefs/' + PACKAGE + '_preferences.xml'


def read_key(path):
    if path.stat().st_mode & 0o077:
        raise ValueError('Key file must be private: chmod 600 <key-file>')
    try:
        value = path.read_text().strip()
    except UnicodeError:
        raise ValueError('Key file is not UTF-8 plain text; export or paste the API key as plain text first') from None
    if len(value) < 16 or not value.isascii() or any(c.isspace() for c in value):
        raise ValueError('Expected a single nonempty API key, without internal whitespace')
    return value


def normalize_server(server):
    parsed = urlsplit(server)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('Use an HTTPS base URL without embedded credentials')
    if parsed.query or parsed.fragment or parsed.path.rstrip('/').endswith('/api'):
        raise ValueError('Use the Immich base URL without /api, query, or fragment')
    return server.rstrip('/')


def build_preferences(original, server, key):
    root = ET.fromstring(original) if original else ET.Element('map')
    if root.tag != 'map':
        raise ValueError('Unexpected Android preference format')
    values = {'hostName': ('string', server), 'apiKey': ('string', key),
              'disableSSLVerification': ('boolean', 'false'), 'debug_mode': ('boolean', 'false')}
    for child in list(root):
        if child.get('name') in values:
            root.remove(child)
    for name, (kind, value) in values.items():
        element = ET.SubElement(root, kind, {'name': name})
        if kind == 'string':
            element.text = value
        else:
            element.set('value', value)
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def adb(serial, *args, data=None, label='ADB operation'):
    try:
        result = subprocess.run(['adb', '-s', serial, *args], input=data,
                                capture_output=True, timeout=20)
    except (OSError, subprocess.TimeoutExpired):
        raise ValueError(label + ' could not complete') from None
    if result.returncode:
        # Do not print captured output: private preference XML may be in it.
        raise ValueError(label + ' failed; credential output suppressed')
    return result.stdout


def provision(serial, server, key):
    if adb(serial, 'shell', 'id -u', label='Root-access check').strip() != b'0':
        raise ValueError('Provisioning requires the frame\'s existing root ADB shell')
    if not adb(serial, 'shell', 'pm path ' + PACKAGE, label='Installed-app check').startswith(b'package:'):
        raise ValueError('Immix must be installed first; this helper never installs APKs')
    adb(serial, 'shell', 'am force-stop ' + PACKAGE, label='Stop Immix')
    uid = adb(serial, 'shell', 'stat -c %u /data/user/0/' + PACKAGE,
              label='App owner check').decode().strip()
    if not re.fullmatch(r'\d+', uid) or int(uid) < 10000:
        raise ValueError('Could not verify the ordinary app UID; system-app provisioning needs separate review')
    state = adb(serial, 'shell', 'if [ -f ' + PREFS + '.bak ]; then echo BACKUP; '
                'elif [ -f ' + PREFS + ' ]; then echo EXISTS; else echo NEW; fi',
                label='Preference state check').strip()
    if state == b'BACKUP':
        raise ValueError('Android has a pending preference backup; inspect it before provisioning')
    original = adb(serial, 'exec-out', 'cat', PREFS, label='Read existing settings') if state == b'EXISTS' else b''
    payload = build_preferences(original, server, key)
    backup = ROOT / '.tooling/provisioning-backups' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup.mkdir(parents=True, mode=0o700)
    backup.chmod(0o700)
    fd = os.open(backup / 'preferences-before.xml', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as file:
        file.write(original)
    # The key travels only through stdin, never a command-line argument or log.
    parent = PREFS.rsplit('/', 1)[0]
    command = ('set -e; umask 077; mkdir -p ' + parent + '; chown ' + uid + ':' + uid + ' ' + parent +
               '; chmod 700 ' + parent + '; cat > ' + PREFS + '.provisioning; chown ' + uid + ':' + uid +
               ' ' + PREFS + '.provisioning; chmod 600 ' + PREFS + '.provisioning; mv ' + PREFS +
               '.provisioning ' + PREFS + '; restorecon -R ' + parent)
    adb(serial, 'shell', command, data=payload, label='Write private app settings')
    if adb(serial, 'exec-out', 'cat', PREFS, label='Verify private settings') != payload:
        raise ValueError('Private settings verification failed; do not launch the app')
    print('Private settings provisioned and verified. Immix remains stopped.')
    print('No launcher, Nixplay package, screen timeout, or wireless ADB setting was changed.')
    print('On the approved first launch, verify API connectivity and whether Android Keystore encryption succeeded.')
    print('The existing app falls back to plaintext if its keystore fails; do not assume encryption succeeded.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--key-file', type=Path, default=ROOT / 'immich_api.key')
    parser.add_argument('--server', default='https://immich.example.com')
    parser.add_argument('--serial', default='192.0.2.10:5555')
    parser.add_argument('--apply', action='store_true', help='Explicitly write settings to the installed app')
    args = parser.parse_args()
    try:
        key = read_key(args.key_file)
        server = normalize_server(args.server)
        if not args.apply:
            build_preferences(b'', server, key)
            print('Dry run passed: private key file and provisioning payload are valid. No ADB connection or device changes.')
            return 0
        provision(args.serial, server, key)
        return 0
    except (ValueError, OSError, ET.ParseError) as error:
        # Avoid exceptions that could include XML text or secret input.
        if isinstance(error, ValueError) and not isinstance(error, ET.ParseError):
            print(str(error), file=sys.stderr)
        else:
            print('Private input/settings could not be processed; details suppressed.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
