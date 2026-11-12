package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
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

public class StarTrekHoloEngineerTest extends AITest {
    private void collectTriggers(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    @Test
    public void replacesAngelCharacteristicsButKeepsCombatEntryAndDelayedExile() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Holo-Engineer replacement"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player originalDefender = game.getPlayers().get(1);
        Player tokenDefender = game.getPlayers().get(2);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> choices,
                    DelayedReveal delayed, SpellAbility ability, String title, boolean optional,
                    Player targetedPlayer, Map<String, Object> params) {
                if (params != null && params.get("Attacker") instanceof Card entrant && entrant.isToken()) {
                    return choices.stream().filter(choice -> choice.equals(tokenDefender)).findFirst().orElseThrow();
                }
                return super.chooseSingleEntityForEffect(choices, delayed, ability, title, optional, targetedPlayer, params);
            }
        });
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 1);
        addCardToZone("Holo-Engineer", actor, ZoneType.Battlefield);
        Card geist = addCardToZone("Geist of Saint Traft", actor, ZoneType.Battlefield);
        Combat combat = new Combat(actor);
        combat.addAttacker(geist, originalDefender);
        game.getPhaseHandler().setCombat(combat);
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        CombatUtil.checkDeclaredAttacker(game, geist, combat, true);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 1);
        game.getStack().resolveStack();

        List<Card> tokens = actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList();
        assertEquals(tokens.size(), 1);
        Card token = tokens.get(0);
        assertTrue(token.isTapped() && token.isCreature() && token.isArtifact());
        assertTrue(token.getColor().isColorless() && token.getType().hasCreatureType("Illusion"));
        assertFalse(token.hasKeyword(Keyword.FLYING) || token.getType().hasCreatureType("Angel"));
        assertEquals(token.getNetPower(), 3);
        assertEquals(token.getNetToughness(), 3);
        assertSame(combat.getDefenderByAttacker(token), tokenDefender);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_END, actor, false);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(actor), false);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 1);
        game.getStack().resolveStack();
        assertFalse(actor.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card -> card.getId() == token.getId()));
    }
}
