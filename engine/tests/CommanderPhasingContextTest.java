package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Phasing snapshots and supplied native spell/untap witnesses, not autonomous strategy. */
public class CommanderPhasingContextTest extends AITest {
    @Test public void nativePaidSlipOutTheBackPhasesWithoutChangingTheCreaturesZone() {
        Board board = board();
        long timestamp = board.creature.getGameTimestamp();
        castSlip(board);
        assertTrue(board.creature.isInZone(ZoneType.Battlefield));
        assertEquals(board.creature.getGameTimestamp(), timestamp);
        assertTrue(board.creature.isPhasedOut());
        assertTrue(board.creature.isDirectlyPhasedOut());
        assertSame(board.creature.getPhasedOut(), board.actor);
        assertTrue(board.creature.getView().isPhasedOut());
        assertEquals(board.creature.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test public void aCopiedPhasedCreatureRemainsPhasedOut() {
        Board board = board(); castSlip(board);
        GameCopier copier = copy(board);
        Card creature = copier.find(board.creature);
        assertNotNull(creature);
        assertTrue(creature.isPhasedOut());
        assertEquals(creature.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test public void theCopiedPhaseOwnerBelongsToTheCopiedGame() {
        Board board = board(); castSlip(board);
        GameCopier copier = copy(board);
        Player phaseOwner = copier.find(board.creature).getPhasedOut();
        assertNotNull(phaseOwner);
        assertSame(phaseOwner, copier.find(board.actor));
        assertSame(phaseOwner.getGame(), copier.getCopiedGame());
    }

    @Test public void directPhasingAndPublicPresentationSurviveCopying() {
        Board board = board(); castSlip(board);
        Card creature = copy(board).find(board.creature);
        assertTrue(creature.isDirectlyPhasedOut());
        assertTrue(creature.getView().isPhasedOut());
    }

    @Test public void phasingInTheCopyDoesNotChangeTheOriginal() {
        Board board = board(); castSlip(board);
        Card creature = copy(board).find(board.creature);
        assertTrue(creature.isPhasedOut());
        creature.phase(true);
        assertFalse(creature.isPhasedOut());
        assertFalse(creature.getView().isPhasedOut());
        assertEquals(creature.getCounters(CounterEnumType.P1P1), 1);
        assertTrue(board.creature.isPhasedOut());
        assertSame(board.creature.getPhasedOut(), board.actor);
    }

    @Test public void sameGameLastKnownInformationRetainsNativePhasingState() {
        Board board = board(); castSlip(board);
        Card history = CardCopyService.getLKICopy(board.creature);
        assertTrue(history.isPhasedOut());
        assertSame(history.getPhasedOut(), board.actor);
        assertTrue(history.isDirectlyPhasedOut());
        assertTrue(history.getView().isPhasedOut());
    }

    @Test public void nestedCopiesMapThePhaseOwnerTwice() {
        Board board = board(); castSlip(board);
        GameCopier first = copy(board);
        GameCopier second = new GameCopier(first.getCopiedGame()).withStack(true);
        Game game = second.makeCopy(null, first.find(board.actor));
        Card creature = second.find(first.find(board.creature));
        assertTrue(creature.isPhasedOut());
        assertSame(creature.getPhasedOut(), second.find(first.find(board.actor)));
        assertSame(creature.getPhasedOut().getGame(), game);
    }

    @Test public void anUnphasedCreatureDoesNotAcquirePhasingState() {
        Board board = board();
        Card creature = copy(board).find(board.creature);
        assertNotNull(creature);
        assertFalse(creature.isPhasedOut());
        assertNull(creature.getPhasedOut());
        // The native direct-phasing designation can be true even while unphased.
        assertEquals(creature.isDirectlyPhasedOut(), board.creature.isDirectlyPhasedOut());
        assertFalse(creature.getView().isPhasedOut());
    }

    @Test public void nativeUntapPhasesInOnlyTheCopiedCreature() {
        Board board = board(); castSlip(board);
        GameCopier copier = copy(board);
        Card creature = copier.find(board.creature);
        forge.game.phase.Untap.doPhasing(copier.find(board.actor));
        assertFalse(creature.isPhasedOut());
        assertEquals(creature.getCounters(CounterEnumType.P1P1), 1);
        assertTrue(board.creature.isPhasedOut());
    }

    @Test public void anotherPlayersUntapDoesNotPhaseInTheCopiedCreature() {
        Board board = board(); castSlip(board);
        GameCopier copier = copy(board);
        Player other = copier.find(board.game.getPlayers().get(1));
        forge.game.phase.Untap.doPhasing(other);
        assertTrue(copier.find(board.creature).isPhasedOut());
        assertSame(copier.find(board.creature).getPhasedOut(), copier.find(board.actor));
    }

    /** Equipment attachment is an explicit legal precondition; Slip is cast and paid natively. */
    @Test public void nativeAttachedEquipmentPhasesIndirectly() {
        Board board = board(); Card equipment = equipment(board); castSlip(board);
        assertTrue(equipment.isPhasedOut());
        assertFalse(equipment.isDirectlyPhasedOut());
        assertSame(equipment.getAttachedTo(), board.creature);
        forge.game.phase.Untap.doPhasing(board.actor);
        assertFalse(equipment.isPhasedOut());
        assertSame(equipment.getAttachedTo(), board.creature);
        assertEquals(board.creature.getNetPower(), 5);
    }

    @Test public void copiedIndirectEquipmentPhasesInWithItsCreature() {
        Board board = board(); Card equipment = equipment(board); castSlip(board);
        GameCopier copier = copy(board);
        Card copiedEquipment = copier.find(equipment), creature = copier.find(board.creature);
        assertTrue(copiedEquipment.isPhasedOut());
        assertFalse(copiedEquipment.isDirectlyPhasedOut());
        assertSame(copiedEquipment.getAttachedTo(), creature);
        assertSame(copiedEquipment.getPhasedOut(), copier.find(board.actor));
        forge.game.phase.Untap.doPhasing(copier.find(board.actor));
        assertFalse(creature.isPhasedOut()); assertFalse(copiedEquipment.isPhasedOut());
        assertSame(copiedEquipment.getAttachedTo(), creature);
        assertEquals(creature.getNetPower(), 5);
        assertTrue(equipment.isPhasedOut()); assertTrue(board.creature.isPhasedOut());
    }

    @Test public void indirectPhasingHistoryDoesNotAcquireTheDefaultDirectDesignation() {
        Board board = board(); Card equipment = equipment(board); castSlip(board);
        assertFalse(equipment.isDirectlyPhasedOut());
        Card history = CardCopyService.getLKICopy(equipment);
        assertTrue(history.isPhasedOut());
        assertFalse(history.isDirectlyPhasedOut());
        assertSame(history.getPhasedOut(), board.actor);
    }

    @Test public void nativeOutOfTimeSuppressesUntapAndReturnsCreaturesOnRemoval() {
        Timed timed = outOfTime(); Board board = timed.board;
        forge.game.phase.Untap.doPhasing(board.actor);
        forge.game.phase.Untap.doPhasing(timed.other.getController());
        assertTrue(board.creature.isPhasedOut()); assertTrue(timed.other.isPhasedOut());
        disenchant(board.game, board.actor, timed.enchantment);
        assertFalse(board.creature.isPhasedOut()); assertFalse(timed.other.isPhasedOut());
        assertFalse(board.creature.isWontPhaseInNormal()); assertFalse(timed.other.isWontPhaseInNormal());
        assertTrue(board.creature.isInZone(ZoneType.Battlefield));
        assertTrue(board.game.getCardState(timed.enchantment).isInZone(ZoneType.Graveyard));
    }

    @Test public void copiedOutOfTimeSuppressesBothPlayersNormalPhaseIn() {
        Timed timed = outOfTime(); Board board = timed.board;
        GameCopier copier = copy(board);
        Card own = copier.find(board.creature), opposing = copier.find(timed.other);
        assertTrue(own.isWontPhaseInNormal()); assertTrue(opposing.isWontPhaseInNormal());
        assertSame(own.getPhasedOut(), copier.find(board.actor));
        assertSame(opposing.getPhasedOut(), copier.find(timed.other.getController()));
        forge.game.phase.Untap.doPhasing(copier.find(board.actor));
        forge.game.phase.Untap.doPhasing(copier.find(timed.other.getController()));
        assertTrue(own.isPhasedOut()); assertTrue(opposing.isPhasedOut());
        assertTrue(own.getView().isPhasedOut()); assertTrue(opposing.getView().isPhasedOut());
    }

    @Test public void copiedOutOfTimeReturnsBothCreaturesWhenDisenchanted() {
        Timed timed = outOfTime(); Board board = timed.board;
        GameCopier copier = copy(board); Game game = copier.getCopiedGame();
        Card own = copier.find(board.creature), opposing = copier.find(timed.other);
        long ownTimestamp = own.getGameTimestamp(), opposingTimestamp = opposing.getGameTimestamp();
        disenchant(game, copier.find(board.actor), copier.find(timed.enchantment));
        assertFalse(own.isPhasedOut()); assertFalse(opposing.isPhasedOut());
        assertFalse(own.isWontPhaseInNormal()); assertFalse(opposing.isWontPhaseInNormal());
        assertEquals(own.getGameTimestamp(), ownTimestamp); assertEquals(opposing.getGameTimestamp(), opposingTimestamp);
        assertTrue(own.isInZone(ZoneType.Battlefield)); assertTrue(opposing.isInZone(ZoneType.Battlefield));
        assertTrue(board.creature.isPhasedOut()); assertTrue(timed.other.isPhasedOut());
        assertTrue(timed.enchantment.isInZone(ZoneType.Battlefield));
    }

    @Test public void copiedSuppressionAlsoSurvivesNativeLastKnownInformation() {
        Timed timed = outOfTime(); Board board = timed.board;
        GameCopier copier = copy(board);
        Card history = CardCopyService.getLKICopy(copier.find(board.creature));
        assertTrue(history.isPhasedOut()); assertTrue(history.isWontPhaseInNormal());
        assertSame(history.getPhasedOut(), copier.find(board.actor));
    }

    @Test public void aPhasedTokenRemainsPhysicalAndPhasesInOnlyInTheCopy() {
        Board board = board();
        for (int index = 0; index < 2; index++) addCardToZone("Plains", board.actor, ZoneType.Battlefield);
        Card source = addCardToZone("Raise the Alarm", board.actor, ZoneType.Hand);
        SpellAbility action = source.getFirstSpellAbility(); action.setActivatingPlayer(board.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(board.actor, action, null)); settle(board.game);
        List<Card> tokens = board.actor.getCreaturesInPlay().stream().filter(Card::isToken).toList();
        assertEquals(tokens.size(), 2); Card token = tokens.get(0); long timestamp = token.getGameTimestamp();
        addCardToZone("Island", board.actor, ZoneType.Battlefield);
        castSlip(board, token);
        GameCopier copier = copy(board); Card copied = copier.find(token);
        assertTrue(copied.isToken()); assertTrue(copied.isPhasedOut());
        assertEquals(copied.getGameTimestamp(), timestamp);
        forge.game.phase.Untap.doPhasing(copier.find(board.actor));
        assertFalse(copied.isPhasedOut()); assertTrue(copied.isInZone(ZoneType.Battlefield));
        assertTrue(token.isPhasedOut());
    }

    @Test public void aPhasedStaticSourceIsInactiveUntilCopiedNativePhaseIn() {
        Board board = board();
        Card lord = addCardToZone("Lord of Atlantis", board.actor, ZoneType.Battlefield);
        Card merfolk = addCardToZone("Coral Merfolk", board.actor, ZoneType.Battlefield);
        settle(board.game); assertEquals(merfolk.getNetPower(), 3);
        castSlip(board, lord); assertEquals(merfolk.getNetPower(), 2);
        GameCopier copier = copy(board); Card copied = copier.find(merfolk);
        assertEquals(copied.getNetPower(), 2);
        forge.game.phase.Untap.doPhasing(copier.find(board.actor));
        assertEquals(copied.getNetPower(), 3);
        assertEquals(merfolk.getNetPower(), 2); assertTrue(lord.isPhasedOut());
    }

    /** Explicit remembered-state contract, not an added printed Grizzly Bears ability. */
    @Test public void aPhasedPermanentsRememberedCardMapsIntoItsOwnWorld() {
        Board board = board(); Card remembered = addCardToZone("Sol Ring", board.actor, ZoneType.Battlefield);
        board.creature.addRemembered(remembered); castSlip(board);
        GameCopier copier = copy(board);
        assertTrue(copier.find(board.creature).hasRemembered());
        assertSame(copier.find(board.creature).getRemembered().iterator().next(), copier.find(remembered));
        assertSame(board.creature.getRemembered().iterator().next(), remembered);
    }

    @Test public void outOfTimesCopiedReturnEffectRetainsItsImprintedHost() {
        Timed timed = outOfTime(); Board board = timed.board;
        List<Card> effects = board.game.getCardsIn(ZoneType.Command).stream()
                .filter(card -> card.hasImprintedCard(timed.enchantment)).toList();
        assertEquals(effects.size(), 1);
        GameCopier copier = copy(board); Card effect = copier.find(effects.get(0));
        assertEquals(effect.getImprintedCards().size(), 1);
        assertSame(effect.getImprintedCards().get(0), copier.find(timed.enchantment));
        assertSame(effect.getView().getImprintedCards().get(0), copier.find(timed.enchantment).getView());
    }

    /** Chrome Mox starts with a legal green-creature imprint; its ETB choice is supplied setup. */
    @Test public void aCopiedChromeMoxImprintCanPayForAGreenSpell() {
        Board board = board(); Card mox = legalMox(board);
        GameCopier copier = copy(board); Player actor = copier.find(board.actor);
        Card card = addCardToZone("Elvish Mystic", actor, ZoneType.Hand);
        SpellAbility action = card.getFirstSpellAbility(); action.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, action, null)); settle(copier.getCopiedGame());
        assertTrue(copier.getCopiedGame().getCardState(action.getHostCard()).isInZone(ZoneType.Battlefield));
        assertTrue(copier.find(mox).isTapped()); assertFalse(mox.isTapped());
        assertEquals(action.getPayingMana().size(), 1);
        assertEquals(actor.getSpellsCastThisTurn(), 1); assertEquals(board.actor.getSpellsCastThisTurn(), 0);
    }

    @Test public void copiedImprintContainersAndViewsRemainIndependent() {
        Board board = board(); Card mox = legalMox(board); Card original = mox.getImprintedCards().get(0);
        GameCopier copier = copy(board); Card copied = copier.find(mox);
        assertEquals(copied.getImprintedCards().size(), 1);
        assertSame(copied.getImprintedCards().get(0), copier.find(original));
        assertSame(copied.getView().getImprintedCards().get(0), copier.find(original).getView());
        copied.clearImprintedCards();
        assertTrue(copied.getImprintedCards().isEmpty()); assertTrue(copied.getView().getImprintedCards().isEmpty());
        assertEquals(mox.getImprintedCards().size(), 1); assertEquals(mox.getView().getImprintedCards().size(), 1);
    }

    /** Raw historical imprint contract, not an added printed Sol Ring ability. */
    @Test public void aHistoricalImprintKeepsItsTimestampAndCopiedWorld() {
        Board board = board();
        Card host = addCardToZone("Sol Ring", board.actor, ZoneType.Battlefield);
        Card old = addCardToZone("Grizzly Bears", board.actor, ZoneType.Exile);
        Card moved = board.game.getAction().moveToHand(old, null);
        assertNotEquals(old.getGameTimestamp(), moved.getGameTimestamp());
        host.addImprintedCard(old);
        GameCopier copier = copy(board); Card copied = copier.find(host);
        assertEquals(copied.getImprintedCards().size(), 1);
        Card history = copied.getImprintedCards().get(0);
        assertNotSame(history, copier.find(moved)); assertSame(history.getGame(), copier.getCopiedGame());
        assertEquals(history.getGameTimestamp(), old.getGameTimestamp());
    }

    /** Raw self-imprint graph contract, not an added printed Sol Ring ability. */
    @Test public void aSelfImprintMapsToTheCopiedCard() {
        Board board = board(); Card host = addCardToZone("Sol Ring", board.actor, ZoneType.Battlefield);
        host.addImprintedCard(host);
        GameCopier copier = copy(board); Card copied = copier.find(host);
        assertEquals(copied.getImprintedCards().size(), 1); assertSame(copied.getImprintedCards().get(0), copied);
        assertSame(copied.getView().getImprintedCards().get(0), copied.getView());
        assertSame(host.getImprintedCards().get(0), host);
    }

    private Card legalMox(Board board) {
        Card mox = addCardToZone("Chrome Mox", board.actor, ZoneType.Battlefield);
        Card exiled = addCardToZone("Grizzly Bears", board.actor, ZoneType.Exile);
        exiled.setExiledWith(mox); mox.addImprintedCard(exiled);
        return mox;
    }

    private Card equipment(Board board) {
        Card equipment = addCardToZone("Bonesplitter", board.actor, ZoneType.Battlefield);
        equipment.attachToEntity(board.creature, null);
        assertSame(equipment.getAttachedTo(), board.creature);
        return equipment;
    }

    private Timed outOfTime() {
        Board board = board();
        Card other = addCardToZone("Elvish Visionary", board.game.getPlayers().get(1), ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) addCardToZone("Plains", board.actor, ZoneType.Battlefield);
        Card enchantment = addCardToZone("Out of Time", board.actor, ZoneType.Hand);
        SpellAbility action = enchantment.getFirstSpellAbility(); action.setActivatingPlayer(board.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(board.actor, action, null)); settle(board.game);
        enchantment = board.game.getCardState(action.getHostCard());
        assertTrue(enchantment.isInZone(ZoneType.Battlefield));
        assertEquals(enchantment.getCounters(CounterEnumType.TIME), 2);
        assertTrue(board.creature.isPhasedOut()); assertTrue(other.isPhasedOut());
        assertTrue(board.creature.isWontPhaseInNormal()); assertTrue(other.isWontPhaseInNormal());
        return new Timed(board, other, enchantment);
    }

    private void disenchant(Game game, Player actor, Card target) {
        for (int index = 0; index < 2; index++) addCardToZone("Plains", actor, ZoneType.Battlefield);
        Card card = addCardToZone("Disenchant", actor, ZoneType.Hand);
        SpellAbility action = card.getFirstSpellAbility(); action.setActivatingPlayer(actor); action.getTargets().add(target);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, action, null)); settle(game);
        assertEquals(action.getPayingMana().size(), 2);
        assertTrue(game.getCardState(card).isInZone(ZoneType.Graveyard));
    }

    private record Timed(Board board, Card other, Card enchantment) { }

    private GameCopier copy(Board board) {
        assertTrue(board.game.getStack().isEmpty());
        GameCopier copier = new GameCopier(board.game).withStack(true);
        Game game = copier.makeCopy(null, board.actor);
        assertTrue(game.getStack().isEmpty());
        return copier;
    }

    private void castSlip(Board board) {
        castSlip(board, board.creature);
        assertEquals(board.actor.getSpellsCastThisTurn(), 1);
        assertTrue(board.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
    }

    private void castSlip(Board board, Card target) {
        int before = board.actor.getSpellsCastThisTurn();
        Card source = addCardToZone("Slip Out the Back", board.actor, ZoneType.Hand);
        SpellAbility spell = source.getFirstSpellAbility();
        spell.setActivatingPlayer(board.actor);
        spell.getTargets().add(target);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(board.actor, spell, null));
        settle(board.game);
        assertEquals(board.actor.getSpellsCastThisTurn(), before + 1);
        assertEquals(spell.getPayingMana().size(), 1);
        assertTrue(board.game.getCardState(spell.getHostCard()).isInZone(ZoneType.Graveyard));
    }

    private void settle(Game game) {
        for (int step = 0; step < 40; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Phasing research stack did not settle");
    }

    private Board board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("phasing-context-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Phasing context research"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card creature = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        return new Board(game, actor, creature);
    }

    private record Board(Game game, Player actor, Card creature) { }
}
