package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.event.EventValueChangeType;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventManaPool;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Fixed mana-source preconditions; native payment, not source casting or AI strategy proof. */
public class UnpaidCastRecoveryProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("mana" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Reflected mana"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 8; count++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private SpellAbility inHand(Player actor, String name) {
        SpellAbility spell = addCardToZone(name, actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        return spell;
    }




    @Test
    public void returnsFailedCastToItsOriginalHandPosition() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        inHand(actor, "Island");
        SpellAbility spell = inHand(actor, "Opt");
        inHand(actor, "Mountain");
        game.getAction().checkStateEffects(true);
        int spellId = spell.getHostCard().getId();
        var originalHand = actor.getCardsIn(ZoneType.Hand).stream().map(Card::getId).toList();
        PaymentFault fault = new PaymentFault(spellId, island);
        game.subscribeToEvents(fault);

        assertTrue(ComputerUtilMana.canPayManaCost(spell, actor, 0, false));
        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertTrue(fault.injected);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty(),
                "A failed payment must not leave a card in the stack zone");
        assertEquals(actor.getCardsIn(ZoneType.Hand).stream().map(Card::getId).toList(), originalHand);
    }

    @Test
    public void unpaidCastPreservesAnEarlierWaitingTrigger() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        Card arena = addCardToZone("Phyrexian Arena", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Opt");
        game.getAction().checkStateEffects(true);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, actor, 2);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(actor), true);
        game.subscribeToEvents(new PaymentFault(spell.getHostCard().getId(), island));

        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
        game.getTriggerHandler().collectTriggerForWaiting();
        assertTrue(game.getTriggerHandler().runWaitingTriggers());
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertFalse(game.getStack().isEmpty());
        assertEquals(game.getStack().peekAbility().getHostCard().getId(), arena.getId());
    }

    @Test
    public void returnsFailedNoStackCastToItsSourceZone() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Opt");
        game.getAction().checkStateEffects(true);
        int spellId = spell.getHostCard().getId();
        game.subscribeToEvents(new PaymentFault(spellId, island));

        assertFalse(ComputerUtil.playNoStack(actor, spell, game, false));
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
        assertEquals(actor.getCardsIn(ZoneType.Hand).stream().map(Card::getId).toList(), List.of(spellId));
        assertTrue(game.getStack().isEmpty());
    }


    @Test
    public void undoesTheReversibleActivationAfterPartialManaPayment() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        Card backup = addCardToZone("Island", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Thassa's Oracle");
        game.getAction().checkStateEffects(true);
        PartialManaFault fault = new PartialManaFault(actor, island, backup);
        game.subscribeToEvents(fault);

        assertTrue(ComputerUtilMana.canPayManaCost(spell, actor, 0, false));
        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertTrue(fault.activated != null, "A real mana ability must have resolved before failure");
        assertFalse(fault.activated.isTapped());
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 0);
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Hand);
    }

    @Test
    public void keepsNonUndoableManaAndDamageAfterPartialPayment() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card island = addCardToZone("Island", actor, ZoneType.Battlefield);
        Card backup = addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        SpellAbility spell = inHand(actor, "Tempest Djinn");
        game.getAction().checkStateEffects(true);
        PartialManaFault fault = new PartialManaFault(actor, island, backup);
        game.subscribeToEvents(fault);
        int life = actor.getLife();

        assertTrue(ComputerUtilMana.canPayManaCost(spell, actor, 0, false));
        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertTrue(fault.activated != null, "A real mana ability must have resolved before failure");
        assertTrue(fault.activated.isTapped());
        assertEquals(actor.getLife(), life - 1);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 2);
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Hand);
    }


    private SpellAbility graveyardSpell(Player actor, String name) {
        Card card = addCardToZone(name, actor, ZoneType.Graveyard);
        actor.getGame().getAction().checkStateEffects(true);
        SpellAbility base = card.getFirstSpellAbility();
        base.setActivatingPlayer(actor);
        return GameActionUtil.getAlternativeCosts(base, actor, false).stream()
                .filter(SpellAbility::isSpell)
                .filter(alternative -> alternative.getMayPlayOption() != null)
                .findFirst().orElseThrow();
    }

    private void putWaitingTriggersOnStack(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    @Test
    public void successfulGraveyardCastQueuesKonrad() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Syr Konrad, the Grim", actor, ZoneType.Battlefield);
        addCardToZone("Undead Augur", actor, ZoneType.Battlefield);
        addCardToZone("Swamp", actor, ZoneType.Battlefield);
        SpellAbility spell = graveyardSpell(actor, "Gravecrawler");

        assertTrue(ComputerUtil.playStack(spell, actor, game));
        putWaitingTriggersOnStack(game);
        assertEquals(game.getStack().peekAbility().getHostCard().getName(), "Syr Konrad, the Grim");
    }

    @Test
    public void abortedGraveyardCastDoesNotQueueKonrad() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Syr Konrad, the Grim", actor, ZoneType.Battlefield);
        addCardToZone("Undead Augur", actor, ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        SpellAbility spell = graveyardSpell(actor, "Gravecrawler");
        PaymentFault fault = new PaymentFault(spell.getHostCard().getId(), swamp);
        game.subscribeToEvents(fault);

        assertTrue(ComputerUtilMana.canPayManaCost(spell, actor, 0, false));
        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertTrue(fault.injected);
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Graveyard);
        putWaitingTriggersOnStack(game);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
    }

    @Test
    public void partiallyPaidGraveyardCastDoesNotQueueKonrad() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Syr Konrad, the Grim", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        SpellAbility spell = graveyardSpell(actor, "Haakon, Stromgald Scourge");
        PartialManaFault fault = new PartialManaFault(actor, first, second);
        game.subscribeToEvents(fault);

        assertTrue(ComputerUtilMana.canPayManaCost(spell, actor, 0, false));
        assertFalse(ComputerUtil.playStack(spell, actor, game));
        assertTrue(fault.activated != null);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLACK), 2);
        assertEquals(spell.getHostCard().getZone().getZoneType(), ZoneType.Graveyard);
        putWaitingTriggersOnStack(game);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardsIn(ZoneType.Stack).isEmpty());
    }

    public static final class PartialManaFault {
        private final Player actor;
        private final Card first;
        private final Card second;
        private Card activated;

        private PartialManaFault(Player actor, Card first, Card second) {
            this.actor = actor;
            this.first = first;
            this.second = second;
        }

        /** Make the other land unavailable once the first activation adds its mana. */
        @Subscribe
        public void onMana(GameEventManaPool event) {
            if (activated == null && event.player().getId() == actor.getId()
                    && event.mode() == EventValueChangeType.Added && first.isTapped() != second.isTapped()) {
                activated = first.isTapped() ? first : second;
                (activated == first ? second : first).setTapped(true);
            }
        }
    }

    public static final class PaymentFault {
        private final int spellId;
        private final Card onlySource;
        private boolean injected;

        private PaymentFault(int spellId, Card onlySource) {
            this.spellId = spellId;
            this.onlySource = onlySource;
        }

        /** Simulate a late payment failure after a successful preflight. */
        @Subscribe
        public void onZoneChange(GameEventCardChangeZone event) {
            if (event.card().getId() == spellId && event.to() != null
                    && event.to().zoneType() == ZoneType.Stack) {
                onlySource.setTapped(true);
                injected = true;
            }
        }
    }
}
