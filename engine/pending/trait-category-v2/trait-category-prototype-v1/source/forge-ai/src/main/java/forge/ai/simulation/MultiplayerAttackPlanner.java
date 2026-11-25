package forge.ai.simulation;

import forge.ai.PlayerControllerAi;
import forge.ai.AiAttackController;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.combat.AttackConstraints;
import forge.game.combat.AttackRestriction;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.util.AITimeoutTracker;
import forge.util.MyRandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Compare legal multiplayer attacks through successive native opponent turns. */
public final class MultiplayerAttackPlanner {
    private static final int MAX_CANDIDATES = 32;
    private static final int MAX_TURNS = 8;
    private static final ThreadLocal<Boolean> PLANNING = ThreadLocal.withInitial(() -> false);

    private MultiplayerAttackPlanner() { }

    /** Called inside the ordinary attack decision's armed timeout, before original costs are paid. */
    public static void refine(final Player actor, final Combat combat) {
        if (PLANNING.get() || actor.isControlled() || actor.getOpponents().size() < 2
                // With no creatures and no declared attackers, every candidate
                // is the same empty declaration; a future-round score cannot change it.
                || actor.getCreaturesInPlay().isEmpty() && combat.getAttackers().isEmpty()
                || combat.getAttackers().anyMatch(card -> card.getController() != actor)
                || !actor.getGame().getPhaseHandler().is(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
            return;
        }
        PLANNING.set(true);
        try {
            MyRandom.withRandom(new Random(0L), () -> {
                final Search search = new Search(actor, combat);
                try {
                    search.run();
                } catch (final AITimeoutTracker.Expired e) {
                    // Retain the best fully evaluated legal declaration when the shared deadline expires.
                }
                if (search.best != null) {
                    combat.clearAttackers();
                    for (final Map.Entry<Card, GameEntity> entry : search.best.entrySet()) {
                        combat.addAttacker(entry.getKey(), entry.getValue());
                    }
                }
                return null;
            });
        } finally {
            PLANNING.remove();
        }
    }

    /** A single-combat forecast must not recursively start a whole-round search. */
    public static <T> T withCombatForecast(final java.util.function.Supplier<T> forecast) {
        final boolean previous = PLANNING.get();
        PLANNING.set(true);
        try {
            return forecast.get();
        } finally {
            if (previous) {
                PLANNING.set(true);
            } else {
                PLANNING.remove();
            }
        }
    }

    private static final class Search {
        private final Player actor;
        private final Player beneficiary;
        private final Combat combat;
        private final AttackConstraints constraints;
        private final Map<Card, GameEntity> required;
        private final int minimumViolations;
        private final Set<String> seen = new HashSet<>();
        private Map<Card, GameEntity> best;
        private Integer bestValue;
        private int evaluated;

        private Search(final Player actor, final Combat combat) {
            this.actor = actor;
            this.beneficiary = DecisionPerspective.beneficiary(actor);
            this.combat = combat;
            this.constraints = combat.getAttackConstraints();
            final var legal = constraints.getLegalAttackers();
            this.required = new LinkedHashMap<>(legal.getLeft());
            this.minimumViolations = legal.getRight();
        }

        private void run() {
            if (onlyEmptyDeclaration()) return;
            final Map<Card, GameEntity> heuristic = new LinkedHashMap<>(combat.getAttackersAndDefenders());
            consider(heuristic);
            consider(required);
            consider(Collections.emptyMap());
            // Include whole-group attacks so "can't attack alone" does not hide a legal declaration.
            for (final GameEntity defender : combat.getDefenders()) {
                final Map<Card, GameEntity> group = new LinkedHashMap<>(required);
                for (final Card creature : actor.getCreaturesInPlay()) {
                    if (CombatUtil.canAttack(creature, defender)) {
                        group.put(creature, defender);
                    }
                }
                consider(group);
                if (evaluated >= MAX_CANDIDATES) {
                    return;
                }
            }
            // Preserve mandatory assignments while adding, omitting or retargeting individual creatures.
            for (final Map<Card, GameEntity> base : List.of(required, heuristic)) {
                for (final Card creature : actor.getCreaturesInPlay()) {
                    final Map<Card, GameEntity> fewer = new LinkedHashMap<>(base);
                    fewer.remove(creature);
                    consider(fewer);
                    for (final GameEntity defender : combat.getDefenders()) {
                        final Map<Card, GameEntity> changed = new LinkedHashMap<>(base);
                        changed.put(creature, defender);
                        consider(changed);
                        if (evaluated >= MAX_CANDIDATES) {
                            return;
                        }
                    }
                }
            }
        }

        /** Reuse native restrictions; never infer legality from card names or scores. */
        private boolean onlyEmptyDeclaration() {
            AITimeoutTracker.check();
            if (!combat.getAttackers().isEmpty() || !required.isEmpty()
                    || constraints.getClass() != AttackConstraints.class
                    || constraints.countViolations(Collections.emptyMap()) != minimumViolations) {
                return false;
            }
            for (final GameEntity defender : combat.getDefenders()) {
                if (defender.getClass() != Player.class && defender.getClass() != Card.class) return false;
            }
            for (final Card creature : actor.getCreaturesInPlay()) {
                AITimeoutTracker.check();
                if (creature.getClass() != Card.class) return false;
                final AttackRestriction restriction = constraints.getRestrictions().get(creature);
                if (restriction == null || restriction.getClass() != AttackRestriction.class) return false;
                for (final GameEntity defender : combat.getDefenders()) {
                    if (restriction.canAttack(defender)) return false;
                }
            }
            return true;
        }

        private void consider(final Map<Card, GameEntity> declaration) {
            AITimeoutTracker.check();
            if (evaluated >= MAX_CANDIDATES) {
                return;
            }
            for (final Map.Entry<Card, GameEntity> entry : declaration.entrySet()) {
                if (entry.getKey().getController() != actor || !combat.getDefenders().contains(entry.getValue())
                        || !CombatUtil.canAttack(entry.getKey(), entry.getValue())) {
                    return;
                }
            }
            final int violations = constraints.countViolations(declaration);
            if (violations < 0 || violations > minimumViolations) {
                return;
            }
            final List<String> identities = new ArrayList<>();
            for (final Map.Entry<Card, GameEntity> entry : declaration.entrySet()) {
                identities.add(entry.getKey().getId() + ">" + entry.getValue().getId());
            }
            Collections.sort(identities);
            if (!seen.add(String.join(",", identities))) {
                return;
            }
            final Integer value = score(declaration);
            evaluated++;
            if (value == null) {
                return;
            }
            if (bestValue == null || value > bestValue) {
                bestValue = value;
                best = new LinkedHashMap<>(declaration);
            }
        }

        private Integer score(final Map<Card, GameEntity> declaration) {
            // Nested scopes restore the enclosing search's provider and never
            // increment the live undo counter. Each candidate has equal samples.
            return MyRandom.withRandom(new Random(0L), () -> scoreWithRandom(declaration));
        }

        private Integer scoreWithRandom(final Map<Card, GameEntity> declaration) {
            final GameSimulator simulator = GameSimulator.forRollout(actor.getGame(), actor, true);
            final Game game = simulator.getSimulatedGameState();
            final Player copiedActor = simulator.getSimulatedAiPlayer();
            final Player copiedBeneficiary = simulator.getGameCopier().find(beneficiary);
            final Combat copiedCombat = game.getCombat();
            final Map<Card, GameEntity> mapped = new LinkedHashMap<>();
            for (final Map.Entry<Card, GameEntity> entry : declaration.entrySet()) {
                final Card attacker = simulator.getGameCopier().find(entry.getKey());
                final GameEntity defender = simulator.getGameCopier().find(entry.getValue());
                if (attacker == null || defender == null) {
                    throw new IllegalStateException("Multiplayer attack cannot be mapped into its simulation");
                }
                mapped.put(attacker, defender);
            }
            copiedCombat.clearAttackers();
            ((PlayerControllerAi) copiedActor.getController()).getAi().setAttackOverride(mapped);
            game.getPhaseHandler().devDeclareAttackers();
            GameSimulator.resolveStack(game, copiedActor.getWeakestOpponent());
            if (!game.isGameOver() && copiedActor.isInGame()) {
                game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2,
                        () -> GameSimulator.resolveStack(game, copiedActor.getWeakestOpponent()));
            }
            if (!game.isGameOver() && !copiedBeneficiary.hasLost() && !copiedBeneficiary.hasWon()
                    && !game.getPhaseHandler().is(PhaseType.MAIN2)) {
                // A skipped/reversed/aborted phase path is not a comparable completed combat.
                return null;
            }
            if (!advanceOpponentTurns(game, copiedActor)) {
                return null;
            }
            return new GameStateEvaluator().getScoreForCurrentGameState(game, copiedBeneficiary).value;
        }

        /** Carry damage, deaths, cleanup, draws and untaps across actual turns. */
        private boolean advanceOpponentTurns(final Game game, final Player copiedActor) {
            for (int turn = 0; turn < MAX_TURNS; turn++) {
                AITimeoutTracker.check();
                if (game.isGameOver() || !copiedActor.isInGame()) {
                    return true;
                }
                if (!game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1,
                        () -> settle(game, copiedActor))) {
                    return game.isGameOver() || !copiedActor.isInGame();
                }
                if (game.isGameOver() || !copiedActor.isInGame()) {
                    return true;
                }
                final Player active = game.getPhaseHandler().getPlayerTurn();
                if (active == copiedActor) {
                    return true;
                }
                if (active.isOpponentOf(copiedActor) && active.getController() instanceof PlayerControllerAi controller) {
                    // Stress an attack on the actor while retaining the normal
                    // heuristic's creature selection and attack requirements.
                    final Combat projected = new Combat(active);
                    new AiAttackController(active, false, copiedActor).declareAttackers(projected);
                    if (!CombatUtil.validateAttackers(projected)) {
                        return false;
                    }
                    controller.getAi().setAttackOverride(new LinkedHashMap<>(projected.getAttackersAndDefenders()));
                }
                if (!game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2,
                        () -> settle(game, copiedActor))) {
                    return game.isGameOver() || !copiedActor.isInGame();
                }
                settle(game, copiedActor);
            }
            // Extra/reversed/skipped turn paths that exceed the bounded horizon
            // are inconclusive, rather than silently scored as safe.
            return false;
        }

        private void settle(final Game game, final Player copiedActor) {
            final Player resolver = copiedActor.isInGame() ? copiedActor : game.getPlayers().get(0);
            GameSimulator.resolveStack(game, resolver);
        }

    }
}
