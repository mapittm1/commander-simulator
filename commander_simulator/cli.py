"""Four-player Commander heuristic simulator (not a full Magic rules engine).

Uses real 99-card libraries; card effects, costs and combat are approximations.
Outcomes are NOT credible estimates of actual Commander matchup win rates.
"""

from __future__ import annotations

import argparse
import csv
import random
from collections import Counter
from pathlib import Path

from . import settings
from .decks import load
from .simulation import game


def main(argv: list[str] | None = None) -> None:
    """Run the CLI batch and export aggregate results and one sample log."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--games', type=int, default=2000)
    parser.add_argument('--seed', type=int, default=20261025)
    parser.add_argument("--output-dir", type=Path, default=Path.cwd(), help="Directory for CSV exports")
    args = parser.parse_args(argv)
    if args.games < 1:
        parser.error("--games must be at least 1")
    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    rng = random.Random(args.seed)
    decks = load()
    wins = Counter()
    turns = []
    sample = []
    for i in range(args.games):
        m, ev = game(decks, rng, log=i == 0)
        wins[m['winner']] += 1
        turns.append(m['turns'])
        if i == 0:
            sample = ev
    with (output_dir / settings.RESULTS_FILE).open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['Outcome', 'Games', 'Percent'])
        for k, v in wins.most_common():
            w.writerow([k, v, round(v / args.games * 100, 2)])
    with (output_dir / settings.LOG_FILE).open('w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=['turn', 'player', 'action', 'life', 'lands', 'ramp', 'board', 'tokens'])
        w.writeheader()
        w.writerows(sample)
    print('games', args.games, 'seed', args.seed, 'wins', wins, 'mean turns', round(sum(turns) / len(turns), 2), 'sample events', len(sample))
