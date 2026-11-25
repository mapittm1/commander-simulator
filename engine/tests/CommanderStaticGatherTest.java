package forge.ai;

import forge.GameCommand;
import forge.ai.simulation.GameCopier;
import forge.card.CardType;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardState;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Initial gathering must retain hidden effects, live removal, callbacks and commands. */
public class CommanderStaticGatherTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }
    private Card subject(Game game, String mode) {
        return actor(game).getCardsIn(mode.equals("manabond") ? ZoneType.Hand : ZoneType.Battlefield).getFirst();
    }
    private Game setup(String mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("gather-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Static gathering"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone(mode.equals("manabond") ? "Opt" : "Grizzly Bears", actor(game), mode.equals("manabond") ? ZoneType.Hand : ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers();
        return game;
    }
    private StaticAbility boost(Card host) {
        return new StaticAbility("Mode$ Continuous | Affected$ Creature.YouCtrl | AddPower$ 1", host, host.getCurrentState());
    }
    private void present(Game game, String mode, boolean expected) {
        Card card = subject(game, mode);
        if (mode.equals("suspected")) {
            assertEquals(card.hasKeyword(Keyword.MENACE), expected);
            assertEquals(card.isSuspected(), expected);
        } else if (mode.equals("flying")) assertEquals(card.hasKeyword(Keyword.FLYING), expected);
        else if (mode.equals("manabond")) assertEquals(card.isLand(), expected);
        else assertEquals(card.getNetPower(), expected ? 3 : 2);
    }
    private static final class CallbackCard extends Card {
        private StaticAbility extra;
        CallbackCard(Game game) { super(game.nextCardId(), game); }
        @Override public void updateStaticAbilities(List<StaticAbility> result, CardState state) {
            super.updateStaticAbilities(result, state);
            if (extra != null) result.add(extra);
        }
    }
    private void check(String mode, int depth) {
        Game original = setup(mode); Card card = subject(original, mode);
        CounterType counter = mode.equals("manabond") ? CounterEnumType.MANABOND : CounterType.getType("Flying");
        if (mode.equals("suspected")) assertTrue(card.setSuspected(true));
        else if (mode.equals("flying") || mode.equals("manabond")) {
            card.setCounters(counter, 1); assertTrue(card.createCounterStatic(counter));
        } else if (mode.equals("intrinsic")) card.addStaticAbility(boost(card));
        original.getAction().checkStaticAbilities(false); original.copyLastState();
        boolean initialEffect = !mode.equals("callback") && !mode.equals("command");
        present(original, mode, initialEffect);
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        present(game, mode, initialEffect); card = subject(game, mode);
        if (mode.equals("command")) {
            final int[] calls = {0};
            card.addStaticCommandList(new Object[]{"1", "EQ1", card, new GameCommand() {
                private static final long serialVersionUID = 1L;
                @Override public void run() { calls[0]++; }
            }});
            game.getAction().checkStaticAbilities(false);
            assertEquals(calls[0], 1); assertTrue(card.getStaticCommandList().isEmpty());
            game.getAction().checkStaticAbilities(false); assertEquals(calls[0], 1);
        } else if (mode.equals("callback")) {
            CallbackCard effect = new CallbackCard(game); effect.setOwner(actor(game));
            effect.setName("Static callback control"); effect.getCurrentState().setType(CardType.parse("Artifact", false));
            actor(game).getZone(ZoneType.Battlefield).add(effect); effect.extra = boost(effect);
            game.getAction().checkStaticAbilities(false); present(game, mode, true);
            effect.extra = null; game.getAction().checkStaticAbilities(false); present(game, mode, false);
        } else {
            if (mode.equals("suspected")) assertTrue(card.setSuspected(false));
            else if (mode.equals("intrinsic")) assertTrue(card.getCurrentState().removeStaticAbility(card.getStaticAbilities().iterator().next()));
            else card.setCounters(counter, 0);
            game.getAction().checkStaticAbilities(false); present(game, mode, false);
        }
        if (depth > 0) present(original, mode, initialEffect);
    }
    @Test public void suspectedDepth0() { check("suspected", 0); }
    @Test public void suspectedDepth1() { check("suspected", 1); }
    @Test public void suspectedDepth2() { check("suspected", 2); }
    @Test public void flyingDepth0() { check("flying", 0); }
    @Test public void flyingDepth1() { check("flying", 1); }
    @Test public void flyingDepth2() { check("flying", 2); }
    @Test public void manabondDepth0() { check("manabond", 0); }
    @Test public void manabondDepth1() { check("manabond", 1); }
    @Test public void manabondDepth2() { check("manabond", 2); }
    @Test public void intrinsicDepth0() { check("intrinsic", 0); }
    @Test public void intrinsicDepth1() { check("intrinsic", 1); }
    @Test public void intrinsicDepth2() { check("intrinsic", 2); }
    @Test public void callbackDepth0() { check("callback", 0); }
    @Test public void callbackDepth1() { check("callback", 1); }
    @Test public void callbackDepth2() { check("callback", 2); }
    @Test public void commandDepth0() { check("command", 0); }
    @Test public void commandDepth1() { check("command", 1); }
    @Test public void commandDepth2() { check("command", 2); }
}
