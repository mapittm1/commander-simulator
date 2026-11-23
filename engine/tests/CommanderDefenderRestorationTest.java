package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
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

/** Paid type restoration must not put a removed defender back into its old combat. */
public class CommanderDefenderRestorationTest extends AITest {
    private enum Defender { BATTLE, PLANESWALKER }
    private enum Trample { ORDINARY, OVER_PLANESWALKERS }
    private enum World { ORIGINAL, COPIED, COPIED_PENDING, NESTED_PENDING }
    private enum Restoration { RESOLVES, COUNTERED }

    @Test
    public void nativeRestoredBattleOrdinaryTrampleRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.ORDINARY, World.ORIGINAL, Restoration.RESOLVES);
    }

    @Test
    public void copiedRestoredBattleOrdinaryTrampleRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.ORDINARY, World.COPIED, Restoration.RESOLVES);
    }

    @Test
    public void nativeRestoredBattleOverPlaneswalkersRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.ORIGINAL, Restoration.RESOLVES);
    }

    @Test
    public void copiedRestoredBattleOverPlaneswalkersRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.COPIED, Restoration.RESOLVES);
    }

    @Test
    public void nativeCounteredBattleRestorationPreservesRemovedCombat() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.ORIGINAL, Restoration.COUNTERED);
    }

    @Test
    public void copiedCounteredBattleRestorationPreservesRemovedCombat() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.COPIED, Restoration.COUNTERED);
    }

    @Test
    public void nativeRestoredWalkerOrdinaryTrampleRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.ORDINARY, World.ORIGINAL, Restoration.RESOLVES);
    }

    @Test
    public void copiedRestoredWalkerOrdinaryTrampleRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.ORDINARY, World.COPIED, Restoration.RESOLVES);
    }

    @Test
    public void nativeRestoredWalkerOverPlaneswalkersRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.ORIGINAL, Restoration.RESOLVES);
    }

    @Test
    public void copiedRestoredWalkerOverPlaneswalkersRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.COPIED, Restoration.RESOLVES);
    }

    @Test
    public void nativeCounteredWalkerRestorationPreservesRemovedCombat() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.ORIGINAL, Restoration.COUNTERED);
    }

    @Test
    public void copiedCounteredWalkerRestorationPreservesRemovedCombat() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.COPIED, Restoration.COUNTERED);
    }

    @Test
    public void pendingBattleOrdinaryRestorationRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.ORDINARY, World.COPIED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void pendingBattleOverPlaneswalkersRestorationRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.COPIED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void pendingBattleCounteredRestorationRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.COPIED_PENDING, Restoration.COUNTERED);
    }

    @Test
    public void nestedPendingBattleOrdinaryRestorationRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.ORDINARY, World.NESTED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void nestedPendingBattleOverPlaneswalkersRestorationRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.NESTED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void nestedPendingBattleCounteredRestorationRetainsLegalDamage() {
        restoration(Defender.BATTLE, Trample.OVER_PLANESWALKERS, World.NESTED_PENDING, Restoration.COUNTERED);
    }

    @Test
    public void pendingWalkerOrdinaryRestorationRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.ORDINARY, World.COPIED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void pendingWalkerOverPlaneswalkersRestorationRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.COPIED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void pendingWalkerCounteredRestorationRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.COPIED_PENDING, Restoration.COUNTERED);
    }

    @Test
    public void nestedPendingWalkerOrdinaryRestorationRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.ORDINARY, World.NESTED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void nestedPendingWalkerOverPlaneswalkersRestorationRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.NESTED_PENDING, Restoration.RESOLVES);
    }

    @Test
    public void nestedPendingWalkerCounteredRestorationRetainsLegalDamage() {
        restoration(Defender.PLANESWALKER, Trample.OVER_PLANESWALKERS, World.NESTED_PENDING, Restoration.COUNTERED);
    }

    /** Combat and priority checkpoints are supplied; all spell payments and resolution are native. */
    private void restoration(Defender kind, Trample trample, World world, Restoration response) {
        Board board = board();
        boolean walker = kind == Defender.PLANESWALKER;
        Player defendingPlayer = walker ? board.battle.getProtectingPlayer() : board.actor;
        Card defender = walker
                ? addCardToZone("Jace, the Mind Sculptor", defendingPlayer, ZoneType.Battlefield)
                : board.battle;
        if (walker) {
            defender.setCounters(CounterEnumType.LOYALTY, 3);
        }
        String attackerName = trample == Trample.OVER_PLANESWALKERS
                ? "Thrasta, Tempest's Roar" : "Colossal Dreadmaw";
        Card attacker = declareAttack(board, attackerName, defender);
        int power = attacker.getNetPower();
        List<Integer> initialLife = life(board.game);
        board.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, board.actor, false, 3);
        Player caster = board.game.getPlayers().get(2);
        Card aura = turnIntoForest(board.game, caster, defender);
        Card physical = board.game.getCardState(defender);
        assertTrue(physical.isInZone(ZoneType.Battlefield));
        assertFalse(physical.isBattle());
        assertFalse(physical.isPlaneswalker());
        assertTrue(physical.isLand());
        assertTrue(physical.getType().hasSubtype("Forest"));
        assertSame(aura.getEntityAttachedTo(), physical);

        Removal removal = queueAuraRemoval(board.game, board.actor, caster, aura, response);
        boolean pendingCopy = world == World.COPIED_PENDING || world == World.NESTED_PENDING;
        if (!pendingCopy) {
            settle(board.game);
        }
        Game game = board.game;
        Player actor = board.actor;
        int copies = world == World.ORIGINAL ? 0 : world == World.NESTED_PENDING ? 2 : 1;
        for (int index = 0; index < copies; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            defendingPlayer = copier.find(defendingPlayer);
            attacker = copier.find(attacker);
            physical = copier.find(physical);
            aura = copier.find(aura);
            removal = new Removal(copier.find(removal.disenchant),
                    removal.counter == null ? null : copier.find(removal.counter));
        }
        settle(game);
        assertTrue(game.getCardState(removal.disenchant).isInZone(ZoneType.Graveyard));
        if (removal.counter != null) {
            assertTrue(game.getCardState(removal.counter).isInZone(ZoneType.Graveyard));
        }
        physical = game.getCardState(physical);
        aura = game.getCardState(aura);
        boolean restored = response == Restoration.RESOLVES;
        assertTrue(physical.isInZone(ZoneType.Battlefield));
        assertEquals(physical.isBattle(), !walker && restored);
        assertEquals(physical.isPlaneswalker(), walker && restored);
        assertEquals(physical.isLand(), !restored);
        assertEquals(aura.isInZone(ZoneType.Graveyard), restored);
        assertEquals(aura.isInZone(ZoneType.Battlefield), !restored);
        if (pendingCopy) {
            assertEquals(board.game.getStack().size(), response == Restoration.COUNTERED ? 2 : 1,
                    "Resolving a copied response cannot resolve the original stack");
            assertTrue(board.game.getCardState(defender).isLand());
            assertFalse(board.game.getCardState(defender).isBattle());
            assertFalse(board.game.getCardState(defender).isPlaneswalker());
        }
        assertTrue(game.getCombat().isAttacking(attacker), "Removing a defender leaves its attacker in combat");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, actor, false, 3);
        assertTrue(game.getCombat().assignCombatDamage(false));
        game.getCombat().dealAssignedDamage();
        settle(game);

        Card settled = game.getCardState(physical);
        CounterEnumType counter = walker ? CounterEnumType.LOYALTY : CounterEnumType.DEFENSE;
        assertTrue(settled.isInZone(ZoneType.Battlefield));
        assertEquals(settled.getCounters(counter), 3, "Type restoration must not restore the old attack");
        List<Integer> expectedLife = new ArrayList<>(initialLife);
        if (walker && trample == Trample.OVER_PLANESWALKERS) {
            int index = game.getPlayers().indexOf(defendingPlayer);
            expectedLife.set(index, initialLife.get(index) - power);
        }
        assertEquals(life(game), expectedLife, "Only trample over a departed planeswalker reaches its player");
        if (world != World.ORIGINAL) {
            assertEquals(life(board.game), initialLife, "Copied damage cannot change original player life");
            assertEquals(board.game.getCardState(defender).getCounters(counter), 3);
        }
    }

    private Card turnIntoForest(Game game, Player caster, Card defender) {
        game.getPhaseHandler().setPriority(caster);
        addLands("Forest", caster, 3);
        SpellAbility song = addCardToZone("Song of the Dryads", caster, ZoneType.Hand).getFirstSpellAbility();
        song.setActivatingPlayer(caster);
        song.getTargets().add(defender);
        assertFalse(song.canPlay(), "The Aura needs flash during declared combat");
        addCardToZone("Vedalken Orrery", caster, ZoneType.Battlefield);
        settle(game);
        castPaid(caster, song, 3);
        settle(game);
        Card aura = game.getCardState(song.getHostCard());
        assertTrue(aura.isInZone(ZoneType.Battlefield));
        return aura;
    }

    private Removal queueAuraRemoval(Game game, Player actor, Player caster, Card aura, Restoration response) {
        addLands("Plains", actor, 2);
        game.getPhaseHandler().setPriority(actor);
        SpellAbility disenchant = addCardToZone("Disenchant", actor, ZoneType.Hand).getFirstSpellAbility();
        disenchant.setActivatingPlayer(actor);
        disenchant.getTargets().add(aura);
        castPaid(actor, disenchant, 2);
        SpellAbility counter = null;
        if (response == Restoration.COUNTERED) {
            addLands("Island", caster, 2);
            game.getPhaseHandler().setPriority(caster);
            counter = addCardToZone("Counterspell", caster, ZoneType.Hand).getFirstSpellAbility();
            counter.setActivatingPlayer(caster);
            counter.getTargets().add(game.getStack().peekAbility());
            castPaid(caster, counter, 2);
        }
        return new Removal(disenchant.getHostCard(), counter == null ? null : counter.getHostCard());
    }

    private void castPaid(Player actor, SpellAbility spell, int mana) {
        assertTrue(spell.canPlay());
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        assertEquals(spell.getPayingMana().size(), mana);
    }

    private void addLands(String name, Player player, int count) {
        for (int index = 0; index < count; index++) {
            addCardToZone(name, player, ZoneType.Battlefield);
        }
    }

    private Card declareAttack(Board board, String name, Card defender) {
        Card attacker = addCardToZone(name, board.actor, ZoneType.Battlefield);
        attacker.setSickness(false);
        board.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, board.actor, 3);
        assertTrue(CombatUtil.getAllPossibleDefenders(board.actor).contains(defender));
        assertTrue(CombatUtil.canAttack(attacker, defender));
        Combat combat = new Combat(board.actor);
        board.game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker, defender);
        attacker.setTapped(true);
        CombatUtil.checkDeclaredAttacker(board.game, attacker, combat, true);
        combat.setBlocked(attacker, false);
        return attacker;
    }

    private Board board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("defender-restoration-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Defender restoration regression"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        addLands("Plains", actor, 2);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        SpellAbility spell = addCardToZone("Invasion of Gobakhan", actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        castPaid(actor, spell, 2);
        settle(game);
        assertEquals(actor.getSpellsCastThisTurn(), 1);
        assertTrue(actor.getLandsInPlay().stream().allMatch(Card::isTapped));
        Card battle = game.getCardState(spell.getHostCard());
        assertTrue(battle.isInZone(ZoneType.Battlefield));
        assertNotNull(battle.getProtectingPlayer());
        return new Board(game, actor, battle);
    }

    private List<Integer> life(Game game) {
        return game.getPlayers().stream().map(Player::getLife).toList();
    }

    private void settle(Game game) {
        for (int step = 0; step < 40; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) {
                return;
            }
            game.getStack().resolveStack();
        }
        throw new AssertionError("Defender restoration stack did not settle");
    }

    private record Board(Game game, Player actor, Card battle) { }
    private record Removal(Card disenchant, Card counter) { }
}
