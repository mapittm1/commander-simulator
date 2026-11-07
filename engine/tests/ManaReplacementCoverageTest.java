package forge.ai;

import forge.card.mana.ManaAtom;
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
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Fixed mana-source preconditions; native payment, not source casting or AI strategy proof. */
public class ManaReplacementCoverageTest extends AITest {
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

    private SpellAbility inHand(Player actor, String name) {
        SpellAbility spell = addCardToZone(name, actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        return spell;
    }



    @Test
    public void cagedSunDoesNotCountAColorReplacedByRitualOfSubdual() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        Card sun = addCardToZone("Caged Sun", actor, ZoneType.Battlefield);
        sun.setChosenColors(List.of("blue"));
        addCardToZone("Ritual of Subdual", game.getPlayers().get(1), ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility spell = inHand(actor, "Myr Retriever");

        boolean affordable = ComputerUtilMana.canPayManaCost(spell, actor, 0, false);
        boolean paid = ComputerUtil.playStack(spell, actor, game);
        assertFalse(paid);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty(),
                "The card must not be left in the stack zone after a refused cast");
        assertFalse(affordable);
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Hand);
        assertFalse(island.isTapped());
    }

    @Test
    public void ritualOfSubdualCanSupplyATypedColorlessPayment() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Ritual of Subdual", opponent, ZoneType.Battlefield);
        Card target = addCardToZone("Grizzly Bears", opponent, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility spell = inHand(actor, "Spatial Contortion");
        spell.getTargets().add(target);

        assertTrue(ComputerUtil.playStack(spell, actor, game));
        var payment = game.getStack().peekAbility().getPayingMana();
        assertEquals(payment.size(), 2);
        assertTrue(payment.stream().allMatch(mana -> mana.getColor() == (byte) ManaAtom.COLORLESS));
    }
}
