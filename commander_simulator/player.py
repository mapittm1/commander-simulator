"""Player state and heuristic board evaluation."""

from __future__ import annotations

from . import settings


class Player:
    """Mutable player state with deliberately abstract battlefield mechanics."""

    def __init__(self, name, deck, rng):
        self.name = name
        self.library = deck.copy()
        rng.shuffle(self.library)
        self.hand = []
        self.lands = 0
        self.rocks = 0
        self.ramp_pending = 0
        self.board = []
        self.life = 40
        self.damage = 0
        self.cmd = False
        self.tax = 0
        self.tokens = 0
        self.drain = 0
        self.treasures = 0
        self.alive = True
        self.draw(7)
        if sum((x in settings.LANDS for x in self.hand)) not in range(2, 6):
            self.library += self.hand
            rng.shuffle(self.library)
            self.hand = []
            self.draw(7)

    def draw(self, n=1):
        for _ in range(n):
            if self.library:
                self.hand.append(self.library.pop())

    def power(self):
        return self.tokens + len([x for x in self.board if x not in settings.RAMP and x not in settings.WIPES]) + self.drain * 1.3 + self.treasures * 0.5 + (4 if self.cmd else 0)

    def mana(self):
        return self.lands + self.rocks

    def engine(self):
        return len([x for x in self.board if x in settings.ENGINES[self.name]])
