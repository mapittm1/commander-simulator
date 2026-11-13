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
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CommanderOracleCorrectionsTest extends AITest {
    @Test
    public void vaultChapterExilesOpposingCreatureAndCannotTargetItself() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card saga = addCardToZone("Vault 13: Dweller's Journey", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", opponent, ZoneType.Battlefield);
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        GameEntityCounterTable counters = new GameEntityCounterTable();
        saga.addCounter(CounterEnumType.LORE, 1, actor, counters);
        counters.replaceCounterEffect(game, null);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 1);
        SpellAbility chapter = game.getStack().peekAbility();
        assertEquals(chapter.getHostCard(), saga);
        assertTrue(chapter.canTarget(bear));
        assertFalse(chapter.canTarget(saga), "The Saga is excluded by other");
        chapter.resetTargets();
        chapter.getTargets().add(bear);
        game.getStack().resolveStack();
        assertTrue(game.getCardState(bear).isInZone(ZoneType.Exile));
        assertTrue(game.getCardState(saga).isInZone(ZoneType.Battlefield));
    }

    @Test
    public void ixidorsWillCountsWizardControlledByThirdPlayer() {
        Game game = newGame();
        Player caster = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Player thirdPlayer = game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", caster, ZoneType.Battlefield);
        }
        addCardToZone("Island", opponent, ZoneType.Battlefield);
        addCardToZone("Prodigal Sorcerer", thirdPlayer, ZoneType.Battlefield);
        addCardToZone("Forest", opponent, ZoneType.Library);
        Card opt = addCardToZone("Opt", opponent, ZoneType.Hand);
        SpellAbility draw = opt.getFirstSpellAbility();
        draw.setActivatingPlayer(opponent);
        assertTrue(ComputerUtil.playStack(draw, opponent, game));
        Card will = addCardToZone("Ixidor's Will", caster, ZoneType.Hand);
        SpellAbility counter = will.getFirstSpellAbility();
        counter.setActivatingPlayer(caster);
        counter.getTargets().add(game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(counter, caster, game));
        assertEquals(game.getStack().size(), 2);

        game.getStack().resolveStack();

        assertTrue(game.getStack().isEmpty(), "The opponent cannot pay for the third player's Wizard");
        assertTrue(game.getCardState(opt).isInZone(ZoneType.Graveyard));
        assertEquals(opponent.getCardsIn(ZoneType.Library).size(), 1);
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Oracle corrections"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        return game;
    }
}
