#!/usr/bin/env python3
"""Fail-closed MODE7 allocated-renderer specialization; never a generic ASM pass."""
import argparse
from pathlib import Path
import re

MARKER = '; mode7-group-bounds: validated-v1'
FIXTURE = Path(__file__).resolve().parent / 'fixtures' / 'mode7_update_allocated.asm'


def canonical(text):
    # Jump shortening changes encodings, not CFG. Comments other than required
    # ABI/fence metadata are not used as a semantic proof.
    return '\n'.join(' '.join(line.split(';', 1)[0].lower().split()).replace('jrl ', 'jr ')
                     for line in text.splitlines() if line.split(';', 1)[0].strip())


def specialize(text):
    if MARKER in text:
        return text, 'already specialized'
    if '; register-bank-abi: current=0' not in text.splitlines():
        return text, 'missing current-bank ABI'
    entries = list(re.finditer(r'(?m)^_update:\s*$', text))
    if len(entries) != 1:
        return text, 'missing or ambiguous update entry'
    start = entries[0].end()
    fence = '; experimental-register-function-end update'
    end = text.find(fence, start)
    if end < 0:
        return text, 'missing allocator fence'
    end += len(fence)
    function = text[start:end]
    expected = FIXTURE.read_text()
    if '; function-abi: return=void clobbers=xwa,xhl' not in function.splitlines():
        return text, 'missing void scratch ABI'
    # Exact normalized code identity proves register roles, all eight pixel CFGs,
    # bounds mask, coordinate updates, stack offsets, stores and common exit.
    # Any compiler/allocator change requires deliberate fixture review, not a
    # permissive regex rewrite. No outside instruction may enter a private label.
    if canonical(function) != canonical(expected):
        return text, 'allocated update differs from validated fixture'
    labels = re.findall(r'(?m)^\s*([.][\w.$]+):', function)
    outside = text[:entries[0].start()] + text[end:]
    outside = '\n'.join(line.split(';', 1)[0] for line in outside.splitlines())
    if any(re.search(r'(?<![\w.$])' + re.escape(label) + r'(?![\w.$])', outside, re.IGNORECASE) for label in labels):
        return text, 'external entry into private update CFG'
    if '.lmode7gb_' in text.lower():
        return text, 'specialization label collision'
    head = function.find('.Lbb_update_alloc_69:\n')
    tail = function.find('.Lbb_update_alloc_exit:\n', max(head, 0))
    if head < 0 or tail < 0:
        return text, 'unsupported allocator label formatting'
    body = function[head:tail]
    if re.search(r'\b(?:xsp|call|calr|push|pop|rfp|sr)\b', canonical(body)):
        return text, 'stack/call/bank barrier inside allocated pixel loop'
    pattern = (r'\tld (de|ix), qbc\n\tor \1, qhl\n\tcp \1, 2\n\tjr C, ([.\w]+)\n'
               r'(?:[.\w]+:\n)?\t(?:ld xde, 3|or xde, 3)\n\tjr ([.\w]+)\n\2:\n')
    # Normalize jump encodings for matching; assembler shortens them afterward.
    body = body.replace('\tjrl ', '\tjr ')
    fast, count = re.subn(pattern, lambda m: m[2] + ':\n', body)
    if count != 8:
        return text, 'pixel windows do not match'
    fast = fast.replace('.Lbb_update_alloc_', '.Lmode7gb_fast_')
    fast = fast.replace('jr C, .Lmode7gb_fast_69', 'jrl C, .Lmode7gb_guard')
    slow = body.replace('.Lbb_update_alloc_', '.Lmode7gb_slow_')
    slow = slow.replace('jr C, .Lmode7gb_slow_69', 'jrl C, .Lmode7gb_guard')
    slow = slow.replace('\tld xde, 3\n', '.Lmode7gb_first_out:\n\tld xde, 3\n', 1)
    setup = '''
\tpush xwa2
\tpush xbc2
\tld xwa, xbc1
\tadd xwa, 18724
\tcp xwa, 37449
\tjrl NC, .Lbb_update_alloc_69
\tld xwa, xhl1
\tadd xwa, 18724
\tcp xwa, 37449
\tjrl NC, .Lbb_update_alloc_69
\tld xwa, xbc1
\tsla 3, xwa
\tsub xwa, xbc1
\tld xwa2, xwa
\tld xwa, xhl1
\tsla 3, xwa
\tsub xwa, xhl1
\tld xbc2, xwa
.Lmode7gb_guard:
\tld ix, qbc
\tor ix, qhl
\tcp ix, 2
\tjrl NC, .Lmode7gb_first_out
\tld xix, xhl
\tadd xix, xwa2
\tld xiz, xbc
\tadd xiz, xbc2
\tor xix, xiz
\tcp xix, 020000h
\tjrl C, .Lmode7gb_fast_69
\tjrl .Lmode7gb_slow_77
'''
    jump = '\tjrl .Lbb_update_alloc_exit\n'
    function = function[:head] + MARKER + setup + slow + jump + fast + jump + body + function[tail:]
    function = function.replace('.Lbb_update_alloc_exit:\n',
                                '.Lbb_update_alloc_exit:\n\tpop xbc2\n\tpop xwa2\n', 1)
    # New slow/fast layout may exceed short-jump range. Start long throughout
    # this function, then let the existing assembler-warning loop shorten it.
    function = re.sub(r'(?m)^(\s*)jr ', r'\1jrl ', function)
    return text[:start] + function + text[end:], 'specialized eight-pixel loop'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('assembly', type=Path)
    parser.add_argument('--require-match', action='store_true', help='Fail if current output is unsupported')
    args = parser.parse_args()
    before = args.assembly.read_text()
    after, reason = specialize(before)
    print('[MODE7 group bounds] ' + reason)
    if args.require_match and MARKER not in after:
        raise SystemExit(1)
    if after != before:
        args.assembly.write_text(after)


if __name__ == '__main__':
    main()