#!/usr/bin/env python3
"""Build baseline and specialized MODE7 ROMs in isolation and compare rendering."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cc', default='clang-22')
    parser.add_argument('--opt', default='-O1')
    args = parser.parse_args()
    backend = Path(__file__).resolve().parent
    project = backend.parent / 'mode7'
    tools = backend.parent.parent / 'as' / 'bin'
    output = Path(tempfile.mkdtemp(prefix='ngpc-mode7-group-bounds-'))
    classes = output / 'classes'
    classes.mkdir()
    fixture = output / 'mode7'
    shutil.copytree(project, fixture)
    environment = dict(os.environ, WINEDEBUG='-all')
    print('Validation artifacts: ' + str(output), flush=True)

    def run(name, command, cwd=fixture):
        log = output / (name + '.log')
        with log.open('w') as stream:
            process = subprocess.run([str(a) for a in command], cwd=cwd, env=environment,
                                     stdout=stream, stderr=subprocess.STDOUT, timeout=1200)
        if process.returncode:
            raise RuntimeError(name + ' failed:\n' + log.read_text())
        return log

    run('python-tests', ['python3', '-m', 'unittest', 'discover', '-s', backend,
                        '-p', 'mode7_group_bounds_test.py', '-v'])
    sources = sorted(backend.glob('*.java')) + sorted((backend / 'sim').rglob('*.java'))
    run('compile-java', ['javac', '-d', classes, *sources])
    for name in ('main', 'music', 'library'):
        run('clang-' + name, [args.cc, '--target=arm-none-eabi', '-S', args.opt,
            '-emit-llvm', '-DCLANG', '-Wall', '-Wno-main-return-type', name + '.c', '-o', name + '.ll'])
    java = ['java', '-Xmx2g', '-cp', classes]
    run('translate', [*java, 'Ir2Tlcs900', 'main.ll', 'music.ll', 'library.ll',
        '--auto-register-allocation', '-o', 'MODE7.asm', '--data-output', 'MODE7.inc'])
    run('optimize', [*java, 'AsmOptimizer', 'MODE7.asm', '-v', '--pre-opt-output', 'MODE7.preopt.asm'])
    includes = 'Z:' + str(backend.parent / 'asm').replace('/', '\\')
    for variant in ('baseline', 'specialized'):
        directory = fixture / variant
        directory.mkdir(exist_ok=True)
        shutil.copyfile(fixture / 'MODE7.asm', directory / 'MODE7.asm')
        shutil.copyfile(fixture / 'MODE7.inc', directory / 'MODE7.inc')
        if variant == 'specialized':
            log = run('specialize', ['python3', backend / 'mode7_group_bounds.py',
                directory / 'MODE7.asm', '--require-match'])
            print(log.read_text().strip(), flush=True)
        for iteration in range(20):
            log = run(variant + '-assemble-' + str(iteration),
                ['wine', tools / 'asl.exe', '-i', includes, '-g', 'map', 'MODE7.asm'], directory)
            change = subprocess.run([str(a) for a in [*java, 'Ir2Tlcs900',
                '--shorten-jumps-from-warnings', 'MODE7.asm', log]], cwd=directory,
                capture_output=True, text=True, timeout=120)
            if change.returncode:
                raise RuntimeError('Jump shortening failed: ' + change.stdout + change.stderr)
            if int(change.stdout.strip()) == 0:
                break
        else:
            raise RuntimeError('Jump shortening did not converge')
        run(variant + '-p2bin', ['wine', tools / 'p2bin.exe', '-r', '$-$', 'MODE7.p', 'MODE7.ngp'], directory)
        for test, extra in (
                ('sim.mode7.Mode7GraphicsTest', []),
                ('sim.tests.IsrRegisterPreservationTest', []),
                ('sim.mode7.Mode7MotionBench', ['--verify']),
                ('sim.mode7.Mode7UpdateBench', ['--calls', '10'])):
            log = run(variant + '-' + test.split('.')[-1],
                [*java, test, directory / 'MODE7.ngp', directory / 'MODE7.map', *extra])
            print(variant + ': ' + log.read_text().strip(), flush=True)
    log = run('differential', [*java, 'sim.mode7.Mode7GroupBoundsTest',
        fixture / 'baseline', fixture / 'specialized'])
    print(log.read_text().strip(), flush=True)
    print('MODE7 group bounds validation: PASS', flush=True)


if __name__ == '__main__':
    main()