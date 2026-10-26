"""Limited log-witness regressions for one closed, legal Commander fixture pod."""

import re

from commander.engine.engine_fixtures import FOREST_POD
from commander.engine.engine_process import MAX_LOG_BYTES
from commander.engine.forge_results import parse_game_result

CHECKER_VERSION = 1
RULES_REFERENCE = "https://magic.wizards.com/en/formats/commander"
PLAYER = r"Ai\(([1-4])\)-seat-\1"
LAND = re.compile(rf"Land: {PLAYER} played Forest \(([0-9]{{1,10}})\)")
MANA = re.compile(r"Mana: Forest \(([0-9]{1,10})\) - \{T\}: Add \{G\}\.")
LIFE = re.compile(rf"Life: Life: {PLAYER} (-?[0-9]{{1,6}}) > (-?[0-9]{{1,6}})")
DAMAGE = re.compile(
    r"Damage: (.{1,300}) \([0-9]{1,10}\) deals ([0-9]{1,6}) combat damage to Ai\(([1-4])\)-seat-\3\."
)
LOSS = re.compile(
    rf"Game Outcome: {PLAYER} has lost due to accumulation of 21 damage from generals"
)


def check_rules_fixture(text: str, inputs: dict, result: dict) -> dict:
    """Require actual witnesses; missing exercise is inconclusive rather than a pass."""
    if (
        not isinstance(text, str)
        or len(text.encode("utf-8")) > MAX_LOG_BYTES
        or text.count("\n") > 100000
    ):
        raise ValueError("Conformance log exceeds supported limits")
    if not _expected_fixture(inputs):
        return _inconclusive("Inputs are not the closed all-Forest fixture pod")
    parsed = parse_game_result(
        text, result.get("process", {"status": "missing", "returncode": 1})
    )
    if result.get("status") != "completed" or parsed["status"] != "completed":
        return _inconclusive("Engine did not produce a recognized completed game")
    lines = text.split("\n")
    checks = [
        _tax_check(lines),
        _combat_trigger_check(lines),
        _commander_loss_check(lines),
    ]
    status = "passed"
    if any(check["status"] == "failed" for check in checks):
        status = "failed"
    elif any(check["status"] == "not_exercised" for check in checks):
        status = "inconclusive"
    return {
        "checker_version": CHECKER_VERSION,
        "fixture": "all_forest_pod_v1",
        "status": status,
        "checks": checks,
        "rules_reference": RULES_REFERENCE,
        "limitations": [
            "These are limited closed-deck log regressions, not complete rules conformance or AI validation.",
            "The commander-damage check does not prove the exact 21-point boundary or every zone-change interaction.",
            "Stack priority, targeted removal, replacement effects, and combo correctness require additional fixtures.",
        ],
    }


def _inconclusive(reason) -> dict:
    return {
        "checker_version": CHECKER_VERSION,
        "fixture": "all_forest_pod_v1",
        "status": "inconclusive",
        "reason": reason,
        "checks": [],
    }


def _expected_fixture(inputs) -> bool:
    try:
        seats = inputs["seats"]
        if len(seats) != 4:
            return False
        for seat, name in zip(seats, FOREST_POD):
            cards = seat["revision"]["cards"]
            actual = sorted(
                (entry["zone"], entry["card"]["name"], entry["quantity"])
                for entry in cards
            )
            if actual != [("commander", name, 1), ("mainboard", "Forest", 99)]:
                return False
        commander = next(
            entry
            for entry in seats[3]["revision"]["cards"]
            if entry["zone"] == "commander"
        )
        ruxa = commander["oracle"]
        goreclaw = next(
            entry["oracle"]
            for entry in seats[1]["revision"]["cards"]
            if entry["zone"] == "commander"
        )
        return (
            ruxa.get("mana_cost") == "{2}{G}{G}"
            and goreclaw.get("power") == "4"
            and "gets +1/+1" in goreclaw.get("oracle_text", "")
        )
    except (KeyError, TypeError, StopIteration):
        return False


def _tax_check(lines) -> dict:
    owners, pending, observations = {}, [], []
    for number, line in enumerate(lines, 1):
        line = line.rstrip("\r")
        land = LAND.fullmatch(line)
        if land:
            owners[int(land[2])] = int(land[1])
        mana = MANA.fullmatch(line)
        if mana:
            pending.append((int(mana[1]), number))
            continue
        if line == "Add To Stack: Ai(4)-seat-4 cast Ruxa, Patient Professor":
            if len(observations) >= 32:
                return {
                    "id": "commander_tax_recast",
                    "status": "not_exercised",
                    "reason": "Witness limit exceeded",
                }
            if not pending or any(owners.get(card_id) != 4 for card_id, _ in pending):
                return {
                    "id": "commander_tax_recast",
                    "status": "not_exercised",
                    "reason": "Cannot attribute the mana payment",
                }
            observations.append(
                {
                    "line": number,
                    "expected_cost": 4 + 2 * len(observations),
                    "observed_one_mana_activations": len(pending),
                    "distinct_sources": len({card_id for card_id, _ in pending}),
                }
            )
        pending = []
    failed = any(
        row["expected_cost"] != row["observed_one_mana_activations"]
        or row["distinct_sources"] != row["observed_one_mana_activations"]
        for row in observations
    )
    return {
        "id": "commander_tax_recast",
        "status": _case_status(failed, len(observations) >= 2),
        "evidence": observations[:10],
        "scope": "Ruxa's contiguous basic-Forest payments, including at least one recast",
    }


def _combat_trigger_check(lines) -> dict:
    triggered, resolved, unblocked = None, None, set()
    observations = []
    for number, line in enumerate(lines, 1):
        if (
            line.rstrip("\r")
            == "Add To Stack: Ai(2)-seat-2 triggered Goreclaw, Terror of Qal Sisma"
        ):
            triggered, resolved = number, None
            unblocked.clear()
        elif (
            triggered
            and line.startswith(
                "Resolve Stack: Whenever Goreclaw, Terror of Qal Sisma attacks,"
            )
            and "gets +1/+1" in line
        ):
            resolved = number
        elif "End of Combat Step" in line:
            triggered, resolved = None, None
            unblocked.clear()
        block = re.fullmatch(
            rf"Combat: {PLAYER} didn't block Goreclaw, Terror of Qal Sisma \([0-9]{{1,10}}\)\.",
            line.rstrip("\r"),
        )
        if block:
            unblocked.add(int(block[1]))
        damage = DAMAGE.fullmatch(line.rstrip("\r"))
        if (
            damage
            and damage[1] == "Goreclaw, Terror of Qal Sisma"
            and resolved
            and int(damage[3]) in unblocked
        ):
            observations.append(
                {
                    "trigger_line": triggered,
                    "resolve_line": resolved,
                    "damage_line": number,
                    "expected_damage": 5,
                    "observed_damage": int(damage[2]),
                }
            )
    return {
        "id": "goreclaw_attack_trigger",
        "status": _case_status(
            any(row["observed_damage"] != 5 for row in observations), bool(observations)
        ),
        "evidence": observations[:10],
        "scope": "Unblocked Goreclaw deals 5 player combat damage after its attack trigger resolves",
    }


def _commander_loss_check(lines) -> dict:
    damage, life, losses = {}, {}, []
    for number, line in enumerate(lines, 1):
        line = line.rstrip("\r")
        hit = DAMAGE.fullmatch(line)
        if hit and hit[1] in FOREST_POD:
            key = (hit[1], int(hit[3]))
            damage[key] = damage.get(key, 0) + int(hit[2])
        change = LIFE.fullmatch(line)
        if change:
            life[int(change[1])] = int(change[3])
        loss = LOSS.fullmatch(line)
        if loss:
            seat = int(loss[1])
            maximum = max(
                (count for (_, target), count in damage.items() if target == seat),
                default=0,
            )
            losses.append(
                {
                    "line": number,
                    "seat": seat,
                    "largest_single_commander_damage": maximum,
                    "last_logged_life": life.get(seat),
                }
            )
    bad = any(row["largest_single_commander_damage"] < 21 for row in losses)
    positive = any(
        row["last_logged_life"] is not None and row["last_logged_life"] > 0
        for row in losses
    )
    return {
        "id": "commander_loss_with_positive_life",
        "status": _case_status(bad, positive),
        "evidence": losses[:10],
        "scope": "A player loses to one commander's accumulated combat damage while still above zero life",
    }


def _case_status(failed: bool, exercised: bool) -> str:
    if failed:
        return "failed"
    return "passed" if exercised else "not_exercised"
