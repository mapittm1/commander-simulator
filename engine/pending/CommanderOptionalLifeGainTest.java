package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
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
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Research fixture: supplied decisions, native casts, payments and life changes. */
public class CommanderOptionalLifeGainTest extends AITest {
    @Test
    public void erebosDoesNotUseCornucopiasForbiddenLifeGain() {
        verifyLife("Erebos, God of the Dead", true, 0);
    }

    @Test
    public void tibaltDoesNotUseCornucopiasForbiddenLifeGain() {
        verifyLife("Tibalt, Rakish Instigator", true, 0);
    }

    @Test
    public void rainOfGoreReplacesALegalLifeGainAndUsesTheAction() {
        verifyLife("Rain of Gore", false, -1);
    }

    @Test
    public void anOrdinaryLifeGainUsesTheAction() {
        verifyLife(null, false, 1);
    }

    private void verifyLife(String blockerName, boolean prohibited, int firstChange) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Optional life gain"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        Card cornucopia = addCardToZone("Ancient Cornucopia", actor, ZoneType.Battlefield);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                return ability.getHostCard().equals(cornucopia) || super.confirmTrigger(ability);
            }
        });
        for (String name : List.of("Forest", "Forest", "Forest", "Forest", "Forest", "Swamp", "Swamp", "Plains", "Plains")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        Card blocker = blockerName == null ? null
                : addCardToZone(blockerName, game.getPlayers().get(2), ZoneType.Battlefield);
        if (blocker != null && blocker.isPlaneswalker()) {
            blocker.setCounters(CounterEnumType.LOYALTY, 5);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        assertEquals(actor.canGainLife(), !prohibited);
        int lifeBefore = actor.getLife();
        cast(game, actor, "Llanowar Elves");
        assertEquals(actor.getLife(), lifeBefore + firstChange, "First life outcome");
        if (blocker != null) {
            SpellAbility removal = addCardToZone("Anguished Unmaking", actor, ZoneType.Hand).getFirstSpellAbility();
            removal.setActivatingPlayer(actor);
            removal.getTargets().add(blocker);
            assertTrue(ComputerUtil.playStack(removal, actor, game));
            settle(game);
            assertTrue(game.getCardState(blocker).isInZone(ZoneType.Exile));
            assertEquals(actor.getLife(), lifeBefore + firstChange - 3);
        }
        assertTrue(actor.canGainLife());
        int lifeAfterRemoval = actor.getLife();
        cast(game, actor, "Runeclaw Bear");
        assertEquals(actor.getLife(), lifeAfterRemoval + (prohibited ? 1 : 0),
                "A forbidden optional life gain must leave the action available");
        int lifeAfterSecond = actor.getLife();
        cast(game, actor, "Elvish Mystic");
        assertEquals(actor.getLife(), lifeAfterSecond);
    }

    private void cast(Game game, Player player, String name) {
        SpellAbility spell = addCardToZone(name, player, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        assertTrue(ComputerUtil.playStack(spell, player, game));
        settle(game);
        assertTrue(game.getCardState(spell.getHostCard()).isInZone(ZoneType.Battlefield));
    }

    private void settle(Game game) {
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        for (int step = 0; step < 12 && !game.getStack().isEmpty(); step++) {
            game.getStack().resolveStack();
        }
        assertTrue(game.getStack().isEmpty());
    }
}
