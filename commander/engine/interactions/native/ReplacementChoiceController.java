package commander.interactions;

import forge.LobbyPlayer;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed test controller: choose native effects without changing damage or card scripts. */
public final class ReplacementChoiceController extends PlayerControllerAi {
    private final Player affectedPlayer;
    private final List<String> preference;
    private final List<Map<String, Object>> choices = new ArrayList<>();

    public ReplacementChoiceController(Game game, Player player, LobbyPlayer lobby,
                                       List<String> preference) {
        super(game, player, lobby);
        if (preference.size() != 2 || !new HashSet<>(preference).equals(
                Set.of("Furnace of Rath", "Urza's Armor"))) {
            throw new IllegalArgumentException("Only the reviewed replacement pair is allowed");
        }
        this.affectedPlayer = player;
        this.preference = List.copyOf(preference);
    }

    @Override
    public ReplacementEffect chooseSingleReplacementEffect(List<ReplacementEffect> candidates) {
        if (candidates.isEmpty() || candidates.size() > 2 || choices.size() >= 2) {
            throw new IllegalStateException("Unexpected replacement choice inventory");
        }
        ReplacementEffect chosen = null;
        int rank = preference.size();
        Set<Integer> identities = new HashSet<>();
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (ReplacementEffect candidate : candidates) {
            Card host = candidate.getHostCard();
            int candidateRank = preference.indexOf(host.getName());
            if (candidate.getMode() != ReplacementType.DamageDone || candidateRank < 0
                    || !identities.add(candidate.getId())) {
                throw new IllegalStateException("Unexpected native replacement candidate");
            }
            evidence.add(Map.of(
                    "effect_id", candidate.getId(), "card_id", host.getId(),
                    "timestamp", host.getGameTimestamp(), "name", host.getName(),
                    "controller_id", host.getController().getId()));
            if (candidateRank < rank) {
                rank = candidateRank;
                chosen = candidate;
            }
        }
        if (chosen == null) {
            throw new IllegalStateException("No reviewed replacement candidate is available");
        }
        choices.add(Map.of(
                "decider_id", affectedPlayer.getId(), "candidates", List.copyOf(evidence),
                "chosen_id", chosen.getId()));
        return chosen;
    }

    public List<Map<String, Object>> getChoices() {
        return List.copyOf(choices);
    }
}
