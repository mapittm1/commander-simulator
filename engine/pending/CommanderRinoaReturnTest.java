package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderRinoaReturnTest extends AITest {
    @Test
    public void decliningLeavesTheOncePerTurnReturnAvailable() {
        verifyLimit(false);
    }

    @Test
    public void exilingTheDeadCreatureLeavesTheOncePerTurnReturnAvailable() {
        verifyLimit(true);
    }

    private void verifyLimit(boolean exileFirst) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Rinoa"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        Card reaper = addCardToZone("Rinoa, Angel Wing", actor, ZoneType.Battlefield);
        int[] prompts = {0};
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                if (ability.getHostCard().equals(reaper)) {
                    return ++prompts[0] > 1 || exileFirst;
                }
                return super.confirmTrigger(ability);
            }
        });
        for (int index = 0; index < 3; index++) {
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        Card first = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        Card third = addCardToZone("Balduvian Bears", actor, ZoneType.Battlefield);
        for (Card bear : List.of(first, second, third)) {
            bear.setCounters(CounterEnumType.M1M1, 1);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        Combat combat = new Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        for (Card bear : List.of(first, second, third)) {
            combat.addAttacker(bear, opponent);
        }


        if (exileFirst) {
            queueDeath(game, actor, first);
            assertEquals(game.getStack().size(), 1);
            Player thirdPlayer = game.getPlayers().get(2);
            addCardToZone("Forest", thirdPlayer, ZoneType.Battlefield);
            Card vacuum = addCardToZone("Ghost Vacuum", thirdPlayer, ZoneType.Battlefield);
            SpellAbility exile = vacuum.getSpellAbilities().stream().filter(SpellAbility::usesTargeting)
                    .findFirst().orElseThrow();
            exile.setActivatingPlayer(thirdPlayer);
            exile.getTargets().add(game.getCardState(first));
            assertTrue(ComputerUtil.playStack(exile, thirdPlayer, game));
            game.getStack().resolveStack();
            assertTrue(game.getCardState(first).isInZone(ZoneType.Exile), "Ghost Vacuum must first exile the dead card");
            settle(game);
            assertTrue(game.getCardState(first).isInZone(ZoneType.Exile), "Rinoa must not return the exiled card; actual zone=" + game.getCardState(first).getZone().getZoneType());
        } else {
            kill(game, actor, first);
            assertTrue(game.getCardState(first).isInZone(ZoneType.Graveyard));
        }
        // If the engine skips the impossible return without asking, that is also valid.
        int promptsAfterFirst = prompts[0];

        kill(game, actor, second);
        assertEquals(prompts[0], promptsAfterFirst + 1);
        assertTrue(game.getCardState(second).isInZone(ZoneType.Battlefield));
        assertEquals(game.getCardState(second).getController(), actor);

        kill(game, actor, third);
        assertEquals(prompts[0], promptsAfterFirst + 1);
        assertTrue(game.getCardState(third).isInZone(ZoneType.Graveyard));
    }

    private void kill(Game game, Player actor, Card target) {
        queueDeath(game, actor, target);
        settle(game);
    }

    private void queueDeath(Game game, Player actor, Card target) {
        Card cut = addCardToZone("Cut Down", actor, ZoneType.Hand);
        SpellAbility cast = cut.getFirstSpellAbility();
        cast.setActivatingPlayer(actor);
        cast.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(cast, actor, game));
        game.getStack().resolveStack();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void settle(Game game) {
        for (int i = 0; i < 12 && !game.getStack().isEmpty(); i++) {
            game.getStack().resolveStack();
        }
        assertTrue(game.getStack().isEmpty());
    }
}
