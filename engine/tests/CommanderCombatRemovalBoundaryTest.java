package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Separate real paid combat rescue from autonomous action selection in partial Commander pods. */
public class CommanderCombatRemovalBoundaryTest extends AITest {
    @Test public void originalClassicChoosesLethalAttacker() { multiple(false, null); }
    @Test public void originalHybridChoosesLethalAttacker() { multiple(false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void originalFullChoosesLethalAttacker() { multiple(false, AIOption.USE_FULL_SIMULATION); }
    @Test public void originalPaidRemovalIgnoresIndestructible() { indestructible(false); }
    @Test public void originalClassicUnaffordableDeclines() { unavailable("unaffordable", false, null); }
    @Test public void originalHybridUnaffordableDeclines() { unavailable("unaffordable", false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void originalClassicTappedDeclines() { unavailable("tapped", false, null); }
    @Test public void originalHybridTappedDeclines() { unavailable("tapped", false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void originalClassicHexproofDeclines() { unavailable("hexproof", false, null); }
    @Test public void originalHybridHexproofDeclines() { unavailable("hexproof", false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void originalClassicNocombatDeclines() { unavailable("noCombat", false, null); }
    @Test public void originalHybridNocombatDeclines() { unavailable("noCombat", false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void copiedClassicChoosesLethalAttacker() { multiple(true, null); }
    @Test public void copiedHybridChoosesLethalAttacker() { multiple(true, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void copiedFullChoosesLethalAttacker() { multiple(true, AIOption.USE_FULL_SIMULATION); }
    @Test public void copiedPaidRemovalIgnoresIndestructible() { indestructible(true); }
    @Test public void copiedClassicUnaffordableDeclines() { unavailable("unaffordable", true, null); }
    @Test public void copiedHybridUnaffordableDeclines() { unavailable("unaffordable", true, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void copiedClassicTappedDeclines() { unavailable("tapped", true, null); }
    @Test public void copiedHybridTappedDeclines() { unavailable("tapped", true, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void copiedClassicHexproofDeclines() { unavailable("hexproof", true, null); }
    @Test public void copiedHybridHexproofDeclines() { unavailable("hexproof", true, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void copiedClassicNocombatDeclines() { unavailable("noCombat", true, null); }
    @Test public void copiedHybridNocombatDeclines() { unavailable("noCombat", true, AIOption.USE_HYBRID_SIMULATION); }

    private Scenario scenario(boolean fog, boolean copied) { return scenario(fog, copied, "normal"); }

    private Scenario scenario(boolean fog, boolean copied, String boundary) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("combat-removal-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Combat removal"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player enemy = game.getPlayers().get(0), actor = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 12; index++) addCardToZone("Shock", player, ZoneType.Library);
        }
        actor.setLife(3, null);
        for (int index = 0; index < (boundary.equals("unaffordable") ? 3 : 4); index++) addCardToZone("Forest", actor, ZoneType.Battlefield);
        Card source = addCardToZone(fog ? "Fog" : "Labyrinth of Skophos", actor,
                fog ? ZoneType.Hand : ZoneType.Battlefield);
        Card attacker = addCardToZone(boundary.equals("hexproof") ? "Sphinx of the Final Word"
                : boundary.equals("indestructible") ? "Darksteel Colossus" : "Craw Wurm", enemy, ZoneType.Battlefield);
        attacker.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, enemy, 3);
        assertTrue(CombatUtil.canAttack(attacker, actor));
        Combat combat = new Combat(enemy); game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker, actor); attacker.setTapped(true);
        CombatUtil.checkDeclaredAttacker(game, attacker, combat, true); combat.setBlocked(attacker, false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, enemy, false, 3);
        if (boundary.equals("multiple")) {
            Card smaller = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
            smaller.setSickness(false); combat.addAttacker(smaller, actor); smaller.setTapped(true);
            CombatUtil.checkDeclaredAttacker(game, smaller, combat, true); combat.setBlocked(smaller, false);
        }
        if (boundary.equals("tapped")) source.setTapped(true);
        if (boundary.equals("noCombat")) combat.removeFromCombat(attacker);
        game.getPhaseHandler().setPriority(actor); game.copyLastState();
        Game original = game; Player originalActor = actor; Card originalSource = source;
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor); actor = copier.find(actor); enemy = copier.find(enemy);
            source = copier.find(source); attacker = copier.find(attacker);
        }
        assertEquals(ComputerUtilCombat.lifeThatWouldRemain(actor, game.getCombat()),
                boundary.equals("multiple") ? -5 : boundary.equals("hexproof") ? -2
                : boundary.equals("indestructible") ? -8 : boundary.equals("noCombat") ? 3 : -3);
        assertEquals(game.getCombat().isAttacking(attacker), !boundary.equals("noCombat"));
        return new Scenario(game, actor, enemy, source, attacker, original, originalActor, originalSource);
    }

    private SpellAbility action(Scenario setup, boolean fog) {
        SpellAbility result = fog ? setup.source.getFirstSpellAbility()
                : setup.source.getSpellAbilities().stream().filter(sa -> sa.getApi() == ApiType.RemoveFromCombat)
                        .findFirst().orElseThrow();
        result.setActivatingPlayer(setup.actor);
        if (!fog) { assertTrue(result.canTarget(setup.attacker)); result.getTargets().add(setup.attacker); }
        assertTrue(result.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(result, setup.actor, false));
        return result;
    }

    private void multiple(boolean copied, AIOption mode) {
        Scenario setup = scenario(false, copied, "multiple");
        PlayerControllerAi controller = (PlayerControllerAi) setup.actor.getController();
        controller.getAi().setUseSimulation(mode);
        List<SpellAbility> choices = controller.getAi().chooseSpellAbilityToPlay();
        assertNotNull(choices, "One removable attacker causes lethal; removing only the smaller one does not rescue");
        SpellAbility selected = choices.stream().filter(sa -> sa.getApi() == ApiType.RemoveFromCombat)
                .findFirst().orElseThrow();
        assertEquals(selected.getTargets().getFirstTargetedCard(), setup.attacker);
        assertTrue(ComputerUtil.playStack(selected, setup.actor, setup.game));
        assertEquals(selected.getPayingMana().size(), 4); assertTrue(setup.source.isTapped());
        settle(setup.game); assertFalse(setup.game.getCombat().isAttacking(setup.attacker));
        damage(setup); assertFalse(setup.actor.hasLost()); assertEquals(setup.actor.getLife(), 1);
        independent(setup);
    }

    private void indestructible(boolean copied) {
        Scenario setup = scenario(false, copied, "indestructible");
        SpellAbility removal = action(setup, false);
        assertTrue(ComputerUtil.playStack(removal, setup.actor, setup.game));
        assertEquals(removal.getPayingMana().size(), 4); assertTrue(setup.source.isTapped());
        settle(setup.game); assertFalse(setup.game.getCombat().isAttacking(setup.attacker));
        assertEquals(setup.attacker.getZone().getZoneType(), ZoneType.Battlefield);
        damage(setup); assertFalse(setup.actor.hasLost()); assertEquals(setup.actor.getLife(), 3);
        independent(setup);
    }

    private void unavailable(String boundary, boolean copied, AIOption mode) {
        Scenario setup = scenario(false, copied, boundary);
        SpellAbility removal = setup.source.getSpellAbilities().stream()
                .filter(sa -> sa.getApi() == ApiType.RemoveFromCombat).findFirst().orElseThrow();
        removal.setActivatingPlayer(setup.actor);
        if (boundary.equals("hexproof") || boundary.equals("noCombat")) {
            assertFalse(removal.canTarget(setup.attacker));
        } else {
            assertTrue(removal.canTarget(setup.attacker));
            removal.getTargets().add(setup.attacker);
            if (boundary.equals("unaffordable")) assertFalse(ComputerUtilCost.canPayCost(removal, setup.actor, false));
            else assertFalse(removal.canPlay());
            removal.resetTargets();
        }
        boolean tappedBefore = setup.source.isTapped();
        PlayerControllerAi controller = (PlayerControllerAi) setup.actor.getController();
        controller.getAi().setUseSimulation(mode);
        List<SpellAbility> choices = controller.getAi().chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(sa -> sa.getApi() == ApiType.RemoveFromCombat));
        assertEquals(setup.source.isTapped(), tappedBefore);
        assertTrue(setup.actor.getLandsInPlay().stream().filter(c -> c != setup.source).noneMatch(Card::isTapped));
        assertEquals(setup.game.getStack().size(), 0);
        assertEquals(setup.actor.getLife(), 3);
        if (copied) {
            assertEquals(setup.originalActor.getLife(), 3);
            assertTrue(setup.original.getStack().isEmpty());
            assertEquals(setup.originalSource.isTapped(), tappedBefore);
        }
    }

    private void damage(Scenario setup) {
        Combat combat = setup.game.getCombat();
        setup.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, setup.enemy, false, 3);
        boolean attackerRemains = !combat.getAttackers().isEmpty();
        assertEquals(combat.assignCombatDamage(false), attackerRemains, "Only remaining attackers assign damage");
        combat.dealAssignedDamage(); settle(setup.game);
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Combat removal fixture did not settle");
    }

    private void independent(Scenario setup) {
        if (setup.game == setup.original) return;
        assertEquals(setup.originalActor.getLife(), 3); assertFalse(setup.originalActor.hasLost());
        assertFalse(setup.originalSource.isTapped());
        assertTrue(setup.originalActor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertEquals(setup.original.getStack().size(), 0);
    }

    private record Scenario(Game game, Player actor, Player enemy, Card source, Card attacker,
                            Game original, Player originalActor, Card originalSource) { }
}
