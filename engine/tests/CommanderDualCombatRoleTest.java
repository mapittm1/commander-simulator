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
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Supplied legal declarations with actual paid animation, Donate, equip and Stifle. */
public class CommanderDualCombatRoleTest extends AITest {
    @Test
    public void nativeAttackedWalkerKeepsBlockingAfterLuxior() {
        scenario(true, false, false);
    }
    @Test
    public void copiedAttackedWalkerKeepsBlockingAfterLuxior() {
        scenario(true, true, false);
    }
    @Test
    public void nativeUnattackedWalkerKeepsBlockingAfterLuxior() {
        scenario(false, false, false);
    }
    @Test
    public void copiedUnattackedWalkerKeepsBlockingAfterLuxior() {
        scenario(false, true, false);
    }
    @Test
    public void nativeStifledEquipKeepsBothRoles() {
        scenario(true, false, true);
    }
    @Test
    public void copiedStifledEquipKeepsBothRoles() {
        scenario(true, true, true);
    }

    private void scenario(boolean attacked, boolean copied, boolean stifled) {
        Board board = board();
        Game game = board.game;
        Player actor = board.actor;
        Player opponent = board.opponent;
        Card gideon = board.gideon;
        Card equipment = addCardToZone("Luxior, Giada's Gift", opponent, ZoneType.Battlefield);
        // Mature attackers and the actual defender/blocker declarations are supplied.
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        bear.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 3);
        Combat combat = new Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        assertTrue(CombatUtil.canAttack(bear, opponent));
        combat.addAttacker(bear, opponent);
        bear.setTapped(true);
        CombatUtil.checkDeclaredAttacker(game, bear, combat, true);
        if (attacked) {
            Card other = addCardToZone("Colossal Dreadmaw", actor, ZoneType.Battlefield);
            other.setSickness(false);
            assertTrue(CombatUtil.getAllPossibleDefenders(actor).contains(gideon));
            assertTrue(CombatUtil.canAttack(other, gideon));
            combat.addAttacker(other, gideon);
            other.setTapped(true);
            CombatUtil.checkDeclaredAttacker(game, other, combat, true);
            combat.setBlocked(other, false);
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, false, 3);
        assertTrue(CombatUtil.canBlock(bear, gideon, combat));
        combat.addBlocker(bear, gideon);
        combat.setBlocked(bear, true);
        combat.orderBlockersForDamageAssignment();
        combat.orderAttackersForDamageAssignment();
        assertTrue(combat.getBlockers(bear).contains(gideon));

        addCardToZone("Plains", opponent, ZoneType.Battlefield);
        game.getPhaseHandler().setPriority(opponent);
        SpellAbility equip = equipment.getSpellAbilities().stream()
                .filter(SpellAbility::isEquip)
                .filter(ability -> ability.getPayCosts().getTotalMana().getCMC() == 1)
                .findFirst().orElseThrow();
        equip.setActivatingPlayer(opponent);
        equip.getTargets().add(gideon);
        assertFalse(equip.canPlay(), "Equip needs instant timing permission during combat");
        addCardToZone("Leonin Shikari", opponent, ZoneType.Battlefield);
        settle(game);
        paid(opponent, equip, 1);
        if (stifled) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
            game.getPhaseHandler().setPriority(actor);
            SpellAbility stifle = addCardToZone("Stifle", actor, ZoneType.Hand).getFirstSpellAbility();
            stifle.setActivatingPlayer(actor);
            stifle.getTargets().add(game.getStack().peekAbility());
            paid(actor, stifle, 1);
        }
        settle(game);
        gideon = game.getCardState(gideon);
        assertTrue(gideon.isInZone(ZoneType.Battlefield));
        assertTrue(gideon.isCreature());
        assertEquals(gideon.isPlaneswalker(), stifled);
        assertEquals(gideon.getCounters(CounterEnumType.LOYALTY), 5);
        if (!stifled) {
            assertSame(game.getCardState(equipment).getEntityAttachedTo(), gideon);
        }
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            bear = copier.find(bear);
            gideon = copier.find(gideon);
            combat = game.getCombat();
        }
        assertTrue(combat.getBlockers(bear).contains(gideon),
                "CR 506.4d: losing planeswalker type must preserve the surviving blocking-creature role");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, actor, false, 3);
        assertTrue(combat.assignCombatDamage(false));
        combat.dealAssignedDamage();
        settle(game);
        assertTrue(game.getCardState(bear).isInZone(ZoneType.Graveyard), "Gideon must still deal blocking damage");
        assertEquals(game.getCardState(gideon).getCounters(CounterEnumType.LOYALTY), 5);
        assertTrue(game.getPlayers().stream().allMatch(player -> player.getLife() == 40));
        if (copied) {
            assertTrue(board.game.getCardState(board.gideon).isInZone(ZoneType.Battlefield));
            assertTrue(board.game.getPlayers().stream().allMatch(player -> player.getLife() == 40));
        }
    }

    private Board board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("dual-role-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Dual combat role research"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        Card gideon = addCardToZone("Gideon, Ally of Zendikar", actor, ZoneType.Battlefield);
        gideon.setCounters(CounterEnumType.LOYALTY, 4);
        settle(game);
        SpellAbility animate = gideon.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.Animate).findFirst().orElseThrow();
        animate.setActivatingPlayer(actor);
        paid(actor, animate, 0);
        settle(game);
        assertTrue(gideon.isCreature());
        assertTrue(gideon.isPlaneswalker());
        assertEquals(gideon.getCounters(CounterEnumType.LOYALTY), 5);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        SpellAbility donate = addCardToZone("Donate", actor, ZoneType.Hand).getFirstSpellAbility();
        donate.setActivatingPlayer(actor);
        donate.getTargets().add(opponent);
        donate.getSubAbility().getTargets().add(gideon);
        paid(actor, donate, 3);
        settle(game);
        gideon = game.getCardState(gideon);
        assertSame(gideon.getController(), opponent);
        assertTrue(gideon.isCreature());
        assertTrue(gideon.isPlaneswalker());
        return new Board(game, actor, opponent, gideon);
    }

    @Test
    public void nativeWalkerKeepsBeingAttackedAfterCreatureLoss() {
        creatureLoss(true, false, false);
    }
    @Test
    public void copiedWalkerKeepsBeingAttackedAfterCreatureLoss() {
        creatureLoss(true, true, false);
    }
    @Test
    public void nativeNonblockingWalkerKeepsBeingAttackedAfterCreatureLoss() {
        creatureLoss(false, false, false);
    }
    @Test
    public void copiedNonblockingWalkerKeepsBeingAttackedAfterCreatureLoss() {
        creatureLoss(false, true, false);
    }
    @Test
    public void nativeCounteredMarchRemovalKeepsBothRoles() {
        creatureLoss(true, false, true);
    }
    @Test
    public void copiedCounteredMarchRemovalKeepsBothRoles() {
        creatureLoss(true, true, true);
    }

    private void creatureLoss(boolean blocking, boolean copied, boolean countered) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("creature-loss-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Creature-role loss research"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        Card gideon = addCardToZone("Gideon, Ally of Zendikar", actor, ZoneType.Battlefield);
        gideon.setCounters(CounterEnumType.LOYALTY, 4);
        Card coating = addCardToZone("Liquimetal Coating", actor, ZoneType.Battlefield);
        SpellAbility metal = coating.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.Animate).findFirst().orElseThrow();
        metal.setActivatingPlayer(actor);
        metal.getTargets().add(gideon);
        paid(actor, metal, 0);
        settle(game);
        assertTrue(coating.isTapped());
        assertTrue(gideon.isArtifact());
        for (int index = 0; index < 3; index++) addCardToZone("Island", actor, ZoneType.Battlefield);
        SpellAbility donate = addCardToZone("Donate", actor, ZoneType.Hand).getFirstSpellAbility();
        donate.setActivatingPlayer(actor);
        donate.getTargets().add(opponent);
        donate.getSubAbility().getTargets().add(gideon);
        paid(actor, donate, 3);
        settle(game);
        Card march = addCardToZone("March of the Machines", actor, ZoneType.Battlefield);
        settle(game);
        gideon = game.getCardState(gideon);
        assertSame(gideon.getController(), opponent);
        assertTrue(gideon.isCreature());
        assertTrue(gideon.isPlaneswalker());
        assertTrue(gideon.isArtifact());
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card dreadmaw = addCardToZone("Colossal Dreadmaw", actor, ZoneType.Battlefield);
        bear.setSickness(false);
        dreadmaw.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 3);
        Combat combat = new Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        assertTrue(CombatUtil.canAttack(bear, opponent));
        assertTrue(CombatUtil.canAttack(dreadmaw, gideon));
        combat.addAttacker(bear, opponent);
        combat.addAttacker(dreadmaw, gideon);
        for (Card attacker : List.of(bear, dreadmaw)) {
            attacker.setTapped(true);
            CombatUtil.checkDeclaredAttacker(game, attacker, combat, true);
            combat.setBlocked(attacker, false);
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, false, 3);
        if (blocking) {
            assertTrue(CombatUtil.canBlock(bear, gideon, combat));
            combat.addBlocker(bear, gideon);
            combat.setBlocked(bear, true);
            combat.orderBlockersForDamageAssignment();
            combat.orderAttackersForDamageAssignment();
        }
        for (int index = 0; index < 2; index++) addCardToZone("Plains", actor, ZoneType.Battlefield);
        game.getPhaseHandler().setPriority(actor);
        SpellAbility disenchant = addCardToZone("Disenchant", actor, ZoneType.Hand).getFirstSpellAbility();
        disenchant.setActivatingPlayer(actor);
        disenchant.getTargets().add(march);
        paid(actor, disenchant, 2);
        if (countered) {
            for (int index = 0; index < 2; index++) addCardToZone("Island", opponent, ZoneType.Battlefield);
            game.getPhaseHandler().setPriority(opponent);
            SpellAbility counter = addCardToZone("Counterspell", opponent, ZoneType.Hand).getFirstSpellAbility();
            counter.setActivatingPlayer(opponent);
            counter.getTargets().add(game.getStack().peekAbility());
            paid(opponent, counter, 2);
        }
        settle(game);
        gideon = game.getCardState(gideon);
        assertTrue(gideon.isPlaneswalker());
        assertEquals(gideon.isCreature(), countered);
        assertTrue(gideon.isArtifact());
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            opponent = copier.find(opponent);
            bear = copier.find(bear);
            dreadmaw = copier.find(dreadmaw);
            gideon = copier.find(gideon);
            combat = game.getCombat();
        }
        assertSame(combat.getDefenderByAttacker(dreadmaw), gideon,
                "CR 506.4d: losing creature type must preserve the surviving attacked-planeswalker role");
        assertEquals(combat.isBlocking(gideon), blocking && countered);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, actor, false, 3);
        assertTrue(combat.assignCombatDamage(false));
        combat.dealAssignedDamage();
        settle(game);
        assertTrue(game.getCardState(gideon).isInZone(ZoneType.Graveyard));
        assertEquals(game.getCardState(bear).isInZone(ZoneType.Graveyard), blocking && countered);
        assertEquals(opponent.getLife(), blocking ? 40 : 38);
    }

    private void paid(Player actor, SpellAbility ability, int mana) {
        assertTrue(ability.canPlay(), "Native admission: " + ability);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, ability, null));
        assertEquals(ability.getPayingMana().size(), mana);
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
        throw new AssertionError("Dual-role research stack did not settle");
    }

    private record Board(Game game, Player actor, Player opponent, Card gideon) { }
}
