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
import forge.game.combat.CombatUtil;
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

public class StarTrekBattlefrontTest extends AITest {
    @Test
    public void ownTheDayCountsAnEliminatedOpponentAndIgnoresAPlaneswalker() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Battlefront"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player eliminated = game.getPlayers().get(1);
        Player other = game.getPlayers().get(2);
        Player planeswalkerOwner = game.getPlayers().get(3);
        Card walker = addCardToZone("Jace, the Mind Sculptor", planeswalkerOwner, ZoneType.Battlefield);
        walker.setCounters(CounterEnumType.LOYALTY, 3);
        Card first = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        Card third = addCardToZone("Silvercoat Lion", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 1);
        Combat combat = new Combat(actor);
        combat.addAttacker(first, eliminated);
        combat.addAttacker(second, other);
        combat.addAttacker(third, walker);
        game.getPhaseHandler().setCombat(combat);
        for (Card attacker : List.of(first, second, third)) {
            attacker.setTapped(true);
            CombatUtil.checkDeclaredAttacker(game, attacker, combat, true);
        }
        eliminated.setLife(0, null);
        game.getAction().checkStateEffects(true);
        assertTrue(eliminated.hasLost());
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, actor, 1);

        for (int index = 0; index < 4; index++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        Card card = addCardToZone("Own the Day", actor, ZoneType.Hand);
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 4);
        game.getStack().resolveStack();
        for (Card attacker : List.of(first, second, third)) {
            assertFalse(attacker.isTapped());
        }
    }
}
