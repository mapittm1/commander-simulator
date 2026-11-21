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
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Supplied four-player states; native mana payment, tokens, triggers and loyalty. */
public class CommanderEmpowerAiTest extends AITest {
    @Test public void nativePaymentCreatesAJaceWithTwoLoyalty() {
        Pod p = pod(); play(p);
        assertEquals(tokens(p).size(), 1);
        assertEquals(tokens(p).get(0).getCounters(CounterEnumType.LOYALTY), 2);
        assertEquals(p.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(p.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 6L);
    }
    @Test public void nativePaymentReusesAnExistingToken() {
        Pod p = pod(); play(p); Card original = tokens(p).get(0); play(p);
        assertEquals(tokens(p).size(), 1); assertSame(tokens(p).get(0), original);
        assertEquals(original.getCounters(CounterEnumType.LOYALTY), 4);
        assertEquals(p.host.getCounters(CounterEnumType.P1P1), 2);
    }
    @Test public void nontokenJaceDoesNotReceiveEmpowerCounters() {
        Pod p = pod(); Card original = addCardToZone("Jace Beleren", p.actor, ZoneType.Battlefield);
        original.setCounters(CounterEnumType.LOYALTY, 3);
        int before = original.getCounters(CounterEnumType.LOYALTY); refresh(p);
        assertTrue(original.isInZone(ZoneType.Battlefield)); play(p);
        assertTrue(original.isInZone(ZoneType.Battlefield));
        assertFalse(original.isToken()); assertEquals(original.getCounters(CounterEnumType.LOYALTY), before);
        assertEquals(tokens(p).size(), 1); assertEquals(tokens(p).get(0).getCounters(CounterEnumType.LOYALTY), 2);
    }
    @Test public void doublingSeasonDoublesTokensAndChosenLoyalty() {
        Pod p = pod(); addCardToZone("Doubling Season", p.actor, ZoneType.Battlefield); refresh(p); play(p);
        assertEquals(tokens(p).size(), 1, "Unchosen zero-loyalty token dies to state-based actions");
        assertEquals(tokens(p).get(0).getCounters(CounterEnumType.LOYALTY), 4);
    }
    @Test public void artifactCounterProhibitionLeavesNoSurvivingJace() {
        Pod p = blocked(); play(p); assertTrue(tokens(p).isEmpty());
        assertEquals(p.host.getCounters(CounterEnumType.P1P1), 0);
    }
    @Test public void aiAdmitsABeneficialPaidEmpower() {
        Pod p = pod(); assertEquals(ai(p).canPlaySa(p.action), AiPlayDecision.WillPlay);
        untouched(p); play(p); assertEquals(tokens(p).get(0).getCounters(CounterEnumType.LOYALTY), 2);
    }
    @Test public void heuristicPickerUsesEmpower() { pick(null); }
    @Test public void hybridPickerUsesEmpower() { pick(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPickerUsesEmpower() { pick(AIOption.USE_FULL_SIMULATION); }
    @Test public void aiDeclinesPayingForProhibitedLoyalty() {
        Pod p = blocked(); assertFalse(ai(p).canPlaySa(p.action) == AiPlayDecision.WillPlay); untouched(p);
    }
    @Test public void copiedPendingEmpowerCreatesOnlyInTheCopiedGame() {
        Pod p = pod(); assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game));
        assertEquals(p.game.getStack().size(), 1);
        GameCopier copier = new GameCopier(p.game).withStack(true); Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty()); settle(copy);
        assertEquals(copier.find(p.actor).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.isToken() && c.isPlaneswalker() && c.getType().hasSubtype("Jace")).count(), 1L);
        assertTrue(tokens(p).isEmpty()); assertEquals(p.game.getStack().size(), 1);
        settle(p.game); assertEquals(tokens(p).get(0).getCounters(CounterEnumType.LOYALTY), 2);
    }
    @Test public void nativeCampusCrierPaysTheGraveyardExile() { other("Campus Crier", ZoneType.Graveyard, 2, false); }
    @Test public void aiUsesCampusCriersGraveyardAbility() { other("Campus Crier", ZoneType.Graveyard, 2, true); }
    @Test public void nativeSanctumPaysManaAndTaps() { other("Theorist's Sanctum", ZoneType.Battlefield, 2, false); }
    @Test public void aiUsesSanctumsTapAbility() { other("Theorist's Sanctum", ZoneType.Battlefield, 2, true); }
    @Test public void nativeJacePaysLoyaltyAndCountsIslands() { other("Jace, Reality Sculptor", ZoneType.Battlefield, 12, false); }
    @Test public void aiUsesJacesLoyaltyAbility() { other("Jace, Reality Sculptor", ZoneType.Battlefield, 12, true); }
    @Test public void nativeAwakeningEmpowersBeforeDrawing() { other("Protege's Awakening", ZoneType.Hand, 6, false); }
    @Test public void aiCastsAwakeningWithItsDraw() { other("Protege's Awakening", ZoneType.Hand, 6, true); }
    @Test public void heuristicPickerDeclinesProhibitedLoyalty() { noPick(null); }
    @Test public void hybridPickerDeclinesProhibitedLoyalty() { noPick(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPickerDeclinesProhibitedLoyalty() { noPick(AIOption.USE_FULL_SIMULATION); }

    private void other(String name, ZoneType zone, int expectedLoyalty, boolean admission) {
        Pod p = pod(name, zone);
        int loyalty = p.host.getCounters(CounterEnumType.LOYALTY);
        int hand = p.actor.getCardsIn(ZoneType.Hand).size();
        if (admission) { assertEquals(ai(p).canPlaySa(p.action), AiPlayDecision.WillPlay); untouched(p); }
        play(p);
        assertEquals(tokens(p).size(), 1);
        assertEquals(tokens(p).get(0).getCounters(CounterEnumType.LOYALTY), expectedLoyalty);
        if (zone == ZoneType.Graveyard) assertTrue(p.host.isInZone(ZoneType.Exile));
        if (zone == ZoneType.Hand) {
            assertTrue(p.actor.getCardsIn(ZoneType.Graveyard).stream().anyMatch(c -> c.getId() == p.host.getId()));
            assertEquals(p.actor.getCardsIn(ZoneType.Hand).size(), hand);
        }
        if (p.host.isLand()) assertTrue(p.host.isTapped());
        if (p.host.isPlaneswalker()) assertEquals(p.host.getCounters(CounterEnumType.LOYALTY), loyalty + 1);
    }
    private void noPick(AIOption mode) {
        Pod p = blocked(); ai(p).setUseSimulation(mode);
        List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(a -> a.getApi() == ApiType.Empower));
        untouched(p);
    }

    @Test public void stackDescriptionUsesLoyaltyCounters() {
        Pod p = pod();
        String description = new Description().describe(p.action);
        assertTrue(description.contains("loyalty counters"), description);
        assertFalse(description.contains("+1/+1"), description);
    }
    @Test public void stackDescriptionUsesTheComputedIslandAmount() {
        Pod p = pod("Jace, Reality Sculptor", ZoneType.Battlefield);
        String description = new Description().describe(p.action);
        assertTrue(description.startsWith("Empower Jace 12"), description);
        assertTrue(description.contains("loyalty counters"), description);
    }
    private static final class Description extends forge.game.ability.effects.EmpowerEffect {
        String describe(SpellAbility action) { return getStackDescription(action); }
    }

    private void pick(AIOption mode) {
        Pod p = pod(); ai(p).setUseSimulation(mode); List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "Expected a useful paid action in mode " + mode);
        SpellAbility selected = choices.stream().filter(a -> a.getApi() == ApiType.Empower).findFirst().orElseThrow();
        untouched(p); assertTrue(ComputerUtil.playStack(selected, p.actor, p.game)); settle(p.game);
        assertEquals(tokens(p).get(0).getCounters(CounterEnumType.LOYALTY), 2);
    }
    private void untouched(Pod p) {
        assertTrue(tokens(p).isEmpty()); assertEquals(p.host.getCounters(CounterEnumType.P1P1), 0);
        assertTrue(p.actor.getLandsInPlay().stream().noneMatch(Card::isTapped)); assertTrue(p.game.getStack().isEmpty());
    }
    private Pod blocked() {
        Pod p = pod(); addCardToZone("Mycosynth Lattice", p.actor, ZoneType.Battlefield);
        addCardToZone("Solemnity", p.actor, ZoneType.Battlefield); refresh(p); return p;
    }
    private AiController ai(Pod p) { return ((PlayerControllerAi) p.actor.getController()).getAi(); }
    private List<Card> tokens(Pod p) {
        return p.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.isToken() && c.isPlaneswalker() && c.getType().hasSubtype("Jace")).toList();
    }
    private void play(Pod p) {
        assertTrue(p.action.canPlay()); assertTrue(ComputerUtilCost.canPayCost(p.action, p.actor, false));
        assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game)); settle(p.game);
    }
    private Pod pod() { return pod("Inspired Tethermage", ZoneType.Battlefield); }
    private Pod pod(String name, ZoneType zone) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Empower AI"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 12; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        Card host = addCardToZone(name, actor, zone);
        host.setTapped(false);
        if (host.isPlaneswalker()) host.setCounters(CounterEnumType.LOYALTY, 5);
        SpellAbility action = host.getSpellAbilities().stream().filter(a -> a.getApi() == ApiType.Empower).findFirst().orElseThrow();
        action.setActivatingPlayer(actor); Pod p = new Pod(game, actor, host, action); refresh(p); return p;
    }
    private void refresh(Pod p) {
        p.game.getAction().checkStateEffects(true); p.game.getTriggerHandler().resetActiveTriggers(); p.game.copyLastState();
    }
    private void settle(Game game) {
        for (int i = 0; i < 20; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Native stack did not settle");
    }
    private record Pod(Game game, Player actor, Card host, SpellAbility action) {}
}
