package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
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

/** Paid native spell-control and retargeting, separate from AI admission. */
public class SpellControlCoverageTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("spellControl" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Spell control"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(1), 1);
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 8; i++) {
                addCardToZone("Island", player, ZoneType.Battlefield);
                addCardToZone("Mountain", player, ZoneType.Battlefield);
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        game.getPlayers().get(0).setLife(2, null);
        return game;
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.isGameOver() || game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixed spell-control fixture did not settle");
    }

    private SpellAbility pending(Game game, String name) {
        Player enemy = game.getPlayers().get(1);
        SpellAbility action = addCardToZone(name, enemy, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(enemy);
        if (name.equals("Shock")) action.getTargets().add(game.getPlayers().get(0));
        assertTrue(ComputerUtil.playStack(action, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), name.equals("Shock") || name.equals("Sol Ring") ? 1 : 6);
        return game.getStack().peekAbility();
    }

    private void outcome(Game game, String source, String threat, int incomingId) {
        Player actor = game.getRegisteredPlayers().get(0);
        Player enemy = game.getRegisteredPlayers().get(1);
        if (threat.equals("Shock")) {
            int expected = source.equals("Chef's Kiss") ? 2 : 1;
            assertEquals(game.getStack().size(), expected);
            for (var entry : game.getStack()) {
                assertSame(entry.getSpellAbility().getActivatingPlayer(), actor);
                assertNotEquals(entry.getSpellAbility().getTargets().getFirstTargetedPlayer(), actor,
                        "AI retargeting must not leave a preventable lethal spell aimed at itself");
            }
            settle(game);
            assertFalse(actor.hasLost());
            assertEquals(actor.getLife(), 2);
            assertEquals(game.getRegisteredPlayers().stream().mapToInt(Player::getLife).sum(), 122 - 2 * expected);
        } else {
            settle(game);
            Card permanent = game.getCardsInGame().stream().filter(card -> card.getId() == incomingId).findFirst().orElseThrow();
            assertTrue(permanent.isInZone(ZoneType.Battlefield));
            assertSame(permanent.getController(), actor);
            assertSame(permanent.getOwner(), enemy, "Control is not ownership");
        }
    }

    private void manual(String source, String threat) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        SpellAbility incoming = pending(game, threat);
        int identity = incoming.getHostCard().getId();
        SpellAbility response = addCardToZone(source, actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        assertTrue(response.canTargetSpellAbility(incoming));
        response.getTargets().add(incoming);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), source.equals("Aethersnatch") ? 6 : source.equals("Commandeer") ? 7 : 3);
        game.getStack().resolveStack();
        outcome(game, source, threat, identity);
    }

    private void selection(String source, AIOption mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        SpellAbility incoming = pending(game, "Shock");
        int identity = incoming.getHostCard().getId();
        addCardToZone(source, actor, ZoneType.Hand);
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        agent.getAi().setUseSimulation(mode);
        int tapped = (int) actor.getLandsInPlay().stream().filter(Card::isTapped).count();
        List<SpellAbility> choices = agent.chooseSpellAbilityToPlay();
        assertEquals(game.getStack().size(), 1);
        assertEquals(actor.getLife(), 2);
        assertEquals((int) actor.getLandsInPlay().stream().filter(Card::isTapped).count(), tapped);
        System.out.println("SPELL_CONTROL source=" + source + " mode=" + mode + " chosen=" + (choices == null || choices.isEmpty() ? "pass" : choices.get(0).getHostCard().getName()));
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        SpellAbility response = choices.get(0);
        assertEquals(response.getApi(), ApiType.ControlSpell);
        assertEquals(response.getHostCard().getName(), source);
        assertEquals(com.google.common.collect.Iterables.getOnlyElement(response.getTargets().getTargetSpells()).getHostCard().getId(), identity);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), source.equals("Aethersnatch") ? 6 : source.equals("Commandeer") ? 7 : 3);
        game.getStack().resolveStack();
        outcome(game, source, "Shock", identity);
    }

    @Test public void nativeAetherArtifact() { manual("Aethersnatch", "Sol Ring"); }
    @Test public void nativeAetherCreature() { manual("Aethersnatch", "Shivan Dragon"); }
    @Test public void nativeAetherDamage() { manual("Aethersnatch", "Shock"); }
    @Test public void nativeCommandeerArtifact() { manual("Commandeer", "Sol Ring"); }
    @Test public void nativeCommandeerDamage() { manual("Commandeer", "Shock"); }
    @Test public void nativeChefsDamage() { manual("Chef's Kiss", "Shock"); }
    @Test public void commandCannotTargetCreature() {
        Game game = pod();
        SpellAbility incoming = pending(game, "Shivan Dragon");
        SpellAbility response = addCardToZone("Commandeer", game.getPlayers().get(0), ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(game.getPlayers().get(0));
        assertFalse(response.canTargetSpellAbility(incoming));
    }
    @Test public void aetherHeuristic() { selection("Aethersnatch", null); }
    @Test public void aetherHybrid() { selection("Aethersnatch", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void aetherFull() { selection("Aethersnatch", AIOption.USE_FULL_SIMULATION); }
    @Test public void commandHeuristic() { selection("Commandeer", null); }
    @Test public void commandHybrid() { selection("Commandeer", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void commandFull() { selection("Commandeer", AIOption.USE_FULL_SIMULATION); }
    @Test public void chefsHeuristic() { selection("Chef's Kiss", null); }
    @Test public void chefsHybrid() { selection("Chef's Kiss", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void chefsFull() { selection("Chef's Kiss", AIOption.USE_FULL_SIMULATION); }
    @Test public void controlledRetargetPreservesBeneficiaryAndCaster() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player master = game.getPlayers().get(2);
        actor.setLife(40, null);
        master.setLife(2, null);
        actor.addController(game.getNextTimestamp(), master);
        SpellAbility incoming = addCardToZone("Shock", game.getPlayers().get(1), ZoneType.Hand).getFirstSpellAbility();
        incoming.setActivatingPlayer(game.getPlayers().get(1));
        incoming.getTargets().add(master);
        assertTrue(ComputerUtil.playStack(incoming, game.getPlayers().get(1), game));
        incoming = game.getStack().peekAbility();
        SpellAbility response = addCardToZone("Aethersnatch", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(incoming);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 6);
        game.getStack().resolveStack();
        assertSame(game.getStack().peekAbility().getActivatingPlayer(), actor);
        assertNotEquals(game.getStack().peekAbility().getTargets().getFirstTargetedPlayer(), master);
        settle(game);
        assertEquals(master.getLife(), 2);
        assertFalse(master.hasLost());
    }

    @Test public void retargetPreservesAnnouncedDividedAllocations() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        SpellAbility incoming = addCardToZone("Arc Lightning", enemy, ZoneType.Hand).getFirstSpellAbility();
        incoming.setActivatingPlayer(enemy);
        incoming.getTargets().add(actor);
        incoming.addDividedAllocation(actor, 2);
        incoming.getTargets().add(game.getPlayers().get(3));
        incoming.addDividedAllocation(game.getPlayers().get(3), 1);
        assertTrue(ComputerUtil.playStack(incoming, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        incoming = game.getStack().peekAbility();
        SpellAbility response = addCardToZone("Aethersnatch", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(incoming);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        game.getStack().resolveStack();
        SpellAbility changed = game.getStack().peekAbility();
        assertEquals(changed.getTargets().size(), 2);
        assertEquals(changed.getDividedValue(changed.getTargets().get(0)).intValue(), 2);
        assertEquals(changed.getDividedValue(changed.getTargets().get(1)).intValue(), 1);
        assertFalse(changed.getTargets().getTargetPlayers().iterator().next() == actor);
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
    }

    @Test public void retargetPreservesAnnouncedX() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        SpellAbility incoming = addCardToZone("Fireball", enemy, ZoneType.Hand).getFirstSpellAbility();
        incoming.setActivatingPlayer(enemy);
        incoming.setXManaCostPaid(2);
        incoming.getTargets().add(actor);
        assertTrue(ComputerUtil.playStack(incoming, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        incoming = game.getStack().peekAbility();
        SpellAbility response = addCardToZone("Aethersnatch", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(incoming);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        game.getStack().resolveStack();
        assertEquals(game.getStack().peekAbility().getXManaCostPaid().intValue(), 2);
        assertNotEquals(game.getStack().peekAbility().getTargets().getFirstTargetedPlayer(), actor);
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
    }

    @Test public void counterspellCanBeRetargetedToResponse() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        SpellAbility ring = addCardToZone("Sol Ring", actor, ZoneType.Hand).getFirstSpellAbility();
        ring.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(ring, actor, game));
        ring = game.getStack().peekAbility();
        int ringId = ring.getHostCard().getId();
        SpellAbility counter = addCardToZone("Counterspell", enemy, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(enemy);
        counter.getTargets().add(ring);
        assertTrue(ComputerUtil.playStack(counter, enemy, game));
        counter = game.getStack().peekAbility();
        SpellAbility response = addCardToZone("Aethersnatch", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(counter);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        SpellAbility resolvingResponse = game.getStack().peekAbility();
        game.getStack().resolveStack();
        SpellAbility changed = game.getStack().peekAbility();
        assertSame(changed.getActivatingPlayer(), actor);
        assertSame(com.google.common.collect.Iterables.getOnlyElement(changed.getTargets().getTargetSpells()), resolvingResponse,
                "A stolen counterspell can target the still-resolving control spell");
        settle(game);
        Card resolved = game.getCardsInGame().stream().filter(card -> card.getId() == ringId).findFirst().orElseThrow();
        assertTrue(resolved.isInZone(ZoneType.Battlefield));
        assertSame(resolved.getController(), actor);
    }

    private void commandAlternate(int blueCards, boolean success) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        SpellAbility incoming = pending(game, "Shock");
        for (Card land : actor.getLandsInPlay()) land.setTapped(true);
        List<Card> fodder = new ArrayList<>();
        for (int i = 0; i < blueCards; i++) fodder.add(addCardToZone("Divination", actor, ZoneType.Hand));
        Card red = addCardToZone("Shock", actor, ZoneType.Hand);
        Card source = addCardToZone("Commandeer", actor, ZoneType.Hand);
        SpellAbility base = source.getFirstSpellAbility();
        base.setActivatingPlayer(actor);
        List<SpellAbility> alternatives = forge.game.GameActionUtil.getAlternativeCosts(base, actor, true);
        assertFalse(alternatives.isEmpty());
        SpellAbility response = alternatives.get(0);
        response.setActivatingPlayer(actor);
        response.getTargets().add(incoming);
        assertEquals(ComputerUtil.playStack(response, actor, game), success);
        assertTrue(red.isInZone(ZoneType.Hand));
        if (success) {
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 0);
            for (Card card : fodder) assertTrue(card.isInZone(ZoneType.Exile));
            game.getStack().resolveStack();
            outcome(game, "Commandeer", "Shock", incoming.getHostCard().getId());
        } else {
            assertEquals(game.getStack().size(), 1);
            assertTrue(source.isInZone(ZoneType.Hand));
            for (Card card : fodder) assertTrue(card.isInZone(ZoneType.Hand));
        }
    }
    @Test public void commandAlternatePaysTwoOtherBlueCards() { commandAlternate(2, true); }
    @Test public void commandAlternateCannotExileItself() { commandAlternate(1, false); }

    private void alternateSelection(AIOption mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        pending(game, "Shock");
        for (Card land : actor.getLandsInPlay()) land.setTapped(true);
        Card first = addCardToZone("Divination", actor, ZoneType.Hand);
        Card second = addCardToZone("Divination", actor, ZoneType.Hand);
        addCardToZone("Commandeer", actor, ZoneType.Hand);
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        agent.getAi().setUseSimulation(mode);
        List<SpellAbility> choices = agent.chooseSpellAbilityToPlay();
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        SpellAbility response = choices.get(0);
        assertEquals(response.getHostCard().getName(), "Commandeer");
        assertTrue(first.isInZone(ZoneType.Hand));
        assertTrue(second.isInZone(ZoneType.Hand));
        assertEquals(game.getStack().size(), 1);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 0);
        assertTrue(first.isInZone(ZoneType.Exile));
        assertTrue(second.isInZone(ZoneType.Exile));
        game.getStack().resolveStack();
        outcome(game, "Commandeer", "Shock", 0);
    }
    @Test public void commandAlternateHeuristic() { alternateSelection(null); }
    @Test public void commandAlternateHybrid() { alternateSelection(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void commandAlternateFull() { alternateSelection(AIOption.USE_FULL_SIMULATION); }

    private void declineOwnSpell(String name) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        SpellAbility ring = addCardToZone("Sol Ring", actor, ZoneType.Hand).getFirstSpellAbility();
        ring.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(ring, actor, game));
        ring = game.getStack().peekAbility();
        SpellAbility response = addCardToZone(name, actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        assertTrue(response.canTargetSpellAbility(ring));
        java.util.Random provider = forge.util.MyRandom.getRandom();
        long draws = forge.util.MyRandom.callCount();
        assertFalse(SpellApiToAi.Converter.get(response).canPlay(actor, response).willingToPlay());
        assertSame(forge.util.MyRandom.getRandom(), provider);
        assertEquals(forge.util.MyRandom.callCount(), draws);
        assertEquals(game.getStack().size(), 1);
        assertTrue(response.getHostCard().isInZone(ZoneType.Hand));
    }
    @Test public void aetherDeclinesPayingToStealOwnPermanentSpell() { declineOwnSpell("Aethersnatch"); }
    @Test public void commandeerDeclinesPayingToStealOwnPermanentSpell() { declineOwnSpell("Commandeer"); }

    @Test public void retargetRespectsAdditionalRestrictionAndRestoresRandomProvider() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        SpellAbility incoming = pending(game, "Shock");
        java.util.Random provider = forge.util.MyRandom.getRandom();
        long draws = forge.util.MyRandom.callCount();
        Object original = incoming.getTargets().get(0);
        ((PlayerControllerAi) actor.getController()).chooseNewTargetsFor(incoming, target -> false, false);
        assertSame(incoming.getTargets().get(0), original);
        assertSame(incoming.getActivatingPlayer(), game.getPlayers().get(1));
        assertEquals(incoming.getPayingMana().size(), 1);
        assertSame(forge.util.MyRandom.getRandom(), provider);
        assertEquals(forge.util.MyRandom.callCount(), draws);
        assertEquals(actor.getLife(), 2);
        assertEquals(game.getStack().size(), 1);
    }
}
