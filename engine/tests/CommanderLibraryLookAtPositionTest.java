package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native API witnesses for explicit positions versus identity-only private disclosure. */
public class CommanderLibraryLookAtPositionTest extends AITest {
    @Test public void anExplicitTopLookPreservesAnUnshuffledDraw() {
        Pod pod = pod(); look(pod, "ValidAll Card.TopLibrary+TargetedPlayerCtrl", false);
        assertTrue(known(pod.actor, pod.top));
        Card drawn = draw(pod, pod.top);
        assertTrue(known(pod.actor, drawn)); assertPrivate(pod, drawn);
    }
    @Test public void anExplicitBottomLookPreservesABottomDraw() {
        Pod pod = pod(); bottomDraws(pod);
        look(pod, "ValidAll Card.BottomLibrary+TargetedPlayerCtrl", false);
        assertTrue(known(pod.actor, pod.bottom));
        Card drawn = draw(pod, pod.bottom);
        assertTrue(known(pod.actor, drawn)); assertPrivate(pod, drawn);
    }
    @Test public void anIdentityOnlyLookDoesNotInferThePhysicalTopPosition() {
        Pod pod = pod(); pod.host.addRemembered(pod.top);
        look(pod, "Remembered", false);
        assertTrue(known(pod.actor, pod.top));
        assertFalse(known(pod.actor, draw(pod, pod.top)));
    }
    @Test public void aFilteredTopCreatureLookDoesNotDiscloseAnAbsoluteIndex() {
        Pod pod = pod(); look(pod, "ValidAll Card.TopLibrary_Creature+TargetedPlayerCtrl", false);
        assertTrue(known(pod.actor, pod.top), "The look must disclose the creature identity");
        assertFalse(known(pod.actor, draw(pod, pod.top)), "A first matching creature need not be the physical top card");
    }
    @Test public void aFilteredBottomCreatureLookDoesNotDiscloseAnAbsoluteIndex() {
        Pod pod = pod(); bottomDraws(pod);
        look(pod, "ValidAll Card.BottomLibrary_Creature+TargetedPlayerCtrl", false);
        assertTrue(known(pod.actor, pod.bottom));
        assertFalse(known(pod.actor, draw(pod, pod.bottom)));
    }
    @Test public void aMixedSelectionDoesNotInferPositionFromActualPlacement() {
        Pod pod = pod();
        look(pod, "ValidAll Card.TopLibrary+Red+TargetedPlayerCtrl,Card.Creature+TargetedPlayerCtrl", false);
        assertTrue(known(pod.actor, pod.top));
        assertTrue(known(pod.actor, pod.bottom));
        assertFalse(known(pod.actor, draw(pod, pod.top)), "A nonred creature was revealed by the identity-only alternative");
    }
    @Test public void aLaterIdentityOnlyLookDoesNotErasePreviouslyKnownPosition() {
        Pod pod = pod(); look(pod, "ValidAll Card.TopLibrary+TargetedPlayerCtrl", false);
        pod.host.addRemembered(pod.top); look(pod, "Remembered", false);
        assertTrue(known(pod.actor, draw(pod, pod.top)));
    }
    @Test public void copiedPendingBottomInspectionStaysPrivateAndIndependent() {
        Pod pod = pod(); bottomDraws(pod);
        look(pod, "ValidAll Card.BottomLibrary+TargetedPlayerCtrl", true);
        GameCopier copier = new GameCopier(pod.game).withStack(true); Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty()); settle(copy);
        Player actor = copy.getPlayer(pod.actor.getId()), owner = copy.getPlayer(pod.owner.getId());
        assertTrue(known(actor, copier.find(pod.bottom)));
        assertFalse(known(pod.actor, pod.bottom)); assertEquals(pod.game.getStack().size(), 1);
        owner.drawCards(1);
        Card drawn = owner.getCardsIn(ZoneType.Hand).stream().filter(card -> card.getId() == pod.bottom.getId())
                .findFirst().orElseThrow();
        assertTrue(known(actor, drawn));
        assertFalse(known(pod.actor, pod.bottom)); assertTrue(pod.bottom.isInZone(ZoneType.Library));
    }

    @Test public void aKnownBottomPositionSurvivesAnUnknownTopDraw() {
        Pod pod = pod(); look(pod, "ValidAll Card.BottomLibrary+TargetedPlayerCtrl", false);
        assertFalse(known(pod.actor, draw(pod, pod.top)));
        bottomDraws(pod);
        assertTrue(known(pod.actor, draw(pod, pod.bottom)));
    }
    @Test public void aKnownTopPositionSurvivesAnUnknownBottomDraw() {
        Pod pod = pod(); bottomDraws(pod);
        look(pod, "ValidAll Card.TopLibrary+TargetedPlayerCtrl", false);
        assertFalse(known(pod.actor, draw(pod, pod.bottom)));
        Card song = pod.owner.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("River Song")).findFirst().orElseThrow();
        pod.game.getAction().moveToGraveyard(song, null); pod.game.getAction().checkStateEffects(true);
        assertTrue(known(pod.actor, draw(pod, pod.top)));
    }
    @Test public void aShuffleHidesTheSamePhysicalBottomCard() {
        Pod pod = pod(); bottomDraws(pod);
        look(pod, "ValidAll Card.BottomLibrary+TargetedPlayerCtrl", false);
        List<Card> order = new ArrayList<>();
        for (Card card : pod.owner.getCardsIn(ZoneType.Library)) order.add(card);
        boolean sameBottom = false;
        for (int seed = 0; seed < 256; seed++) {
            pod.owner.getZone(ZoneType.Library).setCards(order);
            forge.util.MyRandom.setRandom(new java.util.Random(seed)); pod.owner.shuffle(null);
            if (pod.owner.getCardsIn(ZoneType.Library).getLast().getId() == pod.bottom.getId()) {
                sameBottom = true; break;
            }
        }
        assertTrue(sameBottom, "Supply a hidden shuffle that retains the physical bottom card");
        assertFalse(known(pod.actor, draw(pod, pod.bottom)));
    }
    @Test public void aHiddenReorderInvalidatesAnOtherwiseUnchangedTopCard() {
        Pod pod = pod(); look(pod, "ValidAll Card.TopLibrary+TargetedPlayerCtrl", false);
        pod.owner.getZone(ZoneType.Library).reorder(pod.top, 0);
        assertFalse(known(pod.actor, draw(pod, pod.top)));
    }
    @Test public void aFreshLookReestablishesPositionAfterAHiddenReorder() {
        Pod pod = pod(); look(pod, "ValidAll Card.TopLibrary+TargetedPlayerCtrl", false);
        pod.owner.getZone(ZoneType.Library).reorder(pod.top, 0);
        look(pod, "ValidAll Card.TopLibrary+TargetedPlayerCtrl", false);
        assertTrue(known(pod.actor, draw(pod, pod.top)));
    }
    @Test public void topAndBottomConjunctionIdentifiesASingleCardLibrary() {
        Pod pod = pod(); List<Card> cards = new ArrayList<>();
        for (Card card : pod.owner.getCardsIn(ZoneType.Library)) cards.add(card);
        for (Card card : cards) if (card.getId() != pod.top.getId())
            pod.game.getAction().exile(card, null, java.util.Map.of());
        assertEquals(pod.owner.getCardsIn(ZoneType.Library).size(), 1);
        look(pod, "ValidAll Card.TopLibrary+BottomLibrary+TargetedPlayerCtrl", false);
        assertTrue(known(pod.actor, pod.top));
        assertTrue(known(pod.actor, draw(pod, pod.top)), "Both qualifiers identify the same public index in a one-card library");
    }

    private void look(Pod pod, String definition, boolean pending) {
        pod.host.setSVar("ObserveLibrary", "DB$ LookAt | Defined$ " + definition);
        SpellAbility action = AbilityFactory.getAbility(
                "AB$ Pump | Cost$ 0 | ValidTgts$ Player | SubAbility$ ObserveLibrary", pod.host);
        action.setActivatingPlayer(pod.actor); action.getTargets().add(pod.owner);
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
        if (!pending) settle(pod.game);
    }
    private void bottomDraws(Pod pod) {
        addCardToZone("River Song", pod.owner, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true); pod.game.copyLastState();
    }
    private Card draw(Pod pod, Card expected) {
        pod.owner.drawCards(1);
        return pod.owner.getCardsIn(ZoneType.Hand).stream().filter(card -> card.getId() == expected.getId())
                .findFirst().orElseThrow();
    }
    private boolean known(Player actor, Card card) {
        return ((PlayerControllerAi) actor.getController()).getAi().getCardMemory()
                .isRememberedCard(card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }
    private void assertPrivate(Pod pod, Card card) {
        for (Player observer : pod.game.getPlayers()) if (observer != pod.actor && observer != pod.owner)
            assertFalse(known(observer, card));
    }
    private void settle(Game game) {
        for (int i = 0; i < 24; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Native explicit-position stack did not settle");
    }
    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("position" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Explicit position disclosure"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), owner = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        Card top = addCardToZone("Grizzly Bears", owner, ZoneType.Library); owner.getZone(ZoneType.Library).reorder(top, 0);
        Card bottom = addCardToZone("Runeclaw Bear", owner, ZoneType.Library);
        Card host = addCardToZone("Smoke Teller", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        return new Pod(game, actor, owner, host, top, bottom);
    }
    private record Pod(Game game, Player actor, Player owner, Card host, Card top, Card bottom) { }
}
