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
import forge.game.card.CounterType;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Existing counters and their derived state must survive world copies in every zone. */
public class CommanderCardCounterZoneCopyTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }
    private ZoneType zone(String mode) {
        switch (mode) {
            case "battlefield": return ZoneType.Battlefield;
            case "grave": case "graveFlying": return ZoneType.Graveyard;
            case "hand": return ZoneType.Hand;
            case "library": return ZoneType.Library;
            case "command": return ZoneType.Command;
            default: return ZoneType.Exile;
        }
    }
    private CounterType counter(String mode) {
        if (mode.equals("suspend")) return CounterEnumType.TIME;
        return mode.endsWith("Flying") ? CounterType.getType("Flying") : CounterEnumType.P1P1;
    }
    private Card subject(Game game, String mode, int id) {
        return actor(game).getCardsIn(zone(mode)).stream().filter(card -> card.getId() == id).findFirst().orElseThrow();
    }
    private void state(Card card, String mode, int expected) {
        assertEquals(card.getCounters(counter(mode)), expected, mode + " counter data in " + card.getZone());
        if (mode.equals("suspend")) assertTrue(card.hasSuspend(), "Existing suspended state must remain visible");
        if (mode.endsWith("Flying")) assertTrue(card.hasKeyword(Keyword.FLYING), "Off-battlefield keyword-counter effect");
    }
    private void check(String mode, int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("counter-zone-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game original = new Game(seats, rules, new Match(rules, seats, "All-zone counter state"));
        original.setNoGUIUser(); original.setAge(GameStage.Play);
        for (Player player : original.getPlayers()) {
            player.setLife(100, null);
            if (player != actor(original) || !mode.equals("library")) {
                for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        String name = mode.equals("suspend") ? "Ancestral Vision" : (mode.equals("grave") || mode.equals("command") || mode.endsWith("Flying") ? "Skullbriar, the Walking Grave" : "Grizzly Bears");
        Card card = addCardToZone(name, actor(original), zone(mode));
        if (mode.equals("command")) card.setCommander(true);
        int count = mode.equals("suspend") ? 3 : (mode.endsWith("Flying") ? 1 : 2);
        card.setCounters(counter(mode), count);
        if (mode.endsWith("Flying")) assertTrue(card.createCounterStatic(counter(mode)));
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(original), 5);
        original.getAction().checkStaticAbilities(false); original.getTriggerHandler().resetActiveTriggers(); original.copyLastState();
        final int id = card.getId(); state(card, mode, count);
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
            state(subject(game, mode, id), mode, count);
        }
        if (depth > 0) {
            Card copied = subject(game, mode, id);
            copied.setCounters(counter(mode), count + 1);
            assertEquals(card.getCounters(counter(mode)), count, "Copied counter storage must be independent");
            card.setCounters(counter(mode), count + 2);
            assertEquals(copied.getCounters(counter(mode)), count + 1);
        }
    }
    @Test public void battlefieldDepth0() { check("battlefield", 0); }
    @Test public void battlefieldDepth1() { check("battlefield", 1); }
    @Test public void battlefieldDepth2() { check("battlefield", 2); }
    @Test public void graveDepth0() { check("grave", 0); }
    @Test public void graveDepth1() { check("grave", 1); }
    @Test public void graveDepth2() { check("grave", 2); }
    @Test public void handDepth0() { check("hand", 0); }
    @Test public void handDepth1() { check("hand", 1); }
    @Test public void handDepth2() { check("hand", 2); }
    @Test public void libraryDepth0() { check("library", 0); }
    @Test public void libraryDepth1() { check("library", 1); }
    @Test public void libraryDepth2() { check("library", 2); }
    @Test public void commandDepth0() { check("command", 0); }
    @Test public void commandDepth1() { check("command", 1); }
    @Test public void commandDepth2() { check("command", 2); }
    @Test public void suspendDepth0() { check("suspend", 0); }
    @Test public void suspendDepth1() { check("suspend", 1); }
    @Test public void suspendDepth2() { check("suspend", 2); }
    @Test public void graveFlyingDepth0() { check("graveFlying", 0); }
    @Test public void graveFlyingDepth1() { check("graveFlying", 1); }
    @Test public void graveFlyingDepth2() { check("graveFlying", 2); }
    @Test public void exileFlyingDepth0() { check("exileFlying", 0); }
    @Test public void exileFlyingDepth1() { check("exileFlying", 1); }
    @Test public void exileFlyingDepth2() { check("exileFlying", 2); }
    private void stack(int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("counter-stack-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game original = new Game(seats, rules, new Match(rules, seats, "Paid stack counter snapshot"));
        original.setNoGUIUser(); original.setAge(GameStage.Play);
        for (Player player : original.getPlayers()) {
            player.setLife(100, null); addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int index = 0; index < 3; index++) addCardToZone("Mountain", actor(original), ZoneType.Battlefield);
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(original), 5);
        SpellAbility spell = addCardToZone("Lightning Storm", actor(original), ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor(original)); spell.getTargets().add(original.getPlayers().get(1));
        assertTrue(spell.canPlay()); assertTrue(ComputerUtil.playStack(spell, actor(original), original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 3);
        Card source = original.getStack().peekAbility().getHostCard(); source.setCounters(CounterEnumType.CHARGE, 2);
        assertTrue(source.isInZone(ZoneType.Stack)); original.copyLastState();
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
            Card copied = game.getStack().peekAbility().getHostCard();
            assertEquals(copied.getCounters(CounterEnumType.CHARGE), 2, "Existing stack charge counters");
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
            assertTrue(copied.isInZone(ZoneType.Stack));
        }
        if (depth > 0) {
            Card copied = game.getStack().peekAbility().getHostCard(); copied.setCounters(CounterEnumType.CHARGE, 3);
            assertEquals(source.getCounters(CounterEnumType.CHARGE), 2);
            source.setCounters(CounterEnumType.CHARGE, 4); assertEquals(copied.getCounters(CounterEnumType.CHARGE), 3);
        }
    }
    @Test public void stackDepth0() { stack(0); }
    @Test public void stackDepth1() { stack(1); }
    @Test public void stackDepth2() { stack(2); }

}
