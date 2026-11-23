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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Real paid player control must preserve both rightful observers and use only actor resources. */
public class CommanderControlledInformationBaselineTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("decision" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Controlled decision"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getPlayers().get(seat);
            for (int i = 0; i < 10; i++) {
                addCardToZone("Swamp", player, ZoneType.Battlefield);
                addCardToZone("Mountain", player, ZoneType.Battlefield);
                addCardToZone(seat == 1 ? "Shock" : "Forest", player, ZoneType.Library);
            }
        }
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 4; i++) addCardToZone("Island", player, ZoneType.Battlefield);
        }
        game.getPlayers().get(0).setLife(2, null);
        return game;
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture did not settle");
    }

    private Game prepared(boolean controlled) {
        Game game = pod();
        Player master = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        if (controlled) {
            Card fears = addCardToZone("Worst Fears", master, ZoneType.Hand);
            SpellAbility spell = fears.getFirstSpellAbility();
            spell.setActivatingPlayer(master);
            assertTrue(spell.canTarget(actor));
            spell.getTargets().add(actor);
            assertTrue(ComputerUtil.playStack(spell, master, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 8);
            game.getStack().resolveStack();
            settle(game);
        }
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
        settle(game);
        assertEquals(actor.getControllingPlayer(), controlled ? master : null);
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(actor.getCardsIn(ZoneType.Hand).get(0).getName(), "Shock");
        return game;
    }

    @Test public void masterReceivesPaidLibraryKnowledge() { paid(false, "master"); }
    @Test public void copiedMasterReceivesPaidLibraryKnowledge() { paid(true, "master"); }
    @Test public void actorRetainsKnowledgeAfterControlEnds() { paid(false, "after"); }
    @Test public void copiedActorRetainsKnowledgeAfterControlEnds() { paid(true, "after"); }
    @Test public void actingProxyReceivesPaidLibraryKnowledge() { paid(false, "proxy"); }
    @Test public void copiedActingProxyReceivesPaidLibraryKnowledge() { paid(true, "proxy"); }
    @Test public void emptyActorManaCannotUseMasterMana() { unaffordable(false); }
    @Test public void copiedEmptyActorManaCannotUseMasterMana() { unaffordable(true); }
    @Test public void counteredActivationDisclosesNoLibraryCards() { countered(false); }
    @Test public void copiedCounteredActivationDisclosesNoLibraryCards() { countered(true); }
    @Test public void controlledHybridChoosesPaidInformationForItsMaster() { choose(AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void controlledFullChoosesPaidInformationForItsMaster() { choose(AIOption.USE_FULL_SIMULATION, false); }
    @Test public void copiedControlledHybridChoosesPaidInformationForItsMaster() { choose(AIOption.USE_HYBRID_SIMULATION, true); }
    @Test public void copiedControlledFullChoosesPaidInformationForItsMaster() { choose(AIOption.USE_FULL_SIMULATION, true); }

    private Scenario information() {
        Game game = prepared(true); Player master = game.getPlayers().get(0), actor = game.getPlayers().get(1);
        master.setLife(40, null);
        for (Card card : List.copyOf(actor.getCardsIn(ZoneType.Hand))) game.getAction().exile(card, null, null);
        for (Card land : List.copyOf(actor.getLandsInPlay())) if (!land.getName().equals("Island")) game.getAction().exile(land, null, null);
        assertEquals(actor.getLandsInPlay().size(), 4); assertTrue(actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        Card source = addCardToZone("Soothsaying", actor, ZoneType.Battlefield);
        SpellAbility action = source.getNonManaAbilities().stream().filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElseThrow();
        action.setActivatingPlayer(actor); game.getPhaseHandler().setPriority(actor);
        game.getAction().checkStateEffects(true); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        assertSame(actor.getControllingPlayer(), master); assertEquals(known(master, actor), 0); assertEquals(known(actor, actor), 0);
        return new Scenario(game, master, actor, source, action, game, master, actor);
    }
    private Scenario copied(Scenario original) {
        GameCopier copier = new GameCopier(original.game).withStack(true); Game copy = copier.makeCopy(null, original.actor);
        Player actor = copier.find(original.actor), master = copier.find(original.master); Card source = copier.find(original.source);
        SpellAbility action = source.getNonManaAbilities().stream().filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElseThrow();
        action.setActivatingPlayer(actor); assertSame(actor.getControllingPlayer(), master);
        return new Scenario(copy, master, actor, source, action, original.original, original.originalMaster, original.originalActor);
    }
    private void pay(Scenario setup) {
        setup.action.setXManaCostPaid(3); assertTrue(setup.action.canPlay());
        assertTrue(ComputerUtil.playStack(setup.action, setup.actor, setup.game)); assertEquals(setup.action.getPayingMana().size(), 3);
        assertEquals(known(setup.actor, setup.actor), 0); assertEquals(known(setup.master, setup.actor), 0);
    }
    private void independent(Scenario setup) {
        if (setup.original != setup.game) {
            assertEquals(known(setup.originalMaster, setup.originalActor), 0);
            assertEquals(known(setup.originalActor, setup.originalActor), 0);
        }
    }
    private void paid(boolean copy, String observer) {
        Scenario setup = information(); pay(setup); if (copy) setup = copied(setup);
        settle(setup.game); independent(setup);
        if (observer.equals("after")) {
            final Scenario current = setup;
            assertTrue(setup.game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(current.game)));
            settle(setup.game); assertNull(setup.actor.getControllingPlayer());
        }
        int count = known(observer.equals("master") ? setup.master : setup.actor, setup.actor);
        System.out.println("CONTROLLED_INFORMATION observer=" + observer + "; copied=" + copy + "; known=" + count);
        assertEquals(count, 3, "Both rightful players can retain legally observed positions across controller changes");
    }
    private void unaffordable(boolean copy) {
        Scenario setup = information(); if (copy) setup = copied(setup);
        for (Card land : List.copyOf(setup.actor.getLandsInPlay())) setup.game.getAction().exile(land, null, null);
        long masterTapped = setup.master.getLandsInPlay().stream().filter(Card::isTapped).count();
        setup.action.setXManaCostPaid(3); assertFalse(ComputerUtilCost.canPayCost(setup.action, setup.actor, false));
        assertFalse(ComputerUtil.playStack(setup.action, setup.actor, setup.game));
        assertEquals(setup.master.getLandsInPlay().stream().filter(Card::isTapped).count(), masterTapped);
        assertTrue(setup.game.getStack().isEmpty()); assertEquals(known(setup.master, setup.actor), 0); assertEquals(known(setup.actor, setup.actor), 0);
        independent(setup);
    }
    private void countered(boolean copy) {
        Scenario setup = information(); pay(setup); Player opponent = setup.game.getPlayers().get(2);
        SpellAbility stifle = addCardToZone("Stifle", opponent, ZoneType.Hand).getFirstSpellAbility();
        stifle.setActivatingPlayer(opponent); stifle.getTargets().add(setup.game.getStack().peekAbility());
        setup.game.getPhaseHandler().setPriority(opponent); assertTrue(stifle.canPlay());
        assertTrue(ComputerUtil.playStack(stifle, opponent, setup.game)); assertEquals(stifle.getPayingMana().size(), 1);
        if (copy) setup = copied(setup); settle(setup.game); independent(setup);
        assertEquals(known(setup.master, setup.actor), 0); assertEquals(known(setup.actor, setup.actor), 0);
    }
    private void choose(AIOption mode, boolean copy) {
        Scenario setup = information(); if (copy) setup = copied(setup);
        PlayerControllerAi proxy = (PlayerControllerAi) setup.actor.getController(); proxy.getAi().setUseSimulation(mode);
        List<SpellAbility> actions = proxy.getAi().chooseSpellAbilityToPlay();
        assertEquals(known(setup.actor, setup.actor), 0); assertEquals(known(setup.master, setup.actor), 0);
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped)); assertTrue(setup.game.getStack().isEmpty());
        assertNotNull(actions, "The otherwise idle controlled actor can acquire useful private information for its master");
        final Card source = setup.source; SpellAbility selected = actions.stream().filter(sa -> sa.getHostCard().getId() == source.getId()
                && sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElse(null);
        assertNotNull(selected); Integer x = selected.getXManaCostPaid(); assertNotNull(x); assertTrue(x > 0 && x <= 4);
        assertTrue(ComputerUtil.playStack(selected, setup.actor, setup.game)); assertEquals(selected.getPayingMana().size(), x.intValue());
        settle(setup.game); independent(setup); assertEquals(known(setup.master, setup.actor), x.intValue());
    }
    private int known(Player observer, Player owner) {
        int count = 0; for (int index = 0; index < owner.getCardsIn(ZoneType.Library).size(); index++)
            if (AiCardMemory.isLibraryPositionKnown(observer, owner, index)) count++;
        return count;
    }
    private record Scenario(Game game, Player master, Player actor, Card source, SpellAbility action,
            Game original, Player originalMaster, Player originalActor) { }
}
