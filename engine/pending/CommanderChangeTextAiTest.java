package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Supplied native word choices are controls; AI cases use the unmodified native controller. */
public class CommanderChangeTextAiTest extends AITest {
    @Test public void nativeColorWordChangePaysManaAndChangesTheAnthem() {
        Pod p = color(); assertEquals(p.beneficiary.getNetPower(), 2); castChosen(p);
        assertEquals(p.target.getChangedTextColorWords().get("White"), "Blue");
        assertEquals(p.beneficiary.getNetPower(), 3);
        assertEquals(p.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }
    @Test public void nativeBasicLandWordChangeChangesBlocking() {
        Pod p = land(); assertTrue(CombatUtil.canBlock(p.target, p.beneficiary)); castChosen(p);
        assertTrue(p.target.hasKeyword("Landwalk:Island")); assertFalse(p.target.hasKeyword("Landwalk:Forest"));
        assertFalse(CombatUtil.canBlock(p.target, p.beneficiary));
    }
    @Test public void nativeCreatureWordChangeChangesTheTribalBonus() {
        Pod p = creature(); assertEquals(p.beneficiary.getNetPower(), 2); castChosen(p);
        assertEquals(p.target.getChangedTextTypeWords().get("Elf"), "Goblin");
        assertEquals(p.beneficiary.getNetPower(), 3);
    }
    @Test public void nativeColorChangeCopiesWithoutSharingTheWordMap() {
        Pod p = color(); castChosen(p); GameCopier copier = new GameCopier(p.game).withStack(true);
        Game copy = copier.makeCopy(); Card mapped = copier.find(p.target);
        assertSame(mapped.getGame(), copy); assertEquals(mapped.getChangedTextColorWords().get("White"), "Blue");
        assertEquals(copier.find(p.beneficiary).getNetPower(), 3);
        mapped.addChangedTextColorWord("Blue", "Red", copy.getNextTimestamp(), 0);
        copy.getAction().checkStateEffects(true);
        assertEquals(p.target.getChangedTextColorWords().get("White"), "Blue");
        assertEquals(p.beneficiary.getNetPower(), 3);
    }
    @Test public void unusedColorWordsDoNotChangeNamesOrColor() {
        Pod p = base("Sleight of Mind", "White Knight", "Coral Merfolk", false); castChosen(p);
        assertEquals(p.target.getName(), "White Knight"); assertTrue(p.target.isWhite());
        assertEquals(p.target.getNetPower(), 2);
    }
    @Test public void nativeAbsentColorWordDoesNotCreateAnAnthem() {
        Pod p = base("Sleight of Mind", "Grizzly Bears", "Coral Merfolk", false); castChosen(p);
        assertEquals(p.beneficiary.getNetPower(), 2); assertEquals(p.target.getNetPower(), 2);
    }
    @Test public void aiAdmitsAUsefulColorWordChange() { admission(color()); }
    @Test public void aiAdmitsAUsefulBasicLandWordChange() { admission(land()); }
    @Test public void aiAdmitsAUsefulCreatureWordChange() { admission(creature()); }
    @Test public void heuristicPickerUsesAUsefulColorWordChange() { pick(null); }
    @Test public void hybridPickerUsesAUsefulColorWordChange() { pick(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPickerUsesAUsefulColorWordChange() { pick(AIOption.USE_FULL_SIMULATION); }

    private void admission(Pod p) {
        assertEquals(ai(p).canPlaySa(p.action), AiPlayDecision.WillPlay);
        assertTrue(p.game.getStack().isEmpty());
        assertTrue(p.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }
    private void pick(AIOption mode) {
        Pod p = color(); ai(p).setUseSimulation(mode); List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "Expected a useful text change in mode " + mode);
        SpellAbility selected = choices.stream().filter(a -> a.getApi() == ApiType.ChangeText).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, p.actor, p.game)); settle(p.game);
        assertEquals(p.beneficiary.getNetPower(), 3, "Actual native controller must choose useful words");
    }
    private AiController ai(Pod p) { return ((PlayerControllerAi)p.actor.getController()).getAi(); }
    private Pod color() { return base("Sleight of Mind", "Honor of the Pure", "Coral Merfolk", false); }
    private Pod land() { return base("Magical Hack", "Shanodin Dryads", "Grizzly Bears", true); }
    private Pod creature() { return base("Artificial Evolution", "Elvish Archdruid", "Goblin Piker", false); }
    private Pod base(String spell, String targetName, String beneficiaryName, boolean enemyBeneficiary) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0;i<4;i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat"+i,null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats,rules,new Match(rules,seats,"Text change AI")); game.setNoGUIUser();game.setAge(GameStage.Play);
        Player actor=game.getPlayers().get(0); Player enemy=game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1,actor,2);
        for (Player player:game.getPlayers()) for(int i=0;i<8;i++) addCardToZone("Forest",player,ZoneType.Library);
        for(int i=0;i<2;i++) addCardToZone("Island",actor,ZoneType.Battlefield);
        addCardToZone("Island",enemy,ZoneType.Battlefield);
        Card target=addCardToZone(targetName,actor,ZoneType.Battlefield);target.setSickness(false);
        Card beneficiary=addCardToZone(beneficiaryName,enemyBeneficiary?enemy:actor,ZoneType.Battlefield);beneficiary.setSickness(false);
        // Blue cards that cannot be cast with two lands prevent a coincidental empty-hand color choice.
        addCardToZone("Omniscience",actor,ZoneType.Hand);addCardToZone("Enter the Infinite",actor,ZoneType.Hand);
        Card host=addCardToZone(spell,actor,ZoneType.Hand);
        SpellAbility action=host.getSpellAbilities().stream().filter(a->a.getApi()==ApiType.ChangeText).findFirst().orElseThrow();action.setActivatingPlayer(actor);
        game.getAction().checkStateEffects(true);game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
        return new Pod(game,actor,host,target,beneficiary,action);
    }
    private void castChosen(Pod p) {
        PlayerController original=p.actor.getController();
        p.actor.dangerouslySetController(new PlayerControllerAi(p.game,p.actor,p.actor.getLobbyPlayer()) {
            @Override public byte chooseColor(String title,SpellAbility action,ColorSet options) {
                return (options.getColor() & MagicColor.WHITE)!=0 ? MagicColor.WHITE : MagicColor.BLUE;
            }
            @Override public String chooseSomeType(String kind,SpellAbility action,Collection<String> valid,boolean optional) {
                if(kind.equals("Creature")) return valid.contains("Elf")?"Elf":"Goblin";
                return valid.contains("Forest")?"Forest":"Island";
            }
        });
        try {
            p.action.getTargets().add(p.target);assertTrue(p.action.isTargetNumberValid());
            assertTrue(ComputerUtilCost.canPayCost(p.action,p.actor,false));
            assertTrue(ComputerUtil.playStack(p.action,p.actor,p.game));settle(p.game);
        } finally { p.actor.dangerouslySetController(original); }
    }
    private void settle(Game game) {
        for(int i=0;i<20;i++) {
            game.getAction().checkStateEffects(true);game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();
            if(game.getStack().isEmpty())return;game.getStack().resolveStack();
        }
        throw new AssertionError("Native stack did not settle");
    }
    private record Pod(Game game,Player actor,Card host,Card target,Card beneficiary,SpellAbility action) {}
}
