package forge.ai;

import forge.card.CardStateName;
import forge.card.MagicColor;
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
public class MixedReflectedManaTest extends AITest {
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

    private void biasHand(Player actor, String spellName) {
        for (int count = 0; count < 4; count++) {
            addCardToZone(spellName, actor, ZoneType.Hand);
        }
    }

    @Test
    public void overabundanceChoosesBlueForDoubleBlueSpell() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card land = addCardToZone("Izzet Boilerworks", actor, ZoneType.Battlefield);
        Card enchantment = addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        biasHand(actor, "Lightning Bolt");
        game.getAction().checkStateEffects(true);
        int life = actor.getLife();
        assertTrue(ComputerUtil.playStack(inHand(actor, "Thassa's Oracle"), actor, game),
                "The land and trigger can supply two blue mana");
        assertTrue(land.isTapped());
        assertEquals(actor.getLife(), life - 1);
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        assertEquals(game.getStack().peekAbility().getPayingMana().stream()
                .filter(mana -> mana.getSourceCard().getId() == enchantment.getId()).count(), 1L);
    }

    @Test
    public void manualActivationAfterPaymentUsesOrdinaryManaChoice() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card land = addCardToZone("Izzet Boilerworks", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        biasHand(actor, "Lightning Bolt");
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(inHand(actor, "Thassa's Oracle"), actor, game));
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.RED), 1);
        land.setTapped(false);
        SpellAbility mana = land.getManaAbilities().get(0);
        mana.setActivatingPlayer(actor);
        actor.getController().playChosenSpellAbility(mana);
        assertTrue(land.isTapped());
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 1);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.RED), 3);
    }

    @Test
    public void impossibleDoubleBonusLeavesTheSpellAndManaSourceUntouched() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card land = addCardToZone("Izzet Boilerworks", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Crackling Drake");
        int life = actor.getLife();
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Hand);
        assertFalse(land.isTapped());
        assertEquals(actor.getLife(), life);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 0);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.RED), 0);
    }

    @Test
    public void eachSasayaBonusCanSupplyADifferentMissingColor() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card sasaya = addCardToZone("Sasaya, Orochi Ascendant", actor, ZoneType.Battlefield);
        assertTrue(sasaya.setState(CardStateName.Flipped, true));
        Card land = addCardToZone("Izzet Boilerworks", actor, ZoneType.Battlefield);
        addCardToZone("Izzet Boilerworks", actor, ZoneType.Battlefield).setTapped(true);
        addCardToZone("Izzet Boilerworks", actor, ZoneType.Battlefield).setTapped(true);
        biasHand(actor, "Lightning Bolt");
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(inHand(actor, "Crackling Drake"), actor, game));
        assertTrue(land.isTapped());
        var payment = game.getStack().peekAbility().getPayingMana();
        assertEquals(payment.size(), 4);
        assertEquals(payment.stream().filter(mana -> mana.getColor() == MagicColor.BLUE).count(), 2L);
        assertEquals(payment.stream().filter(mana -> mana.getColor() == MagicColor.RED).count(), 2L);
        assertEquals(payment.stream().filter(mana -> mana.getSourceCard().getId() == sasaya.getId()).count(), 2L);
    }
}
