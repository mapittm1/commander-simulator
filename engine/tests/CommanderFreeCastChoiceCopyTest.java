package forge.ai;

import forge.ai.simulation.TextChangeChoices;
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
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Cost-waiver metadata and source isolation, independent of strategic card selection. */
public class CommanderFreeCastChoiceCopyTest extends AITest {
    @Test public void abilityCopyRetainsTheFreeCastMarker() {
        SpellAbility original = original();
        SpellAbility free = original.copyWithNoManaCost();
        assertTrue(free.hasParam("WithoutManaCost"));
        assertTrue(free.getPayCosts().getTotalMana().isZero());
        SpellAbility copied = free.copy();
        assertTrue(copied.getPayCosts().getTotalMana().isZero());
        assertTrue(copied.hasParam("WithoutManaCost"), "A copied alternative cast must retain its mana waiver marker");
    }
    @Test public void textChoiceVariantRetainsTheFreeCastMarker() {
        SpellAbility free = original().copyWithNoManaCost();
        List<SpellAbility> variants = TextChangeChoices.variants(free);
        assertFalse(variants.isEmpty());
        for (SpellAbility variant : variants) {
            assertTrue(variant.getPayCosts().getTotalMana().isZero());
            assertTrue(variant.hasParam("WithoutManaCost"), "Planning a choice must not lose the cast's mana waiver");
        }
    }
    @Test public void grantingAFreeCastDoesNotModifyThePrintedAbility() {
        SpellAbility original = original();
        assertFalse(original.hasParam("WithoutManaCost"));
        assertEquals(original.getPayCosts().getTotalMana().getCMC(), 1);
        SpellAbility free = original.copyWithNoManaCost();
        free.copy();
        TextChangeChoices.variants(free);
        assertFalse(original.hasParam("WithoutManaCost"));
        assertEquals(original.getPayCosts().getTotalMana().getCMC(), 1);
        assertTrue(original.getHostCard().isInZone(ZoneType.Hand));
    }
    private SpellAbility original() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("waiver-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Alternative cast choice copy"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Card card = addCardToZone("Trait Doctoring", actor, ZoneType.Hand);
        SpellAbility action = card.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        return action;
    }
}
