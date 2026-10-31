package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
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

/** Research: distinguish ordinary lethal attacks from controlled attacks on the beneficiary. */
public class ControlledCombatAiCoverageTest extends AITest {
    private Game pod(String creature, int amount) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("decision" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Controlled decision"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getPlayers().get(seat);
            for (int i = 0; i < 10; i++) {
                addCardToZone("Swamp", player, ZoneType.Battlefield);
                addCardToZone("Mountain", player, ZoneType.Battlefield);
                addCardToZone(seat == 1 ? "Shock" : "Forest", player, ZoneType.Library);
            }
        }
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 4; i++) addCardToZone("Island", player, ZoneType.Battlefield);
        }
        for (int i = 0; i < amount; i++) addCardToZone(creature, game.getPlayers().get(1), ZoneType.Battlefield);
        game.getPlayers().get(0).setLife(2, null);
        return game;
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture did not settle");
    }

    private Game prepared(boolean controlled, String creature, int amount, boolean combatOnly) {
        Game game = pod(creature, amount);
        Player master = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        if (controlled) {
            Card fears = addCardToZone(combatOnly ? "Secret of Bloodbending" : "Worst Fears", master, ZoneType.Hand);
            SpellAbility spell = fears.getFirstSpellAbility();
            spell.setActivatingPlayer(master);
            assertTrue(spell.canTarget(actor));
            spell.getTargets().add(actor);
            assertTrue(ComputerUtil.playStack(spell, master, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), combatOnly ? 4 : 8);
            game.getStack().resolveStack();
            settle(game);
        }
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        if (combatOnly) assertNull(actor.getControllingPlayer(), "Normal Secret cost grants combat control only");
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.COMBAT_BEGIN, () -> settle(game)));
        settle(game);
        assertEquals(actor.getControllingPlayer(), controlled ? master : null);
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(actor.getCardsIn(ZoneType.Hand).get(0).getName(), "Shock");
        return game;
    }

    private int tappedLands(Player player) {
        return (int) player.getLandsInPlay().stream().filter(Card::isTapped).count();
    }

    private void attack(boolean controlled, boolean full, String kind) {
        boolean forced = kind.equals("forced") || kind.equals("onlyMaster") || kind.equals("optionalTax");
        Game game = prepared(controlled, forced ? "Juggernaut" : kind.equals("group") ? "Mogg Flunkies" : "Shivan Dragon",
                kind.equals("cap") || kind.equals("group") ? 2 : 1, kind.equals("combatOnly"));
        Player actor = game.getPlayers().get(1);
        Player owner = game.getPlayers().get(0);
        Player beneficiary = controlled ? owner : actor;
        if (kind.equals("chain")) {
            owner.setLife(40, null);
            beneficiary = game.getPlayers().get(2);
            beneficiary.setLife(2, null);
            owner.addController(game.getNextTimestamp(), beneficiary);
        }
        if (kind.equals("self")) actor.addController(game.getNextTimestamp(), actor);
        if (kind.equals("onlyMaster") || kind.equals("optionalTax")) {
            addCardToZone("Blazing Archon", game.getPlayers().get(2), ZoneType.Battlefield);
            addCardToZone("Blazing Archon", game.getPlayers().get(3), ZoneType.Battlefield);
        }
        if (kind.equals("optionalTax")) addCardToZone("Propaganda", owner, ZoneType.Battlefield);
        if (kind.equals("cap")) addCardToZone("Silent Arbiter", owner, ZoneType.Battlefield);
        if (kind.equals("blocked") || kind.equals("paidTax")) {
            addCardToZone("Blazing Archon", game.getPlayers().get(3), ZoneType.Battlefield);
        }
        if (kind.equals("blocked")) addCardToZone("Baneslayer Angel", game.getPlayers().get(2), ZoneType.Battlefield);
        if (kind.equals("paidTax")) {
            addCardToZone("Blazing Archon", owner, ZoneType.Battlefield);
            addCardToZone("Propaganda", game.getPlayers().get(2), ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        Card attacker = actor.getCreaturesInPlay().get(0);
        assertFalse(attacker.isTapped());
        assertFalse(attacker.hasSickness());
        if (kind.equals("onlyMaster") || kind.equals("optionalTax")) {
            assertTrue(forge.game.combat.CombatUtil.canAttack(attacker, owner));
            assertFalse(forge.game.combat.CombatUtil.canAttack(attacker, game.getPlayers().get(2)));
            assertFalse(forge.game.combat.CombatUtil.canAttack(attacker, game.getPlayers().get(3)));
        }
        // A fixed declaration checkpoint, before the phase hook asks for attackers automatically.
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, false);
        forge.game.combat.Combat combat = new forge.game.combat.Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        agent.getAi().setUseSimulation(full ? AIOption.USE_FULL_SIMULATION : null);
        List<Integer> originalLives = new ArrayList<>();
        List<Integer> originalTaps = new ArrayList<>();
        for (Player player : game.getRegisteredPlayers()) {
            originalLives.add(player.getLife());
            originalTaps.add(tappedLands(player));
        }
        java.util.Random originalRandomProvider = forge.util.MyRandom.getRandom();
        if (kind.equals("timeout")) {
            game.AI_TIMEOUT = 0;
            agent.declareAttackers(actor, combat);
            assertTrue(forge.game.combat.CombatUtil.validateAttackers(combat), "Expired decisions retain a legal fallback");
            combat.clearAttackers();
            game.AI_TIMEOUT = 60;
        }
        agent.declareAttackers(actor, combat);
        assertSame(forge.util.MyRandom.getRandom(), originalRandomProvider, "Restore the original RNG provider");
        assertTrue(forge.game.combat.CombatUtil.validateAttackers(combat));
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getRegisteredPlayers().get(seat);
            assertEquals(player.getLife(), originalLives.get(seat).intValue(), "Planning must not deal real damage");
            assertEquals(tappedLands(player), originalTaps.get(seat).intValue(), "Planning cannot pay real costs");
        }
        assertFalse(attacker.isTapped(), "Planning cannot tap the original attacker");
        final Player fixedBeneficiary = beneficiary;
        boolean attacksBeneficiary = combat.getAttackers().stream()
                .anyMatch(card -> combat.getDefenderByAttacker(card) == fixedBeneficiary);
        System.out.println("CONTROL_COMBAT controlled=" + controlled + " full=" + full + " kind=" + kind
                + " attacksBeneficiary=" + attacksBeneficiary + " attackers=" + combat.getAttackers().size());
        if (kind.equals("onlyMaster")) {
            assertEquals(combat.getAttackers().size(), 1, "A legal mandatory attack is required even when harmful");
            assertSame(combat.getDefenderByAttacker(attacker), owner);
        } else if (controlled) {
            assertFalse(attacksBeneficiary, "Avoid unforced harm to the fixed decision beneficiary");
        } else {
            assertEquals(combat.getAttackers().size(), 1);
            assertSame(combat.getDefenderByAttacker(attacker), owner);
        }
        if (kind.equals("optionalTax")) assertTrue(combat.getAttackers().isEmpty(), "Attack requirements cannot force payment");
        if (kind.equals("cap")) assertTrue(combat.getAttackers().size() <= 1);
        if (kind.equals("group")) assertEquals(combat.getAttackers().size(), 2, "Neither Flunkies can attack alone");
        if (kind.equals("forced")) assertEquals(combat.getAttackers().size(), 1);
        if (kind.equals("paidTax") || kind.equals("blocked")) {
            assertSame(combat.getDefenderByAttacker(attacker), game.getPlayers().get(2));
        }
        java.util.Map<Card, forge.game.GameEntity> selected = new java.util.LinkedHashMap<>(combat.getAttackersAndDefenders());
        agent.getAi().setAttackOverride(selected);
        game.getPhaseHandler().devDeclareAttackers();
        settle(game);
        assertTrue(forge.game.combat.CombatUtil.validateAttackers(combat));
        if (kind.equals("paidTax")) {
            assertEquals(tappedLands(actor), originalTaps.get(1).intValue() + 2, "Acting player pays the attack tax");
            assertEquals(tappedLands(owner), originalTaps.get(0).intValue(), "Decision controller does not pay");
        }
        game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game));
        settle(game);
        if (kind.equals("onlyMaster") || !controlled) {
            assertTrue(owner.hasLost(), "Native unblocked combat damage eliminates the two-life defender");
        } else {
            assertFalse(beneficiary.hasLost());
            assertEquals(beneficiary.getLife(), originalLives.get(game.getRegisteredPlayers().indexOf(beneficiary)).intValue());
        }
        if (kind.equals("blocked")) {
            assertTrue(attacker.isInZone(ZoneType.Graveyard), "Native first strike kills the controlled Dragon");
            assertEquals(game.getRegisteredPlayers().get(2).getLife(), 45, "Native lifelink resolves");
        }
        if (kind.equals("paidTax")) assertEquals(game.getRegisteredPlayers().get(2).getLife(), 35);
        if (kind.equals("combatOnly")) assertNull(actor.getControllingPlayer(), "Combat-only control expires before the scored main phase");
    }

    @Test public void ordinaryHeuristic() { attack(false, false, "ordinary"); }
    @Test public void ordinaryFull() { attack(false, true, "ordinary"); }
    @Test public void controlledHeuristic() { attack(true, false, "controlled"); }
    @Test public void controlledFull() { attack(true, true, "controlled"); }
    @Test public void forcedHeuristic() { attack(true, false, "forced"); }
    @Test public void forcedFull() { attack(true, true, "forced"); }
    @Test public void onlyMasterHeuristic() { attack(true, false, "onlyMaster"); }
    @Test public void onlyMasterFull() { attack(true, true, "onlyMaster"); }
    @Test public void optionalTaxHeuristic() { attack(true, false, "optionalTax"); }
    @Test public void optionalTaxFull() { attack(true, true, "optionalTax"); }
    @Test public void capHeuristic() { attack(true, false, "cap"); }
    @Test public void capFull() { attack(true, true, "cap"); }
    @Test public void chainHeuristic() { attack(true, false, "chain"); }
    @Test public void chainFull() { attack(true, true, "chain"); }
    @Test public void selfHeuristic() { attack(false, false, "self"); }
    @Test public void selfFull() { attack(false, true, "self"); }
    @Test public void blockedHeuristic() { attack(true, false, "blocked"); }
    @Test public void blockedFull() { attack(true, true, "blocked"); }
    @Test public void paidTaxHeuristic() { attack(true, false, "paidTax"); }
    @Test public void paidTaxFull() { attack(true, true, "paidTax"); }
    @Test public void groupHeuristic() { attack(true, false, "group"); }
    @Test public void groupFull() { attack(true, true, "group"); }
    @Test public void combatOnlyHeuristic() { attack(true, false, "combatOnly"); }
    @Test public void combatOnlyFull() { attack(true, true, "combatOnly"); }
    @Test public void timeoutRecoveryHeuristic() { attack(true, false, "timeout"); }
    @Test public void timeoutRecoveryFull() { attack(true, true, "timeout"); }
}
