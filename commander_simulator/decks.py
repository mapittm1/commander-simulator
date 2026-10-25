"""Moxfield deck-list loading."""

from __future__ import annotations

from . import settings
from .settings import DATA_DIR


def load() -> dict[str, list[str]]:
    """Read 100-card Moxfield lists and remove commanders from libraries."""
    out = {}
    for name, file in settings.FILES.items():
        deck = []
        for line in (DATA_DIR / file).read_text(encoding="utf-8-sig").splitlines():
            n, card = line.split(' ', 1)
            deck.extend([card] * int(n))
        if len(deck) != 100 or deck.count(settings.COMMANDERS[name]) != 1:
            raise ValueError(f"{name}: expected 100 cards including exactly one commander; got {len(deck)} cards")
        deck.remove(settings.COMMANDERS[name])
        out[name] = deck
    return out
