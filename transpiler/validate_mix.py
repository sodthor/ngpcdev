#!/usr/bin/env python3
"""Build and test an isolated MIX direct-call fixture without changing the game build."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cc', default='clang-22')
    parser.add_argument('--opt', default='-O1')
    args = parser.parse_args()
    backend = Path(__file__).resolve().parent
    project = backend.parent / 'mix'
    tools = backend.parent.parent / 'as' / 'bin'
    output = Path(tempfile.mkdtemp(prefix='ngpc-mix-validation-'))
    classes = output / 'classes'
    classes.mkdir()
    fixture = output / 'mix'
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

    sources = sorted(backend.glob('*.java')) + sorted((backend / 'sim').rglob('*.java'))
    run('compile-java', ['javac', '-d', classes, *sources])
    text = (fixture / 'makefile.llvm').read_text()
    inputs = re.search(r'^LL\s*=\s*(.*?)(?=\n\n)', text, re.M | re.S)
    if inputs is None:
        raise RuntimeError('Cannot find MIX LLVM inputs')
    inputs = inputs[1].replace('\\\n', ' ').split()
    for ir in inputs:
        # Direct-call tests need out-of-line symbols. Production OPT is unchanged.
        run('clang-' + ir.replace('/', '_'), [args.cc, '--target=arm-none-eabi',
            '-S', args.opt, '-fno-inline', '-emit-llvm', '-DCLANG', '-I.', '-Imusix',
            '-fno-builtin-strlen', '-Wall', ir[:-3] + '.c', '-o', ir])
    java = ['java', '-Xmx2g', '-cp', classes]
    run('translate', [*java, 'Ir2Tlcs900', *inputs, '--auto-register-allocation',
                     '-o', 'MIX.asm', '--data-output', 'MIX.inc'])
    run('optimize', [*java, 'AsmOptimizer', 'MIX.asm', '-v',
                     '--pre-opt-output', 'MIX.preopt.asm'])
    includes = 'Z:' + str(backend.parent / 'asm').replace('/', '\\')
    for variant in ('preopt', 'optimized'):
        directory = fixture / variant
        directory.mkdir(exist_ok=True)
        assembly = 'MIX.preopt.asm' if variant == 'preopt' else 'MIX.asm'
        shutil.copyfile(fixture / assembly, directory / 'MIX.asm')
        shutil.copyfile(fixture / 'MIX.inc', directory / 'MIX.inc')
        for iteration in range(20):
            log = run(variant + '-assemble-' + str(iteration),
                      ['wine', tools / 'asl.exe', '-U', '-i', includes, '-g', 'map', 'MIX.asm'], directory)
            change = subprocess.run([str(a) for a in [*java, 'Ir2Tlcs900',
                '--shorten-jumps-from-warnings', 'MIX.asm', log]], cwd=directory,
                capture_output=True, text=True, timeout=120)
            if change.returncode:
                raise RuntimeError('Jump shortening failed: ' + change.stdout + change.stderr)
            if int(change.stdout.strip()) == 0:
                break
        else:
            raise RuntimeError('Jump shortening did not converge')
        run(variant + '-p2bin', ['wine', tools / 'p2bin.exe', '-r', '$-$', 'MIX.p', 'MIX.ngp'], directory)
        for test, extra in (
                ('sim.tests.GemsStringRenderTest', [project / 'mixit.h']),
                ('sim.tests.VexedTest', [project / 'vexed' / 'levels.h']),
                ('sim.tests.GemsLevelUpTest', [])):
            log = run(variant + '-' + test.split('.')[-1],
                      [*java, test, directory / 'MIX.ngp', directory / 'MIX.map', *extra])
            print(variant + ': ' + log.read_text().strip(), flush=True)
    print('MIX optimizer validation: PASS (preopt and optimized)', flush=True)


if __name__ == '__main__':
    main()