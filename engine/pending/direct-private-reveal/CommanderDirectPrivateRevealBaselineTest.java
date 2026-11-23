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

/** Printed private-reveal paths must preserve authorized observers without exposing bystanders. */
public class CommanderDirectPrivateRevealBaselineTest extends AITest {
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

    @Test public void originalHandProxyRetainsPrivateObservation() { inspect(true, false, "proxy", false, true); }
    @Test public void originalHandMasterRetainsPrivateObservation() { inspect(true, false, "master", false, true); }
    @Test public void originalHandAfterRetainsPrivateObservation() { inspect(true, false, "after", false, true); }
    @Test public void copiedHandProxyRetainsPrivateObservation() { inspect(true, true, "proxy", false, true); }
    @Test public void copiedHandMasterRetainsPrivateObservation() { inspect(true, true, "master", false, true); }
    @Test public void copiedHandAfterRetainsPrivateObservation() { inspect(true, true, "after", false, true); }
    @Test public void originalHandCounteredInspectionDisclosesNothing() { inspect(true, false, "master", true, true); }
    @Test public void copiedHandCounteredInspectionDisclosesNothing() { inspect(true, true, "master", true, true); }
    @Test public void normalHandInspectionRemainsPrivate() { inspect(true, false, "proxy", false, false); }
    @Test public void originalLibraryProxyRetainsPrivateObservation() { inspect(false, false, "proxy", false, true); }
    @Test public void originalLibraryMasterRetainsPrivateObservation() { inspect(false, false, "master", false, true); }
    @Test public void originalLibraryAfterRetainsPrivateObservation() { inspect(false, false, "after", false, true); }
    @Test public void copiedLibraryProxyRetainsPrivateObservation() { inspect(false, true, "proxy", false, true); }
    @Test public void copiedLibraryMasterRetainsPrivateObservation() { inspect(false, true, "master", false, true); }
    @Test public void copiedLibraryAfterRetainsPrivateObservation() { inspect(false, true, "after", false, true); }
    @Test public void originalLibraryCounteredInspectionDisclosesNothing() { inspect(false, false, "master", true, true); }
    @Test public void copiedLibraryCounteredInspectionDisclosesNothing() { inspect(false, true, "master", true, true); }
    @Test public void normalLibraryInspectionRemainsPrivate() { inspect(false, false, "proxy", false, false); }

    private void inspect(boolean hand, boolean copy, String observer, boolean countered, boolean controlled) {
        Game original = prepared(controlled);
        Player actor = original.getPlayers().get(1), master = original.getPlayers().get(0);
        Player target = original.getPlayers().get(2);
        Card hidden = hand ? addCardToZone("Lightning Bolt", target, ZoneType.Hand)
                : actor.getCardsIn(ZoneType.Library).get(0);
        Card source = addCardToZone(hand ? "Peek" : "Brutal Deceiver", actor,
                hand ? ZoneType.Hand : ZoneType.Battlefield);
        SpellAbility inspection = hand ? source.getFirstSpellAbility()
                : source.getNonManaAbilities().stream().filter(sa -> sa.hasParam("NoReveal")).findFirst().orElseThrow();
        inspection.setActivatingPlayer(actor);
        if (hand) inspection.getTargets().add(target);
        assertFalse(AiCardMemory.isRememberedCard(master, hidden, AiCardMemory.MemorySet.REVEALED_CARDS));
        assertTrue(ComputerUtil.playStack(inspection, actor, original));
        assertEquals(inspection.getPayingMana().size(), 1);
        if (countered) {
            SpellAbility counter = addCardToZone(hand ? "Counterspell" : "Stifle", target, ZoneType.Hand).getFirstSpellAbility();
            counter.setActivatingPlayer(target);
            counter.getTargets().add(original.getStack().peekAbility());
            original.getPhaseHandler().setPriority(target);
            assertTrue(ComputerUtil.playStack(counter, target, original));
            assertEquals(counter.getPayingMana().size(), hand ? 2 : 1);
        }
        Game game = original;
        final Player originalActor = actor, originalMaster = master;
        final Card originalHidden = hidden;
        if (copy) {
            GameCopier copier = new GameCopier(original).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor); master = copier.find(master); hidden = copier.find(hidden);
        }
        settle(game);
        if (observer.equals("after")) {
            final Game current = game;
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(current)));
            settle(game);
            assertNull(actor.getControllingPlayer());
        }
        Player recipient = observer.equals("master") ? master : actor;
        boolean remembered = AiCardMemory.isRememberedCard(recipient, hidden, AiCardMemory.MemorySet.REVEALED_CARDS);
        System.out.println("DIRECT_PRIVATE hand=" + hand + "; copied=" + copy + "; observer=" + observer
                + "; countered=" + countered + "; controlled=" + controlled + "; remembered=" + remembered);
        assertEquals(remembered, !countered);
        if (!hand) assertEquals(AiCardMemory.isLibraryPositionKnown(recipient, actor, 0), !countered);
        assertFalse(AiCardMemory.isRememberedCard(game.getPlayers().get(3), hidden, AiCardMemory.MemorySet.REVEALED_CARDS));
        if (!controlled) assertFalse(AiCardMemory.isRememberedCard(master, hidden, AiCardMemory.MemorySet.REVEALED_CARDS));
        if (copy) {
            assertFalse(AiCardMemory.isRememberedCard(originalActor, originalHidden, AiCardMemory.MemorySet.REVEALED_CARDS));
            assertFalse(AiCardMemory.isRememberedCard(originalMaster, originalHidden, AiCardMemory.MemorySet.REVEALED_CARDS));
            assertFalse(original.getStack().isEmpty());
        }
    }
}
