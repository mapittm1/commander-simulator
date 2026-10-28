package forge.ai;

import java.util.List;
import java.util.stream.Collectors;

import forge.StaticData;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.cost.Cost;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.player.Player;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.OptionalCostValue;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Local regressions for the pinned Wicker Picker engine patch (CR 702.33h). */
public class StickerKickerRegressionTest extends AITest {
    private static final class Choices extends PlayerControllerAi {
        private final boolean kick;
        private final boolean sticker;

        Choices(Game game, Player player, boolean kick, boolean sticker) {
            super(game, player, player.getLobbyPlayer());
            this.kick = kick;
            this.sticker = sticker;
        }

        @Override
        public boolean addKeywordCost(SpellAbility sa, Cost cost, KeywordInterface keyword, String prompt) {
            SpellAbility proposed = sa.copy();
            proposed.getPayCosts().add(cost);
            return kick && ComputerUtilCost.canPayCost(proposed, player, false);
        }

        @Override
        public Sticker chooseSticker(List<Sticker> options, Card target, SpellAbility sa, boolean optional) {
            return sticker ? options.stream().filter(s -> s.getKind() == StickerKind.ART).findFirst().orElse(null) : null;
        }
    }

    private Player prepare(Game game, int pickers, int forests, boolean kick, boolean sticker, boolean sheet) {
        Player player = game.getPhaseHandler().getPlayerTurn();
        player.dangerouslySetController(new Choices(game, player, kick, sticker));
        for (Player participant : game.getPlayers()) {
            participant.setLife(40, null);
            fillLibrary(participant, 3);
        }
        addCards("Wicker Picker", pickers, player);
        addCards("Forest", forests, player);
        if (sheet) {
            Card card = Card.fromPaperCard(StaticData.instance().getVariantCards().getCard("Eldrazi Guacamole Tightrope"), player);
            player.getZone(ZoneType.StickerSheets).add(card);
        }
        game.getAction().checkStateEffects(true);
        return player;
    }

    private SpellAbility spell(String name, Player player) {
        SpellAbility ability = addCardToZone(name, player, ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(player);
        return ability;
    }

    private Card cast(Game game, Player player, SpellAbility proposal) {
        assertTrue(ComputerUtil.playStack(proposal, player, game), "native casting/payment must succeed");
        return game.getStack().iterator().next().getSpellAbility().getHostCard();
    }

    @Test
    public void testStickerExistsAndIsPaidBeforeSpellResolution() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 3, true, true, true);
        Card card = cast(game, player, spell("Grizzly Bears", player));
        assertTrue(card.isInZone(ZoneType.Stack));
        assertEquals(card.getStickers().size(), 1);
        assertEquals(player.getCounters(CounterEnumType.TICKET), 1);
        assertEquals(card.getCastSA().getPayingMana().size(), 3);
        assertTrue(card.getCastSA().isKicked());
        game.getStack().resolveStack();
        assertEquals(game.getCardState(card).getZone().getZoneType(), ZoneType.Battlefield);
        assertEquals(game.getCardState(card).getStickers().size(), 1);
    }

    @Test
    public void testDecliningKickerPaysOnlyTheBaseSpell() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 3, false, true, true);
        Card card = cast(game, player, spell("Grizzly Bears", player));
        assertFalse(card.getCastSA().isKicked());
        assertEquals(card.getStickers().size(), 0);
        assertEquals(player.getCounters(CounterEnumType.TICKET), 0);
        assertEquals(card.getCastSA().getPayingMana().size(), 2);
    }

    @Test
    public void testDecliningStickerStillPaysKickerAndGainsTicket() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 3, true, false, true);
        Card card = cast(game, player, spell("Grizzly Bears", player));
        assertEquals(card.getStickers().size(), 0);
        assertEquals(player.getCounters(CounterEnumType.TICKET), 1);
        assertEquals(card.getCastSA().getPayingMana().size(), 3);
        assertTrue(card.getCastSA().isKicked());
    }

    @Test
    public void testNoSheetStillAllowsTicketOnlyKicker() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 3, true, true, false);
        Card card = cast(game, player, spell("Grizzly Bears", player));
        assertEquals(card.getStickers().size(), 0);
        assertEquals(player.getCounters(CounterEnumType.TICKET), 1);
    }

    @Test
    public void testUnaffordableKickerLeavesBaseSpellCastable() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 2, true, true, true);
        Card card = cast(game, player, spell("Grizzly Bears", player));
        assertFalse(card.getCastSA().isKicked());
        assertEquals(card.getStickers().size(), 0);
        assertEquals(player.getCounters(CounterEnumType.TICKET), 0);
        assertEquals(card.getCastSA().getPayingMana().size(), 2);
    }

    @Test
    public void testTwoPickersHaveIndependentCostsAndStickerChoices() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 2, 4, true, true, true);
        Card card = cast(game, player, spell("Grizzly Bears", player));
        assertEquals(player.getCounters(CounterEnumType.TICKET), 2);
        assertEquals(card.getCastSA().getPayingMana().size(), 4);
        assertEquals(card.getStickers().size(), 2);
        assertEquals(card.getCastSA().getOptionalKeywordAmount(Keyword.STICKER_KICKER), 2);
        assertEquals(card.getKickerMagnitude(null), 2);
    }

    @Test
    public void testTwoPickersWithOnlyOneExtraManaCannotOvercommitCosts() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 2, 3, true, true, true);
        Card card = cast(game, player, spell("Grizzly Bears", player));
        assertEquals(player.getCounters(CounterEnumType.TICKET), 1);
        assertEquals(card.getStickers().size(), 1);
        assertEquals(card.getCastSA().getPayingMana().size(), 3);
    }

    @Test
    public void testStickerKickerDoesNotSatisfyKavuTitansLinkedPrintedKicker() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 3, true, true, true);
        Card card = cast(game, player, spell("Kavu Titan", player));
        game.getStack().resolveStack();
        card = game.getCardState(card);
        assertEquals(card.getNetPower(), 2);
        assertEquals(card.getCounters(CounterEnumType.P1P1), 0);
    }

    @Test
    public void testPrintedKickerAndStickerKickerCanBothBePaid() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 6, true, true, true);
        SpellAbility proposal = spell("Kavu Titan", player);
        List<OptionalCostValue> costs = GameActionUtil.getOptionalCostValues(proposal).stream()
                .filter(v -> v.getType() == OptionalCost.Kicker1).collect(Collectors.toList());
        assertEquals(costs.size(), 1);
        proposal = GameActionUtil.addOptionalCosts(proposal, costs);
        Card card = cast(game, player, proposal);
        assertEquals(card.getCastSA().getPayingMana().size(), 6);
        assertEquals(card.getKickerMagnitude(null), 2);
        game.getStack().resolveStack();
        assertEquals(game.getCardState(card).getNetPower(), 5);
    }

    @Test
    public void testNoncreatureAndOpponentsSpellDoNotReceiveStickerKicker() {
        Game game = initAndCreateGame();
        Player player = prepare(game, 1, 3, true, true, true);
        Card artifact = cast(game, player, spell("Bonesplitter", player));
        assertFalse(artifact.getCastSA().isKicked());
        assertEquals(player.getCounters(CounterEnumType.TICKET), 0);
        game.getStack().resolveStack();
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 3, opponent);
        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, opponent);
        opponent.dangerouslySetController(new Choices(game, opponent, true, true));
        Card creature = cast(game, opponent, spell("Grizzly Bears", opponent));
        assertFalse(creature.getCastSA().isKicked());
        assertEquals(opponent.getCounters(CounterEnumType.TICKET), 0);
    }
}
