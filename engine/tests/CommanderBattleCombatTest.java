package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
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

/** Native Battle combat and Siege defeat witnesses. Legal setup and action choices are supplied. */
public class CommanderBattleCombatTest extends AITest {
    @Test public void nativeDefenderEnumerationExcludesOnlyTheProtector() {
        Board board = board(); Player protector = board.battle.getProtectingPlayer();
        assertTrue(CombatUtil.getAllPossibleDefenders(board.actor).contains(board.battle));
        assertFalse(CombatUtil.getAllPossibleDefenders(protector).contains(board.battle));
        Player third = board.actor.getOpponents().stream().filter(player -> player != protector).findFirst().orElseThrow();
        assertTrue(CombatUtil.getAllPossibleDefenders(third).contains(board.battle));
    }

    @Test public void copiedDeclaredAttackRetainsTheBattleAndItsDefendingPlayer() {
        Board board = board(); Attack attack = attack(board, "Grizzly Bears");
        Player protector = board.battle.getProtectingPlayer();
        assertSame(attack.combat.getDefenderPlayerByAttacker(attack.attacker), protector);
        GameCopier copier = new GameCopier(board.game).withStack(true); Game game = copier.makeCopy(null, board.actor);
        assertSame(game.getCombat().getDefenderByAttacker(copier.find(attack.attacker)), copier.find(board.battle));
        assertSame(game.getCombat().getDefenderPlayerByAttacker(copier.find(attack.attacker)), copier.find(protector));
        assertTrue(game.getCombat().isUnblocked(copier.find(attack.attacker)));
        assertFalse(CombatUtil.getAllPossibleDefenders(copier.find(protector)).contains(copier.find(board.battle)));
    }

    @Test public void unblockedCombatRemovesDefenseWithoutReducingAnyPlayersLife() {
        Board board = board(); Attack attack = attack(board, "Grizzly Bears");
        List<Integer> life = board.game.getPlayers().stream().map(Player::getLife).toList();
        damage(board.game, attack.combat, board.actor); settle(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 1);
        assertTrue(board.battle.isInZone(ZoneType.Battlefield));
        assertEquals(board.game.getPlayers().stream().map(Player::getLife).toList(), life);
    }

    @Test public void copiedCombatDamageLeavesTheOriginalDefenseUntouched() {
        Board board = board(); Attack attack = attack(board, "Grizzly Bears");
        GameCopier copier = new GameCopier(board.game).withStack(true); Game game = copier.makeCopy(null, board.actor);
        damage(game, game.getCombat(), copier.find(board.actor)); settle(game);
        assertEquals(copier.find(board.battle).getCounters(CounterEnumType.DEFENSE), 1);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 3);
        assertTrue(attack.combat.isUnblocked(attack.attacker));
    }

    @Test public void lethalNativeCombatDefeatsTheSiegeAndCastsItsBackFace() {
        Board board = board(); Attack attack = attack(board, "Colossal Dreadmaw");
        List<Integer> life = board.game.getPlayers().stream().map(Player::getLife).toList();
        damage(board.game, attack.combat, board.actor); settle(board.game);
        assertArray(board.game, board.battle);
        assertEquals(board.game.getPlayers().stream().map(Player::getLife).toList(), life);
    }

    @Test public void paidFogPreventsBattleDamageAndTheDefeatTrigger() {
        Board board = board(); Attack attack = attack(board, "Colossal Dreadmaw");
        Player responder = board.battle.getProtectingPlayer(); addCardToZone("Forest", responder, ZoneType.Battlefield);
        SpellAbility fog = addCardToZone("Fog", responder, ZoneType.Hand).getFirstSpellAbility();
        fog.setActivatingPlayer(responder); assertTrue(ComputerUtil.handlePlayingSpellAbility(responder, fog, null));
        settle(board.game); assertEquals(fog.getPayingMana().size(), 1);
        damage(board.game, attack.combat, board.actor); settle(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 3);
        assertTrue(board.battle.isInZone(ZoneType.Battlefield)); assertTrue(board.game.getStack().isEmpty());
    }

    @Test public void nativeHexmageSacrificeDefeatsTheBattleAndCastsTransformed() {
        Board board = board(); Activation activation = hexmage(board); settle(board.game);
        assertTrue(activation.sacrificed.isInZone(ZoneType.Graveyard));
        assertArray(board.game, board.battle); assertEquals(board.actor.getSpellsCastThisTurn(), 2);
    }

    @Test public void copiedPaidHexmageActivationResolvesOnlyInTheCopiedWorld() {
        Board board = board(); Activation activation = hexmage(board);
        GameCopier copier = new GameCopier(board.game).withStack(true); Game game = copier.makeCopy(null, board.actor);
        assertEquals(game.getStack().size(), 1); settle(game);
        assertArray(game, copier.find(board.battle));
        assertTrue(copier.find(activation.sacrificed).isInZone(ZoneType.Graveyard));
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 3);
        assertTrue(board.battle.isInZone(ZoneType.Battlefield)); assertEquals(board.game.getStack().size(), 1);
    }

    @Test public void stiflingTheDefeatTriggerPutsTheZeroDefenseBattleInTheGraveyard() {
        Board board = board(); hexmage(board); board.game.getStack().resolveStack(); queue(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 0);
        assertTrue(board.battle.isInZone(ZoneType.Battlefield));
        assertTrue(board.game.getStack().peekAbility().isTrigger());
        Player responder = board.battle.getProtectingPlayer(); addCardToZone("Island", responder, ZoneType.Battlefield);
        SpellAbility stifle = addCardToZone("Stifle", responder, ZoneType.Hand).getFirstSpellAbility();
        stifle.setActivatingPlayer(responder); stifle.getTargets().add(board.game.getStack().peekAbility());
        assertTrue(ComputerUtil.handlePlayingSpellAbility(responder, stifle, null)); settle(board.game);
        Card card = board.game.getCardState(board.battle);
        assertTrue(card.isInZone(ZoneType.Graveyard)); assertEquals(card.getName(), "Invasion of Gobakhan");
        assertEquals(stifle.getPayingMana().size(), 1); assertEquals(board.actor.getSpellsCastThisTurn(), 1);
    }

    @Test public void destroyingTheBattleBeforeItsDefeatTriggerPreventsTheBackFaceCast() {
        Board board = board(); hexmage(board); board.game.getStack().resolveStack(); queue(board.game);
        assertTrue(board.game.getStack().peekAbility().isTrigger());
        Player responder = board.battle.getProtectingPlayer();
        for (int index=0; index<3; index++) addCardToZone("Forest", responder, ZoneType.Battlefield);
        SpellAbility response = addCardToZone("Beast Within", responder, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(responder); response.getTargets().add(board.battle);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(responder, response, null)); settle(board.game);
        Card card = board.game.getCardState(board.battle);
        assertTrue(card.isInZone(ZoneType.Graveyard)); assertEquals(card.getName(), "Invasion of Gobakhan");
        assertEquals(response.getPayingMana().size(), 3); assertEquals(board.actor.getSpellsCastThisTurn(), 1);
        assertTrue(board.actor.getCreaturesInPlay().stream().anyMatch(Card::isToken));
    }

    /** A matured attacker and unblocked declaration are supplied legal combat preconditions. */
    private Attack attack(Board board, String name) {
        Card attacker=addCardToZone(name, board.actor, ZoneType.Battlefield); attacker.setSickness(false);
        board.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, board.actor, 3);
        assertTrue(CombatUtil.getAllPossibleDefenders(board.actor).contains(board.battle));
        assertTrue(CombatUtil.canAttack(attacker, board.battle));
        Combat combat=new Combat(board.actor); board.game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker, board.battle); attacker.setTapped(true);
        CombatUtil.checkDeclaredAttacker(board.game, attacker, combat, true); combat.setBlocked(attacker, false);
        return new Attack(attacker,combat);
    }

    private void damage(Game game, Combat combat, Player actor) {
        // The default development overload ends combat; preserve the declared combat here.
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, actor, false, 3);
        assertTrue(combat.assignCombatDamage(false)); combat.dealAssignedDamage();
    }

    private Activation hexmage(Board board) {
        Card card=addCardToZone("Vampire Hexmage", board.actor, ZoneType.Battlefield);
        SpellAbility action=card.getSpellAbilities().stream()
                .filter(ability -> ability.getApi()==forge.game.ability.ApiType.RemoveCounter).findFirst().orElseThrow();
        action.setActivatingPlayer(board.actor); action.getTargets().add(board.battle);
        assertTrue(action.canPlay()); assertTrue(ComputerUtil.handlePlayingSpellAbility(board.actor, action, null));
        Card sacrificed=board.game.getCardState(card); assertTrue(sacrificed.isInZone(ZoneType.Graveyard));
        return new Activation(sacrificed,action);
    }

    private void queue(Game game) {
        game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void assertArray(Game game, Card original) {
        Card card=game.getCardState(original); assertTrue(card.isInZone(ZoneType.Battlefield));
        assertEquals(card.getName(), "Lightshield Array"); assertFalse(card.isBattle());
        assertTrue(card.isEnchantment()); assertEquals(card.getCounters(CounterEnumType.DEFENSE), 0);
    }

    private record Attack(Card attacker, Combat combat) { }
    private record Activation(Card sacrificed, SpellAbility action) { }

    private Board board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("battle-protector-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Battle combat research"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int index = 0; index < 2; index++) addCardToZone("Plains", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        Card source = addCardToZone("Invasion of Gobakhan", actor, ZoneType.Hand);
        SpellAbility spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null)); settle(game);
        assertEquals(actor.getSpellsCastThisTurn(), 1);
        assertEquals(spell.getPayingMana().size(), 2);
        assertTrue(actor.getLandsInPlay().stream().allMatch(Card::isTapped));
        Card battle = game.getCardState(spell.getHostCard());
        assertTrue(battle.isInZone(ZoneType.Battlefield));
        assertNotNull(battle.getProtectingPlayer());
        return new Board(game, actor, battle);
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
        throw new AssertionError("Battle research stack did not settle");
    }

    private record Board(Game game, Player actor, Card battle) { }
}
