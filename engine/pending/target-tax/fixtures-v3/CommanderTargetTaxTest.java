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
import forge.game.card.CounterEnumType;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerHandler;
import forge.game.trigger.TriggerWaiting;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Legacy targeting taxes, cast counters and bounded native pre-goal execution. */
public class CommanderTargetTaxTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("tax-0")).findFirst().orElseThrow();
    }

    private Map<Integer, String> state(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) {
            StringBuilder history = new StringBuilder();
            for (var trigger : card.getTriggers()) history.append(trigger.getId()).append(":")
                    .append(trigger.getActivationsThisTurn()).append(":").append(trigger.getActivationsThisGame()).append(";");
            result.put(card.getId(), card.getZone().getZoneType()+":"+card.isTapped()+":"
                    +card.getCounters(CounterEnumType.STUN)+":"+card.getCounters(CounterEnumType.CHARGE)+":"
                    +card.hasBecomeTargetThisTurn()+":"+card.isValiant(card.getController())+":"+history);
        }
        for (Player player : game.getPlayers()) result.put(-player.getId(), "life="+player.getLife()+";mana="+player.getManaPool().totalMana());
        result.put(Integer.MIN_VALUE, "casts="+game.getStack().getSpellsCastThisTurn().size());
        return result;
    }

    private boolean paid(String mode) {
        return !List.of("frostShort", "frostDoubleShort", "kiraFirst", "netherShort", "annexShort", "chaliceCounter").contains(mode);
    }

    private int forests(String mode) {
        return mode.equals("frostPaid") ? 2 : mode.equals("frostShort") || mode.equals("annexPaid") ? 1
                : mode.equals("frostDoublePaid") ? 4 : mode.equals("frostDoubleShort") || mode.equals("netherPaid") ? 3
                : mode.equals("netherShort") ? 2 : 0;
    }

    private Game setup(String mode, int boundary) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0; i<4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("tax-"+i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Native target and cast triggers"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int i=0; i<24; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Player payer = actor(game), opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, payer, 5);
        addCardToZone("Crashing Wave", payer, ZoneType.Hand);
        addCardToZone("Island", payer, ZoneType.Battlefield);
        addCardToZone("Island", payer, ZoneType.Battlefield);
        if (!mode.endsWith("Uncounterable")) addCardToZone("Welding Jar", payer, ZoneType.Battlefield);
        for (int i=0; i<forests(mode); i++) addCardToZone("Forest", payer, ZoneType.Battlefield);
        boolean frost = mode.startsWith("frost");
        Player targetOwner = List.of("frostOwn", "ownerHistory", "opponentHistory").contains(mode) ? payer : opponent;
        Card target = addCardToZone(frost ? "Frost Titan" : "Grizzly Bears", targetOwner, ZoneType.Battlefield);
        if (mode.startsWith("frostDouble")) {
            Card throne = addCardToZone("Roaming Throne", opponent, ZoneType.Battlefield);
            throne.setChosenType("Giant");
        }
        if (mode.startsWith("kira")) addCardToZone("Kira, Great Glass-Spinner", opponent, ZoneType.Battlefield);
        if (mode.equals("kiraAlreadyTargeted") || mode.equals("ownerHistory")) target.addTargetFromThisTurn(payer);
        if (mode.equals("opponentHistory")) target.addTargetFromThisTurn(opponent);
        if (mode.startsWith("nether")) addCardToZone("Nether Void", opponent, ZoneType.Battlefield);
        if (mode.startsWith("annex")) addCardToZone("Chancellor of the Annex", opponent, ZoneType.Battlefield);
        if (mode.startsWith("chalice")) {
            Card chalice = addCardToZone("Chalice of the Void", opponent, ZoneType.Battlefield);
            chalice.setCounters(CounterEnumType.CHARGE, mode.equals("chaliceOtherCharge") ? 3 : 2);
        }
        if (mode.endsWith("Uncounterable")) {
            Card land = addCardToZone("Boseiju, Who Shelters All", payer, ZoneType.Battlefield);
            land.setTapped(false);
            payer.setLife(102, null);
            SpellAbility mana = land.getManaAbilities().get(0);
            mana.setActivatingPlayer(payer);
            assertTrue(new CostPayment(mana.getPayCosts(), mana).payComputerCosts(new AiCostDecision(payer, mana, false, true)));
            game.getStack().addAndUnfreeze(mana);
            assertEquals(payer.getLife(), 100);
        }
        if (boundary > 0) {
            // A trusted mechanic probe, not a catalog card or a claim about a printed card script.
            for (int i=0; i<boundary; i++) {
                String execute = "BoundaryNoOp"+i;
                target.setSVar(execute, "DB$ Draw | Defined$ You | NumCards$ 0");
                target.addTrigger(TriggerHandler.parseTrigger(
                        "Mode$ BecomesTarget | ValidTarget$ Card.Self | TriggerZones$ Battlefield | Execute$ "+execute, target, true));
            }
        }
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        if (mode.equals("frostSuppressed")) game.getTriggerHandler().suppressMode(TriggerType.BecomesTarget);
        if (mode.equals("chaliceSuppressed")) game.getTriggerHandler().suppressMode(TriggerType.SpellCast);
        return game;
    }

    private Card target(Game game, String mode) {
        Player owner = List.of("frostOwn", "ownerHistory", "opponentHistory").contains(mode) ? actor(game) : game.getPlayers().get(1);
        return owner.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals(mode.startsWith("frost") ? "Frost Titan" : "Grizzly Bears")).findFirst().orElseThrow();
    }

    private void check(String mode, int boundary, int depth, boolean nativeCast) {
        Game original = setup(mode, boundary);
        Map<Integer, String> originalBefore = state(original);
        original.copyLastState();
        Game game = original;
        for (int i=0; i<depth; i++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        Player payer = actor(game);
        Card target = target(game, mode);
        assertEquals(target.hasBecomeTargetThisTurn(), List.of("kiraAlreadyTargeted", "ownerHistory", "opponentHistory").contains(mode), "Copied first-target history is preserved");
        assertEquals(target.isValiant(target.getController()), !mode.equals("ownerHistory"), "Targeting participants are remapped to the copied players");
        SpellAbility spell = payer.getCardsIn(ZoneType.Hand).getFirst().getFirstSpellAbility();
        spell.setActivatingPlayer(payer); spell.setXManaCostPaid(1); spell.getTargets().add(target);
        Map<Integer, String> before = state(game);
        if (!nativeCast) {
            boolean found;
            if (boundary > 0) {
                try {
                    found = (Boolean) ComputerUtilMana.class.getMethod("canPayManaCostWithWardCosts", Cost.class, SpellAbility.class,
                            Player.class, int.class, boolean.class).invoke(null, spell.getPayCosts(), spell, payer, 0, false);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Native pre-goal forecast API must be available", error);
                }
            } else found = ComputerUtilCost.canPayCost(spell, payer, false);
            assertEquals(found, boundary > 0 ? boundary <= 64 : paid(mode), "Bounded native forecast respects actual target/cast effects");
            assertEquals(state(game), before, "Forecast does not spend source resources or consume trigger/cast history");
            assertEquals(spell.getXManaCostPaid(), Integer.valueOf(1));
        } else {
            Card onStack = game.getAction().moveToStack(spell.getHostCard(), spell);
            TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
            spell.setHostCard(onStack);
            assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(new AiCostDecision(payer, spell, false, true)));
            game.getTriggerHandler().commitCastPlacement(placement);
            game.getStack().add(spell);
            GameSimulator.resolveStack(game, payer);
            assertEquals(target.isTapped(), boundary > 0 || paid(mode), "Independent native cast establishes the resolved/countered outcome");
            assertEquals(target.getCounters(CounterEnumType.STUN), !List.of("frostOwn", "ownerHistory", "opponentHistory").contains(mode) && (boundary > 0 || paid(mode)) ? 3 : 0);
            assertEquals(payer.getLife(), 100);
            if (boundary > 0) assertEquals(target.getTriggers().stream().filter(t -> t.hasParam("Execute") && t.getParam("Execute").startsWith("BoundaryNoOp")).mapToInt(t -> t.getActivationsThisTurn()).sum(), boundary);
        }
        if (depth > 0) assertEquals(state(original), originalBefore);
    }

    @Test
    public void frostPaidPreviewDepth0() {
        check("frostPaid", 0, 0, false);
    }

    @Test
    public void frostPaidNativeDepth0() {
        check("frostPaid", 0, 0, true);
    }

    @Test
    public void frostPaidPreviewDepth1() {
        check("frostPaid", 0, 1, false);
    }

    @Test
    public void frostPaidNativeDepth1() {
        check("frostPaid", 0, 1, true);
    }

    @Test
    public void frostPaidPreviewDepth2() {
        check("frostPaid", 0, 2, false);
    }

    @Test
    public void frostPaidNativeDepth2() {
        check("frostPaid", 0, 2, true);
    }

    @Test
    public void frostShortPreviewDepth0() {
        check("frostShort", 0, 0, false);
    }

    @Test
    public void frostShortNativeDepth0() {
        check("frostShort", 0, 0, true);
    }

    @Test
    public void frostShortPreviewDepth1() {
        check("frostShort", 0, 1, false);
    }

    @Test
    public void frostShortNativeDepth1() {
        check("frostShort", 0, 1, true);
    }

    @Test
    public void frostShortPreviewDepth2() {
        check("frostShort", 0, 2, false);
    }

    @Test
    public void frostShortNativeDepth2() {
        check("frostShort", 0, 2, true);
    }

    @Test
    public void frostDoublePaidPreviewDepth0() {
        check("frostDoublePaid", 0, 0, false);
    }

    @Test
    public void frostDoublePaidNativeDepth0() {
        check("frostDoublePaid", 0, 0, true);
    }

    @Test
    public void frostDoublePaidPreviewDepth1() {
        check("frostDoublePaid", 0, 1, false);
    }

    @Test
    public void frostDoublePaidNativeDepth1() {
        check("frostDoublePaid", 0, 1, true);
    }

    @Test
    public void frostDoublePaidPreviewDepth2() {
        check("frostDoublePaid", 0, 2, false);
    }

    @Test
    public void frostDoublePaidNativeDepth2() {
        check("frostDoublePaid", 0, 2, true);
    }

    @Test
    public void frostDoubleShortPreviewDepth0() {
        check("frostDoubleShort", 0, 0, false);
    }

    @Test
    public void frostDoubleShortNativeDepth0() {
        check("frostDoubleShort", 0, 0, true);
    }

    @Test
    public void frostDoubleShortPreviewDepth1() {
        check("frostDoubleShort", 0, 1, false);
    }

    @Test
    public void frostDoubleShortNativeDepth1() {
        check("frostDoubleShort", 0, 1, true);
    }

    @Test
    public void frostDoubleShortPreviewDepth2() {
        check("frostDoubleShort", 0, 2, false);
    }

    @Test
    public void frostDoubleShortNativeDepth2() {
        check("frostDoubleShort", 0, 2, true);
    }

    @Test
    public void frostOwnPreviewDepth0() {
        check("frostOwn", 0, 0, false);
    }

    @Test
    public void frostOwnNativeDepth0() {
        check("frostOwn", 0, 0, true);
    }

    @Test
    public void frostOwnPreviewDepth1() {
        check("frostOwn", 0, 1, false);
    }

    @Test
    public void frostOwnNativeDepth1() {
        check("frostOwn", 0, 1, true);
    }

    @Test
    public void frostOwnPreviewDepth2() {
        check("frostOwn", 0, 2, false);
    }

    @Test
    public void frostOwnNativeDepth2() {
        check("frostOwn", 0, 2, true);
    }

    @Test
    public void frostUncounterablePreviewDepth0() {
        check("frostUncounterable", 0, 0, false);
    }

    @Test
    public void frostUncounterableNativeDepth0() {
        check("frostUncounterable", 0, 0, true);
    }

    @Test
    public void frostUncounterablePreviewDepth1() {
        check("frostUncounterable", 0, 1, false);
    }

    @Test
    public void frostUncounterableNativeDepth1() {
        check("frostUncounterable", 0, 1, true);
    }

    @Test
    public void frostUncounterablePreviewDepth2() {
        check("frostUncounterable", 0, 2, false);
    }

    @Test
    public void frostUncounterableNativeDepth2() {
        check("frostUncounterable", 0, 2, true);
    }

    @Test
    public void kiraFirstPreviewDepth0() {
        check("kiraFirst", 0, 0, false);
    }

    @Test
    public void kiraFirstNativeDepth0() {
        check("kiraFirst", 0, 0, true);
    }

    @Test
    public void kiraFirstPreviewDepth1() {
        check("kiraFirst", 0, 1, false);
    }

    @Test
    public void kiraFirstNativeDepth1() {
        check("kiraFirst", 0, 1, true);
    }

    @Test
    public void kiraFirstPreviewDepth2() {
        check("kiraFirst", 0, 2, false);
    }

    @Test
    public void kiraFirstNativeDepth2() {
        check("kiraFirst", 0, 2, true);
    }

    @Test
    public void kiraAlreadyTargetedPreviewDepth0() {
        check("kiraAlreadyTargeted", 0, 0, false);
    }

    @Test
    public void kiraAlreadyTargetedNativeDepth0() {
        check("kiraAlreadyTargeted", 0, 0, true);
    }

    @Test
    public void kiraAlreadyTargetedPreviewDepth1() {
        check("kiraAlreadyTargeted", 0, 1, false);
    }

    @Test
    public void kiraAlreadyTargetedNativeDepth1() {
        check("kiraAlreadyTargeted", 0, 1, true);
    }

    @Test
    public void kiraAlreadyTargetedPreviewDepth2() {
        check("kiraAlreadyTargeted", 0, 2, false);
    }

    @Test
    public void kiraAlreadyTargetedNativeDepth2() {
        check("kiraAlreadyTargeted", 0, 2, true);
    }

    @Test
    public void netherPaidPreviewDepth0() {
        check("netherPaid", 0, 0, false);
    }

    @Test
    public void netherPaidNativeDepth0() {
        check("netherPaid", 0, 0, true);
    }

    @Test
    public void netherPaidPreviewDepth1() {
        check("netherPaid", 0, 1, false);
    }

    @Test
    public void netherPaidNativeDepth1() {
        check("netherPaid", 0, 1, true);
    }

    @Test
    public void netherPaidPreviewDepth2() {
        check("netherPaid", 0, 2, false);
    }

    @Test
    public void netherPaidNativeDepth2() {
        check("netherPaid", 0, 2, true);
    }

    @Test
    public void netherShortPreviewDepth0() {
        check("netherShort", 0, 0, false);
    }

    @Test
    public void netherShortNativeDepth0() {
        check("netherShort", 0, 0, true);
    }

    @Test
    public void netherShortPreviewDepth1() {
        check("netherShort", 0, 1, false);
    }

    @Test
    public void netherShortNativeDepth1() {
        check("netherShort", 0, 1, true);
    }

    @Test
    public void netherShortPreviewDepth2() {
        check("netherShort", 0, 2, false);
    }

    @Test
    public void netherShortNativeDepth2() {
        check("netherShort", 0, 2, true);
    }

    @Test
    public void annexPaidPreviewDepth0() {
        check("annexPaid", 0, 0, false);
    }

    @Test
    public void annexPaidNativeDepth0() {
        check("annexPaid", 0, 0, true);
    }

    @Test
    public void annexPaidPreviewDepth1() {
        check("annexPaid", 0, 1, false);
    }

    @Test
    public void annexPaidNativeDepth1() {
        check("annexPaid", 0, 1, true);
    }

    @Test
    public void annexPaidPreviewDepth2() {
        check("annexPaid", 0, 2, false);
    }

    @Test
    public void annexPaidNativeDepth2() {
        check("annexPaid", 0, 2, true);
    }

    @Test
    public void annexShortPreviewDepth0() {
        check("annexShort", 0, 0, false);
    }

    @Test
    public void annexShortNativeDepth0() {
        check("annexShort", 0, 0, true);
    }

    @Test
    public void annexShortPreviewDepth1() {
        check("annexShort", 0, 1, false);
    }

    @Test
    public void annexShortNativeDepth1() {
        check("annexShort", 0, 1, true);
    }

    @Test
    public void annexShortPreviewDepth2() {
        check("annexShort", 0, 2, false);
    }

    @Test
    public void annexShortNativeDepth2() {
        check("annexShort", 0, 2, true);
    }

    @Test
    public void chaliceCounterPreviewDepth0() {
        check("chaliceCounter", 0, 0, false);
    }

    @Test
    public void chaliceCounterNativeDepth0() {
        check("chaliceCounter", 0, 0, true);
    }

    @Test
    public void chaliceCounterPreviewDepth1() {
        check("chaliceCounter", 0, 1, false);
    }

    @Test
    public void chaliceCounterNativeDepth1() {
        check("chaliceCounter", 0, 1, true);
    }

    @Test
    public void chaliceCounterPreviewDepth2() {
        check("chaliceCounter", 0, 2, false);
    }

    @Test
    public void chaliceCounterNativeDepth2() {
        check("chaliceCounter", 0, 2, true);
    }

    @Test
    public void chaliceOtherChargePreviewDepth0() {
        check("chaliceOtherCharge", 0, 0, false);
    }

    @Test
    public void chaliceOtherChargeNativeDepth0() {
        check("chaliceOtherCharge", 0, 0, true);
    }

    @Test
    public void chaliceOtherChargePreviewDepth1() {
        check("chaliceOtherCharge", 0, 1, false);
    }

    @Test
    public void chaliceOtherChargeNativeDepth1() {
        check("chaliceOtherCharge", 0, 1, true);
    }

    @Test
    public void chaliceOtherChargePreviewDepth2() {
        check("chaliceOtherCharge", 0, 2, false);
    }

    @Test
    public void chaliceOtherChargeNativeDepth2() {
        check("chaliceOtherCharge", 0, 2, true);
    }

    @Test
    public void chaliceUncounterablePreviewDepth0() {
        check("chaliceUncounterable", 0, 0, false);
    }

    @Test
    public void chaliceUncounterableNativeDepth0() {
        check("chaliceUncounterable", 0, 0, true);
    }

    @Test
    public void chaliceUncounterablePreviewDepth1() {
        check("chaliceUncounterable", 0, 1, false);
    }

    @Test
    public void chaliceUncounterableNativeDepth1() {
        check("chaliceUncounterable", 0, 1, true);
    }

    @Test
    public void chaliceUncounterablePreviewDepth2() {
        check("chaliceUncounterable", 0, 2, false);
    }

    @Test
    public void chaliceUncounterableNativeDepth2() {
        check("chaliceUncounterable", 0, 2, true);
    }

    @Test
    public void boundary63PreviewDepth0() {
        check("boundary", 63, 0, false);
    }

    @Test
    public void boundary63NativeDepth0() {
        check("boundary", 63, 0, true);
    }

    @Test
    public void boundary63PreviewDepth1() {
        check("boundary", 63, 1, false);
    }

    @Test
    public void boundary63NativeDepth1() {
        check("boundary", 63, 1, true);
    }

    @Test
    public void boundary63PreviewDepth2() {
        check("boundary", 63, 2, false);
    }

    @Test
    public void boundary63NativeDepth2() {
        check("boundary", 63, 2, true);
    }

    @Test
    public void boundary64PreviewDepth0() {
        check("boundary", 64, 0, false);
    }

    @Test
    public void boundary64NativeDepth0() {
        check("boundary", 64, 0, true);
    }

    @Test
    public void boundary64PreviewDepth1() {
        check("boundary", 64, 1, false);
    }

    @Test
    public void boundary64NativeDepth1() {
        check("boundary", 64, 1, true);
    }

    @Test
    public void boundary64PreviewDepth2() {
        check("boundary", 64, 2, false);
    }

    @Test
    public void boundary64NativeDepth2() {
        check("boundary", 64, 2, true);
    }

    @Test
    public void boundary65PreviewDepth0() {
        check("boundary", 65, 0, false);
    }

    @Test
    public void boundary65NativeDepth0() {
        check("boundary", 65, 0, true);
    }

    @Test
    public void boundary65PreviewDepth1() {
        check("boundary", 65, 1, false);
    }

    @Test
    public void boundary65NativeDepth1() {
        check("boundary", 65, 1, true);
    }

    @Test
    public void boundary65PreviewDepth2() {
        check("boundary", 65, 2, false);
    }

    @Test
    public void boundary65NativeDepth2() {
        check("boundary", 65, 2, true);
    }

    @Test
    public void frostSuppressedPreviewDepth0() {
        check("frostSuppressed", 0, 0, false);
    }

    @Test
    public void frostSuppressedNativeDepth0() {
        check("frostSuppressed", 0, 0, true);
    }

    @Test
    public void frostSuppressedPreviewDepth1() {
        check("frostSuppressed", 0, 1, false);
    }

    @Test
    public void frostSuppressedNativeDepth1() {
        check("frostSuppressed", 0, 1, true);
    }

    @Test
    public void frostSuppressedPreviewDepth2() {
        check("frostSuppressed", 0, 2, false);
    }

    @Test
    public void frostSuppressedNativeDepth2() {
        check("frostSuppressed", 0, 2, true);
    }

    @Test
    public void chaliceSuppressedPreviewDepth0() {
        check("chaliceSuppressed", 0, 0, false);
    }

    @Test
    public void chaliceSuppressedNativeDepth0() {
        check("chaliceSuppressed", 0, 0, true);
    }

    @Test
    public void chaliceSuppressedPreviewDepth1() {
        check("chaliceSuppressed", 0, 1, false);
    }

    @Test
    public void chaliceSuppressedNativeDepth1() {
        check("chaliceSuppressed", 0, 1, true);
    }

    @Test
    public void chaliceSuppressedPreviewDepth2() {
        check("chaliceSuppressed", 0, 2, false);
    }

    @Test
    public void chaliceSuppressedNativeDepth2() {
        check("chaliceSuppressed", 0, 2, true);
    }

    @Test
    public void ownerHistoryPreviewDepth0() {
        check("ownerHistory", 0, 0, false);
    }

    @Test
    public void ownerHistoryNativeDepth0() {
        check("ownerHistory", 0, 0, true);
    }

    @Test
    public void ownerHistoryPreviewDepth1() {
        check("ownerHistory", 0, 1, false);
    }

    @Test
    public void ownerHistoryNativeDepth1() {
        check("ownerHistory", 0, 1, true);
    }

    @Test
    public void ownerHistoryPreviewDepth2() {
        check("ownerHistory", 0, 2, false);
    }

    @Test
    public void ownerHistoryNativeDepth2() {
        check("ownerHistory", 0, 2, true);
    }

    @Test
    public void opponentHistoryPreviewDepth0() {
        check("opponentHistory", 0, 0, false);
    }

    @Test
    public void opponentHistoryNativeDepth0() {
        check("opponentHistory", 0, 0, true);
    }

    @Test
    public void opponentHistoryPreviewDepth1() {
        check("opponentHistory", 0, 1, false);
    }

    @Test
    public void opponentHistoryNativeDepth1() {
        check("opponentHistory", 0, 1, true);
    }

    @Test
    public void opponentHistoryPreviewDepth2() {
        check("opponentHistory", 0, 2, false);
    }

    @Test
    public void opponentHistoryNativeDepth2() {
        check("opponentHistory", 0, 2, true);
    }
}
