package forge.ai;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import forge.StaticData;
import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.card.CardFactory;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/** Applied effects, physical sticker identity and availability must survive snapshots. */
public class StickerGameCopyTest extends AITest {
    private Card sheet(Player player) {
        Card sheet = Card.fromPaperCard(StaticData.instance().getVariantCards().getCard("Eldrazi Guacamole Tightrope"), player);
        player.getZone(ZoneType.StickerSheets).add(sheet);
        return sheet;
    }

    private AppliedSticker apply(Game game, Card sheet, Card target, StickerKind kind) {
        Sticker selected = StickerSheet.getStickers(sheet).stream()
                .filter(sticker -> sticker.getKind() == kind).findFirst().orElseThrow();
        AppliedSticker applied = new AppliedSticker(selected, game.getNextTimestamp(), 0);
        target.addSticker(applied);
        return applied;
    }

    private Player player(Game game, int id) {
        return game.getPlayers().stream().filter(candidate -> candidate.getId() == id).findFirst().orElseThrow();
    }

    private Card card(Game game, int id, ZoneType zone) {
        return game.getCardsIn(zone).stream().filter(candidate -> candidate.getId() == id).findFirst().orElseThrow();
    }

    @DataProvider(name = "publicStickerStates")
    public Object[][] publicStickerStates() {
        return new Object[][] {
            {ZoneType.Battlefield, StickerKind.NAME}, {ZoneType.Battlefield, StickerKind.ART},
            {ZoneType.Battlefield, StickerKind.ABILITY}, {ZoneType.Battlefield, StickerKind.PT},
            {ZoneType.Graveyard, StickerKind.NAME}, {ZoneType.Graveyard, StickerKind.ART},
            {ZoneType.Graveyard, StickerKind.ABILITY}, {ZoneType.Graveyard, StickerKind.PT},
            {ZoneType.Exile, StickerKind.NAME}, {ZoneType.Exile, StickerKind.ART},
            {ZoneType.Exile, StickerKind.ABILITY}, {ZoneType.Exile, StickerKind.PT}
        };
    }

    @DataProvider(name = "stickerKinds")
    public Object[][] stickerKinds() {
        return new Object[][] {{StickerKind.NAME}, {StickerKind.ART}, {StickerKind.ABILITY}, {StickerKind.PT}};
    }

    @Test(dataProvider = "publicStickerStates")
    public void snapshotRetainsRecordsEffectsAndMappedSheet(ZoneType zone, StickerKind kind) {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card source = sheet(owner);
        Card original = addCardToZone("Grizzly Bears", owner, zone);
        AppliedSticker applied = apply(game, source, original, kind);
        Game snapshot = new GameCopier(game).makeCopy();
        Card copied = card(snapshot, original.getId(), zone);
        assertNotSame(copied, original);
        assertEquals(copied.getStickers().size(), 1);
        AppliedSticker copiedSticker = copied.getStickers().get(0);
        assertNotSame(copiedSticker, applied);
        assertEquals(copiedSticker.getTimestamp(), applied.getTimestamp());
        assertEquals(copiedSticker.getNamePosition(), applied.getNamePosition());
        assertEquals(copiedSticker.getSticker().getSlot(), applied.getSticker().getSlot());
        assertSame(copiedSticker.getSticker().getSheet(), card(snapshot, source.getId(), ZoneType.StickerSheets));
        assertEquals(copied.getName(), original.getName());
        assertEquals(copied.getNetPower(), original.getNetPower());
        assertEquals(copied.getNetToughness(), original.getNetToughness());
        assertEquals(copied.hasKeyword("Haste"), original.hasKeyword("Haste"));
        assertEquals(StickerSheet.getAvailableStickers(copied.getOwner(), 100).size(), 9);
    }

    @Test(dataProvider = "stickerKinds")
    public void lastKnownInformationRetainsRecordsWithoutReapplyingEffects(StickerKind kind) {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card original = addCard("Grizzly Bears", owner);
        apply(game, sheet(owner), original, kind);
        Multiset<CounterType> counters = HashMultiset.create();
        counters.add(CounterEnumType.P1P1, 2);
        original.setCounters(counters);
        Card lki = CardCopyService.getLKICopy(original);
        assertEquals(lki.getStickers().size(), 1);
        assertNotSame(lki.getStickers(), original.getStickers());
        assertEquals(lki.getName(), original.getName());
        assertEquals(lki.getNetPower(), original.getNetPower());
        assertEquals(lki.getNetToughness(), original.getNetToughness());
        assertEquals(lki.getTriggers().size(), original.getTriggers().size());
        assertEquals(lki.getStaticAbilities().size(), original.getStaticAbilities().size());
    }

    @Test
    public void sourceZoneChangesDoNotFreeTheSnapshotsUsedSticker() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card original = addCard("Grizzly Bears", owner);
        apply(game, sheet(owner), original, StickerKind.ART);
        Game snapshot = new GameCopier(game).makeCopy();
        Player copiedOwner = player(snapshot, owner.getId());
        game.getAction().moveToHand(original, null);
        assertEquals(StickerSheet.getAvailableStickers(owner, 100).size(), 10);
        assertEquals(StickerSheet.getAvailableStickers(copiedOwner, 100).size(), 9);
    }

    @Test
    public void snapshotTransitionsKeepPublicStickersAndClearHiddenOnes() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card original = addCard("Grizzly Bears", owner);
        apply(game, sheet(owner), original, StickerKind.NAME);
        Game snapshot = new GameCopier(game).makeCopy();
        Card copied = card(snapshot, original.getId(), ZoneType.Battlefield);
        Card graveyard = snapshot.getAction().moveToGraveyard(copied, null);
        assertEquals(graveyard.getStickers().size(), 1);
        assertEquals(graveyard.getName(), "Eldrazi Grizzly Bears");
        Card hand = snapshot.getAction().moveToHand(graveyard, null);
        assertFalse(hand.isStickered());
        assertEquals(hand.getName(), "Grizzly Bears");
        assertEquals(StickerSheet.getAvailableStickers(player(snapshot, owner.getId()), 100).size(), 10);
        assertEquals(original.getStickers().size(), 1);
        assertEquals(original.getName(), "Eldrazi Grizzly Bears");
        assertEquals(StickerSheet.getAvailableStickers(owner, 100).size(), 9);
    }

    @Test
    public void ticketCounterSpendingIsIndependentInBothDirections() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Multiset<CounterType> counters = HashMultiset.create();
        counters.add(CounterEnumType.TICKET, 7);
        owner.setCounters(counters);
        Game snapshot = new GameCopier(game).makeCopy();
        Player copiedOwner = player(snapshot, owner.getId());
        copiedOwner.subtractCounter(CounterEnumType.TICKET, 1, copiedOwner);
        assertEquals(owner.getCounters(CounterEnumType.TICKET), 7);
        assertEquals(copiedOwner.getCounters(CounterEnumType.TICKET), 6);
        owner.subtractCounter(CounterEnumType.TICKET, 2, owner);
        assertEquals(owner.getCounters(CounterEnumType.TICKET), 5);
        assertEquals(copiedOwner.getCounters(CounterEnumType.TICKET), 6);
    }

    @Test
    public void ordinaryCopyEffectsDoNotInheritStickerValues() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card original = addCard("Grizzly Bears", owner);
        Card source = sheet(owner);
        for (StickerKind kind : StickerKind.values()) {
            apply(game, source, original, kind);
        }
        Card clone = addCard("Clone", owner);
        clone.addCloneState(CardFactory.getCloneStates(original, clone, clone.getFirstSpellAbility()), game.getNextTimestamp());
        assertFalse(clone.isStickered());
        assertEquals(clone.getName(), "Grizzly Bears");
        assertEquals(clone.getNetPower(), 2);
        assertEquals(clone.getNetToughness(), 2);
        assertFalse(clone.hasKeyword("Haste"));
    }

    @Test
    public void paidSpellKeepsItsStickerAcrossStackCopyAndResolution() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        addCards("Forest", 2, owner);
        Card original = addCardToZone("Grizzly Bears", owner, ZoneType.Hand);
        SpellAbility spell = original.getFirstSpellAbility();
        spell.setActivatingPlayer(owner);
        assertTrue(ComputerUtil.playStack(spell, owner, game));
        assertEquals(spell.getPayingMana().size(), 2);
        original = game.getStack().peekAbility().getHostCard();
        apply(game, sheet(owner), original, StickerKind.PT);
        Game snapshot = new GameCopier(game).withStack(true).makeCopy();
        Card copied = snapshot.getStack().peekAbility().getHostCard();
        assertEquals(copied.getStickers().size(), 1);
        assertEquals(copied.getNetPower(), 1);
        snapshot.getStack().resolveStack();
        Card resolved = card(snapshot, original.getId(), ZoneType.Battlefield);
        assertEquals(resolved.getStickers().size(), 1);
        assertEquals(resolved.getNetToughness(), 4);
        assertEquals(game.getStack().size(), 1);
        assertTrue(original.isInZone(ZoneType.Stack));
    }

    @Test
    public void changedControllerDoesNotChangeTheStickerSheetOwner() {
        Game game = initAndCreateGame();
        Player owner = game.getPlayers().get(0);
        Player controller = game.getPlayers().get(1);
        Card original = addCard("Grizzly Bears", owner);
        Card source = sheet(owner);
        apply(game, source, original, StickerKind.NAME);
        owner.getZone(ZoneType.Battlefield).remove(original);
        controller.getZone(ZoneType.Battlefield).add(original);
        original.setController(controller, game.getNextTimestamp());
        assertEquals(StickerSheet.getAvailableStickers(owner, 100).size(), 9);
        Game snapshot = new GameCopier(game).makeCopy();
        Card copied = card(snapshot, original.getId(), ZoneType.Battlefield);
        assertSame(copied.getController(), player(snapshot, controller.getId()));
        assertSame(copied.getStickers().get(0).getSticker().getSheet().getOwner(), player(snapshot, owner.getId()));
        assertEquals(StickerSheet.getAvailableStickers(player(snapshot, owner.getId()), 100).size(), 9);
    }
}
