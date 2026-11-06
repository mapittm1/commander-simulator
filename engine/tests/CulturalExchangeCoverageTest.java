package forge.ai;

import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid exchange choices, legal resolution and explicit manual-choice rules controls. */
public class CulturalExchangeCoverageTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("exchange" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Creature exchange"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 8; count++) {
                for (String land : List.of("Island", "Plains", "Mountain")) addCardToZone(land, player, ZoneType.Battlefield);
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private static final class ManualChoices extends PlayerControllerAi {
        private final boolean zero;
        private int calls;
        private boolean cancelSecond;
        private boolean cancelFirst;
        private Card foreignSecond;
        ManualChoices(Player actor, boolean zero) {
            super(actor.getGame(), actor, actor.getLobbyPlayer());
            this.zero = zero;
        }
        @Override
        public CardCollectionView chooseCardsForEffect(CardCollectionView pool, SpellAbility action,
                String title, int min, int max, boolean optional, Map<String, Object> params) {
            if (action.getApi() != ApiType.ExchangeControlVariant) {
                return super.chooseCardsForEffect(pool, action, title, min, max, optional, params);
            }
            int call = calls++;
            if (call == 0 && cancelFirst) return null;
            // Deliberately malformed controller replies test the engine boundary.
            if (call == 1 && cancelSecond) return new CardCollection();
            if (call == 1 && foreignSecond != null) return new CardCollection(foreignSecond);
            // Legal explicit rules choices, not an AI-admission witness.
            CardCollection selected = new CardCollection();
            if (!zero) for (Card card : pool) {
                if (selected.size() == max) break;
                selected.add(card);
            }
            assertTrue(selected.size() >= min && selected.size() <= max);
            return selected;
        }
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.isGameOver() || game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Exchange fixture did not settle");
    }

    private SpellAbility exchange(Game game, boolean zero) {
        return exchange(game, new ManualChoices(game.getPlayers().get(0), zero));
    }

    private SpellAbility exchange(Game game, ManualChoices choices) {
        Player actor = game.getPlayers().get(0);
        actor.dangerouslySetController(choices);
        SpellAbility action = addCardToZone("Cultural Exchange", actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        action.getTargets().add(actor);
        action.getTargets().add(game.getPlayers().get(1));
        assertTrue(ComputerUtil.playStack(action, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 6);
        return game.getStack().peekAbility();
    }

    private void controlled(Card card, Player owner, Player controller) {
        assertSame(card.getOwner(), owner);
        assertSame(card.getController(), controller);
        assertTrue(controller.getCardsIn(ZoneType.Battlefield).contains(card));
    }

    @Test public void nativeZeroIsAValidEqualChoice() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card own = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        Card other = addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        exchange(game, true);
        settle(game);
        controlled(own, actor, actor);
        controlled(other, enemy, enemy);
    }

    @Test public void nativeEqualNumbersAndUnchosenCreature() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        List<Card> own = List.of(addCardToZone("Ornithopter", actor, ZoneType.Battlefield), addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield));
        List<Card> other = List.of(addCardToZone("Serra Angel", enemy, ZoneType.Battlefield), addCardToZone("Shivan Dragon", enemy, ZoneType.Battlefield));
        Card unchosen = addCardToZone("Craw Wurm", enemy, ZoneType.Battlefield);
        exchange(game, false);
        settle(game);
        for (Card card : own) controlled(card, actor, enemy);
        for (Card card : other) controlled(card, enemy, actor);
        controlled(unchosen, enemy, enemy);
        assertEquals(actor.getSpellsCastThisTurn(), 1);
        assertEquals(enemy.getSpellsCastThisTurn(), 0);
    }

    @Test public void chosenShroudCreatureIsNotTargeted() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card own = addCardToZone("Calciderm", actor, ZoneType.Battlefield);
        Card other = addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        exchange(game, false);
        settle(game);
        controlled(own, actor, enemy);
        controlled(other, enemy, actor);
    }

    @Test public void phasedOutCreatureCannotBeChosen() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card phased = addCardToZone("Shivan Dragon", actor, ZoneType.Battlefield);
        Card own = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        Card other = addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        exchange(game, false);
        SpellAbility march = addCardToZone("March of Swirling Mist", actor, ZoneType.Hand).getFirstSpellAbility();
        march.setActivatingPlayer(actor);
        march.setXManaCostPaid(1);
        march.getTargets().add(phased);
        assertTrue(ComputerUtil.playStack(march, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        game.getStack().resolveStack();
        assertTrue(phased.isPhasedOut());
        settle(game);
        assertSame(phased.getController(), actor);
        controlled(own, actor, enemy);
        controlled(other, enemy, actor);
    }

    @Test public void newlyIllegalPlayerCannotExchangeANewCreature() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card own = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        exchange(game, false);
        SpellAbility protection = addCardToZone("Teferi's Protection", enemy, ZoneType.Hand).getFirstSpellAbility();
        protection.setActivatingPlayer(enemy);
        assertTrue(ComputerUtil.playStack(protection, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        game.getStack().resolveStack();
        SpellAbility pact = addCardToZone("Pact of the Titan", enemy, ZoneType.Hand).getFirstSpellAbility();
        pact.setActivatingPlayer(enemy);
        assertTrue(ComputerUtil.playStack(pact, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 0);
        game.getStack().resolveStack();
        Card giant = enemy.getCreaturesInPlay().stream().filter(card -> !card.isPhasedOut()).findFirst().orElseThrow();
        settle(game);
        controlled(own, actor, actor);
        assertSame(giant.getController(), enemy);
    }

    @Test public void malformedUnequalChoiceCannotTransferOnlyOneSide() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card own = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        Card other = addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        ManualChoices choices = new ManualChoices(actor, false);
        choices.cancelSecond = true;
        exchange(game, choices);
        settle(game);
        controlled(own, actor, actor);
        controlled(other, enemy, enemy);
    }

    @Test public void malformedForeignChoiceCannotStealFromANontargetedPlayer() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1), outsider = game.getPlayers().get(2);
        Card own = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        Card other = addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        Card foreign = addCardToZone("Craw Wurm", outsider, ZoneType.Battlefield);
        ManualChoices choices = new ManualChoices(actor, false);
        choices.foreignSecond = foreign;
        exchange(game, choices);
        settle(game);
        controlled(own, actor, actor);
        controlled(other, enemy, enemy);
        controlled(foreign, outsider, outsider);
    }

    @Test public void canceledFirstChoiceFinishesWithoutChangingControl() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card own = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        Card other = addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        ManualChoices choices = new ManualChoices(actor, false);
        choices.cancelFirst = true;
        exchange(game, choices);
        settle(game);
        controlled(own, actor, actor);
        controlled(other, enemy, enemy);
        assertTrue(game.getStack().isEmpty());
        assertFalse(game.getStack().isResolving());
        assertEquals(actor.getSpellsCastThisTurn(), 1);
    }

    private void choice(int mode, boolean selective, boolean expectPlay) {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card own = addCardToZone(expectPlay ? "Ornithopter" : "Darksteel Colossus", actor, ZoneType.Battlefield);
        Card other = addCardToZone(expectPlay ? "Serra Angel" : "Ornithopter", enemy, ZoneType.Battlefield);
        Card keep = null;
        if (selective) {
            keep = addCardToZone("Darksteel Colossus", actor, ZoneType.Battlefield);
            addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        }
        Card source = addCardToZone("Cultural Exchange", actor, ZoneType.Hand);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(mode == 0 ? null : mode == 1 ? AIOption.USE_HYBRID_SIMULATION : AIOption.USE_FULL_SIMULATION);
        var provider = forge.util.MyRandom.getRandom();
        List<SpellAbility> actions = actor.getController().chooseSpellAbilityToPlay();
        assertSame(forge.util.MyRandom.getRandom(), provider);
        SpellAbility action = actions == null || actions.isEmpty() ? null : actions.get(0);
        if (!expectPlay) {
            assertTrue(action == null || action.getHostCard() != source);
            return;
        }
        assertNotNull(action);
        assertSame(action.getHostCard(), source);
        controlled(own, actor, actor);
        controlled(other, enemy, enemy);
        assertTrue(actor.getController().playChosenSpellAbility(action));
        assertFalse(game.getStack().isEmpty());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 6);
        settle(game);
        controlled(own, actor, enemy);
        controlled(other, enemy, actor);
        if (keep != null) controlled(keep, actor, actor);
    }

    @Test public void simpleHeuristic() { choice(0, false, true); }
    @Test public void simpleHybrid() { choice(1, false, true); }
    @Test public void simpleFull() { choice(2, false, true); }
    @Test public void selectiveHeuristic() { choice(0, true, true); }
    @Test public void selectiveHybrid() { choice(1, true, true); }
    @Test public void selectiveFull() { choice(2, true, true); }
    @Test public void harmfulExchangeDeclined() { choice(0, false, false); }
}
