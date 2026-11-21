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
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import forge.util.MyRandom;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Supplied battlefield/decisions and native paid die rolls and responses. */
public class CommanderPriorityBoardingTest extends AITest {
    @Test
    public void exileWaitsForASeparateReflexiveTrigger() {
        Pod pod = pod();
        roll(pod);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library),
                "The reveal resolves before the separately counterable exile choice");
        assertEquals(pod.game.getStack().size(), 1);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
    }
    @Test
    public void anAcceptedRevealEventuallyExilesTheCard() {
        Pod pod = pod();
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getNumRollsThisTurn(), 1);
    }
    @Test
    public void decliningTheRevealPreservesTheLaterChoice() {
        Pod pod = pod();
        pod.decline[0] = true;
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        pod.decline[0] = false;
        untap(pod);
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getNumRollsThisTurn(), 2);
    }
    @Test
    public void counteringTheRevealPreservesTheLaterChoice() {
        Pod pod = pod();
        roll(pod);
        counterTop(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        untap(pod);
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
    }
    @Test
    public void counteringTheExileKeepsTheCardAndUsesTheRevealAllowance() {
        Pod pod = pod();
        roll(pod);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1, "The reflexive trigger needs its own stack entry");
        counterTop(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        untap(pod);
        rollWithoutReveal(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
    }
    @Test
    public void anEmptyLibraryDoesNotConsumeTheRevealAllowance() {
        Pod pod = pod(true);
        Card elixir = addCardToZone("Elixir of Immortality", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        roll(pod);
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 0);
        SpellAbility refill = elixir.getSpellAbilities().stream()
                .filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        refill.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(refill, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 1);
        assertEquals(pod.actor.getLife(), 45);
        untap(pod);
        roll(pod);
        settle(pod.game);
        assertEquals(pod.actor.getNumRollsThisTurn(), 2);
    }
    @Test
    public void decliningTheExileDoesNotReopenTheReveal() {
        Pod pod = pod();
        pod.declineExile[0] = true;
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        pod.declineExile[0] = false;
        untap(pod);
        rollWithoutReveal(pod);
        assertTrue(pod.game.getStack().isEmpty());
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        assertEquals(pod.actor.getNumRollsThisTurn(), 2);
    }
    @Test
    public void equalManaValueUsesTheRevealWithoutAnExileTrigger() {
        Pod pod = pod(false, "Elixir of Immortality");
        rollWithoutReveal(pod, 1);
        assertEquals(pod.game.getStack().size(), 1);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        untap(pod);
        rollWithoutReveal(pod, 20);
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getDiceRollsThisTurn(), List.of(1, 20));
    }
    @Test
    public void aQueuedRevealResolvesIndependentlyInCopiedGames() {
        Pod pod = pod();
        roll(pod);
        Pod fork = snapshot(pod);
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.top).isInZone(ZoneType.Exile));
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
    }
    @Test
    public void aQueuedReflexiveTriggerRetainsItsCardInCopiedGames() {
        Pod pod = pod();
        reflexive(pod);
        Pod fork = snapshot(pod);
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.top).isInZone(ZoneType.Exile));
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
    }
    @Test
    public void aReflexiveTriggerSurvivesItsSourceReturningToHand() {
        Pod pod = pod();
        reflexive(pod);
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility spell = addCardToZone("Echoing Truth", opponent, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(opponent);
        spell.getTargets().add(pod.host);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, opponent, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
        assertTrue(!pod.game.getCardState(pod.top).mayPlay(pod.actor).isEmpty());
    }
    @Test
    public void drawingTheRevealedCardPreventsItsLaterExile() {
        Pod pod = pod();
        reflexive(pod);
        SpellAbility spell = addCardToZone("Reach Through Mists", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        Card drawn = pod.game.getCardState(pod.top);
        assertTrue(drawn.isInZone(ZoneType.Hand));
        assertTrue(drawn.mayPlay(pod.actor).isEmpty());
        assertEquals(pod.actor.getCardsIn(ZoneType.Exile).size(), 0);
    }
    @Test
    public void aRevealedCardReturnedToTheLibraryIsANewObject() {
        Pod pod = pod();
        addCardToZone("Forest", pod.actor, ZoneType.Battlefield);
        reflexive(pod);
        long stamp = pod.top.getGameTimestamp();
        SpellAbility mill = addCardToZone("Thought Scour", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        mill.setActivatingPlayer(pod.actor);
        mill.getTargets().add(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(mill, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        Card milled = pod.game.getCardState(pod.top);
        assertTrue(milled.isInZone(ZoneType.Graveyard));
        revive(pod, milled);
        Card returned = pod.game.getCardState(milled);
        assertTrue(returned.isInZone(ZoneType.Library));
        assertTrue(returned.getGameTimestamp() != stamp);
        settle(pod.game);
        assertTrue(pod.game.getCardState(returned).isInZone(ZoneType.Library));
        assertEquals(pod.actor.getCardsIn(ZoneType.Exile).size(), 0);
    }
    @Test
    public void changingTheTopCardDoesNotChangeTheRevealedObject() {
        Pod pod = pod();
        addCardToZone("Forest", pod.actor, ZoneType.Battlefield);
        Card other = addCardToZone("Plains", pod.actor, ZoneType.Graveyard);
        reflexive(pod);
        revive(pod, other);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).getFirst().getId(), other.getId());
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
        assertTrue(pod.game.getCardState(other).isInZone(ZoneType.Library));
    }
    @Test
    public void nativeCleanupExpiresPermissionAndResetsTheRevealForAnExtraTurn() {
        Pod pod = pod();
        roll(pod);
        settle(pod.game);
        Card exiled = pod.game.getCardState(pod.top);
        assertTrue(!exiled.mayPlay(pod.actor).isEmpty());
        extraTurn(pod);
        assertTrue(pod.game.getCardState(exiled).mayPlay(pod.actor).isEmpty());
        roll(pod);
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Exile).size(), 2);
    }
    @Test
    public void copiedPlayPermissionExpiresWithoutChangingTheOriginalGame() {
        Pod pod = pod();
        roll(pod);
        settle(pod.game);
        Pod fork = snapshot(pod);
        assertTrue(!fork.game.getCardState(fork.top).mayPlay(fork.actor).isEmpty());
        extraTurn(fork);
        assertTrue(fork.game.getCardState(fork.top).mayPlay(fork.actor).isEmpty());
        assertTrue(!pod.game.getCardState(pod.top).mayPlay(pod.actor).isEmpty());
        extraTurn(pod);
        assertTrue(pod.game.getCardState(pod.top).mayPlay(pod.actor).isEmpty());
    }
    @Test
    public void copiedRevealTriggersUseTheAllowanceOnlyOnce() {
        Pod pod = pod();
        Card resonator = addCardToZone("Strionic Resonator", pod.actor, ZoneType.Battlefield);
        roll(pod);
        SpellAbility ability = resonator.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.CopySpellAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);
        ability.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ability, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 2);
        settle(pod.game);
        assertTrue(resonator.isTapped());
        assertEquals(pod.actor.getCardsIn(ZoneType.Exile).size(), 1);
    }
    @Test
    public void twoQueuedRollTriggersUseTheRevealOnlyOnce() {
        Pod pod = pod();
        Card second = addCardToZone("Component Pouch", pod.actor, ZoneType.Battlefield);
        roll(pod);
        Pod secondPouch = new Pod(pod.game, pod.actor, pod.host, second, pod.top, pod.decline, pod.declineExile);
        rollWithoutReveal(secondPouch);
        assertEquals(pod.game.getStack().size(), 2);
        settle(pod.game);
        assertEquals(pod.actor.getNumRollsThisTurn(), 2);
        assertEquals(pod.actor.getCardsIn(ZoneType.Exile).size(), 1);
    }
    private void reflexive(Pod pod) {
        roll(pod);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
    }
    private void revive(Pod pod, Card card) {
        SpellAbility spell = addCardToZone("Noxious Revival", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(card);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
    }
    private void extraTurn(Pod pod) {
        SpellAbility warp = addCardToZone("Time Warp", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        warp.setActivatingPlayer(pod.actor);
        warp.getTargets().add(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(warp, pod.actor, pod.game));
        settle(pod.game);
        int turn = pod.game.getPhaseHandler().getTurn();
        assertTrue(pod.game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(pod.game)));
        settle(pod.game);
        assertEquals(pod.game.getPhaseHandler().getTurn(), turn + 1);
        assertEquals(pod.game.getPhaseHandler().getPlayerTurn(), pod.actor);
    }
    private Pod snapshot(Pod original) {
        GameCopier copier = new GameCopier(original.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Pod fork = new Pod(copy, copy.getPlayer(original.actor.getId()), find(copy, original.host),
                find(copy, original.pouch), original.top == null ? null : find(copy, original.top),
                new boolean[]{false}, new boolean[]{false});
        control(fork);
        copy.getTriggerHandler().resetActiveTriggers();
        return fork;
    }
    private Card find(Game game, Card card) {
        return game.getCardsInGame().stream().filter(candidate -> candidate.getId() == card.getId())
                .findFirst().orElseThrow();
    }
    private void control(Pod pod) {
        Game game = pod.game;
        Player actor = pod.actor;
        Card host = pod.host;
        boolean[] decline = pod.decline;
        boolean[] declineExile = pod.declineExile;
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().getId() == host.getId()) {
                        assertTrue(ComputerUtil.playStack(ability, actor, game));
                    } else super.orderAndPlaySimultaneousSa(List.of(ability));
                }
            }
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                return ability.getHostCard().getId() == host.getId() ? !decline[0] : super.confirmTrigger(ability);
            }
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (ability.getHostCard().getId() == host.getId()) {
                    return ability.getApi() != ApiType.ChangeZone || !declineExile[0];
                }
                return super.confirmAction(ability, mode, message, options, card, params);
            }
        });
    }
    private void counterTop(Pod pod) {
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility spell = addCardToZone("Disallow", opponent, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(opponent);
        spell.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, opponent, pod.game));
    }
    private void untap(Pod pod) {
        SpellAbility spell = addCardToZone("Dramatic Reversal", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(!pod.pouch.isTapped());
    }
    private void roll(Pod pod) {
        rollWithoutReveal(pod);
        assertEquals(pod.game.getStack().size(), 1);
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getName(), "Priority Boarding");
    }
    private void rollWithoutReveal(Pod pod) { rollWithoutReveal(pod, null); }
    private void rollWithoutReveal(Pod pod, Integer face) {
        SpellAbility ability = pod.pouch.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.RollDice)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ability, pod.actor, pod.game));
        if (face == null) {
            pod.game.getStack().resolveStack();
        } else {
            int[] used = {0};
            MyRandom.withRandom(new Random(42) {
                @Override
                public int nextInt(int bound) {
                    if (bound == 20) { used[0]++; return face - 1; }
                    return super.nextInt(bound);
                }
            }, () -> { pod.game.getStack().resolveStack(); return null; });
            assertEquals(used[0], 1);
        }
        queue(pod.game);
        assertTrue(pod.pouch.isTapped());
    }
    private Pod pod() { return pod(false); }
    private Pod pod(boolean emptyLibrary) { return pod(emptyLibrary, "Forest"); }
    private Pod pod(boolean emptyLibrary, String topName) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Priority Boarding timing"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            if (!emptyLibrary || player != actor) {
                for (int i = 0; i < 8; i++) addCardToZone(i == 0 && player == actor ? topName : "Forest", player, ZoneType.Library);
            }
            for (int i = 0; i < 12; i++) addCardToZone("Island", player, ZoneType.Battlefield);
        }
        Card top = emptyLibrary ? null : actor.getCardsIn(ZoneType.Library).getFirst();
        Card host = addCardToZone("Priority Boarding", actor, ZoneType.Battlefield);
        Card pouch = addCardToZone("Component Pouch", actor, ZoneType.Battlefield);
        boolean[] decline = {false};
        boolean[] declineExile = {false};
        Pod pod = new Pod(game, actor, host, pouch, top, decline, declineExile);
        control(pod);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return pod;
    }
    private void queue(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }
    private void settle(Game game) {
        queue(game);
        for (int step = 0; step < 16 && !game.getStack().isEmpty(); step++) {
            game.getStack().resolveStack();
            queue(game);
        }
        assertTrue(game.getStack().isEmpty());
    }
    private record Pod(Game game, Player actor, Card host, Card pouch, Card top, boolean[] decline, boolean[] declineExile) {}
}
