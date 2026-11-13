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
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CommanderConditionalManaTest extends AITest {
    @Test
    public void luckCounterLetsGemstoneCavernsPayForEsperSentinel() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card caverns = addCardToZone("Gemstone Caverns", actor, ZoneType.Battlefield);
        caverns.setCounters(CounterEnumType.LUCK, 1);
        assertTrue(ComputerUtilMana.canPayManaCost(spell("Esper Sentinel", actor), actor, 0, false));
        assertTrue(caverns.isUntapped());
        cast(game, actor, "Esper Sentinel");
        assertTrue(caverns.isTapped());
    }

    @Test
    public void gemstoneWithoutLuckCannotPayWhite() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card caverns = addCardToZone("Gemstone Caverns", actor, ZoneType.Battlefield);
        assertFalse(ComputerUtilMana.canPayManaCost(spell("Esper Sentinel", actor), actor, 0, false));
        assertTrue(caverns.isUntapped());
    }

    @Test
    public void cavernOfSoulsCanPayRequiredColorlessMana() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card cavern = addCardToZone("Cavern of Souls", actor, ZoneType.Battlefield);
        cavern.setChosenType("Eldrazi");
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        cast(game, actor, "Glaring Fleshraker");
        assertTrue(cavern.isTapped());
    }

    @Test
    public void halflingProtectsLegendarySpellWhenPayingGenericMana() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card halfling = addCardToZone("Delighted Halfling", actor, ZoneType.Battlefield);
        halfling.setSickness(false);
        addCardToZone("Mountain", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        SpellAbility paid = cast(game, actor, "Squee, Goblin Nabob");
        assertTrue(paid.getPayingMana().stream()
                .anyMatch(mana -> mana.getSourceCard().equals(halfling) && mana.addsNoCounterMagic(paid)));
    }

    @Test
    public void halflingDoesNotProtectNonlegendarySpell() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card halfling = addCardToZone("Delighted Halfling", actor, ZoneType.Battlefield);
        halfling.setSickness(false);
        addCardToZone("Mountain", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        SpellAbility paid = cast(game, actor, "Glaring Fleshraker");
        assertFalse(paid.getPayingMana().stream().anyMatch(mana -> mana.addsNoCounterMagic(paid)));
    }

    private SpellAbility spell(String name, Player actor) {
        Card card = addCardToZone(name, actor, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.setActivatingPlayer(actor);
        return ability;
    }

    private SpellAbility cast(Game game, Player actor, String name) {
        SpellAbility ability = spell(name, actor);
        game.getAction().checkStateEffects(true);
        assertTrue(ComputerUtil.playStack(ability, actor, game));
        SpellAbility paid = game.getStack().peekAbility();
        assertTrue(paid.getHostCard().getName().equals(name));
        return paid;
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Conditional mana"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 2);
        return game;
    }
}
