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
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native paid Spy Network disclosures and position knowledge, separate from AI admission policy. */
public class CommanderSpyNetworkKnowledgeTest extends AITest {
    @Test public void paidSpyNetworkDisclosesOnlyToItsActor() {
        Pod pod = pod();
        cast(pod); settle(pod.game);
        assertTrue(known(pod.actor, pod.hand), "The authorized actor must learn the target hand");
        assertTrue(known(pod.actor, pod.top), "The authorized actor must learn the top card");
        for (Player observer : pod.game.getPlayers()) if (observer != pod.actor && observer != pod.owner) {
            assertFalse(known(observer, pod.hand)); assertFalse(known(observer, pod.top));
        }
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }

    @Test public void inspectedTopDrawKeepsItsKnownIdentityWithoutAHiddenReorder() {
        Pod pod = pod(); cast(pod); settle(pod.game);
        assertTrue(known(pod.actor, pod.top));
        pod.owner.drawCards(1);
        Card drawn = pod.owner.getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getId() == pod.top.getId()).findFirst().orElseThrow();
        assertTrue(known(pod.actor, drawn), "Spy Network explicitly discloses this top position before its unshuffled draw");
    }

    @Test public void aHiddenShuffleStillHidesTheSamePhysicalTopCard() {
        Pod pod = pod(); cast(pod); settle(pod.game);
        List<Card> order = new ArrayList<>();
        for (Card card : pod.owner.getCardsIn(ZoneType.Library)) order.add(card);
        boolean sameTop = false;
        for (int seed = 0; seed < 256; seed++) {
            pod.owner.getZone(ZoneType.Library).setCards(order);
            MyRandom.setRandom(new Random(seed)); pod.owner.shuffle(null);
            if (pod.owner.getCardsIn(ZoneType.Library).get(0).getId() == pod.top.getId()) {
                sameTop = true; break;
            }
        }
        assertTrue(sameTop, "Supply a hidden shuffle that happens to retain the physical top card");
        pod.owner.drawCards(1);
        Card drawn = pod.owner.getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getId() == pod.top.getId()).findFirst().orElseThrow();
        assertFalse(known(pod.actor, drawn), "Physical identity alone must not disclose a shuffled draw");
    }

    @Test public void drawingWithoutInspectionDoesNotDiscloseTheCard() {
        Pod pod = pod(); pod.owner.drawCards(1);
        Card drawn = pod.owner.getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getId() == pod.top.getId()).findFirst().orElseThrow();
        assertFalse(known(pod.actor, drawn));
    }

    @Test public void copiedPendingSpyNetworkRetainsItsOwnInspectedDrawKnowledge() {
        Pod pod = pod(); cast(pod);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        settle(copy);
        Player actor = copy.getPlayer(pod.actor.getId()), owner = copy.getPlayer(pod.owner.getId());
        assertTrue(known(actor, copier.find(pod.top)));
        owner.drawCards(1);
        Card drawn = owner.getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getId() == pod.top.getId()).findFirst().orElseThrow();
        assertTrue(known(actor, drawn), "The copied observer must retain its authorized position knowledge");
        assertFalse(known(pod.actor, pod.top));
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.top.isInZone(ZoneType.Library));
    }

    private void cast(Pod pod) {
        SpellAbility action = pod.spell.getSpellAbilities().stream().filter(SpellAbility::isSpell)
                .findFirst().orElseThrow();
        action.setActivatingPlayer(pod.actor); action.getTargets().add(pod.owner);
        assertTrue(action.canPlay()); assertTrue(action.isTargetNumberValid());
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
    }
    private boolean known(Player actor, Card card) {
        return ((PlayerControllerAi) actor.getController()).getAi().getCardMemory()
                .isRememberedCard(card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }
    private void settle(Game game) {
        for (int i = 0; i < 24; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Native Spy Network stack did not settle");
    }
    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("spy-network" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Authorized library-position knowledge"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), owner = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card top = addCardToZone("Counterspell", owner, ZoneType.Library);
        owner.getZone(ZoneType.Library).reorder(top, 0);
        Card hand = addCardToZone("Lightning Bolt", owner, ZoneType.Hand);
        Card spell = addCardToZone("Spy Network", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        return new Pod(game, actor, owner, spell, top, hand);
    }
    private record Pod(Game game, Player actor, Player owner, Card spell, Card top, Card hand) { }
}
