package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/** Scheduled turns and phases retain their players, order and obligations. */
public class TurnScheduleGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("schedule" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Schedule fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 4; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private void pay(Game game, SpellAbility ability, Player owner, int mana) {
        ability.setActivatingPlayer(owner);
        assertTrue(ability.canPlay(), "Native action admission");
        assertTrue(ComputerUtil.playStack(ability, owner, game), "Native action payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getAction().checkStateEffects(true);
    }

    private Card cast(Game game, String name, Player owner, int mana) {
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        pay(game, card.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        return find(game, card.getId());
    }

    private Card find(Game game, int identity) {
        return game.getCardsInGame().stream().filter(card -> card.getId() == identity)
                .findFirst().orElseThrow();
    }

    private SpellAbility activated(Card card, ApiType api, Player owner) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    private Game snapshot(Game game) {
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(),
                "All callbacks in this fixed witness have explicit mappings: "
                        + copier.getUnmappedSnapshotCommands());
        return copy;
    }

    private void triggers(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }



    /** Run native phase hooks, not individual counter-reset methods. No priority is offered. */
    private void nextTurn(Game game) {
        int previousTurn = game.getPhaseHandler().getTurn();
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)), "Native turn advance");
        settle(game);
        assertEquals(game.getPhaseHandler().getTurn(), previousTurn + 1);
        assertEquals(game.getPhaseHandler().getPhase(), PhaseType.MAIN1);
    }

    private void settle(Game game) {
        triggers(game);
        int guard = 0;
        while (!game.getStack().isEmpty()) {
            assertTrue(++guard < 40, "Bounded fixture trigger resolution");
            game.getStack().resolveStack();
            triggers(game);
        }
    }

    private void round(Game game, int ownerId) {
        for (int seat = 0; seat < 4; seat++) {
            nextTurn(game);
        }
        assertEquals(game.getPhaseHandler().getPlayerTurn().getId(), ownerId);
    }



    private void advance(Game game, PhaseType phase) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(phase, () -> settle(game)));
        settle(game);
    }

    private Player prepare(Game game, String land, int count) {
        Player owner = game.getPlayers().get(0);
        for (int i = 0; i < count; i++) addCardToZone(land, owner, ZoneType.Battlefield);
        return owner;
    }

    private void targetedTurn(Game game, String name, Player owner, Player target, int mana) {
        Card spell = addCardToZone(name, owner, ZoneType.Hand);
        spell.getFirstSpellAbility().getTargets().add(target);
        pay(game, spell.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        settle(game);
    }

    @Test
    public void timeWarpRetainsItsTargetAndTheNormalTurnAfterward() {
        Game game = pod();
        Player owner = prepare(game, "Island", 5);
        Player target = game.getPlayers().get(2);
        targetedTurn(game, "Time Warp", owner, target, 5);
        Game copy = snapshot(game);
        assertEquals(copy.getPhaseHandler().getNextTurn().getId(), target.getId());
        nextTurn(copy);
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), target.getId());
        assertTrue(copy.getPhaseHandler().getPlayerTurn().isExtraTurn());
        nextTurn(copy);
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), game.getPlayers().get(1).getId());
        assertFalse(copy.getPhaseHandler().getPlayerTurn().isExtraTurn());
        assertEquals(game.getPhaseHandler().getNextTurn(), target);
        assertEquals(game.getPhaseHandler().getTurn(), 1);
    }

    @Test
    public void timeStretchRetainsBothExtraTurnsAndIndependentCounts() {
        Game game = pod();
        Player owner = prepare(game, "Island", 10);
        targetedTurn(game, "Time Stretch", owner, owner, 10);
        Game copy = snapshot(game);
        assertEquals(copy.getPlayer(owner.getId()).getExtraTurnCount(), 2);
        for (int remaining = 1; remaining >= 0; remaining--) {
            nextTurn(copy);
            assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), owner.getId());
            assertTrue(copy.getPlayer(owner.getId()).isExtraTurn());
            assertEquals(copy.getPlayer(owner.getId()).getExtraTurnCount(), remaining);
        }
        nextTurn(copy);
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), game.getPlayers().get(1).getId());
        assertEquals(owner.getExtraTurnCount(), 2);
    }

    @Test
    public void savorTheMomentSkipsOnlyTheScheduledUntapStep() {
        Game game = pod();
        Player owner = prepare(game, "Island", 3);
        cast(game, "Savor the Moment", owner, 3);
        Game copy = snapshot(game);
        nextTurn(copy);
        Player copiedOwner = copy.getPlayer(owner.getId());
        assertEquals(copy.getPhaseHandler().getPlayerTurn(), copiedOwner);
        assertTrue(copiedOwner.isExtraTurn());
        assertEquals(copiedOwner.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count(), 3L);
        for (int i = 0; i < 4; i++) nextTurn(copy);
        assertEquals(copy.getPhaseHandler().getPlayerTurn(), copiedOwner);
        assertEquals(copiedOwner.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count(), 0L);
        assertEquals(owner.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count(), 3L);
    }

    @Test
    public void finalFortuneRetainsTheExtraTurnsEndStepLoss() {
        Game game = pod();
        Player owner = prepare(game, "Mountain", 2);
        cast(game, "Final Fortune", owner, 2);
        Game copy = snapshot(game);
        nextTurn(copy);
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), owner.getId());
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        assertTrue(copy.getPlayer(owner.getId()).hasLost());
        assertEquals(copy.getPlayers().size(), 3);
        assertFalse(owner.hasLost());
        assertEquals(game.getPlayers().size(), 4);
    }

    @Test
    public void relentlessAssaultRetainsTheInsertedCombatAndMainPhase() {
        Game game = pod();
        Player owner = prepare(game, "Mountain", 4);
        cast(game, "Relentless Assault", owner, 4);
        Game copy = snapshot(game);
        assertTrue(copy.getPhaseHandler().hasExtraPhaseAfter(PhaseType.MAIN1, PhaseType.COMBAT_BEGIN));
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(copy)));
        settle(copy);
        assertEquals(copy.getPhaseHandler().getNumCombat(), 1);
        assertTrue(copy.getPhaseHandler().hasExtraPhaseAfter(PhaseType.MAIN2, PhaseType.COMBAT_BEGIN));
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        assertEquals(copy.getPhaseHandler().getNumCombat(), 2);
        assertTrue(game.getPhaseHandler().hasExtraPhaseAfter(PhaseType.MAIN1, PhaseType.COMBAT_BEGIN));
        assertEquals(game.getPhaseHandler().getNumCombat(), 0);
    }

    @Test
    public void midExtraTurnSnapshotKeepsTheCurrentTurnFlagAndPhaseCounters() {
        Game game = pod();
        Player owner = prepare(game, "Island", 3);
        cast(game, "Savor the Moment", owner, 3);
        nextTurn(game);
        assertTrue(owner.isExtraTurn());
        assertEquals(game.getPhaseHandler().getNumUpkeep(), 1);
        Game copy = snapshot(game);
        assertTrue(copy.getPlayer(owner.getId()).isExtraTurn());
        assertEquals(copy.getPhaseHandler().getNumUpkeep(), 1);
        assertEquals(copy.getPhaseHandler().getPreviousPlayerTurn().getId(), owner.getId());
        assertEquals(copy.getPhaseHandler().getNextTurn().getId(), game.getPhaseHandler().getNextTurn().getId());
    }
    @Test
    public void laterExtraTurnEffectsResolveBeforeEarlierOnes() {
        Game game = pod();
        Player owner = prepare(game, "Island", 10);
        Player earlier = game.getPlayers().get(1);
        Player later = game.getPlayers().get(2);
        targetedTurn(game, "Time Warp", owner, earlier, 5);
        targetedTurn(game, "Time Warp", owner, later, 5);
        Game copy = snapshot(game);
        for (int identity : new int[] {later.getId(), earlier.getId(), earlier.getId()}) {
            nextTurn(copy);
            assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), identity);
        }
        assertFalse(copy.getPhaseHandler().getPlayerTurn().isExtraTurn());
        assertEquals(game.getPhaseHandler().getNextTurn(), later);
    }

    @Test
    public void multipleAssaultsKeepEveryInsertedCombatAndMain() {
        Game game = pod();
        Player owner = prepare(game, "Mountain", 8);
        cast(game, "Relentless Assault", owner, 4);
        cast(game, "Relentless Assault", owner, 4);
        Game copy = snapshot(game);
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        assertEquals(copy.getPhaseHandler().getNumCombat(), 3);
        assertEquals(copy.getPhaseHandler().getNumMain(), 3);
        assertEquals(game.getPhaseHandler().getNumCombat(), 0);
    }

    @Test
    public void moraugPhaseEntryUntapsOnlyTheCopiedPlayersCreatures() {
        Game game = pod();
        Player owner = prepare(game, "Mountain", 6);
        Card moraug = cast(game, "Moraug, Fury of Akoum", owner, 6);
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        bear.setTapped(true);
        Card land = addCardToZone("Mountain", owner, ZoneType.Hand);
        SpellAbility play = land.getAllPossibleAbilities(owner, false).stream()
                .filter(SpellAbility::isLandAbility).findFirst().orElseThrow();
        play.setActivatingPlayer(owner);
        assertTrue(play.canPlay());
        assertTrue(ComputerUtil.playNoStack(owner, play, game, false));
        settle(game);
        Game copy = snapshot(game);
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.COMBAT_BEGIN, () -> settle(copy)));
        settle(copy);
        assertFalse(find(copy, bear.getId()).isTapped());
        assertTrue(bear.isTapped());
        assertEquals(find(copy, moraug.getId()).getController(), copy.getPlayer(owner.getId()));
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN, () -> settle(copy)));
        assertEquals(copy.getPhaseHandler().getNumCombat(), 2);
    }

    @Test
    public void consumingOneCopyDoesNotChangeAnotherCopiesTurnStack() {
        Game game = pod();
        Player owner = prepare(game, "Island", 5);
        targetedTurn(game, "Time Warp", owner, owner, 5);
        Game first = snapshot(game);
        Game second = snapshot(game);
        nextTurn(first);
        nextTurn(first);
        assertEquals(first.getPhaseHandler().getPlayerTurn().getId(), game.getPlayers().get(1).getId());
        assertEquals(second.getPhaseHandler().getNextTurn().getId(), owner.getId());
        nextTurn(second);
        assertEquals(second.getPhaseHandler().getPlayerTurn().getId(), owner.getId());
        assertTrue(second.getPlayer(owner.getId()).isExtraTurn());
        assertEquals(owner.getExtraTurnCount(), 1);
        assertEquals(game.getPhaseHandler().getTurn(), 1);
    }

}
