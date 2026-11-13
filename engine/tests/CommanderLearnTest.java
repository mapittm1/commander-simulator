package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderLearnTest extends AITest {
    @Test
    public void popQuizOffersDiscardAndDrawWithoutAccessToSideboardLessons() {
        verifyPopQuiz(GameType.Commander, false, false);
    }

    @Test
    public void retrieverPhoenixReplacesLearningWithoutDiscarding() {
        verifyPopQuiz(GameType.Commander, true, false);
    }

    @Test
    public void constructedPopQuizCanStillChooseASideboardLesson() {
        verifyPopQuiz(GameType.Constructed, false, true);
    }

    private void verifyPopQuiz(GameType type, boolean usePhoenix, boolean allowLesson) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            RegisteredPlayer seat = type == GameType.Commander
                    ? RegisteredPlayer.forCommander(new Deck()) : new RegisteredPlayer(new Deck());
            seats.add(seat.setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(type);
        if (type == GameType.Commander) {
            rules.addAppliedVariant(type);
        }
        Game game = new Game(seats, rules, new Match(rules, seats, "Learn"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        Card lesson = addCardToZone("Environmental Sciences", actor, ZoneType.Sideboard);
        Card toDiscard = addCardToZone("Mountain", actor, ZoneType.Hand);
        Card firstDraw = addCardToZone("Plains", actor, ZoneType.Library);
        Card secondDraw = addCardToZone("Forest", actor, ZoneType.Library);
        Card phoenix = usePhoenix ? addCardToZone("Retriever Phoenix", actor, ZoneType.Graveyard) : null;
        List<List<Card>> prompts = new ArrayList<>();
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin,
                    SpellAbility ability, CardCollection choices, DelayedReveal delayed,
                    String prompt, boolean optional, Player decider) {
                if (ability.getHostCard().getName().equals("Pop Quiz")) {
                    prompts.add(List.copyOf(choices));
                    return choices.contains(lesson) ? lesson : toDiscard;
                }
                return super.chooseSingleCardForZoneChange(destination, origin, ability, choices,
                        delayed, prompt, optional, decider);
            }

            @Override
            public boolean confirmReplacementEffect(ReplacementEffect effect, SpellAbility ability,
                    GameEntity affected, String question) {
                return effect.getHostCard().equals(phoenix)
                        || super.confirmReplacementEffect(effect, ability, affected, question);
            }
        });
        Card quiz = addCardToZone("Pop Quiz", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        SpellAbility spell = quiz.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();

        assertTrue(actor.getCardsIn(ZoneType.Hand).contains(firstDraw));
        if (usePhoenix) {
            assertTrue(game.getCardState(phoenix).isInPlay());
            assertTrue(prompts.isEmpty());
            assertTrue(actor.getCardsIn(ZoneType.Hand).contains(toDiscard));
            assertTrue(actor.getCardsIn(ZoneType.Library).contains(secondDraw));
        } else if (allowLesson) {
            assertEquals(prompts, List.of(List.of(lesson, toDiscard, firstDraw)));
            assertTrue(actor.getCardsIn(ZoneType.Hand).contains(lesson));
            assertTrue(actor.getCardsIn(ZoneType.Library).contains(secondDraw));
        } else {
            assertEquals(prompts, List.of(List.of(toDiscard, firstDraw)));
            assertTrue(actor.getCardsIn(ZoneType.Sideboard).contains(lesson));
            assertTrue(actor.getCardsIn(ZoneType.Graveyard).contains(toDiscard));
            assertTrue(actor.getCardsIn(ZoneType.Hand).contains(secondDraw));
        }
    }
}
