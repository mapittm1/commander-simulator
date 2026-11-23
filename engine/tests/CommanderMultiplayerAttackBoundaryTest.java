package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Separate missed native declarations from the rules outcomes of an explicitly selected attack. */
public class CommanderMultiplayerAttackBoundaryTest extends AITest {
    private Game board(String kind, boolean copied) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("opportunity-" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Multiplayer attack opportunity"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        actor.setLife(80, null);
        for (Player player : game.getPlayers()) {
            player.setLife(player == actor ? 80 : 40, null);
            for (int index = 0; index < 12; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 5; index++) addCardToZone("Forest", player, ZoneType.Battlefield);
        }
        String name = kind.equals("vigilance") ? "Serra Angel" : kind.equals("forced") ? "Juggernaut" : kind.equals("group") ? "Mogg Flunkies" : kind.equals("bears") ? "Grizzly Bears" : kind.equals("flying") ? "Serra Angel"
                : kind.equals("defender") ? "Wall of Wood" : "Goreclaw, Terror of Qal Sisma";
        Card creature = addCardToZone(name, actor, ZoneType.Battlefield);
        creature.setSickness(kind.equals("sick"));
        if (kind.equals("group")) addCardToZone("Mogg Flunkies", actor, ZoneType.Battlefield).setSickness(false);
        if (kind.equals("guard") || kind.equals("vigilance")) actor.setLife(7, null);
        if (kind.equals("tax")) {
            addCardToZone("Propaganda", game.getPlayers().get(1), ZoneType.Battlefield);
            for (Card land : actor.getCardsIn(ZoneType.Battlefield)) if (land.isLand()) land.setTapped(true);
        }
        if (kind.equals("peace")) addCardToZone("Peacekeeper", game.getPlayers().get(1), ZoneType.Battlefield);
        if (kind.equals("allblocked")) addCardToZone("Colossal Dreadmaw", game.getPlayers().get(1), ZoneType.Battlefield);
        creature.setTapped(kind.equals("tapped"));
        if (!kind.equals("open")) {
            for (int seat = 2; seat < 4; seat++) {
                addCardToZone((kind.equals("guard") || kind.equals("vigilance")) ? "Craw Wurm" : "Colossal Dreadmaw", game.getPlayers().get(seat), ZoneType.Battlefield);
            }
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 5);
        game.copyLastState();
        if (copied) game = new GameCopier(game).withStack(true).makeCopy(null, actor);
        return game;
    }

    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("opportunity-" + index)).findFirst().orElseThrow();
    }


    private void nativePlanningBoundary(boolean copied, String kind) {
        Game game = board(kind.equals("cap") ? "group" : kind.equals("optionalTax") ? "forced" : kind.equals("vigilance") ? "vigilance" : "goreclaw", false);
        Player actor = seat(game, 0);
        Player open = seat(game, 1);
        Card creature = actor.getCreaturesInPlay().get(0);
        if (kind.equals("paidTax") || kind.equals("optionalTax")) {
            addCardToZone("Propaganda", open, ZoneType.Battlefield);
            addCardToZone("Blazing Archon", seat(game, 2), ZoneType.Battlefield);
            addCardToZone("Blazing Archon", seat(game, 3), ZoneType.Battlefield);
            if (kind.equals("optionalTax")) for (Card land : actor.getLandsInPlay()) land.setTapped(true);
        }
        if (kind.equals("cap")) addCardToZone("Silent Arbiter", open, ZoneType.Battlefield);
        if (kind.equals("goad")) creature.addGoad(game.getNextTimestamp(), open);
        if (kind.equals("controlled")) actor.addController(game.getNextTimestamp(), open);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            open = copier.find(open);
            creature = copier.find(creature);
        }
        Combat combat = new Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        if (kind.equals("expired")) combat.addAttacker(creature, open);
        java.util.Map<Card, forge.game.GameEntity> beforeDeclaration = new LinkedHashMap<>(combat.getAttackersAndDefenders());
        String before = state(game);
        java.util.Random previous = forge.util.MyRandom.getRandom();
        java.util.Random fixed = new java.util.Random(93217L);
        java.util.Random shadow = new java.util.Random(93217L);
        Object deadline = null;
        try {
            forge.util.MyRandom.setRandom(fixed);
            java.util.Random installed = forge.util.MyRandom.getRandom();
            long drawsBefore = forge.util.MyRandom.callCount();
            if (kind.equals("expired")) deadline = forge.util.AITimeoutTracker.arm(System.nanoTime() - 1L, false);
            forge.ai.simulation.MultiplayerAttackPlanner.refine(actor, combat);
            assertSame(forge.util.MyRandom.getRandom(), installed, "Restore the caller's random provider");
            assertEquals(forge.util.MyRandom.callCount(), drawsBefore, "Preview draws cannot invalidate live undo");
            assertEquals(fixed.nextLong(), shadow.nextLong(), "No candidate advances the caller's random state");
        } finally {
            if (deadline != null) forge.util.AITimeoutTracker.disarm(deadline);
            forge.util.MyRandom.setRandom(previous);
        }
        assertEquals(state(game), before, "Search cannot mutate original life, zones, taps or stack");
        assertTrue(CombatUtil.validateAttackers(combat));
        if (kind.equals("controlled") || kind.equals("expired")) {
            assertEquals(new LinkedHashMap<>(combat.getAttackersAndDefenders()), beforeDeclaration);
        } else if (kind.equals("optionalTax") || kind.equals("cap")) {
            assertTrue(combat.getAttackers().isEmpty());
        } else if (kind.equals("goad")) {
            assertTrue(combat.isAttacking(creature));
            assertNotSame(combat.getDefenderByAttacker(creature), open, "Honor goad's defender requirement");
        } else {
            assertTrue(combat.isAttacking(creature));
            assertSame(combat.getDefenderByAttacker(creature), open);
        }
        if (kind.equals("paidTax")) {
            assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
            ((PlayerControllerAi) actor.getController()).getAi().setAttackOverride(new LinkedHashMap<>(combat.getAttackersAndDefenders()));
            game.getPhaseHandler().devDeclareAttackers();
            settle(game, actor);
            assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 2L, "Pay the native tax only on the real declaration");
            final Game actualGame = game;
            final Player actualActor = actor;
            assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(actualGame, actualActor)));
            assertEquals(open.getLife(), 35, "Resolve the selected native trigger and damage");
        }
        System.out.println("PLANNER_BOUNDARY copied=" + copied + " kind=" + kind + " attackers=" + combat.getAttackers().size());
    }

    private String state(Game game) {
        StringBuilder result = new StringBuilder();
        for (Player player : game.getPlayers()) {
            result.append(player.getName()).append(':').append(player.getLife());
            for (ZoneType zone : new ZoneType[] {ZoneType.Battlefield, ZoneType.Hand, ZoneType.Library, ZoneType.Graveyard, ZoneType.Exile}) {
                result.append('|').append(zone);
                for (Card card : player.getCardsIn(zone)) {
                    result.append('/').append(card.getId()).append(':').append(card.isTapped())
                            .append(':').append(card.getNetPower()).append(':').append(card.getNetToughness());
                }
            }
        }
        return result.append("stack:").append(game.getStack().size()).toString();
    }

    private void settle(Game game, Player actor) {
        GameSimulator.resolveStack(game, actor);
    }
    // Named entry points keep every fixed case unique in native evidence.
    @Test public void rngOriginal() { nativePlanningBoundary(false, "rng"); }
    @Test public void expiredOriginal() { nativePlanningBoundary(false, "expired"); }
    @Test public void controlledOriginal() { nativePlanningBoundary(false, "controlled"); }
    @Test public void paidTaxOriginal() { nativePlanningBoundary(false, "paidTax"); }
    @Test public void optionalTaxOriginal() { nativePlanningBoundary(false, "optionalTax"); }
    @Test public void capOriginal() { nativePlanningBoundary(false, "cap"); }
    @Test public void goadOriginal() { nativePlanningBoundary(false, "goad"); }
    @Test public void vigilanceOriginal() { nativePlanningBoundary(false, "vigilance"); }
    @Test public void rngCopied() { nativePlanningBoundary(true, "rng"); }
    @Test public void expiredCopied() { nativePlanningBoundary(true, "expired"); }
    @Test public void controlledCopied() { nativePlanningBoundary(true, "controlled"); }
    @Test public void paidTaxCopied() { nativePlanningBoundary(true, "paidTax"); }
    @Test public void optionalTaxCopied() { nativePlanningBoundary(true, "optionalTax"); }
    @Test public void capCopied() { nativePlanningBoundary(true, "cap"); }
    @Test public void goadCopied() { nativePlanningBoundary(true, "goad"); }
    @Test public void vigilanceCopied() { nativePlanningBoundary(true, "vigilance"); }

}
