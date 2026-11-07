package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.ability.ApiType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Fixed mana-source preconditions; native payment, not source casting or AI strategy proof. */
public class SnowManaProjectionTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("mana" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Reflected mana"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 8; count++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private void sun(Player owner, String color) {
        Card sun = addCardToZone("Caged Sun", owner, ZoneType.Battlefield);
        sun.setChosenColors(List.of(color));
    }


    @Test
    public void snowIslandAndCagedSunCanPayForFrostwalkBastion() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card bastion = addCardToZone("Frostwalk Bastion", actor, ZoneType.Battlefield);
        bastion.setTapped(true);
        Card island = addCardToZone("Snow-Covered Island", actor, ZoneType.Battlefield);
        sun(actor, "blue");
        game.getAction().checkStateEffects(true);
        SpellAbility animate = prepareAbility(bastion, ApiType.Animate);

        assertTrue(ComputerUtil.playStack(animate, actor, game));
        assertTrue(island.isTapped());
        var payment = game.getStack().peekAbility().getPayingMana();
        assertEquals(payment.size(), 2);
        assertEquals(payment.stream().filter(mana -> mana.getSourceCard().isSnow()).count(), 1L);
    }

    private SpellAbility prepareAbility(Card source, ApiType api) {
        SpellAbility ability = source.getSpellAbilities().stream()
                .filter(candidate -> candidate.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(source.getController());
        return ability;
    }

    @Test
    public void ordinaryIslandAndCagedSunCannotPaySnowCost() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card bastion = addCardToZone("Frostwalk Bastion", actor, ZoneType.Battlefield);
        bastion.setTapped(true);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        sun(actor, "blue");
        game.getAction().checkStateEffects(true);
        SpellAbility animate = prepareAbility(bastion, ApiType.Animate);

        assertFalse(ComputerUtilMana.canPayManaCost(animate, actor, 0, false));
        assertFalse(ComputerUtil.playStack(animate, actor, game));
        assertFalse(island.isTapped());
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void overabundanceManaDoesNotInheritTheSnowLandType() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card troll = addCardToZone("Icehide Troll", actor, ZoneType.Battlefield);
        Card island = addCardToZone("Snow-Covered Island", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility pump = prepareAbility(troll, ApiType.Pump);
        int life = actor.getLife();

        assertFalse(ComputerUtilMana.canPayManaCost(pump, actor, 0, false));
        assertFalse(ComputerUtil.playStack(pump, actor, game));
        assertFalse(island.isTapped());
        assertEquals(actor.getLife(), life);
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void doublingTheManaFromOneSnowSourceCanPayTwoSnowShards() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card troll = addCardToZone("Icehide Troll", actor, ZoneType.Battlefield);
        Card island = addCardToZone("Snow-Covered Island", actor, ZoneType.Battlefield);
        addCardToZone("Mana Reflection", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility pump = prepareAbility(troll, ApiType.Pump);

        assertTrue(ComputerUtil.playStack(pump, actor, game));
        assertTrue(island.isTapped());
        var payment = game.getStack().peekAbility().getPayingMana();
        assertEquals(payment.size(), 2);
        assertTrue(payment.stream().allMatch(mana -> mana.isSnow()
                && mana.getSourceCard().getId() == island.getId()));
    }
}
