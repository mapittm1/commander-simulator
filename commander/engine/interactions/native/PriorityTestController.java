package commander.interactions;

import forge.ai.ComputerUtilCost;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed partial-state test choices. Forge owns payment, priority and resolution. */
public final class PriorityTestController extends PlayerControllerAi {
    private final Card proposal;
    private final Card original;
    private final boolean respond;
    private final List<Map<String, Object>> journal;
    private boolean offered;

    public PriorityTestController(Game game, Player player, Card proposal,
            Card original, boolean respond, List<Map<String, Object>> journal) {
        super(game, player, player.getLobbyPlayer());
        if (!List.of("Bonesplitter", "Disallow").contains(original.getName())
                || (proposal != null && !List.of("Bonesplitter", "Disallow").contains(proposal.getName()))
                || (original.getName().equals("Disallow")
                    && (proposal == null || !proposal.getName().equals("Disallow") || proposal == original))) {
            throw new IllegalArgumentException("Unknown fixed priority proposal");
        }
        this.proposal = proposal;
        this.original = original;
        this.respond = respond;
        this.journal = journal;
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        if (journal.size() >= 32) {
            throw new IllegalStateException("Priority callback bound exceeded");
        }
        List<Integer> stack = new ArrayList<>();
        SpellAbility pending = null;
        for (var entry : getGame().getStack()) {
            SpellAbility ability = entry.getSpellAbility();
            stack.add(ability.getId());
            if (ability.getHostCard().getId() == original.getId()) {
                pending = ability;
            }
        }
        SpellAbility selected = null;
        boolean canPlay = false;
        boolean canPay = false;
        boolean candidate = proposal != null && !offered &&
                (proposal == original ? stack.isEmpty() : pending != null);
        List<Integer> targets = new ArrayList<>();
        if (candidate) {
            offered = true;
            selected = proposal.getFirstSpellAbility();
            selected.setActivatingPlayer(player);
            if (proposal != original) {
                if (!selected.canTarget(pending)) {
                    throw new IllegalStateException("Counter target is illegal");
                }
                selected.getTargets().add(pending);
                targets.add(pending.getId());
            }
            canPlay = selected.canPlay();
            canPay = ComputerUtilCost.canPayCost(selected, player, false);
        }
        boolean cast = candidate && canPlay && canPay && (proposal == original || respond);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("player_id", player.getId());
        row.put("priority_id", getGame().getPhaseHandler().getPriorityPlayer().getId());
        row.put("stack", stack);
        row.put("proposal_id", candidate ? selected.getId() : null);
        row.put("targets", targets);
        row.put("can_play", canPlay);
        row.put("can_pay", canPay);
        row.put("cast", cast);
        journal.add(row);
        return cast ? List.of(selected) : null;
    }
}
