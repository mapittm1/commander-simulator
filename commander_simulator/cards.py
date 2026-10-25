"""Card cost heuristics."""

from __future__ import annotations

from . import settings


def cost(card: str) -> int:
    """Approximate generic casting cost; not an Oracle mana value."""
    if card in settings.RAMP:
        return settings.RAMP[card]
    if card in settings.REMOVAL:
        return 2
    if card in settings.WIPES:
        return 5
    if card in settings.HIGH_COST_CARDS:
        return 6
    if card in settings.LOW_COST_CARDS:
        return 1 if card in settings.OUTLETS or card == settings.SOL_RING else 2
    if card in settings.DRAIN:
        return 2 if card not in settings.EXPENSIVE_DRAIN_CARDS else 3
    if card in settings.FODDER:
        return 3
    return 3
