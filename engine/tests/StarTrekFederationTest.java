package forge.ai;

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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class StarTrekFederationTest extends AITest {
    private record Pod(Game game, Player actor, Card academy, Card leader) {}

    private Pod pod(int forests) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Starfleet Federation"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public byte chooseColor(String message, SpellAbility ability, ColorSet colors) {
                if (ability.getHostCard().getName().equals("Starfleet Academy")) {
                    return MagicColor.BLUE;
                }
                return super.chooseColor(message, ability, colors);
            }
        });
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < forests; index++) {
            addCardToZone("Forest", actor, ZoneType.Battlefield);
        }
        Card academy = addCardToZone("Starfleet Academy", actor, ZoneType.Battlefield);
        Card leader = addCardToZone("Werewolf Pack Leader", actor, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        addCardToZone("Silvercoat Lion", actor, ZoneType.Battlefield);
        addCardToZone("Universal Automaton", actor, ZoneType.Battlefield);
        addCardToZone("Air Elemental", game.getPlayers().get(1), ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        return new Pod(game, actor, academy, leader);
    }

    private void activateAcademy(Pod pod, int expected) {
        SpellAbility mana = pod.academy.getManaAbilities().stream()
                .filter(ability -> ability.getPayCosts().getTotalMana().getCMC() == 5)
                .findFirst().orElseThrow();
        mana.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, mana, null));
        assertTrue(pod.academy.isTapped());
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getManaPool().getAmountOfColor(MagicColor.BLUE), expected);
    }

    @Test
    public void academyCountsUniqueTypesAndExcludesBorgAndOpposingCreatures() {
        Pod pod = pod(5);
        activateAcademy(pod, 4);
    }

    @Test
    public void updatedWerewolfAbilityReplacesAllEarlierTypesBeforeAcademyCounts() {
        Pod pod = pod(11);
        Card dagger = addCardToZone("Stalactite Dagger", pod.actor, ZoneType.Battlefield);
        SpellAbility equip = dagger.getNonManaAbilities().stream()
                .filter(SpellAbility::isEquip).findFirst().orElseThrow();
        equip.setActivatingPlayer(pod.actor);
        equip.getTargets().add(pod.leader);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, equip, null));
        pod.game.getStack().resolveStack();
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.leader.getType().hasCreatureType("Borg"));
        SpellAbility transform = pod.leader.getNonManaAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.Animate).findFirst().orElseThrow();
        transform.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, transform, null));
        pod.game.getStack().resolveStack();
        assertEquals(Set.copyOf(pod.leader.getType().getCreatureTypes()), Set.of("Werewolf"));
        activateAcademy(pod, 3);
    }
}
