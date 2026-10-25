"""Standalone regression checks for the data extraction refactor."""

import hashlib
import json
import random

import commander_simulator as simulator


def test_deck_loading():
    decks = simulator.load()
    assert len(decks) == 4
    for name, deck in decks.items():
        assert len(deck) == 99
        assert simulator.settings.COMMANDERS[name] not in deck


def test_simulation_matches_pre_extraction_snapshots():
    # Captured from the original implementation: 100 games and the first log.
    snapshots = {42: '603e4c241e18f7722fc5fc4632bc8276a5eb95d353f89268e290f1d4fcd575e4', 20261025: '80fbd2b66bbd890f6a4552453468de4e9fcd0875f577f01649b73d3e5bc59365', 999: 'c3ab216149fd9c0274c5e17dbd59674626e4b5492b1fddb1a4dc07c4a9a748f3'}
    decks = simulator.load()
    for seed, expected in snapshots.items():
        rng = random.Random(seed)
        results = [simulator.game(decks, rng, log=i == 0) for i in range(100)]
        actual = hashlib.sha256(json.dumps(results, sort_keys=True).encode()).hexdigest()
        assert actual == expected


def test_cost_classification():
    settings = simulator.settings
    assert simulator.cost(settings.SOL_RING) == 1
    assert simulator.cost(settings.NECROPOTENCE) == 3
    for card in settings.HIGH_COST_CARDS:
        assert simulator.cost(card) == 6


def test_cli_exports_from_another_directory():
    import csv
    import os
    import tempfile
    from commander_simulator.cli import main

    original = os.getcwd()
    with tempfile.TemporaryDirectory(dir=__import__("pathlib").Path(__file__).resolve().parent) as directory:
        try:
            os.chdir(directory)
            main(["--games", "3", "--seed", "42", "--output-dir", "exports"])
            with open("exports/" + simulator.settings.RESULTS_FILE, encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream))
            assert sum(int(row["Games"]) for row in rows) == 3
            with open("exports/" + simulator.settings.LOG_FILE, encoding="utf-8") as stream:
                assert len(list(csv.DictReader(stream))) > 0
        finally:
            os.chdir(original)


def test_invalid_turn_limit():
    try:
        simulator.game(simulator.load(), random.Random(42), max_turn=0)
    except ValueError:
        return
    raise AssertionError("Expected a ValueError for a zero turn limit")


if __name__ == "__main__":
    import unittest

    suite = unittest.TestSuite(
        unittest.FunctionTestCase(function)
        for name, function in sorted(globals().copy().items())
        if name.startswith("test_")
    )
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(not result.wasSuccessful())
