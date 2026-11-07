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
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Fixed mana-source preconditions; native payment, not source casting or AI strategy proof. */
public class OverlappingCastCostsProbeTest extends AITest {
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
    public void altarReapCannotSpendBloodPetTwice() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card pet = addCardToZone("Blood Pet", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Altar's Reap");
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Hand);
        assertEquals(pet.getZone().getZoneType(), ZoneType.Battlefield);
        assertFalse(swamp.isTapped());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
    }

    @Test
    public void altarReapCanUseSeparateCreatureAfterBloodPetPaysMana() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card pet = addCardToZone("Blood Pet", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Altar's Reap");
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(spell, actor, game));
        var pending = game.getStack().peekAbility();
        assertEquals(pending.getPayingMana().size(), 2);
        assertTrue(actor.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == pet.getId()));
        assertTrue(actor.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == bear.getId()));
    }

    @Test
    public void altarReapUsesOtherManaWhenTheOnlyCreatureMustBeSacrificed() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card forest = addCardToZone("Forest", actor, ZoneType.Battlefield);
        Card pet = addCardToZone("Blood Pet", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Altar's Reap");
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(spell, actor, game));
        var pending = game.getStack().peekAbility();
        assertEquals(pending.getPayingMana().size(), 2);
        assertTrue(swamp.isTapped());
        assertTrue(forest.isTapped());
        assertTrue(pending.getPayingMana().stream().noneMatch(mana -> mana.getSourceCard().getId() == pet.getId()));
        assertTrue(actor.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == pet.getId()));
    }

    @Test
    public void altarReapCannotSacrificeTheSameCreatureToAshnodsAltar() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        addCardToZone("Ashnod's Altar", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Altar's Reap");
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Hand);
        assertEquals(bear.getZone().getZoneType(), ZoneType.Battlefield);
        assertFalse(swamp.isTapped());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
    }

    @Test
    public void altarReapCanPayAshnodAndTheSpellWithDifferentCreatures() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card altar = addCardToZone("Ashnod's Altar", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card lion = addCardToZone("Savannah Lions", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Altar's Reap");
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(spell, actor, game));
        var paid = game.getStack().peekAbility().getPayingMana();
        assertEquals(paid.size(), 2);
        assertTrue(paid.stream().anyMatch(mana -> mana.getSourceCard().getId() == altar.getId()));
        assertTrue(actor.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == bear.getId()));
        assertTrue(actor.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == lion.getId()));
    }

    @Test
    public void heraldCannotAssignOneCreatureToTwoColorCosts() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card herald = addCardToZone("Demon's Herald", actor, ZoneType.Battlefield);
        herald.setSickness(false);
        Card flexible = addCardToZone("Gwendlyn Di Corci", actor, ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        addCardToZone("Swamp", actor, ZoneType.Battlefield);
        addCardToZone("Swamp", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = herald.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);

        assertFalse(ComputerUtil.playStack(ability, actor, game));
        assertFalse(swamp.isTapped());
        assertFalse(herald.isTapped());
        assertEquals(flexible.getZone().getZoneType(), ZoneType.Battlefield);
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void heraldMatchesThreeDistinctCreaturesAcrossColors() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card herald = addCardToZone("Demon's Herald", actor, ZoneType.Battlefield);
        herald.setSickness(false);
        Card flexible = addCardToZone("Gwendlyn Di Corci", actor, ZoneType.Battlefield);
        Card red = addCardToZone("Goblin Guide", actor, ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        SpellAbility ability = herald.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);

        assertTrue(ComputerUtil.playStack(ability, actor, game));
        var sacrificed = actor.getCardsIn(ZoneType.Graveyard).stream().map(Card::getId).toList();
        assertTrue(sacrificed.containsAll(List.of(herald.getId(), flexible.getId(), red.getId())));
        assertEquals(sacrificed.size(), 3);
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
    }

    @Test
    public void signetCannotConsumeTheOnlyCreatureNeededForAltarReap() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card signet = addCardToZone("Orzhov Signet", actor, ZoneType.Battlefield);
        Card pet = addCardToZone("Blood Pet", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Altar's Reap");
        game.getAction().checkStateEffects(true);

        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertFalse(signet.isTapped());
        assertEquals(pet.getZone().getZoneType(), ZoneType.Battlefield);
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Hand);
    }

    @Test
    public void signetUsesOneCreatureForManaAndLeavesAnotherForAltarReap() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card signet = addCardToZone("Orzhov Signet", actor, ZoneType.Battlefield);
        Card pet = addCardToZone("Blood Pet", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Altar's Reap");
        game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(spell, actor, game));
        assertTrue(signet.isTapped());
        var paid = game.getStack().peekAbility().getPayingMana();
        assertEquals(paid.size(), 2);
        assertTrue(paid.stream().allMatch(mana -> mana.getSourceCard().getId() == signet.getId()));
        var sacrificed = actor.getCardsIn(ZoneType.Graveyard).stream().map(Card::getId).toList();
        assertTrue(sacrificed.containsAll(List.of(pet.getId(), bear.getId())));
    }
}
