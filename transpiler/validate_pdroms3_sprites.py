#!/usr/bin/env python3
"""Run complete direct-call sprite/gameplay checks without replacing the game ROM."""
import os
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--opt', default='-O3')
    args = parser.parse_args()
    backend = Path(__file__).resolve().parent
    project = backend.parent / 'pdroms3'
    output = Path(tempfile.mkdtemp(prefix='ngpc-pdroms3-sprites-'))
    source = output / 'src'
    source.mkdir()
    fixture = source / 'pdroms3'
    shutil.copytree(project, fixture)
    (source / 'transpiler').symlink_to(backend, target_is_directory=True)
    (source / 'asm').symlink_to(backend.parent / 'asm', target_is_directory=True)
    (output / 'as').symlink_to(backend.parent.parent / 'as', target_is_directory=True)
    print('Sprite validation artifacts: ' + str(output), flush=True)
    # Direct-call tests need all standalone entry points. Do not change the
    # production flags/ROM: inlining removes loadCommon and other test symbols.
    log = output / 'validation.log'
    with log.open('w') as stream:
        process = subprocess.run(['make', '-C', str(fixture), '-f', 'makefile.llvm',
            '-j1', 'test', 'OPT=' + args.opt, 'EXTRA_CFLAGS=-fno-inline'],
            env=dict(os.environ, WINEDEBUG='-all'), stdout=stream,
            stderr=subprocess.STDOUT, timeout=1200)
    for line in log.read_text().splitlines():
        if 'PASS' in line and 'PASS 1' not in line and 'PASS 2' not in line:
            print(line, flush=True)
    if process.returncode:
        raise RuntimeError('Sprite validation failed; see ' + str(log) + '\n' + log.read_text()[-4000:])
    print('PDROMS3 isolated sprite validation: PASS', flush=True)


if __name__ == '__main__':
    main()