package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.MultiplayerAttackPlanner;
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
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** The only legal declaration remains empty without mutating live game state. */
public class CommanderEmptyAttackTest extends AITest {
    private Player actor(Game game) {
        return game.getRegisteredPlayers().stream()
                .filter(player -> player.getName().equals("empty-0")).findFirst().orElseThrow();
    }

    private List<String> state(Game game) {
        List<String> result = new ArrayList<>();
        result.add("phase=" + game.getPhaseHandler().getPhase() + ";turn=" + game.getPhaseHandler().getPlayerTurn().getId());
        for (Player player : game.getRegisteredPlayers()) {
            result.add("player=" + player.getId() + ";life=" + player.getLife() + ";mana=" + player.getManaPool().totalMana());
        }
        for (Card card : game.getCardsInGame()) {
            result.add("card=" + card.getId() + ";zone=" + card.getZone().getZoneType() + ";tap=" + card.isTapped()
                    + ";creature=" + card.isCreature() + ";loyalty=" + card.getCounters(CounterEnumType.LOYALTY));
        }
        return result;
    }

    private void check(String mode, int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("empty-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game original = new Game(seats, rules, new Match(rules, seats, "No creature attack declaration"));
        original.setNoGUIUser();
        original.setAge(GameStage.Play);
        for (Player player : original.getPlayers()) {
            player.setLife(40, null);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Player payer = actor(original);
        if (mode.equals("artifact")) addCardToZone("Sol Ring", payer, ZoneType.Battlefield);
        else if (mode.equals("planeswalker")) {
            Card gideon = addCardToZone("Gideon Jura", payer, ZoneType.Battlefield);
            gideon.setCounters(CounterEnumType.LOYALTY, 6);
        } else if (mode.equals("vehicle")) addCardToZone("Smuggler's Copter", payer, ZoneType.Battlefield);
        else assertEquals(mode, "empty");
        Player opponent = original.getPlayers().stream().filter(player -> player != payer).findFirst().orElseThrow();
        Card threat = addCardToZone("Grizzly Bears", opponent, ZoneType.Battlefield);
        threat.setSickness(false);
        original.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, payer, 5);
        original.getAction().checkStaticAbilities(false);
        original.getTriggerHandler().resetActiveTriggers();
        original.getPhaseHandler().setCombat(new Combat(payer));
        original.copyLastState();
        List<String> originalBefore = state(original);
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        Player active = actor(game);
        assertTrue(active.getCreaturesInPlay().isEmpty());
        Combat combat = game.getCombat();
        assertTrue(combat.getAttackers().isEmpty());
        assertTrue(CombatUtil.validateAttackers(combat));
        List<String> before = state(game);
        long expectedRandom = new Random(472L).nextLong();
        long observedRandom = MyRandom.withRandom(new Random(472L), () -> {
            MultiplayerAttackPlanner.refine(active, combat);
            return MyRandom.getRandom().nextLong();
        });
        assertEquals(observedRandom, expectedRandom);
        assertTrue(combat.getAttackers().isEmpty());
        assertTrue(CombatUtil.validateAttackers(combat));
        assertEquals(state(game), before);
        ((PlayerControllerAi) active.getController()).declareAttackers(active, combat);
        assertTrue(combat.getAttackers().isEmpty());
        assertTrue(CombatUtil.validateAttackers(combat));
        assertEquals(state(game), before);
        assertEquals(state(original), originalBefore);
    }
    @Test public void emptyDepth0() { check("empty", 0); }
    @Test public void emptyDepth1() { check("empty", 1); }
    @Test public void emptyDepth2() { check("empty", 2); }
    @Test public void artifactDepth0() { check("artifact", 0); }
    @Test public void artifactDepth1() { check("artifact", 1); }
    @Test public void artifactDepth2() { check("artifact", 2); }
    @Test public void planeswalkerDepth0() { check("planeswalker", 0); }
    @Test public void planeswalkerDepth1() { check("planeswalker", 1); }
    @Test public void planeswalkerDepth2() { check("planeswalker", 2); }
    @Test public void vehicleDepth0() { check("vehicle", 0); }
    @Test public void vehicleDepth1() { check("vehicle", 1); }
    @Test public void vehicleDepth2() { check("vehicle", 2); }
}
