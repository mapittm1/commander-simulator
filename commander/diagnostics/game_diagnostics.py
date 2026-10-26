"""Descriptive gameplay observations with explicit coverage and no causal claims."""

from collections import Counter
from dataclasses import dataclass, field

SAMPLE_LIMIT = 10
UNSUPPORTED_METRICS = [
    "mana_screw_or_flood",
    "color_access",
    "uncastable_cards",
    "targeted_removal",
    "human_targeting_motives",
    "hate_or_unfun_card_classification",
]


def _seat_stats(seat: int) -> dict:
    return {
        "seat": seat,
        "observed_turn_markers": 0,
        "complete_main_turns": 0,
        "turns_without_logged_land_play": 0,
        "incomplete_turns": 0,
        "land_plays": 0,
        "off_turn_or_unscoped_land_plays": 0,
        "casts": 0,
        "attacks_received": 0,
        "attacks_assigned": 0,
        "no_land_turn_samples": [],
        "incoming_attack_samples": [],
    }


@dataclass
class ObservationState:
    """Small bounded accumulator for one game's supported observations."""

    seats: list = field(
        default_factory=lambda: [_seat_stats(seat) for seat in range(1, 5)]
    )
    attacks: list = field(default_factory=lambda: [[0] * 4 for _ in range(4)])
    cast_cards: list = field(default_factory=lambda: [Counter() for _ in range(4)])
    current: dict | None = None
    context_issues: int = 0
    last_turn: int = 0

    def close_turn(self) -> None:
        """Only a main-phase turn with observed cleanup enters the no-land denominator."""
        if self.current is None:
            return
        turn = self.current
        stats = self.seats[turn["seat"] - 1]
        if not turn["cleanup"]:
            stats["incomplete_turns"] += 1
        elif turn["main"]:
            stats["complete_main_turns"] += 1
            if turn["lands"] == 0:
                stats["turns_without_logged_land_play"] += 1
                if len(stats["no_land_turn_samples"]) < SAMPLE_LIMIT:
                    stats["no_land_turn_samples"].append(
                        {"turn_marker": turn["turn"], "line": turn["line"]}
                    )

    def observe(self, event: dict) -> None:
        """Apply one parser-validated event without inferring hidden state or ownership."""
        kind = event["kind"]
        seat = event["seat"]
        stats = self.seats[seat - 1]
        if kind == "turn":
            self.close_turn()
            if event["turn"] <= self.last_turn:
                self.context_issues += 1
            self.last_turn = event["turn"]
            self.current = {**event, "main": False, "cleanup": False, "lands": 0}
            stats["observed_turn_markers"] += 1
        elif kind == "phase":
            self._phase(event)
        elif kind == "land":
            stats["land_plays"] += 1
            if self.current and self.current["seat"] == seat:
                self.current["lands"] += 1
            else:
                stats["off_turn_or_unscoped_land_plays"] += 1
        elif kind == "cast":
            stats["casts"] += 1
            self.cast_cards[seat - 1][event["card"]] += 1
        elif kind == "attack":
            self._attack(event)

    def _phase(self, event: dict) -> None:
        if self.current is None or self.current["seat"] != event["seat"]:
            self.context_issues += 1
        else:
            key = "cleanup" if event["phase"] == "Cleanup step" else "main"
            self.current[key] = True

    def _attack(self, event: dict) -> None:
        attacker, target = event["seat"] - 1, event["target"] - 1
        count = len(event["attackers"])
        self.attacks[attacker][target] += count
        self.seats[attacker]["attacks_assigned"] += count
        stats = self.seats[target]
        stats["attacks_received"] += count
        for card in event["attackers"]:
            if len(stats["incoming_attack_samples"]) >= SAMPLE_LIMIT:
                break
            stats["incoming_attack_samples"].append(
                {
                    "line": event["line"],
                    "attacker_seat": attacker + 1,
                    "card": card["card"],
                }
            )


def summarize_events(parsed: dict, game_status: str) -> dict:
    """Describe observations separately from game outcomes and unavailable metrics."""
    state = ObservationState()
    for event in parsed["events"]:
        state.observe(event)
    state.close_turn()
    partial = bool(
        parsed["unparsed_candidates"]
        or parsed["event_limit_reached"]
        or state.context_issues
        or game_status != "completed"
        or not state.last_turn
    )
    for stats, cards in zip(state.seats, state.cast_cards):
        stats["most_logged_casts"] = [
            {"card": card, "casts": count} for card, count in cards.most_common(20)
        ]
    return {
        "schema_version": 1,
        "parser_version": parsed["parser_version"],
        "status": "partial" if partial else "observed",
        "eligible_for_batch_aggregate": not partial,
        "game_status": game_status,
        "events_parsed": len(parsed["events"]),
        "coverage": {
            "unparsed_candidates": parsed["unparsed_candidates"],
            "issue_samples": parsed["issue_samples"],
            "event_limit_reached": parsed["event_limit_reached"],
            "context_issues": state.context_issues,
        },
        "seats": state.seats,
        "attack_assignment_matrix": state.attacks,
        "unsupported_metrics": UNSUPPORTED_METRICS,
        "limitations": [
            "No logged land play is an observation, not proof of a missed land drop or mana issue.",
            "Attack assignments count creatures assigned, not damage, combat phases, or targeting motives.",
            "Only the supported text-log dialect is measured; hidden state and other event kinds are not covered.",
        ],
    }
