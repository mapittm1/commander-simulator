package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Normally paid commander cast, real untap/combat and subsequent historical copies. */
public class CommanderDepartedPaidHistoryTest extends AITest {
    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("history-" + index)).findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, game.getPlayers().get(0));
    }

    private void verifyHistory(String change, int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game original = new Game(seats, rules, new Match(rules, seats, "Departed commander history"));
        original.setNoGUIUser();
        original.setAge(GameStage.Play);
        for (Player player : original.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 16; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 5; index++) addCardToZone("Forest", player, ZoneType.Battlefield);
        }
        Player owner = seat(original, 1);
        Player victim = seat(original, 0);
        Card commander = addCardToZone("Goreclaw, Terror of Qal Sisma", owner, ZoneType.Command);
        owner.addCommander(commander);
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 5);
        original.getAction().checkStaticAbilities(false);
        forge.game.spellability.SpellAbility printed = commander.getFirstSpellAbility();
        printed.setActivatingPlayer(owner);
        forge.game.spellability.SpellAbility spell = null;
        for (forge.game.spellability.SpellAbility option : forge.game.GameActionUtil.getAlternativeCosts(printed, owner, false)) {
            option.setActivatingPlayer(owner);
            if (option.canPlay()) {
                spell = option;
                break;
            }
        }
        assertNotNull(spell, "Use the native command-zone play permission");
        assertTrue(spell.canPlay(), "Commander is legally castable from its command zone");
        assertTrue(ComputerUtil.playStack(spell, owner, original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 4, "Pay the printed commander cost");
        original.getStack().resolveStack();
        settle(original);
        commander = original.getCardState(commander);
        assertTrue(commander.isInZone(ZoneType.Battlefield));
        assertEquals(owner.getCommanderCast(commander), 1);
        assertTrue(commander.hasSickness(), "Normal casting does not grant haste");
        assertNotNull(commander.getCastSA());
        assertEquals(commander.getCastSA().getPayingMana().size(), 4);
        // Reach the next actual turn to clear summoning sickness normally.
        for (int turn = 0; turn < 5; turn++) {
            assertTrue(original.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(original)));
            if (original.getPhaseHandler().getPlayerTurn() == owner) break;
        }
        assertSame(original.getPhaseHandler().getPlayerTurn(), owner);
        assertFalse(commander.hasSickness());
        Map<Card, GameEntity> declaration = new LinkedHashMap<>();
        declaration.put(commander, victim);
        ((PlayerControllerAi) owner.getController()).getAi().setAttackOverride(declaration);
        assertTrue(original.getPhaseHandler().devAdvanceToPhase(PhaseType.COMBAT_DECLARE_ATTACKERS, () -> settle(original)));
        settle(original);
        assertEquals(commander.getNetPower(), 5);
        assertTrue(original.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(original)));
        settle(original);
        assertEquals(victim.getLife(), 95, "Actual unblocked native combat damage");
        assertEquals(victim.getCommanderDamage(commander), 5);
        // Move to a surviving participant's real next turn before eliminating
        // the source owner; the snapshot must start from a valid active seat.
        assertTrue(original.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(original)));
        assertSame(original.getPhaseHandler().getPlayerTurn(), seat(original, 2));
        boolean departed = change.equals("concession") || change.equals("zeroLife") || change.equals("poison");
        if (change.equals("concession")) owner.concede();
        if (change.equals("zeroLife")) owner.setLife(0, null);
        if (change.equals("poison")) owner.setPoisonCounters(10, victim);
        if (change.equals("command")) original.getAction().moveToCommand(commander, null);
        if (change.equals("exile")) original.getAction().exile(commander, null, Map.of());
        original.getAction().checkStateEffects(true);
        assertEquals(owner.hasLost(), departed);
        assertEquals(victim.getCommanderDamage(commander), 5);
        if (departed) assertNull(original.getCardState(commander, null), "Departed owner's card is no longer a live game object");
        original.copyLastState();
        Game copied = original;
        for (int index = 0; index < depth; index++) {
            copied = new GameCopier(copied).withStack(true).makeCopy(null, seat(copied, 2));
        }
        Player copiedVictim = seat(copied, 0);
        Player copiedOwner = copied.getPlayer(owner.getId());
        assertEquals(copiedOwner.hasLost(), departed);
        Card key = null;
        for (Map.Entry<Card, Integer> entry : copiedVictim.getCommanderDamage()) {
            if (entry.getKey().getId() == commander.getId()) {
                assertNull(key, "One damage-history entry per physical commander");
                key = entry.getKey();
                assertEquals(entry.getValue().intValue(), 5);
            }
        }
        assertNotNull(key, "Retain historical commander damage even after its owner leaves");
        assertSame(key.getGame(), copied);
        assertSame(key.getOwner(), copiedOwner);
        assertNotSame(key, commander);
        Card expectedState = original.getCardState(commander, commander);
        if (expectedState.getCastSA() == null) {
            // A real zone change creates a new object whose cast state is reset.
            assertNull(key.getCastSA(), "Match the current object's native cast state");
        } else {
            assertNotNull(key.getCastSA(), "Retain the paid cast provenance");
            assertSame(key.getCastSA().getHostCard().getGame(), copied);
            assertSame(key.getCastSA().getActivatingPlayer(), copiedOwner);
            assertEquals(key.getCastSA().getPayingMana().size(), expectedState.getCastSA().getPayingMana().size());
            for (forge.game.mana.Mana mana : key.getCastSA().getPayingMana()) {
                assertSame(mana.getSourceCard().getGame(), copied, "Paid mana sources belong to the copied world");
            }
        }
        if (departed) {
            assertFalse(copied.getPlayers().contains(copiedOwner));
            assertNull(copied.getCardState(key, null), "Historical remapping must not resurrect the commander");
        } else {
            assertSame(key, copied.getCardState(key));
        }
        copiedVictim.addCommanderDamage(key, 1);
        assertEquals(copiedVictim.getCommanderDamage(key), 6);
        assertEquals(victim.getCommanderDamage(commander), 5, "Copied history mutation cannot change the original");
        victim.addCommanderDamage(commander, 2);
        assertEquals(copiedVictim.getCommanderDamage(key), 6, "Original mutation cannot change copied history");
    }
    @Test public void livingAfterOneCopy() { verifyHistory("living", 1); }
    @Test public void livingAfterTwoCopies() { verifyHistory("living", 2); }
    @Test public void concessionAfterOneCopy() { verifyHistory("concession", 1); }
    @Test public void concessionAfterTwoCopies() { verifyHistory("concession", 2); }
    @Test public void zeroLifeAfterOneCopy() { verifyHistory("zeroLife", 1); }
    @Test public void zeroLifeAfterTwoCopies() { verifyHistory("zeroLife", 2); }
    @Test public void poisonAfterOneCopy() { verifyHistory("poison", 1); }
    @Test public void poisonAfterTwoCopies() { verifyHistory("poison", 2); }
    @Test public void commandAfterOneCopy() { verifyHistory("command", 1); }
    @Test public void commandAfterTwoCopies() { verifyHistory("command", 2); }
    @Test public void exileAfterOneCopy() { verifyHistory("exile", 1); }
    @Test public void exileAfterTwoCopies() { verifyHistory("exile", 2); }
}
