package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntityCounterTable;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Resolve the printed fourth chapter through Forge's native counter and trigger paths. */
public class StarTrekSagaProbeTest extends AITest {
    private int lifeFromFinalChapter(String... exiledNames) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Star Trek Saga"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        Card saga = addCardToZone("All Good Things . . .", actor, ZoneType.Battlefield);
        for (String name : exiledNames) {
            Card card = addCardToZone(name, actor, ZoneType.Exile);
            saga.addExiledCard(card);
            card.setExiledWith(saga);
        }
        saga.setCounters(CounterEnumType.LORE, 3);
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        int life = actor.getLife();

        GameEntityCounterTable counters = new GameEntityCounterTable();
        saga.addCounter(CounterEnumType.LORE, 1, actor, counters);
        counters.replaceCounterEffect(game, null);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(saga.getCounters(CounterEnumType.LORE), 4);
        assertEquals(game.getStack().size(), 1);
        assertTrue(game.getStack().peekAbility().getHostCard() == saga);
        game.getStack().resolveStack();
        return actor.getLife() - life;
    }

    @Test
    public void finalChapterGainsLifeWithNoLinkedCards() {
        assertEquals(lifeFromFinalChapter(), 10);
    }

    @Test
    public void finalChapterGainsLifeWithOneLinkedCard() {
        assertEquals(lifeFromFinalChapter("Sol Ring"), 10);
    }

    @Test
    public void finalChapterGainsLifeWhenAllShareArtifact() {
        assertEquals(lifeFromFinalChapter("Sol Ring", "Ornithopter", "Darksteel Ingot"), 10);
    }

    @Test
    public void pairwiseOverlapDoesNotSupplyACommonCardType() {
        assertEquals(lifeFromFinalChapter("Darksteel Ingot", "Ornithopter", "Grizzly Bears"), 0);
    }
}
