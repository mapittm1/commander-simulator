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


    @Test public void copiedPendingDefeatRetainsTheBattleUntilItsTriggerResolves() {
        Board board=board(); pendingDefeat(board);
        GameCopier copier=new GameCopier(board.game).withStack(true); Game game=copier.makeCopy(null,board.actor);
        assertEquals(game.getStack().size(),1); assertTrue(copier.find(board.battle).isInZone(ZoneType.Battlefield));
        assertEquals(copier.find(board.battle).getCounters(CounterEnumType.DEFENSE),0);
        settle(game); assertArray(game,copier.find(board.battle));
        assertTrue(board.battle.isInZone(ZoneType.Battlefield)); assertEquals(board.game.getStack().size(),1);
        assertEquals(board.actor.getSpellsCastThisTurn(),1);
    }

    @Test public void copiedCounterspellTargetsTheCopiedDefeatTrigger() {
        Board board=board(); pendingDefeat(board); Player responder=board.battle.getProtectingPlayer();
        addCardToZone("Island",responder,ZoneType.Battlefield);
        SpellAbility stifle=addCardToZone("Stifle",responder,ZoneType.Hand).getFirstSpellAbility();
        stifle.setActivatingPlayer(responder);stifle.getTargets().add(board.game.getStack().peekAbility());
        assertTrue(ComputerUtil.handlePlayingSpellAbility(responder,stifle,null));
        GameCopier copier=new GameCopier(board.game).withStack(true);Game game=copier.makeCopy(null,board.actor);
        assertEquals(game.getStack().size(),2); settle(game);
        Card card=game.getCardState(copier.find(board.battle));assertTrue(card.isInZone(ZoneType.Graveyard));
        assertEquals(card.getName(),"Invasion of Gobakhan");assertEquals(copier.find(board.actor).getSpellsCastThisTurn(),1);
        assertEquals(board.game.getStack().size(),2);assertTrue(board.battle.isInZone(ZoneType.Battlefield));
    }

    @Test public void onlyTheProtectorMayBlockAnAttackOnTheBattle() {
        Board board=board();Attack attack=attack(board,"Grizzly Bears");
        Player protector=board.battle.getProtectingPlayer();
        Card defender=addCardToZone("Grizzly Bears",protector,ZoneType.Battlefield);
        Card own=addCardToZone("Grizzly Bears",board.actor,ZoneType.Battlefield);
        Player third=board.actor.getOpponents().stream().filter(player->player!=protector).findFirst().orElseThrow();
        Card other=addCardToZone("Grizzly Bears",third,ZoneType.Battlefield);
        assertTrue(CombatUtil.canBlock(attack.attacker,defender,attack.combat));
        assertFalse(CombatUtil.canBlock(attack.attacker,own,attack.combat));
        assertFalse(CombatUtil.canBlock(attack.attacker,other,attack.combat));
    }

    @Test public void blockedCombatKillsBothCreaturesWithoutDamagingTheBattle() {
        Board board=board();Attack attack=attack(board,"Grizzly Bears");Card blocker=block(board,attack,"Grizzly Bears");
        damage(board.game,attack.combat,board.actor);settle(board.game);
        assertTrue(board.game.getCardState(attack.attacker).isInZone(ZoneType.Graveyard));
        assertTrue(board.game.getCardState(blocker).isInZone(ZoneType.Graveyard));
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),3);
    }

    @Test public void copiedBlockedCombatLeavesBothOriginalCreaturesAlive() {
        Board board=board();Attack attack=attack(board,"Grizzly Bears");Card blocker=block(board,attack,"Grizzly Bears");
        GameCopier copier=new GameCopier(board.game).withStack(true);Game game=copier.makeCopy(null,board.actor);
        assertTrue(game.getCombat().isBlocked(copier.find(attack.attacker)));
        damage(game,game.getCombat(),copier.find(board.actor));settle(game);
        assertTrue(game.getCardState(copier.find(attack.attacker)).isInZone(ZoneType.Graveyard));
        assertTrue(game.getCardState(copier.find(blocker)).isInZone(ZoneType.Graveyard));
        assertTrue(attack.attacker.isInZone(ZoneType.Battlefield));assertTrue(blocker.isInZone(ZoneType.Battlefield));
        assertEquals(copier.find(board.battle).getCounters(CounterEnumType.DEFENSE),3);
    }

    @Test public void firstStrikeBlockerKillsTheAttackerBeforeNormalDamage() {
        Board board=board();Attack attack=attack(board,"Grizzly Bears");Card blocker=block(board,attack,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);
        assertTrue(board.game.getCardState(attack.attacker).isInZone(ZoneType.Graveyard));
        assertTrue(blocker.isInZone(ZoneType.Battlefield));assertEquals(blocker.getDamage(),0);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),3);
        attack.combat.removeAbsentCombatants();assertFalse(attack.combat.assignCombatDamage(false));
    }

    @Test public void trampleThroughABlockerDefeatsOnlyTheBattle() {
        Board board=board();Attack attack=attack(board,"Colossal Dreadmaw");Card blocker=block(board,attack,"Grizzly Bears");
        List<Integer> life=board.game.getPlayers().stream().map(Player::getLife).toList();
        damage(board.game,attack.combat,board.actor);settle(board.game);assertArray(board.game,board.battle);
        assertTrue(board.game.getCardState(blocker).isInZone(ZoneType.Graveyard));assertTrue(attack.attacker.isInZone(ZoneType.Battlefield));
        assertEquals(attack.attacker.getDamage(),2);assertEquals(board.game.getPlayers().stream().map(Player::getLife).toList(),life);
    }

    @Test public void copiedTrampleCombatLeavesOriginalDefenseAndBlockerUntouched() {
        Board board=board();Attack attack=attack(board,"Colossal Dreadmaw");Card blocker=block(board,attack,"Grizzly Bears");
        GameCopier copier=new GameCopier(board.game).withStack(true);Game game=copier.makeCopy(null,board.actor);
        damage(game,game.getCombat(),copier.find(board.actor));settle(game);assertArray(game,copier.find(board.battle));
        assertTrue(game.getCardState(copier.find(blocker)).isInZone(ZoneType.Graveyard));
        assertTrue(blocker.isInZone(ZoneType.Battlefield));assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),3);
        assertEquals(attack.attacker.getDamage(),0);
    }

    @Test public void nativeStrikeHistoryPreventsAFirstStrikerDealingNormalDamageAgain() {
        Board board=board();Attack attack=attack(board,"Centaur Courser");Card blocker=block(board,attack,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);
        assertEquals(attack.attacker.getDamage(),2);assertTrue(attack.attacker.isInZone(ZoneType.Battlefield));
        damage(board.game,attack.combat,board.actor);settle(board.game);
        assertTrue(attack.attacker.isInZone(ZoneType.Battlefield));assertEquals(attack.attacker.getDamage(),2);
        assertTrue(board.game.getCardState(blocker).isInZone(ZoneType.Graveyard));
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),3);
    }

    @Test public void copiedStrikeHistoryPreventsAFirstStrikerDealingNormalDamageAgain() {
        Board board=board();Attack attack=attack(board,"Centaur Courser");Card blocker=block(board,attack,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);
        assertEquals(attack.attacker.getDamage(),2);assertTrue(attack.attacker.isInZone(ZoneType.Battlefield));
        GameCopier copier=new GameCopier(board.game).withStack(true);Game game=copier.makeCopy(null,board.actor);
        Card copiedAttacker=copier.find(attack.attacker);assertEquals(copiedAttacker.getDamage(),2);
        damage(game,game.getCombat(),copier.find(board.actor));
        assertEquals(copiedAttacker.getDamage(),2,"A first-strike-only blocker must not deal damage again in the normal step");
        settle(game);
        assertTrue(game.getCardState(copiedAttacker).isInZone(ZoneType.Battlefield));assertEquals(copiedAttacker.getDamage(),2);
        assertTrue(game.getCardState(copier.find(blocker)).isInZone(ZoneType.Graveyard));
        assertTrue(attack.attacker.isInZone(ZoneType.Battlefield));assertEquals(attack.attacker.getDamage(),2);
        assertTrue(blocker.isInZone(ZoneType.Battlefield));
    }

    @Test public void nativeFirstStrikeAttackerDoesNotDamageItsBlockerTwice() { attackerHistory(false); }
    @Test public void copiedFirstStrikeAttackerDoesNotDamageItsBlockerTwice() { attackerHistory(true); }
    @Test public void nativeUnblockedFirstStrikerDoesNotDefeatTheBattleInTheNormalStep() { unblockedHistory(false); }
    @Test public void copiedUnblockedFirstStrikerDoesNotDefeatTheBattleInTheNormalStep() { unblockedHistory(true); }
    @Test public void nativeDoubleStrikerStillDamagesTheBattleInBothSteps() { doubleStrikeHistory(false); }
    @Test public void copiedDoubleStrikerStillDamagesTheBattleInBothSteps() { doubleStrikeHistory(true); }
    @Test public void nativeLossOfFirstStrikeDoesNotPermitAnotherDamageStep() { lostFirstStrike(false); }
    @Test public void copiedLossOfFirstStrikeDoesNotPermitAnotherDamageStep() { lostFirstStrike(true); }
    @Test public void nativeGainedDoubleStrikePermitsNormalDamage() { gainedDoubleStrike(false); }
    @Test public void copiedGainedDoubleStrikePermitsNormalDamage() { gainedDoubleStrike(true); }
    @Test public void nativeHistoryTracksBothFirstStrikeAttackers() { multipleFirstStrikers(false); }
    @Test public void copiedHistoryTracksBothFirstStrikeAttackers() { multipleFirstStrikers(true); }

    @Test public void nestedStrikeCopiesResolveIndependentlyOfEachOtherAndTheOriginal() {
        Board board=board();Attack attack=attack(board,"Centaur Courser");Card blocker=block(board,attack,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);
        GameCopier first=new GameCopier(board.game).withStack(true);Game firstGame=first.makeCopy(null,board.actor);
        GameCopier second=new GameCopier(firstGame).withStack(true);Game secondGame=second.makeCopy(null,first.find(board.actor));
        Card secondAttacker=second.find(first.find(attack.attacker));
        damage(secondGame,secondGame.getCombat(),second.find(first.find(board.actor)));
        assertEquals(secondAttacker.getDamage(),2);settle(secondGame);assertTrue(secondAttacker.isInZone(ZoneType.Battlefield));
        damage(firstGame,firstGame.getCombat(),first.find(board.actor));
        assertEquals(first.find(attack.attacker).getDamage(),2);settle(firstGame);
        assertTrue(first.find(attack.attacker).isInZone(ZoneType.Battlefield));
        assertTrue(attack.attacker.isInZone(ZoneType.Battlefield));assertEquals(attack.attacker.getDamage(),2);
        assertTrue(blocker.isInZone(ZoneType.Battlefield));
    }

    @Test public void departedFirstStrikerHistoryDoesNotBreakACopyAfterPaidUnsummon() {
        Board board=board();Attack attack=attack(board,"Centaur Courser");Card blocker=block(board,attack,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);
        Player responder=board.battle.getProtectingPlayer();addCardToZone("Island",responder,ZoneType.Battlefield);
        SpellAbility bounce=addCardToZone("Unsummon",responder,ZoneType.Hand).getFirstSpellAbility();
        bounce.setActivatingPlayer(responder);bounce.getTargets().add(blocker);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(responder,bounce,null));settle(board.game);
        Card returned=board.game.getCardState(blocker);assertTrue(returned.isInZone(ZoneType.Hand));
        assertNotEquals(returned.getGameTimestamp(),blocker.getGameTimestamp());assertEquals(bounce.getPayingMana().size(),1);
        GameCopier copier=new GameCopier(board.game).withStack(true);Game game=copier.makeCopy(null,board.actor);
        damage(game,game.getCombat(),copier.find(board.actor));settle(game);
        assertTrue(copier.find(returned).isInZone(ZoneType.Hand));assertEquals(copier.find(attack.attacker).getDamage(),2);
        assertTrue(copier.find(attack.attacker).isInZone(ZoneType.Battlefield));
        assertEquals(copier.find(board.battle).getCounters(CounterEnumType.DEFENSE),3);
        assertTrue(returned.isInZone(ZoneType.Hand));assertEquals(attack.attacker.getDamage(),2);
    }

    private void attackerHistory(boolean copied) {
        Board board=board();Attack attack=attack(board,"Vampire Hexmage");Card blocker=block(board,attack,"Centaur Courser");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);assertEquals(blocker.getDamage(),2);
        World world=world(board,attack,blocker,copied);damage(world.game,world.game.getCombat(),world.actor);
        assertEquals(world.blocker.getDamage(),2,"First-strike-only attacker must not assign normal damage again");settle(world.game);
        assertTrue(world.game.getCardState(world.attacker).isInZone(ZoneType.Graveyard));
        assertTrue(world.blocker.isInZone(ZoneType.Battlefield));assertEquals(world.battle.getCounters(CounterEnumType.DEFENSE),3);
    }

    private void unblockedHistory(boolean copied) {
        Board board=board();Attack attack=attack(board,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),1);
        World world=world(board,attack,null,copied);normalIfAssigned(world);
        assertEquals(world.game.getCardState(world.battle).getCounters(CounterEnumType.DEFENSE),1);
        assertTrue(world.battle.isBattle());assertTrue(world.battle.isInZone(ZoneType.Battlefield));
        assertEquals(world.actor.getSpellsCastThisTurn(),1);
    }

    private void doubleStrikeHistory(boolean copied) {
        Board board=board();Attack attack=attack(board,"Fencing Ace");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),2);
        World world=world(board,attack,null,copied);assertTrue(world.attacker.hasDoubleStrike());
        damage(world.game,world.game.getCombat(),world.actor);settle(world.game);
        assertEquals(world.battle.getCounters(CounterEnumType.DEFENSE),1);assertTrue(world.battle.isInZone(ZoneType.Battlefield));
    }

    private void lostFirstStrike(boolean copied) {
        Board board=board();Attack attack=attack(board,"Centaur Courser");Card blocker=block(board,attack,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);assertEquals(attack.attacker.getDamage(),2);
        Player responder=board.battle.getProtectingPlayer();
        for(int index=0;index<2;index++)addCardToZone("Island",responder,ZoneType.Battlefield);
        SpellAbility dress=addCardToZone("Dress Down",responder,ZoneType.Hand).getFirstSpellAbility();dress.setActivatingPlayer(responder);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(responder,dress,null));settle(board.game);
        assertEquals(dress.getPayingMana().size(),2);assertFalse(blocker.hasFirstStrike());
        World world=world(board,attack,blocker,copied);assertFalse(world.blocker.hasFirstStrike());
        damage(world.game,world.game.getCombat(),world.actor);assertEquals(world.attacker.getDamage(),2);settle(world.game);
        assertTrue(world.attacker.isInZone(ZoneType.Battlefield));assertTrue(world.game.getCardState(world.blocker).isInZone(ZoneType.Graveyard));
    }

    private void gainedDoubleStrike(boolean copied) {
        Board board=board();Attack attack=attack(board,"Centaur Courser");Card blocker=block(board,attack,"Vampire Hexmage");
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);
        Player responder=board.battle.getProtectingPlayer();
        for(int index=0;index<2;index++)addCardToZone("Mountain",responder,ZoneType.Battlefield);
        SpellAbility rage=addCardToZone("Temur Battle Rage",responder,ZoneType.Hand).getFirstSpellAbility();
        rage.setActivatingPlayer(responder);rage.getTargets().add(blocker);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(responder,rage,null));settle(board.game);
        assertEquals(rage.getPayingMana().size(),2);assertTrue(blocker.hasDoubleStrike());
        World world=world(board,attack,blocker,copied);assertTrue(world.blocker.hasDoubleStrike());
        damage(world.game,world.game.getCombat(),world.actor);assertEquals(world.attacker.getDamage(),4);settle(world.game);
        assertTrue(world.game.getCardState(world.attacker).isInZone(ZoneType.Graveyard));
        assertTrue(world.game.getCardState(world.blocker).isInZone(ZoneType.Graveyard));
        assertEquals(world.battle.getCounters(CounterEnumType.DEFENSE),3);
    }

    /** Five defense counters are an explicit legal board precondition, not printed entry defense. */
    private void multipleFirstStrikers(boolean copied) {
        Board board=board();board.battle.setCounters(CounterEnumType.DEFENSE,5);Attack attack=attack(board,"Vampire Hexmage");
        Card other=addCardToZone("Vampire Hexmage",board.actor,ZoneType.Battlefield);other.setSickness(false);
        assertTrue(CombatUtil.canAttack(other,board.battle));attack.combat.addAttacker(other,board.battle);other.setTapped(true);
        CombatUtil.checkDeclaredAttacker(board.game,other,attack.combat,true);attack.combat.setBlocked(other,false);
        firstStrike(board.game,attack.combat,board.actor);settle(board.game);assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),1);
        World world=world(board,attack,null,copied);normalIfAssigned(world);
        assertEquals(world.game.getCardState(world.battle).getCounters(CounterEnumType.DEFENSE),1);
        assertTrue(world.battle.isBattle());assertTrue(world.battle.isInZone(ZoneType.Battlefield));
    }

    private void normalIfAssigned(World world) {
        world.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,world.actor,false,3);
        if(world.game.getCombat().assignCombatDamage(false))world.game.getCombat().dealAssignedDamage();
        settle(world.game);
    }

    private World world(Board board,Attack attack,Card blocker,boolean copied) {
        if(!copied)return new World(board.game,board.actor,board.battle,attack.attacker,blocker);
        GameCopier copier=new GameCopier(board.game).withStack(true);Game game=copier.makeCopy(null,board.actor);
        return new World(game,copier.find(board.actor),copier.find(board.battle),copier.find(attack.attacker),copier.find(blocker));
    }

    private record World(Game game,Player actor,Card battle,Card attacker,Card blocker) { }

    private void pendingDefeat(Board board) {
        hexmage(board);board.game.getStack().resolveStack();queue(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE),0);
        assertTrue(board.battle.isInZone(ZoneType.Battlefield));assertTrue(board.game.getStack().peekAbility().isTrigger());
    }

    /** Blocker declaration is supplied; native eligibility and both damage orders are required. */
    private Card block(Board board,Attack attack,String name) {
        Card blocker=addCardToZone(name,board.battle.getProtectingPlayer(),ZoneType.Battlefield);
        board.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,board.actor,false,3);
        assertTrue(CombatUtil.canBlock(attack.attacker,blocker,attack.combat));
        attack.combat.addBlocker(attack.attacker,blocker);attack.combat.setBlocked(attack.attacker,true);
        attack.combat.orderBlockersForDamageAssignment();attack.combat.orderAttackersForDamageAssignment();
        return blocker;
    }

    private void firstStrike(Game game,Combat combat,Player actor) {
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_FIRST_STRIKE_DAMAGE,actor,false,3);
        assertTrue(combat.assignCombatDamage(true));combat.dealAssignedDamage();
    }



    @Test public void nativeRemovedBattleRetainsAttackerButDealsNoOrdinaryTrampleDamage() { scenario(false,false,false,true,false,false); }
    @Test public void copiedRemovedBattleRetainsAttackerButDealsNoOrdinaryTrampleDamage() { scenario(false,false,true,true,false,false); }
    @Test public void nativeRemovedWalkerDealsNoOrdinaryTrampleDamageToItsController() { scenario(true,false,false,true,false,false); }
    @Test public void copiedRemovedWalkerDealsNoOrdinaryTrampleDamageToItsController() { scenario(true,false,true,true,false,false); }
    @Test public void nativeOverPlaneswalkersDoesNotRedirectDamageFromARemovedBattle() { scenario(false,true,false,true,false,false); }
    @Test public void copiedOverPlaneswalkersDoesNotRedirectDamageFromARemovedBattle() { scenario(false,true,true,true,false,false); }
    @Test public void nativeOverPlaneswalkersStillDamagesARemovedWalkersController() { scenario(true,true,false,true,false,false); }
    @Test public void copiedOverPlaneswalkersStillDamagesARemovedWalkersController() { scenario(true,true,true,true,false,false); }
    @Test public void nativeLiveBattleDoesNotRedirectOverPlaneswalkerDamage() { scenario(false,true,false,false,false,false); }
    @Test public void copiedLiveBattleDoesNotRedirectOverPlaneswalkerDamage() { scenario(false,true,true,false,false,false); }
    @Test public void nativeLiveWalkerReceivesLethalBeforeOverPlaneswalkerSpillover() { scenario(true,true,false,false,false,false); }
    @Test public void copiedLiveWalkerReceivesLethalBeforeOverPlaneswalkerSpillover() { scenario(true,true,true,false,false,false); }
    @Test public void nativeOrdinaryTrampleAtALiveWalkerDoesNotSpillToAPlayer() { scenario(true,false,false,false,false,false); }
    @Test public void copiedOrdinaryTrampleAtALiveWalkerDoesNotSpillToAPlayer() { scenario(true,false,true,false,false,false); }
    @Test public void nativeBlockedRemovedWalkerAssignsOnlyToBlockerAndPlayer() { scenario(true,true,false,true,true,false); }
    @Test public void copiedBlockedRemovedWalkerAssignsOnlyToBlockerAndPlayer() { scenario(true,true,true,true,true,false); }
    @Test public void nativeBlockedRemovedBattleDoesNotRedirectTrampleToAPlayer() { scenario(false,true,false,true,true,false); }
    @Test public void copiedBlockedRemovedBattleDoesNotRedirectTrampleToAPlayer() { scenario(false,true,true,true,true,false); }
    @Test public void nativeCounteredRemovalRetainsTheLiveWalkersLoyaltyRequirement() { scenario(true,true,false,true,false,true); }
    @Test public void copiedCounteredRemovalRetainsTheLiveWalkersLoyaltyRequirement() { scenario(true,true,true,true,false,true); }

    @Test public void nativeJitteGetsNoCountersForAnAbsentBattle() { scenario(false,false,false,true,false,false,true); }
    @Test public void copiedJitteGetsNoCountersForAnAbsentBattle() { scenario(false,false,true,true,false,false,true); }
    @Test public void nativeJitteGetsNoCountersForAnAbsentWalkerWithOrdinaryTrample() { scenario(true,false,false,true,false,false,true); }
    @Test public void copiedJitteGetsNoCountersForAnAbsentWalkerWithOrdinaryTrample() { scenario(true,false,true,true,false,false,true); }
    @Test public void nativeJitteGetsNoCountersForOverPlaneswalkersAtAnAbsentBattle() { scenario(false,true,false,true,false,false,true); }
    @Test public void copiedJitteGetsNoCountersForOverPlaneswalkersAtAnAbsentBattle() { scenario(false,true,true,true,false,false,true); }
    @Test public void nativeJitteTriggersForLegalOverPlaneswalkerDamage() { scenario(true,true,false,true,false,false,true); }
    @Test public void copiedJitteTriggersForLegalOverPlaneswalkerDamage() { scenario(true,true,true,true,false,false,true); }


    @Test public void copiedAssignedJitteAtRemovedBattleWithOrdinaryTrampleDoesNotTrigger() { scenario(false,false,false,true,false,false,true,true); }
    @Test public void copiedAssignedJitteAtRemovedWalkerWithOrdinaryTrampleDoesNotTrigger() { scenario(true,false,false,true,false,false,true,true); }
    @Test public void copiedAssignedJitteAtRemovedBattleWithOverPlaneswalkersDoesNotTrigger() { scenario(false,true,false,true,false,false,true,true); }
    @Test public void copiedAssignedJitteAtRemovedWalkerTriggersForPlayerDamage() { scenario(true,true,false,true,false,false,true,true); }
    @Test public void copiedAssignedJitteAtLiveBattleTriggersForDefenseDamage() { scenario(false,true,false,false,false,false,true,true); }
    @Test public void copiedAssignedJitteAtLiveWalkerTriggersOnceForWalkerAndPlayerDamage() { scenario(true,true,false,false,false,false,true,true); }
    @Test public void copiedAssignedJitteAtBlockedRemovedBattleTriggersForBlockerDamageOnly() { scenario(false,true,false,true,true,false,true,true); }
    @Test public void copiedAssignedJitteAtBlockedRemovedWalkerTriggersOnceForBlockerAndPlayerDamage() { scenario(true,true,false,true,true,false,true,true); }

    @Test public void copiedAssignmentAtRemovedBattleWithOrdinaryTrampleSettlesWithoutDamage() { scenario(false,false,false,true,false,false,false,true); }
    @Test public void copiedAssignmentAtRemovedBattleWithOverPlaneswalkersSettlesWithoutDamage() { scenario(false,true,false,true,false,false,false,true); }
    @Test public void copiedAssignmentAtRemovedWalkerWithOrdinaryTrampleSettlesWithoutDamage() { scenario(true,false,false,true,false,false,false,true); }
    @Test public void copiedAssignmentAtRemovedWalkerWithOverPlaneswalkersRetainsPlayerDamage() { scenario(true,true,false,true,false,false,false,true); }
    @Test public void copiedAssignmentAtLiveBattleRetainsDefenseDamage() { scenario(false,true,false,false,false,false,false,true); }
    @Test public void copiedAssignmentAtLiveWalkerRetainsLoyaltyAndPlayerDamage() { scenario(true,true,false,false,false,false,false,true); }
    @Test public void copiedBlockedAssignmentAtRemovedBattleSettlesBlockerDamageOnly() { scenario(false,true,false,true,true,false,false,true); }
    @Test public void copiedBlockedAssignmentAtRemovedWalkerRetainsBlockerAndPlayerDamage() { scenario(true,true,false,true,true,false,false,true); }

    private void scenario(boolean walker,boolean overPlaneswalkers,boolean copied,boolean remove,boolean blocked,boolean countered) {
        scenario(walker,overPlaneswalkers,copied,remove,blocked,countered,false);
    }

    private void scenario(boolean walker,boolean overPlaneswalkers,boolean copied,boolean remove,boolean blocked,boolean countered,boolean withJitte) {
        scenario(walker,overPlaneswalkers,copied,remove,blocked,countered,withJitte,false);
    }

    private void scenario(boolean walker,boolean overPlaneswalkers,boolean copied,boolean remove,boolean blocked,boolean countered,boolean withJitte,boolean copyAssignedDamage) {
        Board board=board();Player opponent=board.battle.getProtectingPlayer();Card defender=board.battle;
        if(walker) {
            defender=addCardToZone("Jace, the Mind Sculptor",opponent,ZoneType.Battlefield);
            defender.setCounters(CounterEnumType.LOYALTY,3);
        }
        Card jitte=withJitte?addCardToZone("Umezawa's Jitte",board.actor,ZoneType.Battlefield):null;
        Attack attack=attackDefender(board,overPlaneswalkers?"Thrasta, Tempest's Roar":"Colossal Dreadmaw",defender,jitte);
        int power=attack.attacker.getNetPower();List<Integer> initial=board.game.getPlayers().stream().map(Player::getLife).toList();
        board.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,board.actor,false,3);
        Card blocker=null;
        if(blocked) {
            blocker=addCardToZone("Grizzly Bears",opponent,ZoneType.Battlefield);
            assertTrue(CombatUtil.canBlock(attack.attacker,blocker,attack.combat));attack.combat.addBlocker(attack.attacker,blocker);
            attack.combat.setBlocked(attack.attacker,true);attack.combat.orderBlockersForDamageAssignment();attack.combat.orderAttackersForDamageAssignment();
        }
        if(remove) {
            for(int index=0;index<3;index++)addCardToZone("Forest",opponent,ZoneType.Battlefield);
            SpellAbility removal=addCardToZone("Beast Within",opponent,ZoneType.Hand).getFirstSpellAbility();
            removal.setActivatingPlayer(opponent);removal.getTargets().add(defender);
            assertTrue(ComputerUtil.handlePlayingSpellAbility(opponent,removal,null));assertEquals(removal.getPayingMana().size(),3);
            if(countered) {
                for(int index=0;index<2;index++)addCardToZone("Island",board.actor,ZoneType.Battlefield);
                SpellAbility counter=addCardToZone("Counterspell",board.actor,ZoneType.Hand).getFirstSpellAbility();
                counter.setActivatingPlayer(board.actor);counter.getTargets().add(board.game.getStack().peekAbility());
                assertTrue(ComputerUtil.handlePlayingSpellAbility(board.actor,counter,null));assertEquals(counter.getPayingMana().size(),2);
                assertEquals(board.game.getStack().size(),2);
            } else {
                settle(board.game);assertTrue(board.game.getCardState(defender).isInZone(ZoneType.Graveyard));
            }
        }
        assertTrue(attack.combat.isAttacking(attack.attacker));assertEquals(attack.combat.isBlocked(attack.attacker),blocked);
        Game game=board.game;Player actor=board.actor,target=opponent;Card attacker=attack.attacker;
        Card targetCard=board.game.getCardState(defender),targetBlocker=blocker,targetJitte=jitte;
        if(copied) {
            GameCopier copier=new GameCopier(board.game).withStack(true);game=copier.makeCopy(null,board.actor);
            actor=copier.find(board.actor);target=copier.find(opponent);attacker=copier.find(attack.attacker);
            targetCard=copier.find(targetCard);targetBlocker=copier.find(blocker);targetJitte=copier.find(jitte);
            assertTrue(game.getCombat().isAttacking(attacker));assertEquals(game.getCombat().isBlocked(attacker),blocked);
        }
        if(countered)settle(game);
        boolean live=!remove||countered;
        assertEquals(game.getCardState(targetCard).isInZone(ZoneType.Battlefield),live);
        assertEquals(attacker.getNetPower(),power);assertEquals(attacker.getNetCombatDamage(),power);
        Card recipient=(Card)game.getCombat().getDefenderByAttacker(attacker);
        System.out.println("REMOVED_DEFENDER before: walker="+walker+"; over="+overPlaneswalkers+"; copy="+copied+"; live="+live+"; blocked="+blocked
                +"; name="+recipient.getName()+"; id="+recipient.getId()+"; zone="+recipient.getZone()+"; battle="+recipient.isBattle()+"; pw="+recipient.isPlaneswalker()
                +"; loyalty="+recipient.getCounters(CounterEnumType.LOYALTY)+"; toughness="+recipient.getNetToughness()+"; damage="+attacker.getNetCombatDamage());
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,actor,false,3);
        assertTrue(game.getCombat().assignCombatDamage(false));
        System.out.println("REMOVED_DEFENDER assigned: defender="+recipient.getAssignedDamageMap().values().stream().mapToInt(Integer::intValue).sum());
        // Internal assignment snapshots are tested here, not a player priority window.
        if(copyAssignedDamage) {
            GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,actor);
            actor=copier.find(actor);target=copier.find(target);attacker=copier.find(attacker);
            targetCard=copier.find(targetCard);targetBlocker=copier.find(targetBlocker);targetJitte=copier.find(targetJitte);
            assertTrue(game.getCombat().isAttacking(attacker));
        }
        game.getCombat().dealAssignedDamage();settle(game);
        List<Integer> expected=new ArrayList<>(initial);
        if(walker&&overPlaneswalkers) {
            int index=game.getPlayers().indexOf(target);int required=(live?3:0)+(blocked?2:0);
            expected.set(index,initial.get(index)-Math.max(0,power-required));
        }
        if(withJitte)assertEquals(targetJitte.getCounters(CounterEnumType.CHARGE),live||blocked||walker&&overPlaneswalkers?2:0,"Jitte counts actual combat damage, not assignment to a removed-target stand-in");
        if(!withJitte||copyAssignedDamage)assertEquals(game.getPlayers().stream().map(Player::getLife).toList(),expected,
                "A departed walker requires no phantom lethal damage; live loyalty and real blockers still require assignment");
        assertTrue(attacker.isInZone(ZoneType.Battlefield));
        if(blocked)assertTrue(game.getCardState(targetBlocker).isInZone(ZoneType.Graveyard));
        if(live) {
            if(walker)assertTrue(game.getCardState(targetCard).isInZone(ZoneType.Graveyard));
            else assertArray(game,targetCard);
        }
        if(copied||copyAssignedDamage)assertEquals(board.game.getPlayers().stream().map(Player::getLife).toList(),initial,"Copy damage cannot change original player life");
    }

    /** Mature attacks, a loyalty-three walker and blocker declaration are supplied legal state preconditions. */
    private Attack attackDefender(Board board,String name,Card defender,Card jitte) {
        Card attacker=addCardToZone(name,board.actor,ZoneType.Battlefield);attacker.setSickness(false);
        if(jitte!=null) {
            for(int index=0;index<2;index++)addCardToZone("Plains",board.actor,ZoneType.Battlefield);
            SpellAbility equip=jitte.getSpellAbilities().stream().filter(SpellAbility::isEquip).findFirst().orElseThrow();
            equip.setActivatingPlayer(board.actor);equip.getTargets().add(attacker);assertTrue(equip.canPlay());
            assertTrue(ComputerUtil.handlePlayingSpellAbility(board.actor,equip,null));settle(board.game);
            assertEquals(equip.getPayingMana().size(),2);assertSame(jitte.getEntityAttachedTo(),attacker);
            assertEquals(jitte.getCounters(CounterEnumType.CHARGE),0);
        }
        board.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,board.actor,3);
        assertTrue(CombatUtil.getAllPossibleDefenders(board.actor).contains(defender));assertTrue(CombatUtil.canAttack(attacker,defender));
        Combat combat=new Combat(board.actor);board.game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker,defender);attacker.setTapped(true);CombatUtil.checkDeclaredAttacker(board.game,attacker,combat,true);
        combat.setBlocked(attacker,false);return new Attack(attacker,combat);
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
