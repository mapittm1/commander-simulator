package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** AI-selected paid modal rewrites of an actual pending counterspell. */
public class CommanderCrystalSprayStackTest extends AITest {
    @Test public void heuristicRewritesPendingCounterspell() { verify(null, false); }
    @Test public void hybridRewritesPendingCounterspell() { verify(AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullRewritesPendingCounterspell() { verify(AIOption.USE_FULL_SIMULATION, false); }
    @Test public void copiedPendingRewriteKeepsOriginalStackIndependent() {
        verify(AIOption.USE_FULL_SIMULATION, true);
    }

    @Test public void heuristicRewritesPendingLandDestruction() { verifyLand(null, false); }
    @Test public void hybridRewritesPendingLandDestruction() { verifyLand(AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullRewritesPendingLandDestruction() { verifyLand(AIOption.USE_FULL_SIMULATION, false); }
    @Test public void copiedPendingLandRewriteKeepsOriginalIndependent() {
        verifyLand(AIOption.USE_FULL_SIMULATION, true);
    }

    private void verifyLand(AIOption mode, boolean copiedPending) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("crystal-land-stack-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Crystal Spray land destruction rewrite"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 3; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        for (int i = 0; i < 4; i++) addCardToZone("Mountain", enemy, ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", enemy, ZoneType.Battlefield);
        SpellAbility destruction = addCardToZone("Boil", enemy, ZoneType.Hand).getFirstSpellAbility();
        destruction.setActivatingPlayer(enemy);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        assertTrue(ComputerUtil.playStack(destruction, enemy, game));
        Card spell = addCardToZone("Crystal Spray", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        AiController ai = ((PlayerControllerAi) actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay();
        assertTrue(actions != null && actions.stream().anyMatch(action -> action.getHostCard().getId() == spell.getId()),
                "Choose the paid land-destruction rewrite in mode " + mode);
        SpellAbility selected = actions.stream().filter(action -> action.getHostCard().getId() == spell.getId())
                .findFirst().orElseThrow();
        boolean targetsDestruction = false;
        for (SpellAbility target : selected.getTargets().getTargetSpells()) {
            if (target.getHostCard().getId() == destruction.getHostCard().getId()) targetsDestruction = true;
        }
        assertTrue(targetsDestruction, "The land-word mode must target the pending destruction spell");
        assertTrue(ComputerUtil.playStack(selected, actor, game));
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 3L);
        if (copiedPending) {
            GameCopier copier = new GameCopier(game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            copy.getStack().resolveStack();
            assertEquals(copy.getCardState(copier.find(destruction.getHostCard())).getChangedTextTypeWords().get("Island"),
                    "Mountain", "Use the land word that destroys the opponent's four Mountains");
            settle(copy);
            assertEquals(copy.getPlayer(actor.getId()).getLandsInPlay().size(), 3);
            assertEquals(copy.getPlayer(actor.getId()).getCardsIn(ZoneType.Library).size(), 7);
            assertTrue(copy.getCardState(copier.find(swamp)).isInZone(ZoneType.Battlefield));
            assertEquals(copy.getPlayer(enemy.getId()).getLandsInPlay().size(), 1);
            assertEquals(copy.getPlayer(enemy.getId()).getCardsIn(ZoneType.Graveyard).stream()
                    .filter(card -> card.getName().equals("Mountain")).count(), 4L);
            assertEquals(game.getStack().size(), 2);
            assertEquals(actor.getLandsInPlay().size(), 3);
            assertEquals(actor.getCardsIn(ZoneType.Library).size(), 8);
            assertTrue(game.getCardState(swamp).isInZone(ZoneType.Battlefield));
        }
        game.getStack().resolveStack();
        assertEquals(game.getCardState(destruction.getHostCard()).getChangedTextTypeWords().get("Island"),
                "Mountain", "Use the land word that destroys the opponent's four Mountains");
        settle(game);
        assertEquals(actor.getLandsInPlay().size(), 3);
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 7);
        assertTrue(game.getCardState(swamp).isInZone(ZoneType.Battlefield));
        assertEquals(enemy.getLandsInPlay().size(), 1);
        assertEquals(enemy.getCardsIn(ZoneType.Graveyard).stream()
                .filter(card -> card.getName().equals("Mountain")).count(), 4L);
    }

    private void verify(AIOption mode, boolean copiedPending) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("crystal-stack-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Crystal Spray stack targeting"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 6; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        for (int i = 0; i < 2; i++) addCardToZone("Island", enemy, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        SpellAbility draw = addCardToZone("Divination", actor, ZoneType.Hand).getFirstSpellAbility();
        draw.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(draw, actor, game));
        SpellAbility counter = addCardToZone("Gainsay", enemy, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(enemy);
        counter.getTargets().add(draw);
        assertTrue(ComputerUtil.playStack(counter, enemy, game));
        Card spell = addCardToZone("Crystal Spray", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        AiController ai = ((PlayerControllerAi) actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay();
        assertTrue(actions != null && actions.stream().anyMatch(action -> action.getHostCard().getId() == spell.getId()),
                "Choose the paid counterspell rewrite in mode " + mode);
        SpellAbility selected = actions.stream().filter(action -> action.getHostCard().getId() == spell.getId())
                .findFirst().orElseThrow();
        boolean targetsCounter = false;
        for (SpellAbility target : selected.getTargets().getTargetSpells()) {
            if (target.getHostCard().getId() == counter.getHostCard().getId()) targetsCounter = true;
        }
        assertTrue(targetsCounter, "The selected rewrite must target the pending counterspell");
        assertTrue(ComputerUtil.playStack(selected, actor, game));
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 6L);
        assertEquals(game.getStack().size(), 3);
        if (copiedPending) {
            GameCopier copier = new GameCopier(game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            settle(copy);
            assertEquals(copy.getPlayer(actor.getId()).getCardsIn(ZoneType.Library).size(), 5);
            assertEquals(game.getStack().size(), 3);
            assertEquals(actor.getCardsIn(ZoneType.Library).size(), 8);
            assertTrue(game.getCardState(counter.getHostCard()).getChangedTextColorWords().isEmpty());
        }
        settle(game);
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 5,
                "The paid cantrip and the saved Divination must both resolve");
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 6L);
    }

    private void settle(Game game) {
        for (int i = 0; i < 32; i++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Crystal Spray counterspell stack did not settle");
    }
}
