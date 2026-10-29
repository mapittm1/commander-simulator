package forge.ai;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import forge.StaticData;
import forge.ai.simulation.GameCopier;
import forge.card.CardStateName;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/** History records describe the old object, not its current zone incarnation. */
public class HistoricalGameCopyTest extends AITest {
    private void counters(Card card, int count) {
        Multiset<CounterType> values = HashMultiset.create();
        values.add(CounterEnumType.P1P1, count);
        card.setCounters(values);
    }

    private Card sticker(Game game, Player owner, Card target, StickerKind kind) {
        Card sheet = Card.fromPaperCard(StaticData.instance().getVariantCards().getCard("Eldrazi Guacamole Tightrope"), owner);
        owner.getZone(ZoneType.StickerSheets).add(sheet);
        Sticker selected = StickerSheet.getStickers(sheet).stream()
                .filter(value -> value.getKind() == kind).findFirst().orElseThrow();
        target.addSticker(new AppliedSticker(selected, game.getNextTimestamp(), 0));
        return sheet;
    }

    @Test
    public void departedPermanentRetainsItsCounterModifiedPower() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card bear = addCard("Grizzly Bears", owner);
        counters(bear, 2);
        Card current = game.getAction().moveToGraveyard(bear, null);
        assertEquals(current.getNetPower(), 2);
        Card history = game.getLeftBattlefieldThisTurn().get(0);
        assertEquals(history.getNetPower(), 4);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = copy.getLeftBattlefieldThisTurn().get(0);
        assertEquals(copied.getNetPower(), 4);
        assertEquals(copied.getCounters(CounterEnumType.P1P1), 2);
        assertSame(copied.getGame(), copy);
        assertNotSame(copied, history);
    }

    @Test
    public void aHiddenCurrentCardDoesNotEraseHistoricalNameStickers() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card bear = addCard("Grizzly Bears", owner);
        Card sheet = sticker(game, owner, bear, StickerKind.NAME);
        Card current = game.getAction().moveToHand(bear, null);
        assertFalse(current.isStickered());
        assertEquals(game.getLeftBattlefieldThisTurn().get(0).getName(), "Eldrazi Grizzly Bears");
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        assertEquals(history.getName(), "Eldrazi Grizzly Bears");
        assertEquals(history.getStickers().size(), 1);
        assertSame(history.getStickers().get(0).getSticker().getSheet().getGame(), copy);
        assertNotSame(history.getStickers().get(0).getSticker().getSheet(), sheet);
    }

    @Test
    public void departedTransformedPermanentKeepsItsHistoricalFace() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card fable = addCard("Fable of the Mirror-Breaker", owner);
        fable.setState(CardStateName.Backside, true);
        Card current = game.getAction().moveToGraveyard(fable, null);
        assertEquals(current.getName(), "Fable of the Mirror-Breaker");
        assertEquals(game.getLeftBattlefieldThisTurn().get(0).getName(), "Reflection of Kiki-Jiki");
        Game copy = new GameCopier(game).makeCopy();
        assertEquals(copy.getLeftBattlefieldThisTurn().get(0).getName(), "Reflection of Kiki-Jiki");
        assertTrue(copy.getLeftBattlefieldThisTurn().get(0).isCreature());
    }

    @Test
    public void graveyardHistoryRetainsPublicStickerStateAfterReturningToHand() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Graveyard);
        sticker(game, owner, bear, StickerKind.PT);
        game.getAction().moveToHand(bear, null);
        assertEquals(game.getLeftGraveyardThisTurn().get(0).getNetToughness(), 4);
        Game copy = new GameCopier(game).makeCopy();
        assertEquals(copy.getLeftGraveyardThisTurn().get(0).getNetToughness(), 4);
    }
    @Test
    public void historicalOwnersControllersZonesAndMemoriesUseTheCopiedGame() {
        Game game = initAndCreateGame();
        Player owner = game.getPlayers().get(0);
        Player controller = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", owner);
        Card remembered = addCard("Forest", owner);
        bear.addRemembered(controller);
        bear.addRemembered(remembered);
        bear.setChosenCards(java.util.List.of(remembered));
        owner.getZone(ZoneType.Battlefield).remove(bear);
        controller.getZone(ZoneType.Battlefield).add(bear);
        bear.setController(controller, game.getNextTimestamp());
        game.getAction().moveToHand(bear, null);
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        assertSame(history.getOwner(), copy.getPlayer(owner.getId()));
        assertSame(history.getController(), copy.getPlayer(controller.getId()));
        assertSame(history.getLastKnownZone(), copy.getPlayer(controller.getId()).getZone(ZoneType.Battlefield));
        for (Object item : history.getRemembered()) {
            if (item instanceof Card) assertSame(((Card) item).getGame(), copy);
            if (item instanceof Player) assertSame(((Player) item).getGame(), copy);
        }
        assertSame(history.getChosenCards().getFirst().getGame(), copy);
    }

    @Test
    public void repeatedDeparturesKeepDistinctTimestampedIncarnations() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card bear = addCard("Grizzly Bears", owner);
        counters(bear, 1);
        bear = game.getAction().moveToGraveyard(bear, null);
        bear = game.getAction().moveToPlay(bear, null, null);
        counters(bear, 2);
        game.getAction().moveToGraveyard(bear, null);
        assertEquals(game.getLeftBattlefieldThisTurn().size(), 2);
        Game copy = new GameCopier(game).makeCopy();
        Card first = copy.getLeftBattlefieldThisTurn().get(0);
        Card second = copy.getLeftBattlefieldThisTurn().get(1);
        assertEquals(first.getNetPower(), 3);
        assertEquals(second.getNetPower(), 4);
        assertNotEquals(first.getGameTimestamp(), second.getGameTimestamp());
        assertNotSame(first, second);
        first.getCounters().clear();
        assertEquals(game.getLeftBattlefieldThisTurn().get(0).getNetPower(), 3);
        assertEquals(second.getNetPower(), 4);
    }

    @Test
    public void historicalDamageRecordsDoNotShareMutableSourceState() {
        Game game = initAndCreateGame();
        Player owner = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", owner);
        bear.getDamageHistory().setCreatureAttackedLastTurnOf(opponent, true);
        game.getAction().moveToGraveyard(bear, null);
        Card source = game.getLeftBattlefieldThisTurn().get(0);
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        assertNotSame(history.getDamageHistory(), source.getDamageHistory());
        assertTrue(history.getDamageHistory().getCreatureAttackedLastTurnOf(copy.getPlayer(opponent.getId())));
        history.getDamageHistory().setCreatureAttackedLastTurnOf(copy.getPlayer(opponent.getId()), false);
        assertTrue(source.getDamageHistory().getCreatureAttackedLastTurnOf(opponent));
    }

    @Test
    public void vanishedTokenHistoryRetainsItsModifiedCharacteristics() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card bear = addCard("Grizzly Bears", owner);
        bear.setGamePieceType(forge.card.GamePieceType.TOKEN);
        counters(bear, 3);
        game.getAction().moveToGraveyard(bear, null);
        assertNull(game.findById(bear.getId()));
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        assertEquals(history.getNetPower(), 5);
        assertTrue(history.isToken());
        assertSame(history.getGame(), copy);
    }
    @Test
    public void rememberedHistoricalIncarnationsWithTheSameIdRemainDistinct() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card bear = addCard("Grizzly Bears", owner);
        counters(bear, 1);
        Card earlier = forge.game.card.CardCopyService.getLKICopy(bear);
        counters(bear, 2);
        bear.addRemembered(earlier);
        game.getAction().moveToGraveyard(bear, null);
        Card original = game.getLeftBattlefieldThisTurn().get(0);
        assertEquals(((Card) original.getRemembered().iterator().next()).getNetPower(), 3);
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        Card remembered = (Card) history.getRemembered().iterator().next();
        assertEquals(history.getNetPower(), 4);
        assertEquals(remembered.getNetPower(), 3);
        assertNotSame(remembered, history);
        assertSame(remembered.getGame(), copy);
    }
    @Test
    public void historicalCombatBandsMapToTheSameCopiedLiveCombatBand() {
        Game game = initAndCreateGame();
        Player attacker = game.getPlayers().get(0);
        Player defender = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", attacker);
        Card companion = addCard("Grizzly Bears", attacker);
        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.COMBAT_DECLARE_ATTACKERS, attacker, false);
        game.getPhaseHandler().setCombat(new forge.game.combat.Combat(attacker));
        game.getCombat().addAttacker(bear, defender);
        game.getCombat().addAttacker(companion, defender);
        game.getAction().moveToGraveyard(bear, null);
        Card source = game.getLeftBattlefieldThisTurn().get(0);
        assertNotNull(source.getCombatLKI());
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        assertTrue(history.isAttacking());
        assertNotSame(history.getCombatLKI(), source.getCombatLKI());
        assertNotSame(history.getCombatLKI().getFirstBand(), source.getCombatLKI().getFirstBand());
        for (Card member : history.getCombatLKI().getFirstBand().getAttackers()) {
            assertSame(member.getGame(), copy);
        }
    }

    @Test
    public void liveAndHistoricalInformationShareTheMappedBandIdentity() {
        Game game = initAndCreateGame();
        Player attacker = game.getPlayers().get(0);
        Player defender = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", attacker);
        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.COMBAT_DECLARE_ATTACKERS, attacker, false);
        game.getPhaseHandler().setCombat(new forge.game.combat.Combat(attacker));
        game.getCombat().addAttacker(bear, defender);
        game.addLeftBattlefieldThisTurn(forge.game.card.CardCopyService.getLKICopy(bear));
        Game copy = new GameCopier(game).makeCopy();
        Card current = copy.getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.getId() == bear.getId()).findFirst().orElseThrow();
        forge.game.combat.AttackingBand historical = copy.getLeftBattlefieldThisTurn().get(0).getCombatLKI().getFirstBand();
        assertSame(historical, copy.getCombat().getBandOfAttacker(current));
        assertNotSame(historical, game.getCombat().getBandOfAttacker(bear));
    }
    @Test
    public void reusingACopierDoesNotReuseCombatObjectsFromItsPreviousSnapshot() {
        Game game = initAndCreateGame();
        Player attacker = game.getPlayers().get(0);
        Player defender = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", attacker);
        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.COMBAT_DECLARE_ATTACKERS, attacker, false);
        game.getPhaseHandler().setCombat(new forge.game.combat.Combat(attacker));
        game.getCombat().addAttacker(bear, defender);
        GameCopier copier = new GameCopier(game);
        Game first = copier.makeCopy();
        Game second = copier.makeCopy();
        Card firstBear = first.getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.getId() == bear.getId()).findFirst().orElseThrow();
        Card secondBear = second.getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.getId() == bear.getId()).findFirst().orElseThrow();
        assertNotSame(first.getCombat().getBandOfAttacker(firstBear), second.getCombat().getBandOfAttacker(secondBear));
        assertSame(second.getCombat().getBandOfAttacker(secondBear).getAttackers().getFirst(), secondBear);
    }
    @org.testng.annotations.DataProvider(name = "morbidHistory")
    public Object[][] morbidHistory() {
        return new Object[][] {{true}, {false}};
    }

    @Test(dataProvider = "morbidHistory")
    public void paidTragicSlipUsesTheDepartedCreaturesHistoricalFaceForMorbid(boolean creatureDied) {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Player opponent = game.getPlayers().stream().filter(player -> player != owner).findFirst().orElseThrow();
        Card fable = addCard("Fable of the Mirror-Breaker", owner);
        if (creatureDied) fable.setState(CardStateName.Backside, true);
        game.getAction().moveToGraveyard(fable, null);
        Card target = addCard("Colossal Dreadmaw", opponent);
        addCard("Swamp", owner);
        Card slip = addCardToZone("Tragic Slip", owner, ZoneType.Hand);
        forge.game.spellability.SpellAbility spell = slip.getFirstSpellAbility();
        spell.setActivatingPlayer(owner);
        spell.getTargets().add(target);
        assertEquals(forge.game.card.CardUtil.getThisTurnEntered(ZoneType.Graveyard, ZoneType.Battlefield,
                "Creature", slip, spell, owner).size(), creatureDied ? 1 : 0, "original morbid history");
        assertTrue(ComputerUtil.playStack(spell, owner, game));
        assertEquals(spell.getPayingMana().size(), 1);
        assertEquals(game.getStack().size(), 1);
        Game copy = new GameCopier(game).withStack(true).makeCopy();
        assertEquals(copy.getStack().size(), 1);
        assertEquals(forge.game.card.CardUtil.getThisTurnEntered(ZoneType.Graveyard, ZoneType.Battlefield,
                "Creature", copy.getStack().peekAbility().getHostCard(), copy.getStack().peekAbility(),
                copy.getPlayer(owner.getId())).size(), creatureDied ? 1 : 0, "copied morbid history");
        copy.getStack().resolveStack();
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.getId() == target.getId()).findFirst().orElseThrow().getNetPower(), creatureDied ? -7 : 5);
        copy.getAction().checkStateEffects(true);
        assertEquals(copy.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == target.getId()), creatureDied);
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card -> card.getId() == target.getId()), !creatureDied);
        if (!creatureDied) assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.getId() == target.getId()).findFirst().orElseThrow().getNetPower(), 5);
        assertTrue(target.isInZone(ZoneType.Battlefield));
        assertEquals(game.getStack().size(), 1);
    }
}
