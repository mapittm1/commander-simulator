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
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Paid private inspection and knowledge boundaries in four-player native games. */
public class CommanderLookAtCoverageTest extends AITest {
    @Test
    public void smokeTellerPaysForAPrivateInspection() {
        verifyInspection("Smoke Teller", 2);
    }

    @Test
    public void avenSoulgazerPaysForAPrivateInspection() {
        verifyInspection("Aven Soulgazer", 3);
    }

    @Test
    public void failedPaymentDoesNotRevealTheHiddenIdentity() {
        Pod pod = pod("Smoke Teller", 2);
        pod.actor.getLandsInPlay().forEach(card -> card.setTapped(true));
        pod.action.getTargets().add(pod.hidden);
        assertFalse(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        assertTrue(pod.game.getStack().isEmpty());
        assertFalse(remembered(pod.actor, pod.hidden));
        assertHiddenPublicState(pod.hidden, pod.publicName);
    }

    @Test
    public void aFaceUpCreatureIsNotALegalInspectionTarget() {
        Pod pod = pod("Aven Soulgazer", 3);
        assertFalse(pod.host.isFaceDown());
        assertFalse(pod.action.canTarget(pod.host));
        assertTrue(pod.action.canTarget(pod.hidden));
        assertFalse(remembered(pod.actor, pod.hidden));
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }

    @Test
    public void aPendingInspectionResolvesOnlyInItsCopiedGame() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        assertFalse(remembered(pod.actor, pod.hidden));
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Card mapped = copier.find(pod.hidden);
        Player copiedActor = copy.getPlayer(pod.actor.getId());
        settle(copy);
        assertTrue(remembered(copiedActor, mapped));
        assertFalse(remembered(pod.actor, pod.hidden));
        assertEquals(pod.game.getStack().size(), 1);
        assertHiddenPublicState(mapped, pod.publicName);
        assertPrivateMemory(copy, copiedActor, mapped);
        settle(pod.game);
        assertTrue(remembered(pod.actor, pod.hidden));
    }

    @Test
    public void anAlreadyInspectedIdentityIsRemappedAndIsolatedInACopy() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        settle(pod.game);
        assertTrue(remembered(pod.actor, pod.hidden));
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Card mapped = copier.find(pod.hidden);
        Player copiedActor = copy.getPlayer(pod.actor.getId());
        assertNotSame(mapped, pod.hidden);
        assertSame(mapped.getGame(), copy);
        assertTrue(remembered(copiedActor, mapped), "The copy must retain acquired private knowledge");
        assertTrue(AiCardMemory.getMemorySet(copiedActor, AiCardMemory.MemorySet.REVEALED_CARDS)
                .stream().anyMatch(card -> card == mapped), "Memory must reference the copied object");
        assertPrivateMemory(copy, copiedActor, mapped);
        AiCardMemory.forgetCard(copiedActor, mapped, AiCardMemory.MemorySet.REVEALED_CARDS);
        assertFalse(remembered(copiedActor, mapped));
        assertTrue(remembered(pod.actor, pod.hidden), "Changing copied memory must preserve the original");
        assertHiddenPublicState(mapped, pod.publicName);
    }

    @Test
    public void copyingAnUninspectedGameDoesNotInventPrivateKnowledge() {
        Pod pod = pod("Smoke Teller", 2);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Card mapped = copier.find(pod.hidden);
        for (Player player : copy.getPlayers()) {
            assertFalse(remembered(player, mapped));
        }
        assertHiddenPublicState(mapped, pod.publicName);
    }

    @Test
    public void aRecastFaceDownCardDoesNotReuseAnEarlierInspection() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        settle(pod.game);
        Card recast = recastHidden(pod);
        assertFalse(remembered(pod.actor, recast),
                "Knowledge of an earlier object must not identify a new hidden cast");
    }

    @Test
    public void aCopyDoesNotTransferInspectionOfAnEarlierIncarnation() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        settle(pod.game);
        Card recast = recastHidden(pod);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertFalse(remembered(copy.getPlayer(pod.actor.getId()), copier.find(recast)),
                "Stale private knowledge must not identify the copied new object");
    }

    @Test
    public void aFreshInspectionReplacesStaleIncarnationKnowledge() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        settle(pod.game);
        Card recast = recastHidden(pod);
        pod.actor.getLandsInPlay().forEach(card -> card.setTapped(false));
        pod.game.getAction().revealTo(recast, pod.actor);
        assertTrue(remembered(pod.actor, recast));
        assertTrue(AiCardMemory.getMemorySet(pod.actor, AiCardMemory.MemorySet.REVEALED_CARDS)
                .stream().anyMatch(card -> card == recast),
                "A new reveal must retain the newly inspected object");
    }

    @Test
    public void copiedPrivateMemoriesRemainSeparateForEachPlayer() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        settle(pod.game);
        Player second = pod.game.getPlayers().get(2);
        pod.game.getAction().revealTo(pod.hidden, second);
        assertTrue(remembered(second, pod.hidden));
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Card mapped = copier.find(pod.hidden);
        Player copiedFirst = copy.getPlayer(pod.actor.getId());
        Player copiedSecond = copy.getPlayer(second.getId());
        assertTrue(remembered(copiedFirst, mapped));
        assertTrue(remembered(copiedSecond, mapped));
        assertFalse(remembered(copy.getPlayers().get(3), mapped));
        AiCardMemory.forgetCard(copiedFirst, mapped, AiCardMemory.MemorySet.REVEALED_CARDS);
        assertTrue(remembered(copiedSecond, mapped), "Copied players must not share memory sets");
        assertTrue(remembered(pod.actor, pod.hidden));
        assertTrue(remembered(second, pod.hidden));
    }

    @Test
    public void returningAFaceDownPermanentPreservesItsPubliclyRevealedIdentity() {
        Pod pod = pod("Smoke Teller", 2);
        Card returned = pod.game.getAction().moveToHand(pod.hidden, null);
        assertTrue(returned.isInZone(ZoneType.Hand));
        assertFalse(returned.isFaceDown());
        for (Player player : pod.game.getPlayers()) {
            if (player != returned.getOwner()) {
                assertTrue(remembered(player, returned),
                        "The mandatory departure reveal identifies the returned card to " + player.getName());
            }
        }
    }

    @Test
    public void returningAPublicFaceUpCreatureKeepsItsIdentityKnown() {
        Pod pod = pod("Smoke Teller", 2);
        Player owner = pod.hidden.getOwner();
        Card publicCard = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        assertTrue(publicCard.getView().canBeShownTo(pod.actor.getView()));
        Card returned = pod.game.getAction().moveToHand(publicCard, null);
        assertTrue(remembered(pod.actor, returned), "A visible return must not lose its known identity");
    }

    @Test
    public void returningAnUnseenFaceDownExiledCardDoesNotRevealIt() {
        Pod pod = pod("Smoke Teller", 2);
        Card hidden = addCardToZone("Fathom Seer", pod.hidden.getOwner(), ZoneType.Exile);
        assertTrue(hidden.turnFaceDown(true));
        assertFalse(hidden.getView().canBeShownTo(pod.actor.getView()));
        Card returned = pod.game.getAction().moveToHand(hidden, null);
        assertFalse(remembered(pod.actor, returned), "A hidden exile return must not leak its identity");
    }

    @Test
    public void movingAnUnseenLibraryCardToHandDoesNotRevealIt() {
        Pod pod = pod("Smoke Teller", 2);
        Card hidden = pod.hidden.getOwner().getCardsIn(ZoneType.Library).get(0);
        assertFalse(hidden.getView().canBeShownTo(pod.actor.getView()));
        Card returned = pod.game.getAction().moveToHand(hidden, null);
        assertFalse(remembered(pod.actor, returned), "A hidden library move must not invent knowledge");
    }

    @Test
    public void activeInspectionMemorySurvivesCopiedPlayerControl() {
        Pod pod = pod("Smoke Teller", 2);
        long control = pod.game.getNextTimestamp();
        pod.actor.addController(control, pod.game.getPlayers().get(2));
        pay(pod);
        settle(pod.game);
        assertTrue(remembered(pod.actor, pod.hidden));
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Player copiedActor = copy.getPlayer(pod.actor.getId());
        Card mapped = copier.find(pod.hidden);
        assertTrue(copiedActor.isControlled());
        assertTrue(remembered(copiedActor, mapped), "The copied active agent must retain its inspection");
        AiCardMemory.forgetCard(copiedActor, mapped, AiCardMemory.MemorySet.REVEALED_CARDS);
        assertTrue(remembered(pod.actor, pod.hidden));
    }

    @Test
    public void baseInspectionMemoryReturnsAfterCopiedControlEnds() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        settle(pod.game);
        long control = pod.game.getNextTimestamp();
        pod.actor.addController(control, pod.game.getPlayers().get(2));
        assertFalse(remembered(pod.actor, pod.hidden), "The supplied control agent starts without memory");
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Player copiedActor = copy.getPlayer(pod.actor.getId());
        assertFalse(remembered(copiedActor, copier.find(pod.hidden)));
        copiedActor.removeController(control);
        assertTrue(remembered(copiedActor, copier.find(pod.hidden)), "The base agent's memory must survive control");
        pod.actor.removeController(control);
        assertTrue(remembered(pod.actor, pod.hidden));
    }

    @Test
    public void stackedInspectionMemoriesStayWithTheirCopiedControllers() {
        Pod pod = pod("Smoke Teller", 2);
        pay(pod);
        settle(pod.game);
        long first = pod.game.getNextTimestamp();
        pod.actor.addController(first, pod.game.getPlayers().get(2));
        pod.game.getAction().revealTo(pod.hidden, pod.actor);
        assertTrue(remembered(pod.actor, pod.hidden));
        long second = pod.game.getNextTimestamp();
        pod.actor.addController(second, pod.game.getPlayers().get(3));
        assertFalse(remembered(pod.actor, pod.hidden));
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Player copiedActor = copy.getPlayer(pod.actor.getId());
        Card mapped = copier.find(pod.hidden);
        assertFalse(remembered(copiedActor, mapped));
        copiedActor.removeController(second);
        assertTrue(remembered(copiedActor, mapped), "Each restored control layer must retain its own memory");
        AiCardMemory.forgetCard(copiedActor, mapped, AiCardMemory.MemorySet.REVEALED_CARDS);
        copiedActor.removeController(first);
        assertTrue(remembered(copiedActor, mapped), "Forgetting in a control layer must preserve the base agent");
        pod.actor.removeController(second);
        assertTrue(remembered(pod.actor, pod.hidden));
    }

    private Card recastHidden(Pod pod) {
        Player owner = pod.hidden.getOwner();
        // A new face-down cast may come from either morph card in this hidden hand.
        addCardToZone("Fathom Seer", owner, ZoneType.Hand);
        Card hand = pod.game.getAction().moveToHand(pod.hidden, null);
        owner.getLandsInPlay().forEach(card -> card.setTapped(false));
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 3);
        SpellAbility morph = hand.getAllPossibleAbilities(owner, false).stream()
                .filter(ability -> ability.isSpell() && ability.isCastFaceDown()).findFirst().orElseThrow();
        morph.setActivatingPlayer(owner);
        assertTrue(ComputerUtil.playStack(morph, owner, pod.game));
        settle(pod.game);
        Card recast = pod.game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == pod.hidden.getId()).findFirst().orElseThrow();
        assertNotSame(recast, pod.hidden);
        assertFalse(recast.equalsWithGameTimestamp(pod.hidden));
        assertTrue(recast.isFaceDown());
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, pod.actor, 3);
        return recast;
    }

    private void verifyInspection(String name, int cost) {
        Pod pod = pod(name, cost);
        pay(pod);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), (long) cost);
        assertFalse(remembered(pod.actor, pod.hidden), "Inspection happens at resolution");
        settle(pod.game);
        assertPrivateMemory(pod.game, pod.actor, pod.hidden);
        assertHiddenPublicState(pod.hidden, pod.publicName);
    }

    private void pay(Pod pod) {
        pod.action.getTargets().add(pod.hidden);
        assertTrue(pod.action.isTargetNumberValid());
        assertTrue(pod.action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
    }

    private boolean remembered(Player player, Card card) {
        return AiCardMemory.isRememberedCard(player, card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }

    private void assertPrivateMemory(Game game, Player actor, Card card) {
        assertTrue(remembered(actor, card));
        for (Player player : game.getPlayers()) {
            if (player != actor && player != card.getController()) {
                assertFalse(remembered(player, card), "Another player must not receive private inspection");
            }
        }
    }

    private void assertHiddenPublicState(Card card, String publicName) {
        assertTrue(card.isFaceDown());
        assertEquals(card.getName(), publicName);
        assertEquals(card.getNetPower(), 2);
        assertEquals(card.getNetToughness(), 2);
    }

    private Pod pod(String source, int cost) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("inspection" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Private inspection"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player owner = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        for (int index = 0; index < cost; index++) {
            addCardToZone(cost == 3 ? "Plains" : "Island", actor, ZoneType.Battlefield);
        }
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", owner, ZoneType.Battlefield);
        }
        Card host = addCardToZone(source, actor, ZoneType.Battlefield);
        host.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 2);
        Card card = addCardToZone("Willbender", owner, ZoneType.Hand);
        SpellAbility morph = card.getAllPossibleAbilities(owner, false).stream()
                .filter(ability -> ability.isSpell() && ability.isCastFaceDown()).findFirst().orElseThrow();
        morph.setActivatingPlayer(owner);
        assertTrue(morph.canPlay());
        assertTrue(ComputerUtil.playStack(morph, owner, game));
        settle(game);
        Card hidden = game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        assertTrue(hidden.isFaceDown());
        assertFalse(remembered(actor, hidden));
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        SpellAbility action = host.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.LookAt).findFirst().orElseThrow();
        action.setActivatingPlayer(actor);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return new Pod(game, actor, host, hidden, action, hidden.getName());
    }

    private void settle(Game game) {
        for (int step = 0; step < 20; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) {
                return;
            }
            game.getStack().resolveStack();
        }
        throw new AssertionError("Native inspection stack did not settle");
    }

    private record Pod(Game game, Player actor, Card host, Card hidden,
            SpellAbility action, String publicName) { }
}
