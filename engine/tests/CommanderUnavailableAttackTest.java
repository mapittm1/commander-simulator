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

/** Controlled declarations distinguish unavailable attackers from legal alternatives. */
public class CommanderUnavailableAttackTest extends AITest {
    private Player seat(Game game, int index) {
        return game.getRegisteredPlayers().stream()
                .filter(player -> player.getName().equals("unavailable-" + index)).findFirst().orElseThrow();
    }

    private List<String> state(Game game) {
        List<String> result = new ArrayList<>();
        result.add("phase=" + game.getPhaseHandler().getPhase() + ";turn=" + game.getPhaseHandler().getPlayerTurn().getId());
        for (Player player : game.getRegisteredPlayers()) {
            result.add("player=" + player.getId() + ";life=" + player.getLife() + ";mana=" + player.getManaPool().totalMana());
        }
        for (Card card : game.getCardsInGame()) {
            result.add("card=" + card.getId() + ";zone=" + card.getZone().getZoneType() + ";tap=" + card.isTapped()
                    + ";sick=" + card.hasSickness() + ";goad=" + card.getGoadMap());
        }
        return result;
    }

    private Game board(String mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("unavailable-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Unavailable and alternative attackers"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Player actor = seat(game, 0);
        String name = switch (mode) {
            case "defender" -> "Wall of Wood";
            case "haste" -> "Raging Goblin";
            case "oneOpen", "required" -> "Juggernaut";
            case "group" -> "Mogg Flunkies";
            default -> "Grizzly Bears";
        };
        Card creature = addCardToZone(name, actor, ZoneType.Battlefield);
        creature.setSickness(mode.equals("sick") || mode.equals("haste"));
        creature.setTapped(mode.equals("tapped") || mode.equals("goadTapped"));
        if (mode.equals("group")) addCardToZone("Mogg Flunkies", actor, ZoneType.Battlefield).setSickness(false);
        if (mode.equals("peace")) addCardToZone("Peacekeeper", seat(game, 1), ZoneType.Battlefield);
        if (mode.equals("archons") || mode.equals("oneOpen")) {
            for (int index = 1; index < (mode.equals("archons") ? 4 : 3); index++) {
                addCardToZone("Blazing Archon", seat(game, index), ZoneType.Battlefield);
            }
        }
        if (mode.equals("goadTapped") || mode.equals("goadReady")) {
            creature.addGoad(game.getNextTimestamp(), seat(game, 1));
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 5);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        game.getPhaseHandler().setCombat(new Combat(actor));
        game.copyLastState();
        return game;
    }

    private void check(String mode, int depth) {
        Game original = board(mode);
        List<String> originalBefore = state(original);
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, seat(game, 0));
            copier.release();
        }
        Player actor = seat(game, 0);
        Combat combat = game.getCombat();
        boolean positive = List.of("haste", "oneOpen", "required", "group", "goadReady").contains(mode);
        assertFalse(actor.getCreaturesInPlay().isEmpty(), "Every control has an actual creature");
        boolean available = false;
        for (Card creature : actor.getCreaturesInPlay()) {
            for (var defender : combat.getDefenders()) available |= CombatUtil.canAttack(creature, defender);
        }
        assertEquals(available, positive, "Native attacking availability: " + mode);
        List<String> before = state(game);
        long expected = new Random(472L).nextLong();
        long observed = MyRandom.withRandom(new Random(472L), () -> {
            MultiplayerAttackPlanner.refine(actor, combat);
            return MyRandom.getRandom().nextLong();
        });
        assertEquals(observed, expected, "Planning restores its random provider");
        assertDeclaration(game, mode, positive);
        assertEquals(state(game), before, "A forecast must not play the live game");
        ((PlayerControllerAi) actor.getController()).declareAttackers(actor, combat);
        assertDeclaration(game, mode, positive);
        assertEquals(state(game), before, "Native attack selection cannot apply simulated effects");
        assertEquals(state(original), originalBefore, "Copied planning cannot mutate the source");
    }

    private void assertDeclaration(Game game, String mode, boolean positive) {
        Combat combat = game.getCombat();
        assertTrue(CombatUtil.validateAttackers(combat), "Native restrictions and requirements: " + mode);
        assertEquals(!combat.getAttackers().isEmpty(), positive, "Beneficial/required attacks remain visible: " + mode);
        if (mode.equals("group")) assertEquals(combat.getAttackers().size(), 2, "Cannot attack alone is a group restriction");
        if (mode.equals("oneOpen")) {
            for (Card card : combat.getAttackers()) assertSame(combat.getDefenderByAttacker(card), seat(game, 3));
        }
        if (mode.equals("goadReady")) {
            for (Card card : combat.getAttackers()) assertNotSame(combat.getDefenderByAttacker(card), seat(game, 1));
        }
    }
    @Test public void tappedDepth0() { check("tapped", 0); }
    @Test public void tappedDepth1() { check("tapped", 1); }
    @Test public void tappedDepth2() { check("tapped", 2); }
    @Test public void sickDepth0() { check("sick", 0); }
    @Test public void sickDepth1() { check("sick", 1); }
    @Test public void sickDepth2() { check("sick", 2); }
    @Test public void defenderDepth0() { check("defender", 0); }
    @Test public void defenderDepth1() { check("defender", 1); }
    @Test public void defenderDepth2() { check("defender", 2); }
    @Test public void peaceDepth0() { check("peace", 0); }
    @Test public void peaceDepth1() { check("peace", 1); }
    @Test public void peaceDepth2() { check("peace", 2); }
    @Test public void archonsDepth0() { check("archons", 0); }
    @Test public void archonsDepth1() { check("archons", 1); }
    @Test public void archonsDepth2() { check("archons", 2); }
    @Test public void goadTappedDepth0() { check("goadTapped", 0); }
    @Test public void goadTappedDepth1() { check("goadTapped", 1); }
    @Test public void goadTappedDepth2() { check("goadTapped", 2); }
    @Test public void hasteDepth0() { check("haste", 0); }
    @Test public void hasteDepth1() { check("haste", 1); }
    @Test public void hasteDepth2() { check("haste", 2); }
    @Test public void oneOpenDepth0() { check("oneOpen", 0); }
    @Test public void oneOpenDepth1() { check("oneOpen", 1); }
    @Test public void oneOpenDepth2() { check("oneOpen", 2); }
    @Test public void requiredDepth0() { check("required", 0); }
    @Test public void requiredDepth1() { check("required", 1); }
    @Test public void requiredDepth2() { check("required", 2); }
    @Test public void groupDepth0() { check("group", 0); }
    @Test public void groupDepth1() { check("group", 1); }
    @Test public void groupDepth2() { check("group", 2); }
    @Test public void goadReadyDepth0() { check("goadReady", 0); }
    @Test public void goadReadyDepth1() { check("goadReady", 1); }
    @Test public void goadReadyDepth2() { check("goadReady", 2); }
}
