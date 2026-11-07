package forge.ai;

import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Fixed mana-source preconditions; native payment, not source casting or AI strategy proof. */
public class ChosenManaReplacementProbeTest extends AITest {
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




    private void beginChosenBlueTurn(Game game) {
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Hall of Gemstone", opponent, ZoneType.Battlefield);
        for (int count = 0; count < 4; count++) {
            inHand(actor, "Opt");
        }
        game.getAction().checkStateEffects(true);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, actor, 2);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(actor), false);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertFalse(game.getStack().isEmpty());
        game.getStack().resolveStack();
        assertTrue(game.getStack().isEmpty());
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, actor, 2);

    }

    @Test
    public void hallOfGemstoneCanPayBlueWithTheColorChosenDuringUpkeep() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        beginChosenBlueTurn(game);

        SpellAbility spell = inHand(actor, "Opt");
        assertTrue(ComputerUtil.playStack(spell, actor, game));
        assertTrue(forest.isTapped());
        var payment = game.getStack().peekAbility().getPayingMana();
        assertEquals(payment.size(), 1);
        assertEquals(payment.get(0).getColor(), MagicColor.BLUE);
    }

    @Test
    public void directForestActivationUsesTheChosenBlueMana() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        beginChosenBlueTurn(game);
        SpellAbility mana = forest.getManaAbilities().get(0);
        mana.setActivatingPlayer(actor);
        actor.getController().playChosenSpellAbility(mana);

        assertTrue(forest.isTapped());
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 1);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.GREEN), 0);
    }

    @Test
    public void chosenBlueDoesNotTurnWastesBlue() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card wastes = addCardToZone("Wastes", actor, ZoneType.Battlefield);
        beginChosenBlueTurn(game);

        assertFalse(ComputerUtil.playStack(inHand(actor, "Opt"), actor, game));
        assertFalse(wastes.isTapped());
        assertTrue(game.getStack().isEmpty());
    }
}
