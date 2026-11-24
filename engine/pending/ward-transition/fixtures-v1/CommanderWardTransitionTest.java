package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerWaiting;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Independent native payment witnesses for cost-generated triggers and state actions. */
public class CommanderWardTransitionTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("transition-0")).findFirst().orElseThrow();
    }

    private boolean paid(String mode) {
        return !List.of("blightNone", "blightDoubleBear", "evidenceShort", "evidenceDoubleShort").contains(mode);
    }

    private Map<Integer, String> state(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) result.put(card.getId(), card.getZone().getZoneType()+":"+card.isTapped()+":"+card.getCounters(CounterEnumType.M1M1));
        for (Player p : game.getPlayers()) result.put(-p.getId(), "life="+p.getLife()+";mana="+p.getManaPool().totalMana());
        return result;
    }

    private Game setup(String mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0; i<4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("transition-"+i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Ward payment transitions"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player p : game.getPlayers()) {
            p.setLife(100, null);
            for (int i=0; i<24; i++) addCardToZone("Forest", p, ZoneType.Library);
        }
        Player payer = actor(game), opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, payer, 5);
        addCardToZone("Crashing Wave", payer, ZoneType.Hand);
        addCardToZone("Island", payer, ZoneType.Battlefield);
        addCardToZone("Island", payer, ZoneType.Battlefield);
        addCardToZone("Welding Jar", payer, ZoneType.Battlefield);
        boolean evidence = mode.startsWith("evidence");
        addCardToZone(evidence ? "Axebane Ferox" : "Auntie Ool, Cursewretch", opponent, ZoneType.Battlefield);
        if (mode.contains("Double")) {
            Card throne = addCardToZone("Roaming Throne", opponent, ZoneType.Battlefield);
            throne.setChosenType(evidence ? "Beast" : "Goblin");
        }
        if (!evidence && !mode.equals("blightNone")) {
            addCardToZone(mode.equals("blightDoubleWurm") ? "Craw Wurm" : "Grizzly Bears", payer, ZoneType.Battlefield);
            if (mode.equals("blightDoubleTwoBears")) addCardToZone("Grizzly Bears", payer, ZoneType.Battlefield);
        }
        if (evidence) {
            addCardToZone(mode.equals("evidenceShort") ? "Grizzly Bears" : "Hill Giant", payer, ZoneType.Graveyard);
            if (mode.equals("evidenceDoublePaid")) addCardToZone("Hill Giant", payer, ZoneType.Graveyard);
        }
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private void check(String mode, int depth, boolean nativeCast) {
        Game original = setup(mode);
        Map<Integer, String> originalBefore = state(original);
        original.copyLastState();
        Game game = original;
        for (int i=0; i<depth; i++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        Player payer = actor(game);
        Card target = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(c -> !c.getName().equals("Roaming Throne")).findFirst().orElseThrow();
        SpellAbility spell = payer.getCardsIn(ZoneType.Hand).getFirst().getFirstSpellAbility();
        spell.setActivatingPlayer(payer); spell.setXManaCostPaid(1); spell.getTargets().add(target);
        Map<Integer, String> before = state(game);
        if (!nativeCast) {
            assertEquals(ComputerUtilCost.canPayCost(spell, payer, false), paid(mode), "Forecast must account for resources and triggered effects between ward resolutions");
            assertEquals(state(game), before, "Forecast preserves the source world");
        } else {
            Card onStack = game.getAction().moveToStack(spell.getHostCard(), spell);
            TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
            spell.setHostCard(onStack);
            assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(new AiCostDecision(payer, spell, false, true)));
            game.getTriggerHandler().commitCastPlacement(placement);
            game.getStack().add(spell);
            GameSimulator.resolveStack(game, payer);
            assertEquals(target.isTapped(), paid(mode), "Actual native resolution is the independent paid/countered witness");
            assertEquals(target.getCounters(CounterEnumType.STUN), paid(mode) ? 3 : 0);
            int blightPayments = mode.startsWith("evidence") || mode.equals("blightNone") ? 0 : mode.contains("Double") && !mode.equals("blightDoubleBear") ? 2 : 1;
            assertEquals(payer.getLife(), 100 - blightPayments * (mode.contains("Double") ? 2 : 1), "Resolve Auntie Ool counter-generated life-loss triggers");
            int evidencePayments = !mode.startsWith("evidence") || mode.equals("evidenceShort") ? 0 : mode.equals("evidenceDoublePaid") ? 2 : 1;
            assertEquals(payer.getCardsIn(ZoneType.Exile).size(), evidencePayments, "Each evidence ward consumes a distinct graveyard budget");
            if (mode.startsWith("blight") && !mode.equals("blightNone")) {
                int dead = mode.equals("blightDoubleTwoBears") ? 2 : 1;
                assertEquals(payer.getCardsIn(ZoneType.Graveyard).stream().filter(Card::isCreature).count(), (long) dead, "State actions remove creatures after counters reduce toughness to zero");
            }
        }
        if (depth > 0) assertEquals(state(original), originalBefore, "Copies do not spend original resources");
    }

    @Test
    public void blightNonePreviewDepth0() { check("blightNone", 0, false); }

    @Test
    public void blightNoneNativeDepth0() { check("blightNone", 0, true); }

    @Test
    public void blightNonePreviewDepth1() { check("blightNone", 1, false); }

    @Test
    public void blightNoneNativeDepth1() { check("blightNone", 1, true); }

    @Test
    public void blightNonePreviewDepth2() { check("blightNone", 2, false); }

    @Test
    public void blightNoneNativeDepth2() { check("blightNone", 2, true); }

    @Test
    public void blightSingleBearPreviewDepth0() { check("blightSingleBear", 0, false); }

    @Test
    public void blightSingleBearNativeDepth0() { check("blightSingleBear", 0, true); }

    @Test
    public void blightSingleBearPreviewDepth1() { check("blightSingleBear", 1, false); }

    @Test
    public void blightSingleBearNativeDepth1() { check("blightSingleBear", 1, true); }

    @Test
    public void blightSingleBearPreviewDepth2() { check("blightSingleBear", 2, false); }

    @Test
    public void blightSingleBearNativeDepth2() { check("blightSingleBear", 2, true); }

    @Test
    public void blightDoubleBearPreviewDepth0() { check("blightDoubleBear", 0, false); }

    @Test
    public void blightDoubleBearNativeDepth0() { check("blightDoubleBear", 0, true); }

    @Test
    public void blightDoubleBearPreviewDepth1() { check("blightDoubleBear", 1, false); }

    @Test
    public void blightDoubleBearNativeDepth1() { check("blightDoubleBear", 1, true); }

    @Test
    public void blightDoubleBearPreviewDepth2() { check("blightDoubleBear", 2, false); }

    @Test
    public void blightDoubleBearNativeDepth2() { check("blightDoubleBear", 2, true); }

    @Test
    public void blightDoubleWurmPreviewDepth0() { check("blightDoubleWurm", 0, false); }

    @Test
    public void blightDoubleWurmNativeDepth0() { check("blightDoubleWurm", 0, true); }

    @Test
    public void blightDoubleWurmPreviewDepth1() { check("blightDoubleWurm", 1, false); }

    @Test
    public void blightDoubleWurmNativeDepth1() { check("blightDoubleWurm", 1, true); }

    @Test
    public void blightDoubleWurmPreviewDepth2() { check("blightDoubleWurm", 2, false); }

    @Test
    public void blightDoubleWurmNativeDepth2() { check("blightDoubleWurm", 2, true); }

    @Test
    public void blightDoubleTwoBearsPreviewDepth0() { check("blightDoubleTwoBears", 0, false); }

    @Test
    public void blightDoubleTwoBearsNativeDepth0() { check("blightDoubleTwoBears", 0, true); }

    @Test
    public void blightDoubleTwoBearsPreviewDepth1() { check("blightDoubleTwoBears", 1, false); }

    @Test
    public void blightDoubleTwoBearsNativeDepth1() { check("blightDoubleTwoBears", 1, true); }

    @Test
    public void blightDoubleTwoBearsPreviewDepth2() { check("blightDoubleTwoBears", 2, false); }

    @Test
    public void blightDoubleTwoBearsNativeDepth2() { check("blightDoubleTwoBears", 2, true); }

    @Test
    public void evidencePaidPreviewDepth0() { check("evidencePaid", 0, false); }

    @Test
    public void evidencePaidNativeDepth0() { check("evidencePaid", 0, true); }

    @Test
    public void evidencePaidPreviewDepth1() { check("evidencePaid", 1, false); }

    @Test
    public void evidencePaidNativeDepth1() { check("evidencePaid", 1, true); }

    @Test
    public void evidencePaidPreviewDepth2() { check("evidencePaid", 2, false); }

    @Test
    public void evidencePaidNativeDepth2() { check("evidencePaid", 2, true); }

    @Test
    public void evidenceShortPreviewDepth0() { check("evidenceShort", 0, false); }

    @Test
    public void evidenceShortNativeDepth0() { check("evidenceShort", 0, true); }

    @Test
    public void evidenceShortPreviewDepth1() { check("evidenceShort", 1, false); }

    @Test
    public void evidenceShortNativeDepth1() { check("evidenceShort", 1, true); }

    @Test
    public void evidenceShortPreviewDepth2() { check("evidenceShort", 2, false); }

    @Test
    public void evidenceShortNativeDepth2() { check("evidenceShort", 2, true); }

    @Test
    public void evidenceDoubleShortPreviewDepth0() { check("evidenceDoubleShort", 0, false); }

    @Test
    public void evidenceDoubleShortNativeDepth0() { check("evidenceDoubleShort", 0, true); }

    @Test
    public void evidenceDoubleShortPreviewDepth1() { check("evidenceDoubleShort", 1, false); }

    @Test
    public void evidenceDoubleShortNativeDepth1() { check("evidenceDoubleShort", 1, true); }

    @Test
    public void evidenceDoubleShortPreviewDepth2() { check("evidenceDoubleShort", 2, false); }

    @Test
    public void evidenceDoubleShortNativeDepth2() { check("evidenceDoubleShort", 2, true); }

    @Test
    public void evidenceDoublePaidPreviewDepth0() { check("evidenceDoublePaid", 0, false); }

    @Test
    public void evidenceDoublePaidNativeDepth0() { check("evidenceDoublePaid", 0, true); }

    @Test
    public void evidenceDoublePaidPreviewDepth1() { check("evidenceDoublePaid", 1, false); }

    @Test
    public void evidenceDoublePaidNativeDepth1() { check("evidenceDoublePaid", 1, true); }

    @Test
    public void evidenceDoublePaidPreviewDepth2() { check("evidenceDoublePaid", 2, false); }

    @Test
    public void evidenceDoublePaidNativeDepth2() { check("evidenceDoublePaid", 2, true); }
}
