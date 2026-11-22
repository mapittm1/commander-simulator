package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.cost.CostPromiseGift;
import forge.game.cost.PaymentDecision;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.ai.simulation.GameCopier;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Gift snapshot contracts and native paid-cast witnesses; supplied actions do not prove AI strategy.
 * The original Long River Lurker cases are raw metadata contracts, not printed Gift abilities.
 */
public class CommanderGiftContextTest extends AITest {
    @Test public void aPromisedRecipientSurvivesAHandCopy() { recipientInZone(ZoneType.Hand); }
    @Test public void aPromisedRecipientSurvivesAStackCopy() { recipientInZone(ZoneType.Stack); }
    @Test public void aPromisedRecipientSurvivesABattlefieldCopy() { recipientInZone(ZoneType.Battlefield); }
    @Test public void aPromisedRecipientSurvivesAGraveyardCopy() { recipientInZone(ZoneType.Graveyard); }
    @Test public void aPromisedRecipientSurvivesAnExileCopy() { recipientInZone(ZoneType.Exile); }
    @Test public void aPromisedRecipientSurvivesALibraryCopy() { recipientInZone(ZoneType.Library); }

    @Test public void anUnpromisedGiftStaysUnpromised() {
        Pod pod = pod();
        Card card = addCardToZone("Into the Flood Maw", pod.actor, ZoneType.Hand);
        GameCopier copier = new GameCopier(pod.game);
        copier.makeCopy(null, pod.actor);
        assertFalse(copier.find(card).hasPromisedGift());
        assertNull(copier.find(card).getView().getPromisedGift());
    }

    @Test public void nativeLastKnownInformationRetainsTheSameGameRecipient() {
        Pod pod = pod();
        Card card = addCardToZone("Long River Lurker", pod.actor, ZoneType.Battlefield);
        promise(pod, card);
        Card history = CardCopyService.getLKICopy(card);
        assertSame(history.getPromisedGift(), pod.enemy);
        assertSame(history.getView().getPromisedGift(), pod.enemy.getView());
    }

    @Test public void historicalGiftRecipientsMapIntoTheCopiedGame() {
        Pod pod = pod();
        Card card = addCardToZone("Long River Lurker", pod.actor, ZoneType.Battlefield);
        promise(pod, card);
        Card host = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Battlefield);
        host.setChosenCards(new CardCollection(card));
        Card moved = pod.game.getAction().moveToGraveyard(card, null);
        assertNotEquals(card.getGameTimestamp(), moved.getGameTimestamp());
        GameCopier copier = new GameCopier(pod.game);
        Game game = copier.makeCopy(null, pod.actor);
        Card history = copier.find(host).getChosenCard();
        assertNotNull(history);
        assertEquals(history.getGameTimestamp(), card.getGameTimestamp());
        assertNotSame(history, copier.find(moved));
        assertSame(history.getPromisedGift(), copier.find(pod.enemy));
        assertSame(history.getPromisedGift().getGame(), game);
    }

    /** Uses native Gift payment and token effect with supplied choices, without full casting. */
    @Test public void aSuppliedGiftEffectCreatesATappedFishForItsRecipient() {
        Pod pod = pod();
        Card card = addCardToZone("Into the Flood Maw", pod.actor, ZoneType.Hand);
        promise(pod, card);
        resolveGift(card, pod.actor);
        assertFish(pod.enemy);
        assertTrue(pod.actor.getCreaturesInPlay().isEmpty());
    }

    @Test public void aCopiedGiftEffectChangesOnlyTheCopiedRecipient() {
        Pod pod = pod();
        Card card = addCardToZone("Into the Flood Maw", pod.actor, ZoneType.Hand);
        promise(pod, card);
        GameCopier copier = new GameCopier(pod.game);
        copier.makeCopy(null, pod.actor);
        Player copiedEnemy = copier.find(pod.enemy);
        resolveGift(copier.find(card), copier.find(pod.actor));
        assertFish(copiedEnemy);
        assertTrue(pod.enemy.getCreaturesInPlay().isEmpty());
        assertSame(card.getPromisedGift(), pod.enemy);
    }

    @Test public void anActualGiftPermanentKeepsItsRecipientAndPublicView() {
        Pod pod = pod();
        Card card = addCardToZone("Scrapshooter", pod.actor, ZoneType.Battlefield);
        promise(pod, card);
        GameCopier copier = new GameCopier(pod.game);
        copier.makeCopy(null, pod.actor);
        assertSame(copier.find(card).getPromisedGift(), copier.find(pod.enemy));
        assertSame(copier.find(card).getView().getPromisedGift(), copier.find(pod.enemy).getView());
    }

    @Test public void anActualGiftPermanentsHistoryMapsItsRecipient() {
        Pod pod = pod();
        Card card = addCardToZone("Scrapshooter", pod.actor, ZoneType.Battlefield);
        promise(pod, card);
        Card host = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Battlefield);
        host.setChosenCards(new CardCollection(card));
        Card moved = pod.game.getAction().moveToGraveyard(card, null);
        GameCopier copier = new GameCopier(pod.game);
        Game game = copier.makeCopy(null, pod.actor);
        Card history = copier.find(host).getChosenCard();
        assertNotSame(history, copier.find(moved));
        assertEquals(history.getGameTimestamp(), card.getGameTimestamp());
        assertSame(history.getPromisedGift(), copier.find(pod.enemy));
        assertSame(history.getPromisedGift().getGame(), game);
    }

    @Test public void aDepartedGiftRecipientRemainsAMappedParticipant() {
        Pod pod = pod();
        Card card = addCardToZone("Into the Flood Maw", pod.actor, ZoneType.Hand);
        promise(pod, card);
        pod.enemy.concede();
        GameCopier copier = new GameCopier(pod.game);
        Game game = copier.makeCopy(null, pod.actor);
        Player recipient = copier.find(card).getPromisedGift();
        assertNotNull(recipient);
        assertSame(recipient, copier.find(pod.enemy));
        assertSame(recipient.getGame(), game);
        assertTrue(recipient.hasLost());
    }

    @Test public void copyingAGiftTwiceDoesNotRetainEitherEarlierWorld() {
        Pod pod = pod();
        Card card = addCardToZone("Into the Flood Maw", pod.actor, ZoneType.Hand);
        promise(pod, card);
        GameCopier first = new GameCopier(pod.game);
        Game firstGame = first.makeCopy(null, pod.actor);
        Card firstCard = first.find(card);
        GameCopier second = new GameCopier(firstGame);
        Game secondGame = second.makeCopy(null, first.find(pod.actor));
        Card secondCard = second.find(firstCard);
        assertSame(secondCard.getPromisedGift(), second.find(first.find(pod.enemy)));
        assertSame(secondCard.getPromisedGift().getGame(), secondGame);
        assertNotSame(secondCard.getPromisedGift(), firstCard.getPromisedGift());
        secondCard.setPromisedGift(null);
        assertSame(firstCard.getPromisedGift(), first.find(pod.enemy));
        assertSame(card.getPromisedGift(), pod.enemy);
    }

    @Test public void paidScrapshooterGivesItsChosenRecipientOneCard() {
        Pod pod = pod();
        SpellAbility spell = castScrapshooter(pod, true);
        Player recipient = spell.getHostCard().getPromisedGift();
        assertNotNull(recipient);
        settle(pod.game);
        assertTrue(pod.game.getCardState(spell.getHostCard()).isInZone(ZoneType.Battlefield));
        assertEquals(recipient.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(recipient.getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(pod.game.getPlayers().stream().mapToInt(p -> p.getCardsIn(ZoneType.Hand).size()).sum(), 1);
    }

    @Test public void copiedPaidScrapshooterResolvesOnlyInItsOwnWorld() {
        Pod pod = pod();
        SpellAbility spell = castScrapshooter(pod, true);
        Card source = spell.getHostCard();
        Player recipient = source.getPromisedGift();
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game game = copier.makeCopy(null, pod.actor);
        assertEquals(game.getStack().size(), 1);
        assertTrue(game.getStack().peekAbility().isGiftPromised());
        assertTrue(copier.find(source).getCastSA().isGiftPromised());
        settle(game);
        assertEquals(copier.find(recipient).getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(copier.find(recipient).getCardsIn(ZoneType.Library).size(), 7);
        assertTrue(game.getCardState(copier.find(source)).isInZone(ZoneType.Battlefield));
        assertTrue(source.isInZone(ZoneType.Stack));
        assertEquals(recipient.getCardsIn(ZoneType.Hand).size(), 0);
        assertEquals(recipient.getCardsIn(ZoneType.Library).size(), 8);
    }

    @Test public void aCounteredGiftPermanentKeepsCostsPaidWithoutGivingTheGift() {
        Pod pod = pod();
        SpellAbility spell = castScrapshooter(pod, true);
        Player recipient = spell.getHostCard().getPromisedGift();
        counterTop(pod);
        settle(pod.game);
        assertEquals(recipient.getCardsIn(ZoneType.Hand).size(), 0);
        assertTrue(pod.game.getCardState(spell.getHostCard()).isInZone(ZoneType.Graveyard));
        assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
    }

    @Test public void anUnpromisedScrapshooterGivesNoOpponentACard() {
        Pod pod = pod();
        SpellAbility spell = castScrapshooter(pod, false);
        assertFalse(spell.isGiftPromised());
        settle(pod.game);
        assertTrue(pod.game.getCardState(spell.getHostCard()).isInZone(ZoneType.Battlefield));
        assertEquals(pod.game.getPlayers().stream().mapToInt(p -> p.getCardsIn(ZoneType.Hand).size()).sum(), 0);
    }

    @Test public void copiedPaidFloodMawGiftsThenReturnsANoncreature() {
        Pod pod = pod();
        Card target = addCardToZone("Sol Ring", pod.enemy, ZoneType.Battlefield);
        SpellAbility spell = castFloodMaw(pod, true, target);
        Card source = spell.getHostCard();
        Player recipient = source.getPromisedGift();
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game game = copier.makeCopy(null, pod.actor);
        assertEquals(game.getStack().size(), 1);
        assertTrue(game.getStack().peekAbility().isGiftPromised());
        settle(game);
        assertFish(copier.find(recipient));
        assertTrue(game.getCardState(copier.find(target)).isInZone(ZoneType.Hand));
        assertTrue(game.getCardState(copier.find(source)).isInZone(ZoneType.Graveyard));
        assertTrue(target.isInZone(ZoneType.Battlefield));
        assertTrue(source.isInZone(ZoneType.Stack));
        assertTrue(pod.game.getPlayers().stream().allMatch(p -> p.getCreaturesInPlay().isEmpty()));
    }

    @Test public void anUnpromisedFloodMawReturnsACreatureWithoutAFish() {
        Pod pod = pod();
        Card target = addCardToZone("Grizzly Bears", pod.enemy, ZoneType.Battlefield);
        SpellAbility spell = castFloodMaw(pod, false, target);
        settle(pod.game);
        assertTrue(pod.game.getCardState(target).isInZone(ZoneType.Hand));
        assertFalse(spell.isGiftPromised());
        assertTrue(pod.game.getPlayers().stream().allMatch(p -> p.getCreaturesInPlay().isEmpty()));
    }

    @Test public void aCounteredGiftInstantCreatesNoFishAndReturnsNothing() {
        Pod pod = pod();
        Card target = addCardToZone("Sol Ring", pod.enemy, ZoneType.Battlefield);
        SpellAbility spell = castFloodMaw(pod, true, target);
        counterTop(pod);
        settle(pod.game);
        assertTrue(target.isInZone(ZoneType.Battlefield));
        assertTrue(pod.game.getCardState(spell.getHostCard()).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getPlayers().stream().allMatch(p -> p.getCreaturesInPlay().isEmpty()));
        assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
    }

    private SpellAbility selectedSpell(Pod pod, String name, boolean gifted) {
        Card card = addCardToZone(name, pod.actor, ZoneType.Hand);
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        if (gifted) {
            var optional = forge.game.GameActionUtil.getOptionalCostValues(spell).stream()
                    .filter(value -> value.getType() == forge.game.spellability.OptionalCost.PromiseGift).toList();
            assertEquals(optional.size(), 1);
            spell = forge.game.GameActionUtil.addOptionalCosts(spell, optional);
            assertTrue(spell.isGiftPromised());
        }
        return spell;
    }

    private SpellAbility castScrapshooter(Pod pod, boolean gifted) {
        for (int index = 0; index < 3; index++) addCardToZone("Forest", pod.actor, ZoneType.Battlefield);
        SpellAbility spell = selectedSpell(pod, "Scrapshooter", gifted);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, null));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
        return spell;
    }

    private SpellAbility castFloodMaw(Pod pod, boolean gifted, Card target) {
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        SpellAbility spell = selectedSpell(pod, "Into the Flood Maw", gifted);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, action -> {
            if (gifted) action.getSubAbility().getTargets().add(target);
            else action.getTargets().add(target);
        }));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
        return spell;
    }

    private void counterTop(Pod pod) {
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.enemy, ZoneType.Battlefield);
        Card counter = addCardToZone("Counterspell", pod.enemy, ZoneType.Hand);
        SpellAbility response = counter.getFirstSpellAbility();
        response.setActivatingPlayer(pod.enemy);
        response.getTargets().add(pod.game.getStack().peekAbility());
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.enemy, response, null));
        assertTrue(pod.enemy.getLandsInPlay().stream().allMatch(Card::isTapped));
    }

    private void settle(Game game) {
        for (int step = 0; step < 40; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Gift stack did not settle");
    }

    private void recipientInZone(ZoneType zone) {
        Pod pod = pod();
        String name = zone == ZoneType.Battlefield ? "Long River Lurker" : "Into the Flood Maw";
        Card card = addCardToZone(name, pod.actor, zone == ZoneType.Stack ? ZoneType.Hand : zone);
        if (zone == ZoneType.Stack) {
            pod.actor.getZone(ZoneType.Hand).remove(card);
            pod.game.getStackZone().add(card);
        }
        promise(pod, card);
        GameCopier copier = new GameCopier(pod.game);
        Game game = copier.makeCopy(null, pod.actor);
        Card result = copier.find(card);
        assertTrue(result.hasPromisedGift());
        assertSame(result.getPromisedGift(), copier.find(pod.enemy));
        assertSame(result.getPromisedGift().getGame(), game);
        assertSame(result.getView().getPromisedGift(), copier.find(pod.enemy).getView());
        result.setPromisedGift(copier.find(pod.game.getPlayers().get(2)));
        assertSame(card.getPromisedGift(), pod.enemy);
    }

    private void promise(Pod pod, Card card) {
        SpellAbility action = card.getFirstSpellAbility();
        action.setActivatingPlayer(pod.actor);
        CostPromiseGift cost = new CostPromiseGift();
        assertTrue(cost.canPay(action, pod.actor, false));
        assertTrue(cost.getPotentialPlayers(pod.actor, action).contains(pod.enemy));
        assertTrue(cost.payAsDecided(pod.actor, PaymentDecision.players(List.of(pod.enemy)), action, false));
        assertSame(card.getPromisedGift(), pod.enemy);
    }

    private void resolveGift(Card card, Player actor) {
        SpellAbility effect = card.getFirstSpellAbility().getAdditionalAbility("GiftAbility");
        assertNotNull(effect);
        effect.setActivatingPlayer(actor);
        AbilityUtils.resolve(effect);
    }

    private void assertFish(Player recipient) {
        List<Card> creatures = recipient.getCreaturesInPlay();
        assertEquals(creatures.size(), 1);
        Card fish = creatures.get(0);
        assertTrue(fish.isToken());
        assertTrue(fish.isTapped());
        assertTrue(fish.getType().hasSubtype("Fish"));
        assertSame(fish.getOwner(), recipient);
        assertSame(fish.getController(), recipient);
        assertEquals(fish.getNetPower(), 1);
        assertEquals(fish.getNetToughness(), 1);
    }

    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("gift-context-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Gift context research"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return new Pod(game, actor, game.getPlayers().get(1));
    }

    private record Pod(Game game, Player actor, Player enemy) { }
}
