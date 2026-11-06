package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.LobbyPlayer;
import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.event.GameEventSpellResolved;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Real native optional-trigger checkpoints: isolation, continuation and guards. */
public class OptionalTriggerCheckpointTest extends AITest {
    public static final class Events {
        int resolutions;
        @Subscribe public void resolved(GameEventSpellResolved event) { resolutions++; }
    }

    private static String fingerprint(Game game) {
        StringBuilder value = new StringBuilder();
        value.append(game.getTimestamp()).append('/').append(game.getPhaseHandler().getPhase())
                .append('/').append(game.getPhaseHandler().getTurn()).append('/')
                .append(game.getStack().isResolving()).append('/').append(game.getStack().isFrozen());
        for (Player player : game.getRegisteredPlayers()) {
            value.append("|P").append(player.getId()).append(':').append(player.getLife())
                    .append(':').append(player.getManaPool().totalMana()).append(':').append(player.getSpellsCastThisTurn());
            for (ZoneType zone : Player.ALL_ZONES) {
                var location = player.getZone(zone);
                if (location == null) continue;
                value.append(zone);
                for (Card card : location.getCards()) value.append(',').append(card.getId());
            }
        }
        for (Card card : game.getCardsInGame()) {
            value.append("|C").append(card.getId()).append(':').append(card.getController().getId())
                    .append(':').append(card.getOwner().getId()).append(':').append(card.isTapped())
                    .append(':').append(card.getCounters()).append(':').append(card.getRemembered())
                    .append(':').append(card.getImprintedCards());
        }
        for (var entry : game.getStack()) {
            SpellAbility action = entry.getSpellAbility();
            value.append("|S").append(action.getId()).append(':').append(action.getActivatingPlayer().getId())
                    .append(':').append(action.getTargets()).append(':').append(action.getPayingMana().size());
        }
        return value.toString();
    }

    private static void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.isGameOver() || game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Checkpoint fixture failed to settle");
    }

    private static final class ObservingController extends PlayerControllerAi {
        private final Game source;
        private final Player actor;
        private final boolean inspectCopies;
        private final boolean rejectLive;
        int decisions;

        ObservingController(Game game, Player actor, LobbyPlayer lobby, boolean inspectCopies, boolean rejectLive) {
            super(game, actor, lobby);
            source = game;
            this.actor = actor;
            this.inspectCopies = inspectCopies;
            this.rejectLive = rejectLive;
        }

        private void inspect(WrappedAbility wrapper, boolean accepted) {
            GameCopier copier = new GameCopier(source).withStack(true);
            Game copy = copier.makeCopy();
            Events events = new Events();
            copy.subscribeToEvents(events);
            String beforeInvalid = fingerprint(copy);
            assertThrows(IllegalStateException.class,
                    () -> copy.getStack().resumeOptionalTriggerCheckpoint(wrapper, accepted));
            assertEquals(fingerprint(copy), beforeInvalid);
            assertEquals(events.resolutions, 0);
            WrappedAbility copiedWrapper = copier.find(wrapper);
            Player copiedActor = copier.find(actor);
            Card copiedHost = copiedWrapper.getHostCard();
            int casts = copiedActor.getSpellsCastThisTurn();
            int tapped = (int) copiedActor.getLandsInPlay().stream().filter(Card::isTapped).count();
            copy.getStack().resumeOptionalTriggerCheckpoint(copiedWrapper, accepted);
            assertFalse(copy.getStack().isResolving());
            assertFalse(copy.getStack().isFrozen());
            assertEquals(events.resolutions, 1, "Native parent completion must emit one resolution event");
            assertEquals(copy.getStack().size(), 1);
            assertEquals(copiedActor.getSpellsCastThisTurn(), casts);
            assertEquals((int) copiedActor.getLandsInPlay().stream().filter(Card::isTapped).count(), tapped);
            assertSame(copiedHost.getOwner(), copiedActor);
            assertSame(copiedHost.getController(), copier.find(source.getPlayers().get(accepted ? 1 : 0)));
            settle(copy);
            assertEquals(events.resolutions, 2, "Parent and opposing spell each complete exactly once");
            assertEquals(copiedActor.hasLost(), !accepted);
            assertEquals(copiedActor.getLife(), accepted ? 2 : 0);
        }

        @Override public boolean confirmTrigger(WrappedAbility wrapper) {
            if (wrapper.getWrappedAbility().getApi() != ApiType.ControlSpell) return super.confirmTrigger(wrapper);
            decisions++;
            assertTrue(source.getStack().isResolving());
            assertSame(source.getStack().peekAbility(), wrapper);
            String before = fingerprint(source);
            Random provider = MyRandom.getRandom();
            long draws = MyRandom.callCount();
            if (rejectLive) {
                assertThrows(IllegalStateException.class,
                        () -> source.getStack().resumeOptionalTriggerCheckpoint(wrapper, false));
            }
            if (inspectCopies) {
                MyRandom.withRandom(new Random(0L), () -> {
                    inspect(wrapper, false);
                    inspect(wrapper, true);
                    return null;
                });
            }
            boolean decision = super.confirmTrigger(wrapper);
            assertTrue(decision);
            assertEquals(fingerprint(source), before, "Planning must not mutate the live checkpoint");
            assertSame(MyRandom.getRandom(), provider);
            assertEquals(MyRandom.callCount(), draws);
            return decision;
        }
    }

    private void run(boolean copies, boolean rejectLive, boolean controlled) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("checkpoint" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Optional checkpoint"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) {
            addCardToZone("Island", player, ZoneType.Battlefield);
            addCardToZone("Mountain", player, ZoneType.Battlefield);
            addCardToZone("Forest", player, ZoneType.Library);
        }
        actor.setLife(2, null);
        ObservingController observer = new ObservingController(game, actor,
                actor.getController().getLobbyPlayer(), copies, rejectLive);
        actor.dangerouslySetController(observer); // Test-only observation; choices remain the real AI's.
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        SpellAbility chimera = addCardToZone("Perplexing Chimera", actor, ZoneType.Hand).getFirstSpellAbility();
        chimera.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(chimera, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 5);
        settle(game);
        Events events = new Events();
        game.subscribeToEvents(events);
        Player beneficiary = controlled ? game.getPlayers().get(2) : actor;
        if (controlled) {
            // Explicit native controller checkpoint; not a claimed Mindslaver cast history.
            actor.setLife(40, null);
            beneficiary.setLife(2, null);
            actor.addController(game.getNextTimestamp(), beneficiary);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, enemy, 1);
        SpellAbility shock = addCardToZone("Shock", enemy, ZoneType.Hand).getFirstSpellAbility();
        shock.setActivatingPlayer(enemy);
        shock.getTargets().add(beneficiary);
        assertTrue(ComputerUtil.playStack(shock, enemy, game));
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertTrue(game.getStack().peekAbility() instanceof WrappedAbility);
        game.getStack().resolveStack();
        assertEquals(game.getStack().size(), 1);
        assertSame(game.getStack().peekAbility().getActivatingPlayer(), actor);
        assertNotEquals(game.getStack().peekAbility().getTargets().getFirstTargetedPlayer(), beneficiary);
        settle(game);
        assertEquals(observer.decisions, controlled ? 0 : 1);
        assertEquals(events.resolutions, 2);
        assertFalse(beneficiary.hasLost());
        assertEquals(beneficiary.getLife(), 2);
    }

    @Test public void previewPreservesLiveWorldAndRandomProvider() { run(false, false, false); }
    @Test public void acceptedAndDeclinedCopiesCompleteNativeSuffixExactlyOnce() { run(true, false, false); }
    @Test public void liveResolvingParentCannotUseCopiedContinuationApi() { run(false, true, false); }
    @Test public void controlledDecisionRetainsActualActorAndProtectsBeneficiary() { run(false, false, true); }
}
