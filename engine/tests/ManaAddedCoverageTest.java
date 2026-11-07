package forge.ai;

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
public class ManaAddedCoverageTest extends AITest {
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


    private void sun(Player owner) {
        sun(owner, "green");
    }

    private void sun(Player owner, String color) {
        Card sun = addCardToZone("Caged Sun", owner, ZoneType.Battlefield);
        sun.setChosenColors(List.of(color));
    }

    @Test
    public void directForestActivationAddsOneCagedSunBonus() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        sun(actor);
        game.getAction().checkStateEffects(true);

        SpellAbility mana = forest.getManaAbilities().get(0);
        mana.setActivatingPlayer(actor);
        actor.getController().playChosenSpellAbility(mana);
        assertTrue(forest.isTapped());
        assertTrue(game.getStack().isEmpty());
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.GREEN), 2);
    }

    @Test
    public void automaticPaymentCanSpendCagedSunBonus() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        sun(actor);
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(inHand(actor, "Scavenging Ooze"), actor, game));
        assertTrue(forest.isTapped());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
    }

    @Test
    public void otherPlayersCagedSunDoesNotBoostThisForest() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        sun(game.getPlayers().get(1));
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtil.playStack(inHand(actor, "Scavenging Ooze"), actor, game));
        assertFalse(forest.isTapped());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
    }

    @Test
    public void cagedSunDoesNotBoostManaFromNonland() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card elf = addCardToZone("Llanowar Elves", actor, ZoneType.Battlefield);
        elf.setSickness(false);
        sun(actor);
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtil.playStack(inHand(actor, "Scavenging Ooze"), actor, game));
        assertFalse(elf.isTapped());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
    }

    @Test
    public void greenCagedSunDoesNotBoostMountain() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card mountain = addCardToZone("Mountain", actor, ZoneType.Battlefield);
        sun(actor);
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtil.playStack(inHand(actor, "Myr Retriever"), actor, game));
        assertFalse(mountain.isTapped());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
    }

    @Test
    public void cagedSunAddsOnceWhenOneActivationProducesTwoGreen() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card cradle = addCardToZone("Gaea's Cradle", actor, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        sun(actor);
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(inHand(actor, "Leatherback Baloth"), actor, game));
        assertTrue(cradle.isTapped());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.GREEN), 0);
    }

    @Test
    public void cagedSunAndOverabundanceEachContributeOnce() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        sun(actor);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        int life = actor.getLife();

        assertTrue(ComputerUtil.playStack(inHand(actor, "Leatherback Baloth"), actor, game));
        assertTrue(forest.isTapped());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        assertEquals(actor.getLife(), life - 1);
    }

    @Test
    public void sacrificeOnlyManaDoesNotTriggerKinnan() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card pet = addCardToZone("Blood Pet", actor, ZoneType.Battlefield);
        addCardToZone("Kinnan, Bonder Prodigy", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Myr Retriever");
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtilMana.canPayManaCost(spell, actor, 0, false));
        assertEquals(pet.getZone().getZoneType(), ZoneType.Battlefield);
        SpellAbility mana = pet.getManaAbilities().get(0);
        mana.setActivatingPlayer(actor);
        actor.getController().playChosenSpellAbility(mana);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLACK), 1);
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void cagedSunUsesBlueFromRiverBeforeAPlayedLand() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card river = addCardToZone("River of Tears", actor, ZoneType.Battlefield);
        sun(actor, "blue");
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(inHand(actor, "Thassa's Oracle"), actor, game));
        assertTrue(river.isTapped());
        var payment = game.getStack().peekAbility().getPayingMana();
        assertEquals(payment.size(), 2);
        assertTrue(payment.stream().allMatch(mana -> mana.getColor() == MagicColor.BLUE));
    }

    @Test
    public void cagedSunUsesBlackFromRiverAfterAPlayedLand() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card river = addCardToZone("River of Tears", actor, ZoneType.Battlefield);
        actor.setLandsPlayedThisTurn(1);
        sun(actor, "black");
        game.getAction().checkStateEffects(true);

        SpellAbility spell = inHand(actor, "Sign in Blood");
        spell.getTargets().add(actor);
        assertTrue(ComputerUtil.playStack(spell, actor, game));
        assertTrue(river.isTapped());
        var payment = game.getStack().peekAbility().getPayingMana();
        assertEquals(payment.size(), 2);
        assertTrue(payment.stream().allMatch(mana -> mana.getColor() == MagicColor.BLACK));
    }
}
