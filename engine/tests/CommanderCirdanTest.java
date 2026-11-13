package forge.ai;

import com.google.common.collect.ListMultimap;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CommanderCirdanTest extends AITest {
    @Test
    public void cirdanAllowsEachPlayerWithNoVotesToPutExactlyOnePermanent() {
        verifyCirdan(true);
    }

    @Test
    public void cirdanLetsAPlayerWithNoVotesDeclineToPutAPermanent() {
        verifyCirdan(false);
    }

    private void verifyCirdan(boolean accept) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Secret votes"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 5; index++) {
            addCardToZone(index == 0 ? "Forest" : "Island", actor, ZoneType.Battlefield);
        }
        List<Card> targetLibrary = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            targetLibrary.add(addCardToZone("Island", target, ZoneType.Library));
        }
        List<Card> actorChoices = List.of(addCardToZone("Forest", actor, ZoneType.Hand),
                addCardToZone("Island", actor, ZoneType.Hand));
        List<Player> offers = new ArrayList<>();
        List<Integer> visibleVoteCounts = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            player.dangerouslySetController(new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
                @Override
                public Object vote(SpellAbility ability, String prompt, List<Object> options,
                        ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
                    assertTrue(options.contains(forPlayer));
                    assertFalse(optional);
                    visibleVoteCounts.add(votes.size());
                    Player choice = target;
                    assertTrue(options.contains(choice));
                    return choice;
                }

                @Override
                public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                        String message, List<String> options, Card card, java.util.Map<String, Object> params) {
                    if (ability.getHostCard().getName().equals("Círdan the Shipwright")
                            && mode == PlayerActionConfirmMode.ChangeZoneGeneral) {
                        offers.add(player);
                        return accept;
                    }
                    return super.confirmAction(ability, mode, message, options, card, params);
                }

                @Override
                public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin,
                        SpellAbility ability, CardCollection choices, DelayedReveal delayed,
                        String prompt, boolean optional, Player decider) {
                    if (ability.getHostCard().getName().equals("Círdan the Shipwright")) {
                        return choices.isEmpty() ? null : choices.getFirst();
                    }
                    return super.chooseSingleCardForZoneChange(destination, origin, ability, choices,
                            delayed, prompt, optional, decider);
                }
            });
        }

        Card verdict = addCardToZone("Círdan the Shipwright", actor, ZoneType.Hand);
        SpellAbility spell = verdict.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();

        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 1);
        game.getStack().resolveStack();

        assertEquals(visibleVoteCounts, List.of(0, 0, 0, 0));
        assertTrue(target.getCardsIn(ZoneType.Hand).containsAll(targetLibrary));
        assertTrue(offers.contains(actor));
        assertFalse(offers.contains(target));
        assertEquals(game.getCardState(actorChoices.get(0)).isInPlay(), accept);
        assertEquals(actor.getCardsIn(ZoneType.Hand).contains(actorChoices.get(0)), !accept);
        assertTrue(actor.getCardsIn(ZoneType.Hand).contains(actorChoices.get(1)));
    }
}
