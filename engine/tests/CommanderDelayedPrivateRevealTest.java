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
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Delayed inspection paths must preserve authorized observations without exposing bystanders. */
public class CommanderDelayedPrivateRevealTest extends AITest {
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

    @Test public void originalStirringsProxyRetainsUnselectedInspection() { inspect(false, false, "proxy", false, true); }
    @Test public void originalStirringsMasterRetainsUnselectedInspection() { inspect(false, false, "master", false, true); }
    @Test public void originalStirringsAfterRetainsUnselectedInspection() { inspect(false, false, "after", false, true); }
    @Test public void copiedStirringsProxyRetainsUnselectedInspection() { inspect(false, true, "proxy", false, true); }
    @Test public void copiedStirringsMasterRetainsUnselectedInspection() { inspect(false, true, "master", false, true); }
    @Test public void copiedStirringsAfterRetainsUnselectedInspection() { inspect(false, true, "after", false, true); }
    @Test public void originalStirringsCounteredInspectionDisclosesNothing() { inspect(false, false, "master", true, true); }
    @Test public void copiedStirringsCounteredInspectionDisclosesNothing() { inspect(false, true, "master", true, true); }
    @Test public void normalStirringsInspectionRemainsPrivate() { inspect(false, false, "proxy", false, false); }
    @Test public void originalImpulseProxyRetainsUnselectedInspection() { inspect(true, false, "proxy", false, true); }
    @Test public void originalImpulseMasterRetainsUnselectedInspection() { inspect(true, false, "master", false, true); }
    @Test public void originalImpulseAfterRetainsUnselectedInspection() { inspect(true, false, "after", false, true); }
    @Test public void copiedImpulseProxyRetainsUnselectedInspection() { inspect(true, true, "proxy", false, true); }
    @Test public void copiedImpulseMasterRetainsUnselectedInspection() { inspect(true, true, "master", false, true); }
    @Test public void copiedImpulseAfterRetainsUnselectedInspection() { inspect(true, true, "after", false, true); }
    @Test public void originalImpulseCounteredInspectionDisclosesNothing() { inspect(true, false, "master", true, true); }
    @Test public void copiedImpulseCounteredInspectionDisclosesNothing() { inspect(true, true, "master", true, true); }
    @Test public void normalImpulseInspectionRemainsPrivate() { inspect(true, false, "proxy", false, false); }

    private void inspect(boolean choice, boolean copy, String observer, boolean countered, boolean controlled) {
        Game original = prepared(controlled);
        Player actor = original.getPlayers().get(1), master = original.getPlayers().get(0);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        int amount = choice ? 4 : 5;
        List<Integer> inspected = actor.getCardsIn(ZoneType.Library, amount).stream().map(Card::getId).toList();
        assertEquals(inspected.size(), amount);
        assertTrue(actor.getCardsIn(ZoneType.Library, amount).stream().allMatch(card -> card.getName().equals("Shock")),
                "The Stirrings branch has no colorless card to choose; Impulse has a real choice");
        SpellAbility spell = addCardToZone(choice ? "Impulse" : "Ancient Stirrings", actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        for (Card card : actor.getCardsIn(ZoneType.Library, amount)) {
            assertFalse(AiCardMemory.isRememberedCard(master, card, AiCardMemory.MemorySet.REVEALED_CARDS));
        }
        assertTrue(ComputerUtil.playStack(spell, actor, original));
        assertEquals(spell.getPayingMana().size(), choice ? 2 : 1);
        if (countered) {
            Player opponent = original.getPlayers().get(2);
            SpellAbility counter = addCardToZone("Counterspell", opponent, ZoneType.Hand).getFirstSpellAbility();
            counter.setActivatingPlayer(opponent);
            counter.getTargets().add(original.getStack().peekAbility());
            original.getPhaseHandler().setPriority(opponent);
            assertTrue(ComputerUtil.playStack(counter, opponent, original));
            assertEquals(counter.getPayingMana().size(), 2);
        }
        Game game = original;
        final Player originalActor = actor, originalMaster = master;
        if (copy) {
            GameCopier copier = new GameCopier(original).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor); master = copier.find(master);
        }
        settle(game);
        if (observer.equals("after")) {
            final Game current = game;
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(current)));
            settle(game);
            assertNull(actor.getControllingPlayer());
        }
        List<Card> witnesses = actor.getCardsIn(ZoneType.Library).stream()
                .filter(card -> inspected.contains(card.getId())).toList();
        assertEquals(witnesses.size(), choice && !countered ? amount-1 : amount);
        Player recipient = observer.equals("master") ? master : actor;
        long known = witnesses.stream().filter(card -> AiCardMemory.isRememberedCard(
                recipient, card, AiCardMemory.MemorySet.REVEALED_CARDS)).count();
        System.out.println("DELAYED_PRIVATE choice=" + choice + "; copied=" + copy + "; observer=" + observer
                + "; countered=" + countered + "; controlled=" + controlled + "; remembered=" + known);
        assertEquals(known, countered ? 0L : (long) witnesses.size());
        for (Card card : witnesses) {
            assertFalse(AiCardMemory.isRememberedCard(game.getPlayers().get(3), card, AiCardMemory.MemorySet.REVEALED_CARDS));
            if (!controlled) assertFalse(AiCardMemory.isRememberedCard(master, card, AiCardMemory.MemorySet.REVEALED_CARDS));
        }
        if (copy) {
            for (Card card : originalActor.getCardsIn(ZoneType.Library)) {
                if (!inspected.contains(card.getId())) continue;
                assertFalse(AiCardMemory.isRememberedCard(originalActor, card, AiCardMemory.MemorySet.REVEALED_CARDS));
                assertFalse(AiCardMemory.isRememberedCard(originalMaster, card, AiCardMemory.MemorySet.REVEALED_CARDS));
            }
            assertFalse(original.getStack().isEmpty());
        }
    }
}
