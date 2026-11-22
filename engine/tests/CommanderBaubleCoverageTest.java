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
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Paid Bauble actions, private top-card information and actual Commander AI casts. */
public class CommanderBaubleCoverageTest extends AITest {
    @Test public void heuristicCastsTheFreeBauble() { pilot(null); }
    @Test public void hybridCastsTheFreeBauble() { pilot(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullSimulationCastsTheFreeBauble() { pilot(AIOption.USE_FULL_SIMULATION); }

    @Test
    public void paidInspectionIsPrivateAndItsSlowtripDrawsOnce() {
        Pod pod = pod();
        cast(pod);
        payInspection(pod);
        assertTrue(currentBauble(pod).isInZone(ZoneType.Graveyard));
        assertEquals(pod.game.getStack().peekAbility().getPayingMana().size(), 0);
        assertFalse(known(pod.actor, pod.top));
        settle(pod.game);
        assertPrivateInspection(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 0);
        advanceUpkeep(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1);
        advanceUpkeep(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1, "A consumed slowtrip must not repeat");
    }

    @Test
    public void aPaidPendingInspectionResolvesIndependentlyInACopy() {
        Pod pod = pod();
        cast(pod);
        payInspection(pod);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Player copiedActor = copy.getPlayer(pod.actor.getId());
        settle(copy);
        assertTrue(known(copiedActor, copier.find(pod.top)));
        assertFalse(known(pod.actor, pod.top));
        assertEquals(pod.game.getStack().size(), 1);
        advanceUpkeep(copy);
        assertEquals(copiedActor.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 0);
    }

    @Test
    public void aTappedBaubleCannotPayItsInspectionCost() {
        Pod pod = pod();
        cast(pod);
        currentBauble(pod).setTapped(true);
        SpellAbility action = inspection(pod);
        assertFalse(ComputerUtilCost.canPayCost(action, pod.actor, false));
        assertFalse(ComputerUtil.playStack(action, pod.actor, pod.game));
        assertTrue(currentBauble(pod).isInZone(ZoneType.Battlefield));
        assertFalse(known(pod.actor, pod.top));
        assertTrue(pod.game.getStack().isEmpty());
    }

    @Test
    public void anUnshuffledInspectedTopDrawKeepsItsKnownIdentity() {
        Pod pod = pod();
        cast(pod);
        payInspection(pod);
        settle(pod.game);
        assertTrue(known(pod.actor, pod.top));
        pod.owner.drawCards(1);
        Card drawn = pod.owner.getCardsIn(ZoneType.Hand).get(0);
        assertEquals(drawn.getId(), pod.top.getId());
        assertTrue(known(pod.actor, drawn), "An observed top-card draw must preserve the inspected identity");
    }

    @Test
    public void aShuffleBreaksKnowledgeEvenWhenTheSamePhysicalCardIsDrawn() {
        Pod pod = pod();
        cast(pod);
        payInspection(pod);
        settle(pod.game);
        List<Card> originalOrder = new ArrayList<>();
        for (Card card : pod.owner.getCardsIn(ZoneType.Library)) {
            originalOrder.add(card);
        }
        boolean sameTop = false;
        for (int seed = 0; seed < 256; seed++) {
            pod.owner.getZone(ZoneType.Library).setCards(originalOrder);
            MyRandom.setRandom(new Random(seed));
            pod.owner.shuffle(null);
            if (pod.owner.getCardsIn(ZoneType.Library).get(0).getId() == pod.top.getId()) {
                sameTop = true;
                break;
            }
        }
        assertTrue(sameTop, "The supplied hidden shuffle must happen to put the inspected card on top");
        pod.owner.drawCards(1);
        Card drawn = pod.owner.getCardsIn(ZoneType.Hand).get(0);
        assertEquals(drawn.getId(), pod.top.getId());
        assertFalse(known(pod.actor, drawn), "Physical card ids must not disclose a shuffled draw");
    }

    @Test
    public void drawingWithoutAPriorInspectionDoesNotCreateObserverKnowledge() {
        Pod pod = pod();
        pod.owner.drawCards(1);
        assertFalse(known(pod.actor, pod.owner.getCardsIn(ZoneType.Hand).get(0)));
    }

    @Test
    public void copiedInspectedTopKnowledgeSurvivesADrawWithoutChangingTheOriginal() {
        Pod pod = pod();
        // A copied revision must match history rather than merely the library size.
        pod.owner.getZone(ZoneType.Library).reorder(pod.top, 0);
        cast(pod);
        payInspection(pod);
        settle(pod.game);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Player copiedActor = copy.getPlayer(pod.actor.getId());
        Player copiedOwner = copy.getPlayer(pod.owner.getId());
        assertTrue(known(copiedActor, copier.find(pod.top)));
        copiedOwner.drawCards(1);
        assertTrue(known(copiedActor, copiedOwner.getCardsIn(ZoneType.Hand).get(0)));
        assertTrue(pod.top.isInZone(ZoneType.Library));
        assertEquals(pod.owner.getCardsIn(ZoneType.Hand).size(), 0);
        assertTrue(known(pod.actor, pod.top));
    }

    @Test
    public void aHiddenReorderInvalidatesPositionEvenIfTheSameCardRemainsOnTop() {
        Pod pod = pod();
        cast(pod);
        payInspection(pod);
        settle(pod.game);
        pod.owner.getZone(ZoneType.Library).reorder(pod.top, 0);
        pod.owner.drawCards(1);
        Card drawn = pod.owner.getCardsIn(ZoneType.Hand).get(0);
        assertEquals(drawn.getId(), pod.top.getId());
        assertFalse(known(pod.actor, drawn), "A hidden reorder must not disclose the resulting top card");
    }

    @Test
    public void aGenericLibraryIdentityRevealDoesNotDiscloseItsPosition() {
        Pod pod = pod();
        pod.game.getAction().revealTo(pod.top, pod.actor);
        assertTrue(known(pod.actor, pod.top));
        pod.owner.drawCards(1);
        assertFalse(known(pod.actor, pod.owner.getCardsIn(ZoneType.Hand).get(0)),
                "Knowing a card somewhere in the library does not identify a later draw");
    }

    @Test
    public void inspectedTopKnowledgeSurvivesATurnBoundary() {
        Pod pod = pod();
        cast(pod);
        payInspection(pod);
        settle(pod.game);
        advanceUpkeep(pod.game);
        assertEquals(pod.game.getPhaseHandler().getPlayerTurn().getId(), pod.owner.getId());
        assertTrue(known(pod.actor, pod.top));
        pod.owner.drawCards(1);
        assertTrue(known(pod.actor, pod.owner.getCardsIn(ZoneType.Hand).get(0)),
                "Ending the observer's turn must not erase legitimately tracked information");
    }

    @Test
    public void anUnknownBottomDrawPreservesKnownTopInformation() {
        Pod pod = pod();
        cast(pod);
        payInspection(pod);
        settle(pod.game);
        Card river = addCardToZone("River Song", pod.owner, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.owner.drawsFromBottom());
        pod.owner.drawCards(1);
        assertFalse(known(pod.actor, pod.owner.getCardsIn(ZoneType.Hand).get(0)));
        pod.game.getAction().moveToGraveyard(river, null);
        pod.game.getAction().checkStateEffects(true);
        assertFalse(pod.owner.drawsFromBottom());
        pod.owner.drawCards(1);
        Card topDrawn = pod.game.getCardState(pod.top, null);
        assertTrue(topDrawn.isInZone(ZoneType.Hand));
        assertTrue(known(pod.actor, topDrawn));
    }

    @Test
    public void aNativeTwoCardPeekTracksTwoConsecutiveDraws() {
        Pod pod = pod();
        cast(pod);
        Card first = pod.owner.getCardsIn(ZoneType.Library).get(0);
        Card second = pod.owner.getCardsIn(ZoneType.Library).get(1);
        // Native API witness for a two-card peek, separate from Bauble's printed ability.
        nativePeek(pod, " | NoReveal$ True");
        pod.owner.drawCards(2);
        assertTrue(known(pod.actor, pod.game.getCardState(first, null)));
        assertTrue(known(pod.actor, pod.game.getCardState(second, null)));
    }

    @Test
    public void aSelectivePublicRevealDoesNotDiscloseUnrevealedTopCards() {
        Pod pod = pod();
        cast(pod);
        Card artifact = addCardToZone("Memnite", pod.owner, ZoneType.Library);
        pod.owner.getZone(ZoneType.Library).reorder(artifact, 1);
        nativePeek(pod, " | NoPeek$ True | RevealValid$ Card.Artifact");
        assertFalse(known(pod.actor, pod.top));
        assertTrue(known(pod.actor, artifact));
        pod.owner.drawCards(1);
        assertFalse(known(pod.actor, pod.owner.getCardsIn(ZoneType.Hand).get(0)));
        pod.owner.drawCards(1);
        assertTrue(known(pod.actor, pod.game.getCardState(artifact, null)));
    }

    private void nativePeek(Pod pod, String options) {
        SpellAbility action = forge.game.ability.AbilityFactory.getAbility(
                "AB$ PeekAndReveal | Cost$ 0 | ValidTgts$ Player | PeekAmount$ 2" + options,
                currentBauble(pod));
        action.setActivatingPlayer(pod.actor);
        action.getTargets().add(pod.owner);
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
        settle(pod.game);
    }

    private void pilot(AIOption mode) {
        Pod pod = pod();
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> choices = ai.chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "The empty-board free cantrip must be an available play");
        SpellAbility selected = choices.stream().filter(action -> action.isSpell()
                && action.getHostCard().getId() == pod.bauble.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(currentBauble(pod).isInZone(ZoneType.Battlefield));
    }

    private void cast(Pod pod) {
        SpellAbility action = currentBauble(pod).getFirstSpellAbility();
        action.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(currentBauble(pod).isInZone(ZoneType.Battlefield));
    }

    private Card currentBauble(Pod pod) {
        return pod.game.getCardsInGame().stream()
                .filter(card -> card.getId() == pod.bauble.getId()).findFirst().orElseThrow();
    }

    private SpellAbility inspection(Pod pod) {
        SpellAbility action = currentBauble(pod).getSpellAbilities().stream()
                .filter(ability -> ability.isActivatedAbility() && ability.getApi() == ApiType.PeekAndReveal)
                .findFirst().orElseThrow();
        action.setActivatingPlayer(pod.actor);
        action.getTargets().add(pod.owner);
        return action;
    }

    private void payInspection(Pod pod) {
        SpellAbility action = inspection(pod);
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
    }

    private boolean known(Player player, Card card) {
        return AiCardMemory.isRememberedCard(player, card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }

    private void assertPrivateInspection(Pod pod) {
        assertTrue(known(pod.actor, pod.top));
        assertTrue(pod.top.isInZone(ZoneType.Library));
        for (Player player : pod.game.getPlayers()) {
            if (player != pod.actor) {
                assertFalse(known(player, pod.top));
            }
        }
    }

    private void advanceUpkeep(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.UPKEEP, () -> settle(game)));
        settle(game);
    }

    private void settle(Game game) {
        for (int step = 0; step < 30; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) {
                return;
            }
            game.getStack().resolveStack();
        }
        throw new AssertionError("Native Bauble stack did not settle");
    }

    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("bauble" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Bauble knowledge"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player owner = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        Card bauble = addCardToZone("Mishra's Bauble", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return new Pod(game, actor, owner, bauble, owner.getCardsIn(ZoneType.Library).get(0));
    }

    private record Pod(Game game, Player actor, Player owner, Card bauble, Card top) { }
}
