package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class StarTrekCombatEntryTest extends AITest {
    private record Pod(Game game, Player actor, Player opponent, Player chosenDefender, Card kira,
            Card attacker, Combat combat, List<Integer> defenderChoices) {
        void collectTriggers() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
    }

    private Pod pod(boolean attacking) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Kira combat entry"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Player chosenDefender = game.getPlayers().get(2);
        List<Integer> choices = new ArrayList<>();
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin,
                    SpellAbility ability, CardCollection choices, DelayedReveal delayed,
                    String prompt, boolean optional, Player decider) {
                if (ability.getHostCard().getName().equals("Kaalia of the Vast")) {
                    return choices.stream().filter(card -> card.getName().equals("Serra Angel"))
                            .findFirst().orElseThrow();
                }
                return super.chooseSingleCardForZoneChange(destination, origin, ability, choices,
                        delayed, prompt, optional, decider);
            }

            @Override
            public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> options,
                    DelayedReveal delayed, SpellAbility ability, String title, boolean optional,
                    Player targetedPlayer, Map<String, Object> params) {
                if (params != null && params.get("Attacker") instanceof Card entrant) {
                    choices.add(entrant.getId());
                    return options.stream().filter(option -> option.equals(chosenDefender))
                            .findFirst().orElseThrow();
                }
                return super.chooseSingleEntityForEffect(options, delayed, ability, title, optional, targetedPlayer, params);
            }
        });
        Card kira = addCardToZone("Kira Nerys, Firebrand", actor, ZoneType.Battlefield);
        Card attacker = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 1);
        Combat combat = new Combat(actor);
        if (attacking) {
            combat.addAttacker(attacker, opponent);
        }
        game.getPhaseHandler().setCombat(combat);
        game.getAction().checkStateEffects(true);
        return new Pod(game, actor, opponent, chosenDefender, kira, attacker, combat, choices);
    }

    private Card blinkVanguard(Pod pod, Player player) {
        Card vanguard = addCardToZone("Skyknight Vanguard", player, ZoneType.Battlefield);
        long timestamp = vanguard.getGameTimestamp();
        addCardToZone("Plains", player, ZoneType.Battlefield);
        addCardToZone("Plains", player, ZoneType.Battlefield);
        Card blink = addCardToZone("Momentary Blink", player, ZoneType.Hand);
        SpellAbility spell = blink.getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        assertTrue(spell.canTarget(vanguard));
        spell.getTargets().add(vanguard);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(player, spell, null));
        pod.game.getStack().resolveStack();
        Card returned = pod.game.getCardState(vanguard);
        assertTrue(returned.isInPlay() && returned.getGameTimestamp() != timestamp);
        assertSame(returned.getController(), player);
        pod.collectTriggers();
        return returned;
    }

    @Test
    public void blinkedCreatureEntersAttackingChosenOpponentWithoutAttackTrigger() {
        Pod pod = pod(true);
        Card returned = blinkVanguard(pod, pod.actor);
        assertTrue(returned.isTapped());
        assertSame(pod.combat.getDefenderByAttacker(pod.attacker), pod.opponent);
        assertSame(pod.combat.getDefenderByAttacker(returned), pod.chosenDefender);
        assertEquals(pod.defenderChoices, List.of(returned.getId()));
        assertEquals(pod.game.getStack().size(), 1);
        assertSame(pod.game.getStack().peekAbility().getHostCard(), pod.kira);
        pod.game.getStack().resolveStack();
        assertEquals(returned.getNetPower(), 2);
        assertTrue(pod.game.getStack().isEmpty());
        assertTrue(pod.actor.getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isToken));
    }

    @Test
    public void noAttackingCreatureMeansOrdinaryEntry() {
        Pod pod = pod(false);
        Card returned = blinkVanguard(pod, pod.actor);
        assertFalse(returned.isTapped() || pod.combat.isAttacking(returned));
        assertTrue(pod.defenderChoices.isEmpty());
        assertEquals(pod.game.getStack().size(), 1);
        assertSame(pod.game.getStack().peekAbility().getHostCard(), pod.kira);
    }

    @Test
    public void opponentsCreatureDoesNotEnterAttackingOrReceiveKiraBonus() {
        Pod pod = pod(true);
        Card returned = blinkVanguard(pod, pod.opponent);
        assertFalse(returned.isTapped() || pod.combat.isAttacking(returned));
        assertTrue(pod.defenderChoices.isEmpty());
        assertTrue(pod.game.getStack().isEmpty());
    }

    @Test
    public void ninjutsuRetainsItsDefenderWithoutAskingForAnother() {
        Pod pod = pod(true);
        pod.combat.getBandOfAttacker(pod.attacker).setBlocked(false);
        Card blocked = addCardToZone("Runeclaw Bear", pod.actor, ZoneType.Battlefield);
        pod.combat.addAttacker(blocked, pod.chosenDefender);
        pod.combat.getBandOfAttacker(blocked).setBlocked(true);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card ninja = addCardToZone("Ninja of the Deep Hours", pod.actor, ZoneType.Hand);
        SpellAbility ninjutsu = ninja.getAllPossibleAbilities(pod.actor, true).stream()
                .filter(SpellAbility::isNinjutsu).findFirst().orElseThrow();
        ninjutsu.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, ninjutsu, null));
        assertTrue(pod.game.getCardState(pod.attacker).isInZone(ZoneType.Hand));
        assertTrue(pod.combat.isAttacking(blocked));
        pod.game.getStack().resolveStack();
        Card entered = pod.game.getCardState(ninja);
        assertTrue(entered.isInPlay() && entered.isTapped());
        assertSame(pod.combat.getDefenderByAttacker(entered), pod.opponent);
        assertEquals(pod.combat.getAttackersOf(pod.chosenDefender), List.of(blocked));
        assertTrue(pod.defenderChoices.isEmpty(), "Ninjutsu already specifies the original defender");
    }

    @Test
    public void kaaliaRetainsItsSpecifiedOpponentWithoutRequestingAChoice() {
        Pod pod = pod(true);
        Card kaalia = addCardToZone("Kaalia of the Vast", pod.actor, ZoneType.Battlefield);
        Card angel = addCardToZone("Serra Angel", pod.actor, ZoneType.Hand);
        pod.combat.addAttacker(kaalia, pod.opponent);
        pod.game.getAction().checkStaticAbilities();
        pod.game.getTriggerHandler().resetActiveTriggers();
        CombatUtil.checkDeclaredAttacker(pod.game, kaalia, pod.combat, true);
        pod.collectTriggers();
        assertEquals(pod.game.getStack().size(), 1);
        assertSame(pod.game.getStack().peekAbility().getHostCard(), kaalia);
        pod.game.getStack().resolveStack();
        Card entered = pod.game.getCardState(angel);
        assertTrue(entered.isInPlay() && entered.isTapped());
        assertSame(pod.combat.getDefenderByAttacker(entered), pod.opponent);
        assertTrue(pod.defenderChoices.isEmpty(), "Kaalia already specifies the attacked opponent");
    }

    @Test
    public void kaaliaEntrantStaysOutOfCombatIfSpecifiedOpponentHasLost() {
        Pod pod = pod(true);
        Card kaalia = addCardToZone("Kaalia of the Vast", pod.actor, ZoneType.Battlefield);
        Card angel = addCardToZone("Serra Angel", pod.actor, ZoneType.Hand);
        Card other = addCardToZone("Runeclaw Bear", pod.actor, ZoneType.Battlefield);
        pod.combat.addAttacker(kaalia, pod.opponent);
        pod.combat.addAttacker(other, pod.chosenDefender);
        pod.game.getAction().checkStaticAbilities();
        pod.game.getTriggerHandler().resetActiveTriggers();
        CombatUtil.checkDeclaredAttacker(pod.game, kaalia, pod.combat, true);
        pod.collectTriggers();
        assertEquals(pod.game.getStack().size(), 1);

        pod.opponent.setLife(0, null);
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.opponent.hasLost());
        assertTrue(pod.combat.isAttacking(other));
        pod.game.getStack().resolveStack();
        Card entered = pod.game.getCardState(angel);
        assertTrue(entered.isInPlay() && entered.isTapped());
        assertFalse(pod.combat.isAttacking(entered));
        assertTrue(pod.defenderChoices.isEmpty());
    }

    @Test
    public void ninjaStaysOutOfCombatIfOriginalDefenderHasLost() {
        Pod pod = pod(true);
        pod.combat.getBandOfAttacker(pod.attacker).setBlocked(false);
        Card blocked = addCardToZone("Runeclaw Bear", pod.actor, ZoneType.Battlefield);
        pod.combat.addAttacker(blocked, pod.chosenDefender);
        pod.combat.getBandOfAttacker(blocked).setBlocked(true);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card ninja = addCardToZone("Ninja of the Deep Hours", pod.actor, ZoneType.Hand);
        SpellAbility ninjutsu = ninja.getAllPossibleAbilities(pod.actor, true).stream()
                .filter(SpellAbility::isNinjutsu).findFirst().orElseThrow();
        ninjutsu.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, ninjutsu, null));
        assertTrue(pod.game.getCardState(pod.attacker).isInZone(ZoneType.Hand));

        pod.opponent.setLife(0, null);
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.opponent.hasLost());
        assertTrue(pod.combat.isAttacking(blocked));
        pod.game.getStack().resolveStack();
        Card entered = pod.game.getCardState(ninja);
        assertTrue(entered.isInPlay() && entered.isTapped());
        assertFalse(pod.combat.isAttacking(entered));
        assertTrue(pod.defenderChoices.isEmpty());
    }
}
