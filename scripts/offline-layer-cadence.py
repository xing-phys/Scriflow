#!/usr/bin/env python3
"""Capture StatusBar producer timestamps while USB is disconnected.

  python3 scripts/offline-layer-cadence.py start /tmp/ink-cadence
  # Unplug USB, write normally, wait at least 65 seconds, reconnect.
  python3 scripts/offline-layer-cadence.py collect /tmp/ink-cadence

Raw SurfaceFlinger timestamps measure layer submissions, not physical e-ink
refresh or pen-to-ink latency. A rolling timestamp buffer can repeat entries.
Do not subtract /proc/uptime from SurfaceFlinger timestamps: their clocks differ.
No app launches, screen captures, input injection, or battery spoofing occurs.
"""
import argparse
import datetime
import json
import pathlib
import shlex
import subprocess
import sys
import tempfile
import uuid


def adb(serial, *args, timeout=15):
    command = ['adb'] + (['-s', serial] if serial else []) + list(args)
    return subprocess.run(command, check=True, text=True, capture_output=True,
                          timeout=timeout).stdout.replace('\r\n', '\n')


def shell(serial, command, timeout=15):
    return adb(serial, 'shell', command, timeout=timeout)


def runner(duration, interval):
    # timeout bounds every service read as well as the entire background runner.
    return f'''#!/system/bin/sh
trap '' HUP
printf 'OFFLINE_LAYER_CADENCE_V1\\n'
printf 'LAYER StatusBar#0\\n'
for prop in ro.product.model ro.build.display.id ro.build.version.release; do
    printf 'PROP %s ' "$prop"
    getprop "$prop"
done
read initial rest < /proc/uptime
initial=${{initial%%.*}}
while :; do
    read current rest < /proc/uptime
    current=${{current%%.*}}
    elapsed=$((current - initial))
    [ "$elapsed" -ge {duration} ] && break
    printf '\\nSAMPLE elapsed=%s uptime=' "$elapsed"
    cat /proc/uptime
    printf 'BATTERY_BEGIN\\n'
    timeout 2 dumpsys battery | grep -E '^  (AC powered:|USB powered:|Wireless powered:|status:|level:|scale:|voltage:|temperature:)'
    printf 'BATTERY_END\\nLATENCY_BEGIN\\n'
    timeout 2 dumpsys SurfaceFlinger --latency 'StatusBar#0'
    printf 'LATENCY_END\\n'
    read current rest < /proc/uptime
    current=${{current%%.*}}
    remaining=$(({duration} - current + initial))
    [ "$remaining" -le 0 ] && break
    pause={interval}
    [ "$remaining" -lt "$pause" ] && pause="$remaining"
    sleep "$pause"
done
printf '\\nCOMPLETE\\n'
'''


def start(args):
    session = args.session.resolve()
    session.mkdir(parents=True, exist_ok=True)
    state_path = session / 'session.json'
    if state_path.exists():
        raise ValueError(f'Session already exists: {state_path}; use a new directory.')
    serial = adb(args.serial, 'get-serialno').strip()
    if not serial or serial == 'unknown':
        raise ValueError('No authorized device selected; use --serial if needed.')
    # Do not launch a job that cannot survive disconnect or enforce its deadline.
    shell(serial, 'command -v nohup && command -v timeout && command -v grep')
    token = uuid.uuid4().hex
    remote_script = f'/data/local/tmp/ink-cadence-{token}.sh'
    remote_log = f'/data/local/tmp/ink-cadence-{token}.txt'
    state = {'format': 1, 'serial': serial, 'durationSeconds': args.duration,
             'intervalSeconds': args.interval, 'remoteScript': remote_script,
             'remoteLog': remote_log, 'startedAtUtc': datetime.datetime.now(
                 datetime.timezone.utc).isoformat(), 'status': 'prepared'}
    state_path.write_text(json.dumps(state, indent=2) + '\n')
    with tempfile.TemporaryDirectory(prefix='ink-cadence-') as directory:
        script = pathlib.Path(directory) / 'sampler.sh'
        script.write_text(runner(args.duration, args.interval))
        adb(serial, 'push', str(script), remote_script)
    # All stdio belongs to device files before adb shell exits. nohup ignores HUP.
    command = (f'nohup timeout {args.duration + 5} sh {shlex.quote(remote_script)} '
               f'< /dev/null > {shlex.quote(remote_log)} 2>&1 &')
    shell(serial, command)
    state['status'] = 'started'
    state_path.write_text(json.dumps(state, indent=2) + '\n')
    print(f'Sampling {serial} for {args.duration}s (every {args.interval}s).')
    print('The recorder runs on the tablet and continues if USB is unplugged.')
    print(f'Collect: python3 {shlex.quote(str(pathlib.Path(__file__).resolve()))} '
          f'collect {shlex.quote(str(session))}')


def collect(args):
    session = args.session.resolve()
    state_path = session / 'session.json'
    state = json.loads(state_path.read_text())
    serial = state['serial']
    # Only remove paths created by this tool; never trust arbitrary JSON paths.
    remote_script = state['remoteScript']
    remote_log = state['remoteLog']
    prefix = '/data/local/tmp/ink-cadence-'
    token = remote_script.removeprefix(prefix).removesuffix('.sh')
    if (len(token) != 32 or any(c not in '0123456789abcdef' for c in token)
            or remote_script != prefix + token + '.sh'
            or remote_log != prefix + token + '.txt'):
        raise ValueError('Invalid remote paths in session state.')
    if state.get('status') == 'collected':
        print(f'Already collected: {session / "timestamps.txt"}')
        return
    # cat receives an explicit serial; reconnecting a different tablet cannot match.
    data = shell(serial, f'cat {shlex.quote(remote_log)}')
    if 'OFFLINE_LAYER_CADENCE_V1\n' not in data:
        raise ValueError('Sampler did not start correctly; remote files retained.')
    if not data.rstrip().endswith('COMPLETE'):
        raise ValueError('Sampler is unfinished or timed out; remote files retained. '
                         'Wait until the window ends and retry collect.')
    destination = session / 'timestamps.txt'
    destination.write_text(data)
    # Success means the full marked log is safely written locally before cleanup.
    shell(serial, f'rm -- {shlex.quote(remote_script)} {shlex.quote(remote_log)}')
    state['status'] = 'collected'
    state['collectedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    state_path.write_text(json.dumps(state, indent=2) + '\n')
    print(f'Saved {destination}; removed the two temporary device files.')
    print('Compare battery USB powered transitions with new, deduplicated '
          'StatusBar timestamps. These are not physical refresh measurements.')


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest='command', required=True)
    begin = commands.add_parser('start', help='Start a bounded device-side sampler')
    begin.add_argument('session', type=pathlib.Path)
    begin.add_argument('--serial', help='Authorized adb serial; required if ambiguous')
    begin.add_argument('--duration', type=int, default=60, help='Seconds, 10–80 (default 60)')
    begin.add_argument('--interval', type=int, default=5, help='Seconds, 5–15 (default 5)')
    begin.set_defaults(action=start)
    end = commands.add_parser('collect', help='Fetch the completed log after reconnecting')
    end.add_argument('session', type=pathlib.Path)
    end.set_defaults(action=collect)
    args = parser.parse_args()
    if args.command == 'start':
        if not 10 <= args.duration <= 80 or not 5 <= args.interval <= 15:
            parser.error('Use duration 10–80 seconds and interval 5–15 seconds.')
    try:
        args.action(args)
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(f'Error: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
