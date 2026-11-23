package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameSnapshot;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Cached game references must live only inside an explicit decision/preview scope. */
public final class CommanderAiCacheLifetimeTest extends AITest {
    @Test public void outsideScopeRecomputesRatherThanKeepingGameArguments() {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        assertEquals(cached("outside",calls,argument),1);
        assertEquals(cached("outside",calls,argument),2);
    }
    @Test public void outsideScopeDoesNotReuseMutatedArgumentState() {
        AtomicInteger state=new AtomicInteger(4);Object argument=new Object();
        assertEquals(AiCache.getCached("mutable",state::get,null,argument),Integer.valueOf(4));
        state.set(9);
        assertEquals(AiCache.getCached("mutable",state::get,null,argument),Integer.valueOf(9));
    }
    @Test public void completedScopeDoesNotPublishItsEntries() {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        assertEquals(AiCache.withIsolatedCache(() -> cached("completed",calls,argument)),Integer.valueOf(1));
        assertEquals(cached("completed",calls,argument),2);
    }
    @Test public void sameScopeMemoizesRepeatedArguments() {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        AiCache.withIsolatedCache(() -> {
            assertEquals(cached("memo",calls,argument),1);assertEquals(cached("memo",calls,argument),1);return null;
        });assertEquals(calls.get(),1);
    }
    @Test public void separateScopesRecompute() {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        for(int expected=1;expected<=3;expected++) {
            assertEquals(AiCache.withIsolatedCache(() -> cached("separate",calls,argument)),Integer.valueOf(expected));
        }
    }
    @Test public void nestedScopeRestoresOuterValues() {
        nested(false);
    }
    @Test public void failedNestedScopeRestoresOuterValues() {
        nested(true);
    }
    private void nested(boolean fail) {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        AiCache.withIsolatedCache(() -> {
            assertEquals(cached("nested",calls,argument),1);
            Runnable inner=() -> AiCache.withIsolatedCache(() -> {
                assertEquals(cached("nested",calls,argument),2);
                if(fail) throw new IllegalStateException("Preview failed");return null;
            });
            if(fail) assertThrows(IllegalStateException.class,inner::run);else inner.run();
            assertEquals(cached("nested",calls,argument),1);return null;
        });assertEquals(calls.get(),2);
    }
    @Test public void failedScopeDoesNotLeakItsEntries() {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        assertThrows(IllegalStateException.class,() -> AiCache.withIsolatedCache(() -> {
            cached("failure",calls,argument);throw new IllegalStateException("Decision failed");
        }));assertEquals(cached("failure",calls,argument),2);
    }
    @Test public void clearInvalidatesOnlyTheCurrentScope() {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        AiCache.withIsolatedCache(() -> {
            assertEquals(cached("clear",calls,argument),1);
            AiCache.withIsolatedCache(() -> { cached("clear",calls,argument);AiCache.clear();return null; });
            assertEquals(cached("clear",calls,argument),1);AiCache.clear();
            assertEquals(cached("clear",calls,argument),3);return null;
        });
    }
    @Test public void explicitIdentityComparisonRemainsSupported() {
        AtomicInteger calls=new AtomicInteger();String first=new String("same"),second=new String("same");
        AiCache.withIsolatedCache(() -> {
            assertEquals(AiCache.getCached("identity",calls::incrementAndGet,List.of(AiCache::identity),first),Integer.valueOf(1));
            assertEquals(AiCache.getCached("identity",calls::incrementAndGet,List.of(AiCache::identity),second),Integer.valueOf(2));
            assertEquals(AiCache.getCached("identity",calls::incrementAndGet,List.of(AiCache::identity),first),Integer.valueOf(1));return null;
        });
    }
    @Test public void nullResultsAreMemoizedInsideScope() {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        AiCache.withIsolatedCache(() -> {
            for(int i=0;i<2;i++) assertNull(AiCache.getCached("null",() -> { calls.incrementAndGet();return null; },null,argument));
            return null;
        });assertEquals(calls.get(),1);
    }
    @Test public void unscopedThreadsCannotReuseOneAnothersResults() throws Exception {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();
        assertEquals(cached("thread-unscoped",calls,argument),1);AtomicInteger result=new AtomicInteger();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(() -> {
            try { result.set(cached("thread-unscoped",calls,argument)); }
            catch(Throwable problem) { failure.set(problem); }
        });worker.start();worker.join(5000);assertFalse(worker.isAlive());assertNull(failure.get());
        assertEquals(result.get(),2);
    }
    @Test public void concurrentExplicitScopesStayIndependent() throws Exception {
        AtomicInteger calls=new AtomicInteger();Object argument=new Object();AtomicReference<Throwable> failure=new AtomicReference<>();
        AiCache.withIsolatedCache(() -> {
            assertEquals(cached("thread-scoped",calls,argument),1);
            Thread worker=new Thread(() -> {
                try { AiCache.withIsolatedCache(() -> {
                    assertEquals(cached("thread-scoped",calls,argument),2);
                    assertEquals(cached("thread-scoped",calls,argument),2);return null;
                }); } catch(Throwable problem) { failure.set(problem); }
            });worker.start();
            try { worker.join(5000); } catch(InterruptedException problem) { Thread.currentThread().interrupt();throw new AssertionError(problem); }
            assertFalse(worker.isAlive());assertNull(failure.get());assertEquals(cached("thread-scoped",calls,argument),1);return null;
        });assertEquals(calls.get(),2);
    }
    @Test public void liveBoardChangesAreVisibleOutsideDecisionScope() { board(0); }
    @Test public void activeCopyBoardChangesAreVisibleOutsideDecisionScope() { board(1); }
    @Test public void commonSnapshotBoardChangesAreVisibleOutsideDecisionScope() { board(2); }

    private int cached(String key,AtomicInteger calls,Object argument) {
        return AiCache.getCached("cache-lifetime-"+key,calls::incrementAndGet,null,argument);
    }
    private void board(int world) {
        List<RegisteredPlayer> seats=new ArrayList<>();
        for(int i=0;i<4;i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("cache-"+i,null)));
        GameRules rules=new GameRules(GameType.Commander);Game game=new Game(seats,rules,new Match(rules,seats,"Cache lifetime"));
        game.setNoGUIUser();game.setAge(GameStage.Play);
        Player actor=game.getPlayers().get(0),enemy=game.getPlayers().get(1),originalEnemy=enemy;
        for(Player player:game.getPlayers()) for(int i=0;i<12;i++) addCardToZone("Shock",player,ZoneType.Library);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1,actor,3);game.copyLastState();
        if(world==1) {
            GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,actor);
            actor=copier.find(actor);enemy=copier.find(enemy);
        } else if(world==2) {
            int actorId=actor.getId(),enemyId=enemy.getId();game=new GameSnapshot(game).makeCopy();game.setNoGUIUser();
            actor=game.getPlayer(actorId);enemy=game.getPlayer(enemyId);
        }
        int before=ComputerUtil.evaluateBoardPosition(actor,enemy);
        addCardToZone("Forest",enemy,ZoneType.Battlefield);
        int after=ComputerUtil.evaluateBoardPosition(actor,enemy);
        assertNotEquals(after,before,"An unscoped helper must observe the current board");
        assertEquals(enemy.getLandsInPlay().size(),1);
        if(world!=0) assertTrue(originalEnemy.getLandsInPlay().isEmpty());
    }
}
