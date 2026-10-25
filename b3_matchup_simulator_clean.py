"""Compatibility entry point; implementation lives in commander_simulator."""

from commander_simulator import Player, cost, game, load, settings
from commander_simulator.cli import main

if __name__ == "__main__":
    main()
