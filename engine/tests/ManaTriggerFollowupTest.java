package forge.ai;

import com.google.common.collect.HashMultiset;
import forge.card.CardStateName;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
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

public class ManaTriggerFollowupTest extends AITest {
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

    private boolean cast(Game game, String name, Card target) {
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone(name, actor, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        if (target != null) action.getTargets().add(target);
        boolean paid = ComputerUtil.playStack(action, actor, game);
        if (!paid) {
            assertTrue(game.getStack().isEmpty());
            assertEquals(spell.getZone().getZoneType(), ZoneType.Hand);
        }
        return paid;
    }
    private void activateMana(Card source) {
        Player actor = source.getController();
        SpellAbility action = source.getManaAbilities().get(0);
        action.setActivatingPlayer(actor);
        assertTrue(action.isManaAbility());
        assertTrue(action.canPlay());
        actor.getController().playChosenSpellAbility(action);
        assertTrue(source.isTapped());
        assertTrue(actor.getGame().getStack().isEmpty());
    }

    private Card sasaya(Game game) {
        Player actor = game.getPlayers().get(0);
        Card sasaya = addCardToZone("Sasaya, Orochi Ascendant", actor, ZoneType.Battlefield);
        assertTrue(sasaya.setState(CardStateName.Flipped, true));
        assertEquals(sasaya.getName(), "Sasaya's Essence");
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield).setTapped(true);
        addCardToZone("Forest", actor, ZoneType.Battlefield).setTapped(true);
        addCardToZone("Mountain", actor, ZoneType.Battlefield).setTapped(true);
        addCardToZone("Forest", game.getPlayers().get(1), ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        return forest;
    }

    @Test
    public void sasayaCountsOtherSameNameLandsControlledByPlayer() {
        Game game = pod();
        Card forest = sasaya(game);
        activateMana(forest);
        assertEquals(forest.getController().getManaPool().getAmountOfColor(MagicColor.GREEN), 3);
        assertTrue(cast(game, "Leatherback Baloth", null));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
    }

    @Test
    public void sasayaCanPayThreeGreenFromOneUntappedForest() {
        Game game = pod();
        Card forest = sasaya(game);
        assertTrue(cast(game, "Leatherback Baloth", null));
        assertTrue(forest.isTapped());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
    }

    @Test
    public void snowfallUpkeepManaIsExcludedFromOrdinarySpellPreview() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Snowfall", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility spell = addCardToZone("Myr Retriever", actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertFalse(ComputerUtilMana.canPayManaCost(spell, actor, 0, false));
        assertFalse(island.isTapped());
    }

    private void resolveSnowfallUpkeep(String land, int previousAge) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone(land, actor, ZoneType.Battlefield);
        Card snowfall = addCardToZone("Snowfall", actor, ZoneType.Battlefield);
        var counters = HashMultiset.create(snowfall.getCounters());
        counters.add(CounterEnumType.AGE, previousAge);
        snowfall.setCounters(counters);
        game.getAction().checkStateEffects(true);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, actor, 2);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(actor), false);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertFalse(game.getStack().isEmpty());
        game.getStack().resolveStack();
        assertTrue(island.isTapped());
        assertEquals(snowfall.getZone().getZoneType(), ZoneType.Battlefield);
        assertEquals(snowfall.getCounters(CounterEnumType.AGE), previousAge + 1);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 0);
    }

    @Test
    public void snowfallOrdinaryIslandPaysTwoUpkeepMana() {
        resolveSnowfallUpkeep("Island", 1);
    }

    @Test
    public void snowfallSnowIslandPaysThreeUpkeepMana() {
        resolveSnowfallUpkeep("Snow-Covered Island", 2);
    }
}
