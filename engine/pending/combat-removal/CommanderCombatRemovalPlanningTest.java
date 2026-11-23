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
public class CommanderCombatRemovalPlanningTest extends AITest {
    @Test public void originalPaidLabyrinthRescues() { paid(false, false); }
    @Test public void originalPaidFogRescues() { paid(true, false); }
    @Test public void originalClassicLabyrinthChoosesRescue() { choice(false, false, null); }
    @Test public void originalClassicFogChoosesRescue() { choice(true, false, null); }
    @Test public void originalHybridLabyrinthChoosesRescue() { choice(false, false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void originalHybridFogChoosesRescue() { choice(true, false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void originalFullLabyrinthChoosesRescue() { choice(false, false, AIOption.USE_FULL_SIMULATION); }
    @Test public void originalFullFogChoosesRescue() { choice(true, false, AIOption.USE_FULL_SIMULATION); }
    @Test public void originalNoResponseIsLethal() { noResponse(false); }
    @Test public void copiedPaidLabyrinthRescues() { paid(false, true); }
    @Test public void copiedPaidFogRescues() { paid(true, true); }
    @Test public void copiedClassicLabyrinthChoosesRescue() { choice(false, true, null); }
    @Test public void copiedClassicFogChoosesRescue() { choice(true, true, null); }
    @Test public void copiedHybridLabyrinthChoosesRescue() { choice(false, true, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void copiedHybridFogChoosesRescue() { choice(true, true, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void copiedFullLabyrinthChoosesRescue() { choice(false, true, AIOption.USE_FULL_SIMULATION); }
    @Test public void copiedFullFogChoosesRescue() { choice(true, true, AIOption.USE_FULL_SIMULATION); }
    @Test public void copiedNoResponseIsLethal() { noResponse(true); }

    private Scenario scenario(boolean fog, boolean copied) {
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
        for (int index = 0; index < 4; index++) addCardToZone("Forest", actor, ZoneType.Battlefield);
        Card source = addCardToZone(fog ? "Fog" : "Labyrinth of Skophos", actor,
                fog ? ZoneType.Hand : ZoneType.Battlefield);
        Card attacker = addCardToZone("Craw Wurm", enemy, ZoneType.Battlefield);
        attacker.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, enemy, 3);
        assertTrue(CombatUtil.canAttack(attacker, actor));
        Combat combat = new Combat(enemy); game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker, actor); attacker.setTapped(true);
        CombatUtil.checkDeclaredAttacker(game, attacker, combat, true); combat.setBlocked(attacker, false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, enemy, false, 3);
        game.getPhaseHandler().setPriority(actor); game.copyLastState();
        Game original = game; Player originalActor = actor; Card originalSource = source;
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor); actor = copier.find(actor); enemy = copier.find(enemy);
            source = copier.find(source); attacker = copier.find(attacker);
        }
        assertEquals(ComputerUtilCombat.lifeThatWouldRemain(actor, game.getCombat()), -3);
        assertTrue(game.getCombat().isAttacking(attacker));
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

    private void paid(boolean fog, boolean copied) {
        Scenario setup = scenario(fog, copied); playAndWitness(setup, action(setup, fog), fog);
    }

    private void choice(boolean fog, boolean copied, AIOption mode) {
        Scenario setup = scenario(fog, copied);
        SpellAbility legal = action(setup, fog); // Independently verify legality/payment.
        legal.resetTargets(); // Autonomous selection must choose its own targets.
        PlayerControllerAi controller = (PlayerControllerAi) setup.actor.getController();
        controller.getAi().setUseSimulation(mode);
        List<SpellAbility> choices = controller.getAi().chooseSpellAbilityToPlay();
        assertFalse(setup.source.isTapped());
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertEquals(setup.game.getStack().size(), 0);
        assertNotNull(choices, "An affordable legal action can prevent the witnessed lethal attack");
        SpellAbility selected = choices.stream().filter(sa -> sa.getHostCard().getId() == setup.source.getId()
                && sa.getApi() == (fog ? ApiType.Fog : ApiType.RemoveFromCombat)).findFirst().orElse(null);
        assertNotNull(selected, "Select the rescue action, not the land's mana ability");
        playAndWitness(setup, selected, fog);
    }

    private void playAndWitness(Scenario setup, SpellAbility action, boolean fog) {
        assertTrue(ComputerUtil.playStack(action, setup.actor, setup.game));
        assertEquals(action.getPayingMana().size(), fog ? 1 : 4);
        if (!fog) assertTrue(setup.source.isTapped());
        settle(setup.game);
        assertEquals(setup.game.getCombat().isAttacking(setup.attacker), fog);
        damage(setup); assertFalse(setup.actor.hasLost()); assertEquals(setup.actor.getLife(), 3);
        independent(setup);
    }

    private void noResponse(boolean copied) {
        Scenario setup = scenario(false, copied); damage(setup); assertTrue(setup.actor.hasLost()); independent(setup);
    }

    private void damage(Scenario setup) {
        Combat combat = setup.game.getCombat();
        setup.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, setup.enemy, false, 3);
        boolean attackerRemains = combat.isAttacking(setup.attacker);
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
