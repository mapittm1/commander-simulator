package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import static org.testng.Assert.*;

/** Damage provenance must survive copying, including equal-valued distinct packets. */
public class DamageGameCopyTest extends AITest {
    private Player owner(Game game) { return game.getPhaseHandler().getPlayerTurn(); }
    private Player opponent(Game game, Player player) {
        return game.getPlayers().stream().filter(other -> other != player).findFirst().orElseThrow();
    }
    private Card card(Game game, Card original, ZoneType zone) {
        return game.getCardsIn(zone).stream().filter(candidate -> candidate.getId() == original.getId()).findFirst().orElseThrow();
    }
    private int total(Game game, Card source, String validSource, String validTarget) {
        return game.getDamageDoneThisTurn(null, false, validSource, validTarget,
                source, source.getController(), null).stream().mapToInt(Integer::intValue).sum();
    }
    private void damage(Card source, forge.game.GameEntity target, int amount, boolean combat) {
        source.getDamageHistory().registerDamage(amount, combat, source, target, new HashMap<>());
    }

    @Test
    public void globalDamageTotalsAndMaximumSurviveSnapshot() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, opponent(game, owner), 3, false);
        assertEquals(total(game, bear, "Creature", "Player.Opponent"), 3);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = card(copy, bear, ZoneType.Battlefield);
        assertEquals(total(copy, copied, "Creature", "Player.Opponent"), 3);
        assertEquals(copy.getSingleMaxDamageDoneThisTurn(), 3);
    }

    @Test
    public void receivedDamageCanStillBeFilteredByItsHistoricalSource() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Player target = opponent(game, owner);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, target, 3, true);
        assertEquals(target.getAssignedDamage(null, bear), 3);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = card(copy, bear, ZoneType.Battlefield);
        assertEquals(copy.getPlayer(target.getId()).getAssignedDamage(null, copied), 3);
    }

    @Test
    public void historicalDamageDoesNotBypassInvalidSourceOrTargetFilters() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, opponent(game, owner), 3, false);
        game.getAction().moveToGraveyard(bear, null);
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        assertEquals(history.getDamageHistory().getDamageDoneThisTurn(null, false,
                "Artifact", "Player.Opponent", history, history.getController(), null), 0);
        assertEquals(history.getDamageHistory().getDamageDoneThisTurn(null, false,
                "Creature", "Creature", history, history.getController(), null), 0);
    }

    @Test
    public void copiedLiveSourceKeepsIndependentPerCardDamageHistory() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, opponent(game, owner), 3, true);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = card(copy, bear, ZoneType.Battlefield);
        assertEquals(copied.getDamageHistory().getAllDmgInstances().size(), 1);
        assertEquals(copied.getDamageHistory().getDamageDoneThisTurn(true, false,
                "Creature", "Player.Opponent", copied, copied.getController(), null), 3);
        copied.getDamageHistory().newTurn();
        assertEquals(bear.getDamageHistory().getAllDmgInstances().size(), 1);
    }

    @Test
    public void equalPacketsRetainTheirSeparateTargetsAndSourceIncarnations() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, opponent(game, owner), 3, true);
        damage(bear, owner, 3, true);
        assertEquals(total(game, bear, "Creature", "Player.Opponent"), 3);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = card(copy, bear, ZoneType.Battlefield);
        assertEquals(total(copy, copied, "Creature", "Player.Opponent"), 3);
        assertEquals(total(copy, copied, "Creature", "Player.You"), 3);
        assertEquals(copy.getPlayer(owner.getId()).getAssignedDamage(null, copied), 3);
    }

    @DataProvider(name = "bloodthirstTargets")
    public Object[][] bloodthirstTargets() { return new Object[][] {{true}, {false}}; }

    @Test(dataProvider = "bloodthirstTargets")
    public void paidBloodthirstSpellUsesActualOpponentDamage(boolean damageOpponent) {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Player opponent = opponent(game, owner);
        owner.setLife(40, null);
        opponent.setLife(40, null);
        addCards("Mountain", 5, owner);
        Card bolt = addCardToZone("Lightning Bolt", owner, ZoneType.Hand);
        SpellAbility boltAbility = bolt.getFirstSpellAbility();
        boltAbility.setActivatingPlayer(owner);
        boltAbility.getTargets().add(damageOpponent ? opponent : owner);
        assertTrue(ComputerUtil.playStack(boltAbility, owner, game));
        assertEquals(boltAbility.getPayingMana().size(), 1);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        assertEquals((damageOpponent ? opponent : owner).getLife(), 37);
        Card minotaur = addCardToZone("Gorehorn Minotaurs", owner, ZoneType.Hand);
        SpellAbility ability = minotaur.getFirstSpellAbility();
        ability.setActivatingPlayer(owner);
        assertTrue(ComputerUtil.playStack(ability, owner, game));
        assertEquals(ability.getPayingMana().size(), 4);
        Game copy = new GameCopier(game).withStack(true).makeCopy();
        assertEquals(copy.getStack().size(), 1);
        copy.getStack().resolveStack();
        copy.getAction().checkStateEffects(true);
        Card resolved = card(copy, minotaur, ZoneType.Battlefield);
        assertEquals(resolved.getCounters(CounterEnumType.P1P1), damageOpponent ? 2 : 0);
        assertEquals(resolved.getNetPower(), damageOpponent ? 5 : 3);
        assertEquals(game.getStack().size(), 1);
    }

    @Test
    public void newSnapshotDamageExtendsTheSameGlobalSourceHistoryExactlyOnce() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Player target = opponent(game, owner);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, target, 3, false);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = card(copy, bear, ZoneType.Battlefield);
        damage(copied, copy.getPlayer(target.getId()), 2, false);
        assertEquals(total(copy, copied, "Creature", "Player.Opponent"), 5);
        assertEquals(total(game, bear, "Creature", "Player.Opponent"), 3);
        damage(bear, target, 4, false);
        assertEquals(total(game, bear, "Creature", "Player.Opponent"), 7);
        assertEquals(total(copy, copied, "Creature", "Player.Opponent"), 5);
    }

    @Test
    public void previousTurnReceiptsKeepProvenanceAndExpireAfterAnotherCleanup() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Player target = opponent(game, owner);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, target, 3, true);
        game.onCleanupPhase();
        assertEquals(target.getAssignedDamage(null, bear, true), 3);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = card(copy, bear, ZoneType.Battlefield);
        Player copiedTarget = copy.getPlayer(target.getId());
        assertEquals(copiedTarget.getAssignedDamage(null, copied, true), 3);
        assertEquals(copiedTarget.getAssignedDamage(null, copied), 0);
        assertEquals(copy.getSingleMaxDamageDoneThisTurn(), 0);
        copy.onCleanupPhase();
        assertEquals(copiedTarget.getAssignedDamage(null, copied, true), 0);
        assertEquals(target.getAssignedDamage(null, bear, true), 3);
    }

    @Test
    public void vanishedTokenSourceStillHasAttributedHistoricalDamage() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Player target = opponent(game, owner);
        Card token = addCard("Grizzly Bears", owner);
        token.setGamePieceType(forge.card.GamePieceType.TOKEN);
        damage(token, target, 3, true);
        game.getAction().moveToGraveyard(token, null);
        assertNull(game.findById(token.getId()));
        Game copy = new GameCopier(game).makeCopy();
        Card history = copy.getLeftBattlefieldThisTurn().get(0);
        assertEquals(total(copy, history, "Creature", "Player.Opponent"), 3);
        assertEquals(copy.getPlayer(target.getId()).getAssignedDamage(null, history), 3);
    }

    @Test
    public void vanishedTokenTargetHasIndependentHistoricalMetadataAndSafeCleanup() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Card bear = addCard("Grizzly Bears", owner);
        Card token = addCard("Grizzly Bears", opponent(game, owner));
        token.setGamePieceType(forge.card.GamePieceType.TOKEN);
        damage(bear, token, 3, false);
        game.getAction().moveToGraveyard(token, null);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = card(copy, bear, ZoneType.Battlefield);
        assertEquals(total(copy, copied, "Creature", "Creature"), 3);
        assertEquals(copied.getDamageHistory().getThisGameDamaged().size(), 1);
        assertSame(copied.getDamageHistory().getThisGameDamaged().getFirst().getGame(), copy);
        copied.getDamageHistory().newTurn();
        assertEquals(copied.getDamageHistory().getThisGameDamaged().size(), 0);
        assertEquals(bear.getDamageHistory().getThisGameDamaged().size(), 1);
    }

    @Test
    public void missingProvenanceCannotSilentlySatisfyAFilteredDamageQuery() {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Card bear = addCard("Grizzly Bears", owner);
        damage(bear, opponent(game, owner), 3, false);
        game.clearGlobalDamageHistory();
        assertThrows(IllegalStateException.class, () -> bear.getDamageHistory().getDamageDoneThisTurn(
                null, false, "Creature", "Player.Opponent", bear, owner, null));
    }

    @DataProvider(name = "freerunningSources")
    public Object[][] freerunningSources() {
        return new Object[][] {{"assassin", true}, {"commander", true}, {"ordinary", false},
            {"noncombat", false}, {"creatureTarget", false}, {"stolenAfter", true}, {"stolenBefore", false}};
    }

    private void control(Card card, Player target) {
        card.getController().getZone(ZoneType.Battlefield).remove(card);
        target.getZone(ZoneType.Battlefield).add(card);
        card.setController(target, card.getGame().getNextTimestamp());
    }

    @Test(dataProvider = "freerunningSources")
    public void paidFreerunningUsesSourceTypeAndControllerAtTheTimeOfDamage(String scenario, boolean eligible) {
        Game game = initAndCreateGame();
        Player owner = owner(game);
        Player opponent = opponent(game, owner);
        owner.setLife(40, null);
        opponent.setLife(40, null);
        Card attacker = addCard(scenario.equals("ordinary") || scenario.equals("commander")
                ? "Grizzly Bears" : "Changeling Outcast", owner);
        if (scenario.equals("commander")) attacker.setCommander(true);
        if (scenario.equals("stolenBefore")) control(attacker, opponent);
        forge.game.GameEntity target = scenario.equals("creatureTarget") ? addCard("Colossal Dreadmaw", opponent) : opponent;
        game.getAction().checkStaticAbilities(false);
        forge.game.card.CardDamageTable dealt = new forge.game.card.CardDamageTable();
        dealt.put(attacker, target, 1);
        game.getAction().dealDamage(!scenario.equals("noncombat"), dealt,
                new forge.game.card.CardDamageTable(), new forge.game.GameEntityCounterTable(), null);
        if (scenario.equals("stolenAfter")) control(attacker, opponent);
        if (scenario.equals("stolenBefore")) control(attacker, owner);
        assertEquals(owner.hasFreerunning(), eligible);
        addCards("Island", 2, owner);
        addCardToZone("Forest", owner, ZoneType.Library);
        addCardToZone("Forest", owner, ZoneType.Library);
        addCardToZone("Forest", owner, ZoneType.Library);
        Card vision = addCardToZone("Eagle Vision", owner, ZoneType.Hand);
        Game copy = new GameCopier(game).makeCopy();
        Player copiedOwner = copy.getPlayer(owner.getId());
        assertEquals(copiedOwner.hasFreerunning(), eligible);
        Card copiedVision = card(copy, vision, ZoneType.Hand);
        SpellAbility spell = copiedVision.getSpellAbilities().stream().filter(SpellAbility::isFreerunning).findFirst().orElseThrow();
        spell.setActivatingPlayer(copiedOwner);
        assertEquals(spell.canPlay(), eligible);
        if (eligible) assertTrue(ComputerUtil.playStack(spell, copiedOwner, copy));
        assertEquals(spell.getPayingMana().size(), eligible ? 2 : 0);
        if (eligible) {
            copy.getStack().resolveStack();
            copy.getAction().checkStateEffects(true);
            assertEquals(copiedOwner.getCardsIn(ZoneType.Hand).size(), 3);
            assertTrue(copy.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == vision.getId()));
        } else {
            assertTrue(copy.getCardsIn(ZoneType.Hand).stream().anyMatch(card -> card.getId() == vision.getId()));
        }
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(owner.getCardsIn(ZoneType.Library).size(), 3);
    }
}
