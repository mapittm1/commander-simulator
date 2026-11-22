package forge.ai;

import forge.ai.simulation.MultiTargetSelector;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Distinguish actual text state when pruning otherwise similar legal targets. */
public class CommanderTextTargetIdentityTest extends AITest {
    @Test public void differentlyColoredTextTargetsRemainDistinct() { verify(false, true); }
    @Test public void differentlyTypedTextTargetsRemainDistinct() { verify(true, true); }
    @Test public void identicalUnmodifiedTargetsCanStillBePruned() { verify(false, false); }
    private void verify(boolean type, boolean different) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("target-identity-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Text target identity"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Card first = addCardToZone(type ? "Crusading Knight" : "Honor of the Pure", actor, ZoneType.Battlefield);
        Card second = addCardToZone(type ? "Crusading Knight" : "Honor of the Pure", actor, ZoneType.Battlefield);
        if (different) {
            if (type) first.addChangedTextTypeWord("Swamp", "Island", game.getNextTimestamp(), 0L);
            else first.addChangedTextColorWord("White", "Blue", game.getNextTimestamp(), 0L);
            assertFalse(first.getChangedTextColorWords().equals(second.getChangedTextColorWords())
                    && first.getChangedTextTypeWords().equals(second.getChangedTextTypeWords()));
        }
        SpellAbility action = addCardToZone("Mind Bend", actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        MultiTargetSelector selector = new MultiTargetSelector(action, null);
        Set<Integer> ids = new HashSet<>();
        while (selector.selectNextTargets()) for (Card card : action.getTargets().getTargetCards()) ids.add(card.getId());
        assertTrue(ids.contains(first.getId()));
        if (different) assertTrue(ids.contains(second.getId()), "Different effective text cannot be pruned as an identical target");
        else assertFalse(ids.contains(second.getId()), "Retain the bounded identical-target optimization control");
    }
}
