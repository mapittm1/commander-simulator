package forge.ai.simulation;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.LobbyPlayerAi;
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
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Fixed resolver boundary checks; not a complete free-cast card-effect witness. */
public class ResolvingControlForecastTest extends AITest {
    private record Context(Game game, Player actor, SpellAbility action, int library) { }

    private Context resolving() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("resolver" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Resolving control"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, actor, 1);
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int i = 0; i < 3; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        Card parent = addCardToZone("Divination", actor, ZoneType.Hand);
        SpellAbility draw = parent.getFirstSpellAbility();
        draw.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(draw, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        // Establish an in-progress resolver boundary, as in the existing free-cast
        // safety tests. Divination itself does not grant a free cast.
        game.getStack().setResolving(true);
        SpellAbility action = addCardToZone("Worst Fears", actor, ZoneType.Hand)
                .getFirstSpellAbility().copyWithNoManaCost(actor);
        action.setCastFromPlayEffect(true);
        action.setActivatingPlayer(actor);
        action.getTargets().add(game.getPlayers().get(1));
        assertTrue(action.canPlay(), "Fixed effect-cast admission must be legal");
        return new Context(game, actor, action, actor.getCardsIn(ZoneType.Library).size());
    }

    private void unchanged(Context context) {
        assertEquals(context.game().getStack().size(), 1);
        assertTrue(context.game().getStack().isResolving());
        assertEquals(context.actor().getCardsIn(ZoneType.Library).size(), context.library());
        assertFalse(PlayerControlPlanner.isEvaluating());
    }

    @Test public void unplayedFutureDeclinesResolvingParent() {
        Context context = resolving();
        assertNull(PlayerControlPlanner.assess(context.actor(), context.action()),
                "Do not forecast through a parent whose remaining resolution is unknown");
        unchanged(context);
    }

    @Test public void paidFutureDeclinesResolvingParent() {
        Context context = resolving();
        // The full constructor retains the prior incremental no-replay safeguard.
        GameSimulator paid = new GameSimulator(new SimulationController(new GameStateEvaluator.Score(0), 0),
                context.game(), context.actor(), null);
        assertTrue(paid.getSimulatedGameState().getStack().isEmpty());
        assertTrue(paid.applySpellAbility(context.action()));
        Player copiedActor = paid.getGameCopier().find(context.actor());
        assertNull(PlayerControlPlanner.assessAfter(context.game(), context.actor(),
                paid.getSimulatedGameState(), copiedActor, copiedActor, paid.getLastPlayedAbility()),
                "A completed child action does not make the resolving parent replayable");
        unchanged(context);
    }
    @Test public void resolvingControlStillChecksLethalCastTriggers() {
        Context context = resolving();
        context.actor().setLife(2, null);
        addCardToZone("Ruric Thar, the Unbowed", context.game().getPlayers().get(2), ZoneType.Battlefield);
        context.game().getAction().checkStateEffects(true);
        assertFalse(OnePlaySafetyChecker.isAcceptable(context.actor(), context.action()),
                "An excluded future forecast must not bypass incremental lethal-cast safety");
        assertEquals(context.actor().getLife(), 2, "The check must leave original life unchanged");
        unchanged(context);
    }
}
