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

/** Condition-bound control and effect cleanup must remain independent in snapshots. */
public class ConditionalGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("condition" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Condition fixture"));
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

    private Player prepare(Game game, String... lands) {
        Player owner = game.getPlayers().get(0);
        for (String land : lands) addCardToZone(land, owner, ZoneType.Battlefield);
        return owner;
    }

    private Card targeted(Game game, String name, Player owner, Card target, int mana) {
        Card spell = addCardToZone(name, owner, ZoneType.Hand);
        spell.getFirstSpellAbility().getTargets().add(target);
        pay(game, spell.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        settle(game);
        return find(game, spell.getId());
    }

    @org.testng.annotations.DataProvider(name = "oldManConditions")
    public Object[][] oldManConditions() {
        return new Object[][] {{"growTarget"}, {"shrinkSource"}, {"untap"}};
    }

    @Test(dataProvider = "oldManConditions")
    public void oldManReturnsControlWhenItsOwnConditionEnds(String condition) {
        Game game = pod();
        Player owner = prepare(game, "Island", "Island", "Island");
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Forest", opponent, ZoneType.Battlefield);
        addCardToZone("Swamp", opponent, ZoneType.Battlefield);
        Card source = cast(game, "Old Man of the Sea", owner, 3);
        round(game, owner.getId());
        Card bear = addCardToZone("Grizzly Bears", opponent, ZoneType.Battlefield);
        SpellAbility steal = activated(source, ApiType.GainControl, owner);
        steal.getTargets().add(bear);
        pay(game, steal, owner, 0);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(bear.getController(), owner);
        assertEquals(source.getStaticCommandList().size(), 1);
        Game copy = snapshot(game);
        Card copiedBear = find(copy, bear.getId());
        Card copiedSource = find(copy, source.getId());
        Player copiedOpponent = copy.getPlayer(opponent.getId());
        if (condition.equals("growTarget")) {
            targeted(copy, "Giant Growth", copiedOpponent, copiedBear, 1);
            assertEquals(copiedBear.getNetPower(), 5);
        } else if (condition.equals("shrinkSource")) {
            targeted(copy, "Disfigure", copiedOpponent, copiedSource, 1);
            assertEquals(copiedSource.getNetPower(), 0);
        } else {
            assertTrue(copiedSource.untap());
            settle(copy);
        }
        assertEquals(copiedBear.getController(), copiedOpponent);
        assertEquals(copiedBear.getZone().getPlayer(), copiedOpponent);
        assertEquals(bear.getController(), owner);
        assertTrue(source.isTapped());
    }

    @org.testng.annotations.DataProvider(name = "auraRemoval")
    public Object[][] auraRemoval() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "auraRemoval")
    public void matriarchControlEndsWhenTheTargetIsNoLongerEnchanted(boolean blink) {
        checkMatriarch(blink ? "Ephemerate" : "Disenchant");
    }

    @Test
    public void cloudshiftCreatesNewControlRatherThanRestoringTheOldCondition() {
        checkMatriarch("Cloudshift");
    }

    private void checkMatriarch(String removal) {
        Game game = pod();
        Player owner = prepare(game, "Island", "Island", "Island", "Island",
                "Forest", "Plains", "Plains");
        Player opponent = game.getPlayers().get(1);
        Card source = cast(game, "Rootwater Matriarch", owner, 4);
        round(game, owner.getId());
        Card bear = addCardToZone("Grizzly Bears", opponent, ZoneType.Battlefield);
        Card aura = targeted(game, "Rancor", owner, bear, 1);
        assertTrue(source.isInPlay());
        assertTrue(bear.isEnchanted());
        SpellAbility steal = activated(source, ApiType.GainControl, owner);
        steal.getTargets().add(bear);
        pay(game, steal, owner, 0);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(bear.getController(), owner);
        Game copy = snapshot(game);
        if (!removal.equals("Disenchant")) {
            targeted(copy, removal, copy.getPlayer(owner.getId()), find(copy, bear.getId()), 1);
        } else {
            targeted(copy, "Disenchant", copy.getPlayer(owner.getId()), find(copy, aura.getId()), 2);
        }
        Card copiedBear = find(copy, bear.getId());
        assertEquals(copiedBear.getController(), copy.getPlayer(
                removal.equals("Cloudshift") ? owner.getId() : opponent.getId()));
        assertEquals(copiedBear.getZone().getZoneType(), ZoneType.Battlefield);
        assertFalse(copiedBear.isEnchanted());
        assertEquals(bear.getController(), owner);
        assertTrue(bear.isEnchanted());
    }

    private SpellAbility bolt(Game game, Player owner, Player target) {
        SpellAbility ability = addCardToZone("Lightning Bolt", owner, ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(owner);
        ability.getTargets().add(target);
        return ability;
    }

    @Test(dataProvider = "auraRemoval")
    public void peaceTalksExpiresOnlyAfterBothTurns(boolean afterFirstEnd) {
        Game game = pod();
        Player owner = prepare(game, "Plains", "Plains");
        cast(game, "Peace Talks", owner, 2);
        if (afterFirstEnd) nextTurn(game);
        Game copy = snapshot(game);
        Player copiedOwner = copy.getPlayer(owner.getId());
        SpellAbility probe = bolt(copy, copy.getPlayers().get(2), copiedOwner);
        assertFalse(probe.canTarget(copiedOwner));
        if (!afterFirstEnd) {
            nextTurn(copy);
            assertFalse(probe.canTarget(copiedOwner));
        }
        nextTurn(copy);
        assertTrue(probe.canTarget(copiedOwner));
        assertFalse(bolt(game, game.getPlayers().get(2), owner).canTarget(owner));
    }

    @Test
    public void silenceExpirationRestoresCastingInOnlyTheCopiedGame() {
        Game game = pod();
        Player owner = prepare(game, "Plains");
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Mountain", opponent, ZoneType.Battlefield);
        cast(game, "Silence", owner, 1);
        Game copy = snapshot(game);
        SpellAbility probe = bolt(copy, copy.getPlayer(opponent.getId()), copy.getPlayer(owner.getId()));
        Player copiedOpponent = copy.getPlayer(opponent.getId());
        assertFalse(probe.checkRestrictions(copiedOpponent));
        assertFalse(ComputerUtil.playStack(probe, copiedOpponent, copy));
        nextTurn(copy);
        assertTrue(probe.checkRestrictions(copiedOpponent));
        pay(copy, probe, copiedOpponent, 1);
        copy.getStack().resolveStack();
        settle(copy);
        assertEquals(copy.getPlayer(owner.getId()).getLife(), 37);
        assertEquals(owner.getLife(), 40);
        SpellAbility sourceProbe = bolt(game, opponent, owner);
        assertFalse(sourceProbe.checkRestrictions(opponent));
        assertFalse(ComputerUtil.playStack(sourceProbe, opponent, game));
    }

    private static final class MarkNumberCommand implements forge.game.SnapshotCommand {
        private static final long serialVersionUID = 1L;
        private final Card card;
        private final int number;
        private MarkNumberCommand(Card card) { this(card, 7); }
        private MarkNumberCommand(Card card, int number) { this.card = card; this.number = number; }
        @Override public void run() { card.setChosenNumber(number); }
        @Override public forge.game.SnapshotCommand copyForSnapshot(
                forge.game.spellability.SpellAbilitySnapshot context) {
            return new MarkNumberCommand(context.map(card), number);
        }
    }

    @Test
    public void mappedStateBasedCleanupRunsInOnlyTheNewWorld() {
        Game game = pod();
        Card card = addCardToZone("Grizzly Bears", game.getPlayers().get(0), ZoneType.Battlefield);
        card.setChosenNumber(0);
        game.addSBACheckedCommand(new MarkNumberCommand(card));
        Game copy = snapshot(game);
        assertEquals(find(copy, card.getId()).getChosenNumber(), Integer.valueOf(7));
        assertEquals(card.getChosenNumber(), Integer.valueOf(0));
        assertEquals(game.sbaCheckedCommandList.size(), 1);
        assertTrue(copy.sbaCheckedCommandList.isEmpty());
    }

    @Test
    public void unknownStateBasedCleanupIsOmittedAndReportedWithoutRunningTheSource() {
        Game game = pod();
        Card card = addCardToZone("Grizzly Bears", game.getPlayers().get(0), ZoneType.Battlefield);
        card.setChosenNumber(0);
        game.addSBACheckedCommand(() -> card.setChosenNumber(9));
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().stream().anyMatch(s -> s.startsWith("game.sba:")));
        assertEquals(card.getChosenNumber(), Integer.valueOf(0));
        assertEquals(find(copy, card.getId()).getChosenNumber(), Integer.valueOf(0));
        game.runSBACheckedCommands();
        assertEquals(card.getChosenNumber(), Integer.valueOf(9));
        assertEquals(find(copy, card.getId()).getChosenNumber(), Integer.valueOf(0));
    }
    @Test
    public void conditionalArraysAreIndependentAndShareOnlyTheirMappedCallbackIdentity() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card host = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        Card target = addCardToZone("Runeclaw Bear", owner, ZoneType.Battlefield);
        target.setChosenNumber(0);
        MarkNumberCommand command = new MarkNumberCommand(target);
        host.addLeavesPlayCommand(command);
        host.addStaticCommandList(new Object[] {"Count$CardPower", "GT100", target, command});
        Game copy = snapshot(game);
        Card copiedHost = find(copy, host.getId());
        Card copiedTarget = find(copy, target.getId());
        assertEquals(copiedHost.getStaticCommandList().size(), 1);
        Object[] condition = copiedHost.getStaticCommandList().get(0);
        assertNotSame(condition, host.getStaticCommandList().get(0));
        assertSame(condition[2], copiedTarget);
        assertSame(condition[3], copiedHost.getLeavesPlayCommands().get(0));
        assertNotSame(condition[3], command);
        condition[1] = "GT0";
        copy.getAction().checkStateEffects(true);
        assertEquals(copiedTarget.getChosenNumber(), Integer.valueOf(7));
        assertEquals(target.getChosenNumber(), Integer.valueOf(0));
        assertTrue(copiedHost.getStaticCommandList().isEmpty());
        assertEquals(host.getStaticCommandList().get(0)[1], "GT100");
    }

    @Test
    public void unknownConditionalCallbacksAreReportedAndNeverRetained() {
        Game game = pod();
        Card host = addCardToZone("Grizzly Bears", game.getPlayers().get(0), ZoneType.Battlefield);
        host.setChosenNumber(0);
        forge.GameCommand unknown = () -> host.setChosenNumber(9);
        host.addStaticCommandList(new Object[] {"Count$CardPower", "GT100", host, unknown});
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().stream().anyMatch(s -> s.startsWith("card.static:")));
        assertTrue(find(copy, host.getId()).getStaticCommandList().isEmpty());
        assertEquals(host.getChosenNumber(), Integer.valueOf(0));
        unknown.run();
        assertEquals(host.getChosenNumber(), Integer.valueOf(9));
        assertEquals(find(copy, host.getId()).getChosenNumber(), Integer.valueOf(0));
    }

    private static final class DurationFixture extends forge.game.ability.SpellAbilityEffect {
        @Override protected String getStackDescription(SpellAbility sa) { return "Trusted duration fixture"; }
        @Override public void resolve(SpellAbility sa) { }
        private static void schedule(SpellAbility ability, forge.GameCommand command) {
            addUntilCommand(ability, command, "ThisTurnAndNextTurn", ability.getActivatingPlayer());
        }
    }

    @Test
    public void unmappedNestedTwoTurnCallbacksCannotHideInsideAMappedWrapper() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card host = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        host.setChosenNumber(0);
        SpellAbility ability = host.getFirstSpellAbility();
        ability.setActivatingPlayer(owner);
        DurationFixture.schedule(ability, () -> host.setChosenNumber(9));
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().stream()
                .anyMatch(s -> s.startsWith("scheduled.followingEndStep:")));
        nextTurn(copy);
        nextTurn(copy);
        assertEquals(find(copy, host.getId()).getChosenNumber(), Integer.valueOf(0));
        assertEquals(host.getChosenNumber(), Integer.valueOf(0));
        nextTurn(game);
        nextTurn(game);
        assertEquals(host.getChosenNumber(), Integer.valueOf(9));
        assertEquals(find(copy, host.getId()).getChosenNumber(), Integer.valueOf(0));
    }

    @Test
    public void stateBasedCleanupRetainsInsertionOrderAndDuplicateCommands() {
        Game game = pod();
        Card host = addCardToZone("Grizzly Bears", game.getPlayers().get(0), ZoneType.Battlefield);
        host.setChosenNumber(0);
        MarkNumberCommand first = new MarkNumberCommand(host, 1);
        game.addSBACheckedCommand(first);
        game.addSBACheckedCommand(new MarkNumberCommand(host, 2));
        game.addSBACheckedCommand(first);
        Game copy = snapshot(game);
        assertEquals(find(copy, host.getId()).getChosenNumber(), Integer.valueOf(1));
        assertEquals(host.getChosenNumber(), Integer.valueOf(0));
        assertEquals(game.sbaCheckedCommandList.size(), 3);
        assertSame(game.sbaCheckedCommandList.get(0), game.sbaCheckedCommandList.get(2));
        assertTrue(copy.sbaCheckedCommandList.isEmpty());
    }

}
