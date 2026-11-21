package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Partial states and supplied decisions;
native costs, effects and attack history. */
public class CommanderOptionalLifeGainTest extends AITest {
    @Test
    public void erebosDoesNotUseCornucopiasForbiddenLifeGain() {
        verifyCornucopia("Erebos, God of the Dead", true, 0, "Llanowar Elves");
    }
    @Test
    public void tibaltDoesNotUseCornucopiasForbiddenLifeGain() {
        verifyCornucopia("Tibalt, Rakish Instigator", true, 0, "Llanowar Elves");
    }
    @Test
    public void rainOfGoreReplacesALegalLifeGainAndUsesTheAction() {
        verifyCornucopia("Rain of Gore", false, -1, "Llanowar Elves");
    }
    @Test
    public void anOrdinaryLifeGainUsesTheAction() {
        verifyCornucopia(null, false, 1, "Llanowar Elves");
    }
    @Test
    public void aThreeColorSpellGainsThreeLifeAndUsesOneAction() {
        verifyCornucopia(null, false, 3, "Rhox War Monk");
    }
    @Test
    public void aThreeColorGainReplacedByLifeLossStillUsesOneAction() {
        verifyCornucopia("Rain of Gore", false, -3, "Rhox War Monk");
    }
    @Test
    public void earthKingdomKeepsItsActionUnderErebos() {
        verifyGeneral("Erebos, God of the Dead", true, 0);
    }
    @Test
    public void earthKingdomUsesAnOrdinaryGain() {
        verifyGeneral(null, false, 1);
    }
    @Test
    public void earthKingdomUsesAGainReplacedByLifeLoss() {
        verifyGeneral("Rain of Gore", false, -1);
    }
    @Test
    public void skyfisherCannotChooseForbiddenGainAndExileItself() {
        verifySpider("Erebos, God of the Dead", 0, ZoneType.Graveyard);
    }
    @Test
    public void skyfisherCanGainLifeAndExileItself() {
        verifySpider(null, 2, ZoneType.Exile);
    }
    @Test
    public void skyfisherCanReplaceTheGainAndStillExileItself() {
        verifySpider("Rain of Gore", -2, ZoneType.Exile);
    }

    @Test
    public void decliningCornucopiaPreservesTheAction() {
        Pod pod = pod("Ancient Cornucopia", null);
        pod.decline[0] = true;
        castCreature(pod, "Llanowar Elves");
        assertEquals(pod.actor.getLife(), 40);
        pod.decline[0] = false;
        castCreature(pod, "Runeclaw Bear");
        assertEquals(pod.actor.getLife(), 41);
        castCreature(pod, "Elvish Mystic");
        assertEquals(pod.actor.getLife(), 41);
        assertEquals(pod.prompts[0], 2);
    }

    @Test
    public void counteringCornucopiasTriggerPreservesTheAction() {
        Pod pod = pod("Ancient Cornucopia", null);
        SpellAbility elves = addCardToZone("Llanowar Elves", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        elves.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(elves, pod.actor, pod.game));
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 2);
        assertEquals(pod.game.getStack().peekAbility().getHostCard(), pod.host);
        Player responder = pod.game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", responder, ZoneType.Battlefield);
        }
        SpellAbility counter = addCardToZone("Disallow", responder, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(responder);
        counter.getTargets().add(pod.game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(counter, responder, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getLife(), 40);
        assertEquals(pod.prompts[0], 0);
        castCreature(pod, "Runeclaw Bear");
        assertEquals(pod.actor.getLife(), 41);
    }

    @Test
    public void priestCanRevealADinosaurEvenWhenTheResultingGainIsForbidden() {
        Pod pod = pod("Priest of the Wakening Sun", "Erebos, God of the Dead");
        Card dinosaur = addCardToZone("Colossal Dreadmaw", pod.actor, ZoneType.Hand);
        pod.game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, pod.actor, 2);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(pod.actor), false);
        settle(pod.game);
        assertEquals(pod.reveals[0], 1, "Revealing remains a legal optional action");
        assertEquals(pod.actor.getLife(), 40);
        assertTrue(pod.game.getCardState(dinosaur).isInZone(ZoneType.Hand));
    }

    @Test
    public void urosAttackStillDrawsWhenLifeGainIsForbiddenAndTheLandIsDeclined() {
        Pod pod = pod("Uro, Titan of Nature's Wrath", "Erebos, God of the Dead");
        pod.decline[0] = true;
        Card land = addCardToZone("Forest", pod.actor, ZoneType.Hand);
        pod.host.setSickness(false);
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, pod.actor, 2);
        Combat combat = new Combat(pod.actor);
        pod.game.getPhaseHandler().setCombat(combat);
        assertTrue(CombatUtil.canAttack(pod.host, pod.game.getPlayers().get(1)));
        combat.addAttacker(pod.host, pod.game.getPlayers().get(1));
        CombatUtil.checkDeclaredAttacker(pod.game, pod.host, combat, true);
        settle(pod.game);
        assertEquals(pod.actor.getLife(), 40);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 2, "The draw is mandatory");
        assertEquals(pod.landChoices[0], 1);
        assertEquals(pod.prompts[0], 0, "Only putting a land is optional");
        assertTrue(pod.game.getCardState(land).isInZone(ZoneType.Hand));
    }

    private void verifyCornucopia(String blocker, boolean prohibited, int firstChange, String spell) {
        Pod pod = pod("Ancient Cornucopia", blocker);
        assertEquals(pod.actor.canGainLife(), !prohibited);
        castCreature(pod, spell);
        assertEquals(pod.actor.getLife(), 40 + firstChange);
        removeBlocker(pod);
        assertTrue(pod.actor.canGainLife());
        int life = pod.actor.getLife();
        castCreature(pod, "Runeclaw Bear");
        assertEquals(pod.actor.getLife(), life + (prohibited ? 1 : 0), "A forbidden gain preserves the action");
        life = pod.actor.getLife();
        castCreature(pod, "Elvish Mystic");
        assertEquals(pod.actor.getLife(), life);
    }

    private void verifyGeneral(String blocker, boolean prohibited, int firstChange) {
        Pod pod = pod("Earth Kingdom General", blocker);
        Card bear = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Battlefield);
        counter(pod, bear);
        assertEquals(pod.actor.getLife(), 40 + firstChange);
        removeBlocker(pod);
        int life = pod.actor.getLife();
        counter(pod, pod.game.getCardState(bear));
        assertEquals(pod.actor.getLife(), life + (prohibited ? 1 : 0));
        life = pod.actor.getLife();
        counter(pod, pod.game.getCardState(bear));
        assertEquals(pod.actor.getLife(), life);
        assertEquals(pod.game.getCardState(bear).getCounters(CounterEnumType.P1P1), 3);
    }

    private void counter(Pod pod, Card target) {
        SpellAbility veil = addCardToZone("Snakeskin Veil", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        veil.setActivatingPlayer(pod.actor);
        veil.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(veil, pod.actor, pod.game));
        settle(pod.game);
    }

    @Test
    public void skyfisherAlsoGainsLifeAfterPaidRemoval() {
        verifySpider(null, 2, ZoneType.Exile, false);
    }

    @Test
    public void counteringTheSpellDoesNotUndoItsPaidSacrificeDeathTrigger() {
        Pod pod = pod("Skyfisher Spider", null);
        addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        pod.game.copyLastState();
        SpellAbility rites = addCardToZone("Village Rites", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        rites.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(rites, pod.actor, pod.game));
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 2);
        SpellAbility paidSpell = null;
        for (var entry : pod.game.getStack()) {
            if (entry.getSpellAbility().getHostCard().getName().equals("Village Rites")) {
                paidSpell = entry.getSpellAbility();
            }
        }
        assertTrue(paidSpell != null);
        Player responder = pod.game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", responder, ZoneType.Battlefield);
        }
        SpellAbility counter = addCardToZone("Counterspell", responder, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(responder);
        counter.getTargets().add(paidSpell);
        assertTrue(ComputerUtil.playStack(counter, responder, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), 42);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 0);
        assertEquals(pod.prompts[0], 1);
    }

    private void verifySpider(String blocker, int change, ZoneType destination) {
        verifySpider(blocker, change, destination, true);
    }

    private void verifySpider(String blocker, int change, ZoneType destination, boolean asCost) {
        Pod pod = pod("Skyfisher Spider", blocker);
        addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        SpellAbility rites = addCardToZone(asCost ? "Village Rites" : "Go for the Throat", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        rites.setActivatingPlayer(pod.actor);
        if (!asCost) {
            rites.getTargets().add(pod.host);
        }
        pod.game.getAction().checkStateEffects(true);
        // Supply the pre-cast snapshot normally taken by the priority loop.
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(rites, pod.actor, pod.game));
        if (asCost) {
            assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Graveyard), "The sacrifice cost must be paid");
        }
        queue(pod.game);
        settle(pod.game);
        assertEquals(pod.prompts[0], change == 0 ? 0 : 1, "The death trigger's optional decision");
        assertEquals(pod.actor.getLife(), 40 + change);
        assertTrue(pod.game.getCardState(pod.host).isInZone(destination));
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), asCost ? 2 : 0);
    }

    private Pod pod(String name, String blockerName) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Optional life gain"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        Card host = addCardToZone(name, actor, ZoneType.Battlefield);
        boolean[] decline = {false};
        int[] prompts = {0};
        int[] reveals = {0};
        int[] landChoices = {0};
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().getId() == host.getId()) {
                        assertTrue(ComputerUtil.playStack(ability, actor, game));
                    } else {
                        super.orderAndPlaySimultaneousSa(List.of(ability));
                    }
                }
            }
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                if (ability.getHostCard().getId() == host.getId()) {
                    prompts[0]++;
                    return !decline[0];
                }
                return super.confirmTrigger(ability);
            }
            @Override
            public boolean payCostToPreventEffect(Cost cost, SpellAbility ability, boolean paid, FCollectionView<Player> payers) {
                if (ability.getHostCard().equals(host) && name.equals("Priest of the Wakening Sun")) {
                    return new CostPayment(cost, ability).payComputerCosts(new AiCostDecision(actor, ability, true));
                }
                return super.payCostToPreventEffect(cost, ability, paid, payers);
            }
            @Override
            public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility ability, CardCollection options, DelayedReveal reveal, String prompt, boolean optional, Player decider) {
                if (ability.getHostCard().equals(host) && name.equals("Uro, Titan of Nature's Wrath")) {
                    assertTrue(optional);
                    landChoices[0]++;
                    return null;
                }
                return super.chooseSingleCardForZoneChange(destination, origin, ability, options, reveal, prompt, optional, decider);
            }
        });
        Player observer = game.getPlayers().get(1);
        observer.dangerouslySetController(new PlayerControllerAi(game, observer, observer.getLobbyPlayer()) {
            @Override
            public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String prefix, boolean suffix) {
                if (owner.equals(actor) && zone == ZoneType.Hand && cards.stream().anyMatch(c -> c.getName().equals("Colossal Dreadmaw"))) {
                    reveals[0]++;
                }
            }
        });
        for (String land : List.of("Forest", "Forest", "Forest", "Forest", "Forest", "Swamp", "Swamp", "Plains", "Plains", "Island")) {
            addCardToZone(land, actor, ZoneType.Battlefield);
        }
        for (int index = 0; index < 8; index++) {
            addCardToZone("Forest", actor, ZoneType.Library);
        }
        Card blocker = blockerName == null ? null : addCardToZone(blockerName, game.getPlayers().get(2), ZoneType.Battlefield);
        if (blocker != null && blocker.isPlaneswalker()) {
            blocker.setCounters(CounterEnumType.LOYALTY, 5);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        return new Pod(game, actor, host, blocker, decline, prompts, reveals, landChoices);
    }

    private void removeBlocker(Pod pod) {
        if (pod.blocker == null) {
            return;
        }
        int life = pod.actor.getLife();
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(pod.blocker);
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.blocker).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), life - 3);
    }

    private void castCreature(Pod pod, String name) {
        SpellAbility spell = addCardToZone(name, pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(spell.getHostCard()).isInZone(ZoneType.Battlefield));
    }
    private void queue(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void settle(Game game) {
        queue(game);
        for (int step = 0; step < 16 && !game.getStack().isEmpty(); step++) {
            game.getStack().resolveStack();
            queue(game);
        }
        assertTrue(game.getStack().isEmpty());
    }

    private record Pod(Game game, Player actor, Card host, Card blocker, boolean[] decline, int[] prompts, int[] reveals, int[] landChoices) {}
}
