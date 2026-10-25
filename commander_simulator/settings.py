"""Load simulator card data and configuration from the adjacent JSON file."""

import json
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent / "data"
_CONFIG_PATH = DATA_DIR / "simulator_config.json"
_DATA = json.loads(_CONFIG_PATH.read_text(encoding="utf-8"))

FILES = _DATA["files"]
COMMANDERS = _DATA["commanders"]
LANDS = frozenset(_DATA["lands"])
RAMP = _DATA["ramp"]
REMOVAL = frozenset(_DATA["removal"])
WIPES = frozenset(_DATA["wipes"])
OUTLETS = frozenset(_DATA["outlets"])
FODDER = frozenset(_DATA["fodder"])
DRAIN = frozenset(_DATA["drain"])
ENGINES = {name: frozenset(cards) for name, cards in _DATA["engines"].items()}
COST = _DATA["cost"]
HIGH_COST_CARDS = frozenset(_DATA["high_cost_cards"])
LOW_COST_CARDS = frozenset(_DATA["low_cost_cards"])
EXPENSIVE_DRAIN_CARDS = frozenset(_DATA["expensive_drain_cards"])
LAND_RAMP = frozenset(_DATA["land_ramp"])
DRAW_CARDS = frozenset(_DATA["draw_cards"])
TEYSA_TOKEN_CARDS = frozenset(_DATA["teysa_token_cards"])
PROSPER_TOKEN_CARDS = frozenset(_DATA["prosper_token_cards"])
AESI_TOKEN_CARDS = frozenset(_DATA["aesi_token_cards"])
RIN_TOKEN_CARDS = frozenset(_DATA["rin_token_cards"])
SOL_RING = _DATA["sol_ring"]
NECROPOTENCE = _DATA["necropotence"]
TEYSA = _DATA["teysa"]
PROSPER = _DATA["prosper"]
AESI = _DATA["aesi"]
UNRESOLVED = _DATA["unresolved"]
RESULTS_FILE = _DATA["results_file"]
LOG_FILE = _DATA["log_file"]
