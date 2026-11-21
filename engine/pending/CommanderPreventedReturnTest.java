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

public class CommanderPreventedReturnTest extends AITest {
    @Test
    public void reaperKeepsItsReturnWhenGrafdiggersCagePreventsTheMove() {
        verifyPreventedReturn("The Reaper, King No More", false, false);
    }

    @Test
    public void rinoaKeepsItsReturnWhenGrafdiggersCagePreventsTheMove() {
        verifyPreventedReturn("Rinoa, Angel Wing", true, false);
    }

    @Test
    public void aReaperReturnReplacedByContainmentPriestStillUsesTheAction() {
        verifyPreventedReturn("The Reaper, King No More", false, true);
    }

    @Test
    public void aRinoaReturnReplacedByContainmentPriestStillUsesTheAction() {
        verifyPreventedReturn("Rinoa, Angel Wing", true, true);
    }

    private void verifyPreventedReturn(String name, boolean attacking, boolean replaced) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Prevented return"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 2);
        Card commander = addCardToZone(name, actor, ZoneType.Battlefield);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                return ability.getHostCard().equals(commander) || super.confirmTrigger(ability);
            }
        });
        for (int index = 0; index < 5; index++) {
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        addCardToZone("Plains", actor, ZoneType.Battlefield);
        addCardToZone("Plains", actor, ZoneType.Battlefield);
        Card cage = addCardToZone(replaced ? "Containment Priest" : "Grafdigger's Cage",
                game.getPlayers().get(2), ZoneType.Battlefield);
        Player owner = attacking ? actor : opponent;
        Card first = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", owner, ZoneType.Battlefield);
        Card third = addCardToZone("Balduvian Bears", owner, ZoneType.Battlefield);
        for (Card bear : List.of(first, second, third)) {
            bear.setCounters(CounterEnumType.M1M1, 1);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.getPhaseHandler().setCombat(new Combat(actor));
        if (attacking) {
            for (Card bear : List.of(first, second, third)) {
                game.getCombat().addAttacker(bear, opponent);
            }
        }

        kill(game, actor, first);
        assertTrue(game.getCardState(first).isInZone(replaced ? ZoneType.Exile : ZoneType.Graveyard),
                "The blocker must actually prevent or replace the first return");
        SpellAbility removal = addCardToZone(replaced ? "Doom Blade" : "Disenchant", actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(actor);
        removal.getTargets().add(cage);
        assertTrue(ComputerUtil.playStack(removal, actor, game));
        settle(game);
        assertTrue(game.getCardState(cage).isInZone(ZoneType.Graveyard));

        kill(game, actor, second);
        assertTrue(game.getCardState(second).isInZone(replaced ? ZoneType.Graveyard : ZoneType.Battlefield),
                "A prohibition preserves the allowance; a replacement uses the action");
        if (!replaced) {
            assertEquals(game.getCardState(second).getController(), actor);
        }
        kill(game, actor, third);
        assertTrue(game.getCardState(third).isInZone(ZoneType.Graveyard));
    }

    private void kill(Game game, Player actor, Card target) {
        SpellAbility cut = addCardToZone("Cut Down", actor, ZoneType.Hand).getFirstSpellAbility();
        cut.setActivatingPlayer(actor);
        cut.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(cut, actor, game));
        game.getStack().resolveStack();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        settle(game);
    }

    private void settle(Game game) {
        for (int index = 0; index < 12 && !game.getStack().isEmpty(); index++) {
            game.getStack().resolveStack();
        }
        assertTrue(game.getStack().isEmpty());
    }
}
