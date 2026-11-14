import random
import unittest
from mode7_group_bounds import FIXTURE, MARKER, specialize


class GroupBoundsTest(unittest.TestCase):
    def setUp(self):
        self.original = '; register-bank-abi: current=0\n_update:\n' + FIXTURE.read_text() + '\n_other:\n\tret\n'

    def test_supported_and_idempotent(self):
        output, _ = specialize(self.original)
        self.assertIn(MARKER, output)
        self.assertEqual(specialize(output)[0], output)
        self.assertEqual(output.count('push xwa2'), self.original.count('push xwa2') + 1)
        self.assertIn('.Lbb_update_alloc_exit:\n\tpop xbc2\n\tpop xwa2\n', output)

    def test_fail_closed(self):
        # Register-role, memory, constant, CFG, ABI and stack changes all reject.
        for old, new in [('xbc1', 'xde1'), ('(xsp+12)', '(xsp+14)'), ('cp ix, 2', 'cp ix, 3'),
                         ('add xhl, xbc1', 'adc xhl, xbc1'), ('ld a, (xiz+ix)', 'call _opaque'),
                         ('register-bank-abi: current=0', 'register-bank-abi: current=1'),
                         ('return=void', 'return=i32'), ('pop xbc', 'pop xwa')]:
            with self.subTest(old=old):
                bad = self.original.replace(old, new, 1)
                self.assertEqual(specialize(bad)[0], bad)
        for extra in ['\n\tjr .Lbb_update_alloc_77\n', '\n\tjr .LBB_UPDATE_ALLOC_77\n',
                      '\n_update:\nret\n', '\n.Lmode7gb_guard:\n']:
            bad = self.original + extra
            self.assertEqual(specialize(bad)[0], bad)
        bad = self.original.replace('.Lbb_update_alloc_69:', '.LBB_UPDATE_ALLOC_69:')
        self.assertEqual(specialize(bad)[0], bad)

    def test_jump_shortening(self):
        output, _ = specialize(self.original.replace('\tjrl ', '\tjr '))
        self.assertIn(MARKER, output)

    def test_final_cleanup_fixture(self):
        # Final generic DCE removes this copy: XWA2 is overwritten before
        # any read, while XWA continues to compute the tile address.
        window = ('\tld xwa, xiy\n\tsla 4, xwa\n\tand xwa, 0380h\n'
                  '\tsla 1, xwa\n\tadd xwa, 0a000h\n\tld (xsp+40), xwa\n'
                  '\tld wa, iy\n\tand xwa, 7\n\tld xwa2, xwa\n')
        self.assertIn(window, FIXTURE.read_text())
        output, reason = specialize(self.original)
        self.assertIn(MARKER, output, reason)
        # Keep the identity gate strict: a live value/role change is not
        # accepted just because this one dead copy disappeared.
        bad = self.original.replace(window, window.replace('ld wa, iy', 'ld wa, ix'), 1)
        self.assertEqual(specialize(bad)[0], bad)

    def test_endpoint_proof(self):
        rng = random.Random(9007)
        accepted = 0
        for i in range(100000):
            x, y = (rng.randrange(131072) for _ in range(2)) if i % 2 else (rng.randrange(2**32) for _ in range(2))
            dx, dy = (rng.randrange(-18724, 18725) for _ in range(2)) if i % 3 else (rng.randrange(-2**31, 2**31) for _ in range(2))
            if (abs(dx) <= 18724 and abs(dy) <= 18724
                    and not ((x | y | ((x + 7*dx) & 0xffffffff) | ((y + 7*dy) & 0xffffffff)) & 0xfffe0000)):
                accepted += 1
                self.assertTrue(all(0 <= x+p*dx <= 131071 and 0 <= y+p*dy <= 131071 for p in range(8)))
        self.assertGreater(accepted, 1000)


if __name__ == '__main__':
    unittest.main()