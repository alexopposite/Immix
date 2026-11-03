#!/usr/bin/env python3
"""Manage a temporary hardware adapter. No APK installation, boot hook, or /system writes."""
import argparse
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent
APK = ROOT / 'frame-hardware/build/outputs/apk/debug/frame-hardware-debug.apk'
REMOTE = '/data/local/tmp/immix-frame-hardware.apk'
ENTRY = 'org.immix.frame.hardware.RootBackend'
PACKAGE = 'org.immix.frame.hardware'


def run(serial, *args):
    result = subprocess.run(['adb', '-s', serial, *args], capture_output=True, text=True, timeout=25)
    if result.returncode:
        raise RuntimeError((result.stderr or result.stdout or 'ADB operation failed').strip())
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument('--probe', action='store_true')
    actions.add_argument('--start', action='store_true')
    actions.add_argument('--status', action='store_true')
    actions.add_argument('--stop', action='store_true')
    parser.add_argument('--serial', default='192.0.2.10:5555')
    args = parser.parse_args()
    serial = args.serial
    if run(serial, 'shell', 'id -u') != '0':
        raise RuntimeError('The temporary backend requires the existing root ADB shell')
    command = 'CLASSPATH=' + REMOTE + ' /system/bin/app_process /system/bin ' + ENTRY
    if args.status:
        print(run(serial, 'shell', 'dumpsys activity service ' + PACKAGE + '/.FrameService'))
    elif args.stop:
        print(run(serial, 'shell', command + ' --command STOP'))
        run(serial, 'shell', 'am stopservice -n ' + PACKAGE + '/.FrameService')
        print('Temporary root adapter and started helper service stopped; no boot configuration changed.')
    elif args.start:
        if not run(serial, 'shell', 'pm path ' + PACKAGE).startswith('package:'):
            raise RuntimeError('Install the separate helper APK before starting the adapter')
        uid = run(serial, 'shell', 'stat -c %u /data/user/0/' + PACKAGE)
        if not re.fullmatch(r'\d+', uid) or int(uid) < 10000:
            raise RuntimeError('Could not verify the helper app UID')
        # Refuse a second backend rather than starting competing hardware owners.
        running = run(serial, 'shell', 'cat /proc/net/unix | grep org.immix.frame.hardware.v1 || true')
        if running:
            raise RuntimeError('An adapter may already be running; inspect --status before restarting')
        print(run(serial, 'push', str(APK), REMOTE))
        run(serial, 'shell', 'chmod 600 ' + REMOTE)
        print(run(serial, 'shell', 'nohup sh -c "' + command + ' --serve ' + uid +
                  '" >/data/local/tmp/immix-frame-hardware.log 2>&1 </dev/null & echo $!'))
        print(run(serial, 'shell', 'am startservice -n ' + PACKAGE + '/.FrameService'))
        print('Started a ten-minute temporary adapter. Helper defaults to observe-only; management requires owner action.')
    else:
        if run(serial, 'shell', 'cat /proc/net/unix | grep org.immix.frame.hardware.v1 || true'):
            raise RuntimeError('Stop the running adapter before replacing its APK for a probe')
        print(run(serial, 'push', str(APK), REMOTE))
        run(serial, 'shell', 'chmod 600 ' + REMOTE)
        print(run(serial, 'shell', command + ' --probe'))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        raise SystemExit(str(error))
