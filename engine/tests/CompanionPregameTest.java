package forge.ai;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native companion selection, hidden-zone types and paid special action. */
public class CompanionPregameTest extends AITest {
    private Card place(String name, Player owner, ZoneType zone) {
        Card card = Card.fromPaperCard(StaticData.instance().getCommonCards().getCard(name), owner);
        owner.getZone(zone).add(card);
        return card;
    }

    private void library(Player owner, String basic, int quantity) {
        for (int i = 0; i < quantity; i++) {
            place(basic, owner, ZoneType.Library);
        }
    }

    private Card selectedCompanion(Game game, String name) {
        Player owner = game.getPhaseHandler().getPlayerTurn();
        owner.assignCompanion(game, owner.getController());
        game.getAction().checkStaticAbilities(false);
        return owner.getCardsIn(ZoneType.Command).stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }

    private Card lurrus(Game game, int lands) {
        Player owner = game.getPhaseHandler().getPlayerTurn();
        for (Player player : game.getPlayers()) {
            player.setLife(40, null);
        }
        Card commander = place("Elas il-Kor, Sadistic Pilgrim", owner, ZoneType.Command);
        owner.addCommander(commander);
        library(owner, "Plains", 99);
        place("Lurrus of the Dream-Den", owner, ZoneType.Sideboard);
        for (int i = 0; i < lands; i++) {
            place("Plains", owner, ZoneType.Battlefield);
        }
        game.getPhaseHandler().setPriority(owner);
        return selectedCompanion(game, "Lurrus of the Dream-Den");
    }

    private SpellAbility action(Card companion) {
        SpellAbility ability = companion.getSpellAbilities().stream()
                .filter(sa -> sa.getApi() == ApiType.ChangeZone && "Command".equals(sa.getParam("ActivationZone")))
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(companion.getOwner());
        return ability;
    }

    @Test
    public void umoriIncludesGristCreatureTypeInTheLibrary() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card commander = place("Meren of Clan Nel Toth", owner, ZoneType.Command);
        owner.addCommander(commander);
        library(owner, "Forest", 97);
        place("Grist, the Hunger Tide", owner, ZoneType.Library);
        place("Llanowar Elves", owner, ZoneType.Library);
        place("Umori, the Collector", owner, ZoneType.Sideboard);
        Card chosen = selectedCompanion(game, "Umori, the Collector");
        assertTrue(chosen != null);
        assertEquals(owner.getCardsIn(ZoneType.Sideboard).size(), 0);
    }

    @Test
    public void umoriRejectsLibraryCardsWithNoSharedType() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        library(owner, "Forest", 97);
        place("Grist, the Hunger Tide", owner, ZoneType.Library);
        place("Cultivate", owner, ZoneType.Library);
        place("Umori, the Collector", owner, ZoneType.Sideboard);
        assertTrue(selectedCompanion(game, "Umori, the Collector") == null);
        assertEquals(owner.getCardsIn(ZoneType.Sideboard).size(), 1);
    }

    @Test
    public void companionSpecialActionPaysThreeAndMovesDirectlyToHand() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card chosen = lurrus(game, 3);
        assertTrue(chosen != null);
        SpellAbility ability = action(chosen);
        assertTrue(ability.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(ability, owner, false));
        assertTrue(ComputerUtil.playNoStack(owner, ability, game, false));
        game.getAction().checkStaticAbilities(false);
        assertEquals(ability.getPayingMana().size(), 3);
        assertEquals(owner.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count(), 3L);
        assertEquals(game.getStack().size(), 0);
        Card inHand = owner.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Lurrus of the Dream-Den")).findFirst().orElseThrow();
        assertFalse(inHand.getSpellAbilities().stream().anyMatch(sa -> sa.getApi() == ApiType.ChangeZone && "Command".equals(sa.getParam("ActivationZone"))));
    }

    @Test
    public void twoManaCannotPayTheCompanionSpecialAction() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card chosen = lurrus(game, 2);
        assertTrue(chosen != null);
        SpellAbility ability = action(chosen);
        assertFalse(ComputerUtilCost.canPayCost(ability, owner, false));
        assertFalse(ComputerUtil.playNoStack(owner, ability, game, false));
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 0);
        assertEquals(owner.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count(), 0L);
        assertEquals(game.getStack().size(), 0);
    }

    @Test
    public void companionTimingAdmissionRejectsCombat() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card chosen = lurrus(game, 3);
        assertTrue(chosen != null);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, owner);
        game.getPhaseHandler().setPriority(owner);
        assertFalse(action(chosen).canPlay());
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 0);
        assertEquals(owner.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count(), 0L);
    }
}
