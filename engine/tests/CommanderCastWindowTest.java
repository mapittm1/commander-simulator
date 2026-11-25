package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.card.CardStateName;
import forge.game.card.CounterEnumType;
import forge.game.trigger.TriggerWaiting;
import forge.game.spellability.AbilityStatic;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native activation windows and guarded in-progress snapshot contracts. */
public class CommanderCastWindowTest extends AITest {
    private Player actor(Game game) {
        return game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("window-0")).findFirst().orElseThrow();
    }

    private Game setup(int forests) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0; i<4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("window-"+i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Native activation windows"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int i=0; i<24; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        Card monolith = addCardToZone("Basalt Monolith", actor(game), ZoneType.Battlefield);
        monolith.setTapped(true);
        for (int i=0; i<forests; i++) addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private Card monolith(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Basalt Monolith")).findFirst().orElseThrow();
    }

    private SpellAbility activation(Game game) {
        SpellAbility result = monolith(game).getSpellAbilities().stream().filter(a -> a.getApi()==ApiType.Untap).findFirst().orElseThrow();
        result.setActivatingPlayer(actor(game));
        return result;
    }

    private Map<Integer, String> state(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) result.put(card.getId(), card.getZone().getZoneType()+":"+card.isTapped());
        for (Player player : game.getRegisteredPlayers()) result.put(-player.getId(), "life="+player.getLife()+";mana="+player.getManaPool().totalMana()+";lost="+player.hasLost());
        result.put(Integer.MIN_VALUE, "over="+game.isGameOver()+";frozen="+game.getStack().isFrozen()+";resolving="+game.getStack().isResolving()+";stack="+game.getStack().size());
        return result;
    }

    private void check(String mode, int depth, boolean nativePayment) {
        int budget = mode.equals("noBudget") ? 0 : 1;
        Game original = setup(mode.equals("funded") ? 4 : 3);
        Map<Integer, String> originalBefore = state(original);
        original.copyLastState();
        Game game = original;
        for (int i=0; i<depth; i++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        Player payer = actor(game);
        SpellAbility goal = activation(game);
        Map<Integer, String> before = state(game);
        boolean expected = !mode.equals("unfunded");
        if (!nativePayment) {
            try {
                boolean found = (Boolean) ComputerUtilMana.class.getMethod("canPayManaCostWithWardCosts", Cost.class, SpellAbility.class,
                        Player.class, int.class, boolean.class).invoke(null, goal.getPayCosts(), goal, payer, budget, false);
                assertEquals(found, expected, "A follow-up cost cannot use resources from the unresolved activation");
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Native pre-goal cost forecast API must be available", error);
            }
            assertEquals(state(game), before);
        } else {
            assertTrue(new CostPayment(goal.getPayCosts(), goal).payComputerCosts(new AiCostDecision(payer, goal, false, true)));
            int originalAbilityId = goal.getId();
            game.getStack().add(goal);
            assertNotEquals(game.getStack().peekAbility().getId(), originalAbilityId, "Native activation makes a fresh ability object");
            assertTrue(monolith(game).isTapped(), "The proposed untap has not resolved");
            SpellAbility feeContext = AbilityFactory.getAbility("DB$ Draw | NumCards$ 0", monolith(game));
            feeContext.setActivatingPlayer(payer);
            Cost fee = new Cost(Integer.toString(budget), false);
            assertEquals(new CostPayment(fee, feeContext).payComputerCosts(new AiCostDecision(payer, feeContext, true, true)), expected);
            assertTrue(monolith(game).isTapped(), "Follow-up payment must precede the untap");
            GameSimulator.resolveStack(game, payer);
            assertFalse(monolith(game).isTapped());
            if (mode.equals("unfunded")) {
                SpellAbility mana = monolith(game).getManaAbilities().get(0);
                mana.setActivatingPlayer(payer);
                assertTrue(new CostPayment(mana.getPayCosts(), mana).payComputerCosts(new AiCostDecision(payer, mana, false, true)));
                game.getStack().addAndUnfreeze(mana);
                assertEquals(payer.getManaPool().totalMana(), 3);
                assertTrue(new CostPayment(fee, feeContext).payComputerCosts(new AiCostDecision(payer, feeContext, true, true)), "The same fee becomes payable after the activation resolves");
            }
        }
        if (depth>0) assertEquals(state(original), originalBefore);
    }

    private GameCopier deferred(Game game) {
        try {
            return (GameCopier) GameCopier.class.getMethod("withStateBasedActions", boolean.class)
                    .invoke(new GameCopier(game).withStack(true), false);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError(error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Guarded state-only snapshot API must be available", error);
        }
    }

    @Test
    public void ordinaryPriorityWindowCannotDisableStateChecks() {
        Game game = setup(3);
        Map<Integer, String> before = state(game);
        assertThrows(IllegalStateException.class, () -> deferred(game));
        assertEquals(state(game), before);
    }

    @Test
    public void inProgressCopyCannotAdvanceAPhase() {
        Game game = setup(3);
        game.getStack().freezeStack(activation(game));
        Map<Integer, String> before = state(game);
        assertThrows(IllegalArgumentException.class, () -> deferred(game).makeCopy(PhaseType.MAIN2, actor(game)));
        assertEquals(state(game), before);
    }

    @Test
    public void deferredCopyCannotOutliveItsNativeWindow() {
        Game game = setup(3);
        game.getStack().freezeStack(activation(game));
        GameCopier copier = deferred(game);
        game.getStack().unfreezeStack();
        Map<Integer, String> before = state(game);
        assertThrows(IllegalStateException.class, () -> copier.makeCopy(null, actor(game)));
        assertEquals(state(game), before);
    }

    @Test
    public void stateOnlyCopyPreservesInProgressValuesWithoutWeakeningTheDefault() {
        Game game = setup(3);
        game.getStack().freezeStack(activation(game));
        // Explicit partial-state API probe. Actual legal continuation timing is witnessed by the
        // retained Stunning Reversal/Panglacial Wurm cleanup-priority cases.
        actor(game).setLife(0, null);
        Map<Integer, String> before = state(game);
        Game copy = deferred(game).makeCopy(null, actor(game));
        assertTrue(actor(copy).isInGame());
        assertEquals(actor(copy).getLife(), 0);
        assertFalse(copy.isGameOver());
        Game checked = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        assertTrue(actor(checked).hasLost(), "Ordinary snapshots retain their existing state checks");
        assertEquals(state(game), before);
    }

    @Test
    public void noBudgetPreviewDepth0() {
        check("noBudget", 0, false);
    }

    @Test
    public void noBudgetNativeDepth0() {
        check("noBudget", 0, true);
    }

    @Test
    public void noBudgetPreviewDepth1() {
        check("noBudget", 1, false);
    }

    @Test
    public void noBudgetNativeDepth1() {
        check("noBudget", 1, true);
    }

    @Test
    public void noBudgetPreviewDepth2() {
        check("noBudget", 2, false);
    }

    @Test
    public void noBudgetNativeDepth2() {
        check("noBudget", 2, true);
    }

    @Test
    public void unfundedPreviewDepth0() {
        check("unfunded", 0, false);
    }

    @Test
    public void unfundedNativeDepth0() {
        check("unfunded", 0, true);
    }

    @Test
    public void unfundedPreviewDepth1() {
        check("unfunded", 1, false);
    }

    @Test
    public void unfundedNativeDepth1() {
        check("unfunded", 1, true);
    }

    @Test
    public void unfundedPreviewDepth2() {
        check("unfunded", 2, false);
    }

    @Test
    public void unfundedNativeDepth2() {
        check("unfunded", 2, true);
    }

    @Test
    public void fundedPreviewDepth0() {
        check("funded", 0, false);
    }

    @Test
    public void fundedNativeDepth0() {
        check("funded", 0, true);
    }

    @Test
    public void fundedPreviewDepth1() {
        check("funded", 1, false);
    }

    @Test
    public void fundedNativeDepth1() {
        check("funded", 1, true);
    }

    @Test
    public void fundedPreviewDepth2() {
        check("funded", 2, false);
    }

    @Test
    public void fundedNativeDepth2() {
        check("funded", 2, true);
    }
    private void check(boolean tapped, int depth, boolean nativePayment) {
        List<RegisteredPlayer> seats=new ArrayList<>();
        for(int i=0;i<4;i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("mana-"+i,null)));
        GameRules rules=new GameRules(GameType.Commander);
        Game game=new Game(seats,rules,new Match(rules,seats,"Nonstack admission probe"));
        game.setNoGUIUser();game.setAge(GameStage.Play);
        Player payer=game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("mana-0")).findFirst().orElseThrow();
        for(Player player:game.getPlayers()) {player.setLife(100,null);for(int i=0;i<20;i++) addCardToZone("Forest",player,ZoneType.Library);}
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1,payer,5);
        addCardToZone("Glava, Five-Advents Mage",payer,ZoneType.Battlefield);
        Card land=addCardToZone("Forest",payer,ZoneType.Battlefield);land.setTapped(tapped);
        game.getAction().checkStaticAbilities(false);game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
        for (int i=0; i<depth; i++) {GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,payer);copier.release();payer=game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("mana-0")).findFirst().orElseThrow();}
        land=payer.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Forest")).findFirst().orElseThrow();
        SpellAbility mana=land.getManaAbilities().get(0);mana.setActivatingPlayer(payer);assertTrue(mana.isManaAbility());
        if(!nativePayment) {assertEquals(ComputerUtilCost.canPayCost(mana,payer,false),!tapped);assertEquals(land.isTapped(),tapped);assertEquals(payer.getManaPool().totalMana(),0);}
        else {boolean paid=new CostPayment(mana.getPayCosts(),mana).payComputerCosts(new AiCostDecision(payer,mana,false,true));assertEquals(paid,!tapped);if(paid) game.getStack().addAndUnfreeze(mana);assertTrue(game.getStack().isEmpty());assertEquals(payer.getManaPool().totalMana(),tapped?0:1);}
    }

    private void ritual(boolean countered, int depth, boolean nativePayment) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0; i<4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("ritual-"+i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Mana spell stack control"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player payer = game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("ritual-0")).findFirst().orElseThrow();
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int i=0; i<20; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, payer, 5);
        addCardToZone("Swamp", payer, ZoneType.Battlefield);
        Card chalice = addCardToZone("Chalice of the Void", game.getPlayers().get(1), ZoneType.Battlefield);
        chalice.setCounters(CounterEnumType.CHARGE, countered ? 1 : 2);
        Card card = addCardToZone("Dark Ritual", payer, ZoneType.Hand);
        SpellAbility spell = card.getFirstSpellAbility(); spell.setActivatingPlayer(payer);
        assertTrue(spell.isSpell()); assertFalse(spell.isManaAbility());
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        for (int i=0; i<depth; i++) {GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,payer);copier.release();payer=game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("ritual-0")).findFirst().orElseThrow();}
        card=payer.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Dark Ritual")).findFirst().orElseThrow();
        spell=card.getFirstSpellAbility();spell.setActivatingPlayer(payer);
        if (!nativePayment) {
            assertEquals(ComputerUtilCost.canPayCost(spell, payer, false), !countered);
            assertTrue(card.isInZone(ZoneType.Hand)); assertTrue(game.getStack().isEmpty());
            assertEquals(payer.getManaPool().totalMana(), 0);
        } else {
            Card onStack = game.getAction().moveToStack(card, spell);
            TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
            spell.setHostCard(onStack);
            assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(new AiCostDecision(payer, spell, false, true)));
            game.getTriggerHandler().commitCastPlacement(placement);
            game.getStack().addAndUnfreeze(spell);
            assertFalse(game.getStack().isEmpty());
            GameSimulator.resolveStack(game, payer);
            assertEquals(payer.getManaPool().totalMana(), countered ? 0 : 3);
            assertTrue(spell.getHostCard().isInZone(ZoneType.Graveyard));
        }
    }

    private void morph(boolean funded, int depth, boolean nativePayment) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0; i<4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("morph-"+i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Non-stack special action"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player payer = game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("morph-0")).findFirst().orElseThrow();
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int i=0; i<20; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, payer, 5);
        addCardToZone("Glava, Five-Advents Mage", payer, ZoneType.Battlefield);
        if (funded) addCardToZone("Island", payer, ZoneType.Battlefield);
        Card creature = addCardToZone("Monastery Flock", payer, ZoneType.Battlefield);
        creature.turnFaceDown(true);
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        for (int i=0; i<depth; i++) {GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,payer);copier.release();payer=game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("morph-0")).findFirst().orElseThrow();}
        creature=payer.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isFaceDown).findFirst().orElseThrow();
        SpellAbility action = creature.getState(CardStateName.Original).getSpellAbilities().stream().filter(SpellAbility::isTurnFaceUp).findFirst().orElseThrow();
        action.setActivatingPlayer(payer);
        assertTrue(action instanceof AbilityStatic); assertFalse(action.isManaAbility());
        if (!nativePayment) {
            assertEquals(ComputerUtilCost.canPayCost(action, payer, false), funded);
            assertTrue(creature.isFaceDown()); assertTrue(game.getStack().isEmpty());
            assertEquals(payer.getManaPool().totalMana(), 0);
        } else {
            boolean paid = new CostPayment(action.getPayCosts(), action).payComputerCosts(new AiCostDecision(payer, action, false, true));
            assertEquals(paid, funded);
            if (paid) game.getStack().addAndUnfreeze(action);
            assertEquals(creature.isFaceDown(), !funded); assertTrue(game.getStack().isEmpty());
        }
    }
    @Test public void untappedManaDepth0Preview() { check(false, 0, false); }
    @Test public void untappedManaDepth0Native() { check(false, 0, true); }
    @Test public void untappedManaDepth1Preview() { check(false, 1, false); }
    @Test public void untappedManaDepth1Native() { check(false, 1, true); }
    @Test public void untappedManaDepth2Preview() { check(false, 2, false); }
    @Test public void untappedManaDepth2Native() { check(false, 2, true); }
    @Test public void tappedManaDepth0Preview() { check(true, 0, false); }
    @Test public void tappedManaDepth0Native() { check(true, 0, true); }
    @Test public void tappedManaDepth1Preview() { check(true, 1, false); }
    @Test public void tappedManaDepth1Native() { check(true, 1, true); }
    @Test public void tappedManaDepth2Preview() { check(true, 2, false); }
    @Test public void tappedManaDepth2Native() { check(true, 2, true); }
    @Test public void counteredManaSpellDepth0Preview() { ritual(true, 0, false); }
    @Test public void counteredManaSpellDepth0Native() { ritual(true, 0, true); }
    @Test public void counteredManaSpellDepth1Preview() { ritual(true, 1, false); }
    @Test public void counteredManaSpellDepth1Native() { ritual(true, 1, true); }
    @Test public void counteredManaSpellDepth2Preview() { ritual(true, 2, false); }
    @Test public void counteredManaSpellDepth2Native() { ritual(true, 2, true); }
    @Test public void allowedManaSpellDepth0Preview() { ritual(false, 0, false); }
    @Test public void allowedManaSpellDepth0Native() { ritual(false, 0, true); }
    @Test public void allowedManaSpellDepth1Preview() { ritual(false, 1, false); }
    @Test public void allowedManaSpellDepth1Native() { ritual(false, 1, true); }
    @Test public void allowedManaSpellDepth2Preview() { ritual(false, 2, false); }
    @Test public void allowedManaSpellDepth2Native() { ritual(false, 2, true); }
    @Test public void fundedMorphDepth0Preview() { morph(true, 0, false); }
    @Test public void fundedMorphDepth0Native() { morph(true, 0, true); }
    @Test public void fundedMorphDepth1Preview() { morph(true, 1, false); }
    @Test public void fundedMorphDepth1Native() { morph(true, 1, true); }
    @Test public void fundedMorphDepth2Preview() { morph(true, 2, false); }
    @Test public void fundedMorphDepth2Native() { morph(true, 2, true); }
    @Test public void unfundedMorphDepth0Preview() { morph(false, 0, false); }
    @Test public void unfundedMorphDepth0Native() { morph(false, 0, true); }
    @Test public void unfundedMorphDepth1Preview() { morph(false, 1, false); }
    @Test public void unfundedMorphDepth1Native() { morph(false, 1, true); }
    @Test public void unfundedMorphDepth2Preview() { morph(false, 2, false); }
    @Test public void unfundedMorphDepth2Native() { morph(false, 2, true); }
}
