package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntity;
import forge.game.combat.CombatUtil;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Forced blocking and finite prevention shields belong to the copied world. */
public class BlockingPreventionGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("expiry" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Keyword expiry"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 5; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        return game;
    }
    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture stack did not settle within its bound");
    }
    private Card find(Game game, int id) {
        return game.getCardsInGame().stream().filter(c -> c.getId() == id).findFirst().orElseThrow();
    }
    private void pay(Game game, SpellAbility ability, Player player, int mana) {
        ability.setActivatingPlayer(player);
        assertTrue(ability.canPlay(), "Action admission");
        assertTrue(ability.checkRestrictions(player), "Native restrictions");
        assertTrue(ComputerUtil.playStack(ability, player, game), "Native payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getStack().resolveStack();
        settle(game);
    }
    private Card cast(Game game, String name, Player player, GameEntity target, int mana) {
        Card card = addCardToZone(name, player, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        if (target instanceof Card c) { ability.getTargets().add(c); assertTrue(c.canBeTargetedBy(ability)); }
        else if (target instanceof Player p) { ability.getTargets().add(p); assertTrue(p.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
        return find(game, card.getId());
    }
    private void activate(Game game, Card card, Player player, GameEntity target, int mana, ApiType api) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(player);
        if (target instanceof Card c) { ability.getTargets().add(c); assertTrue(c.canBeTargetedBy(ability)); }
        else if (target instanceof Player p) { ability.getTargets().add(p); assertTrue(p.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
    }
    private void nextTurn(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
    }
    private void block(String name, boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player defender = game.getPlayers().get(1);
        for (int i = 0; i < 7; i++) addCardToZone("Forest", owner, ZoneType.Battlefield);
        addCardToZone("Island", owner, ZoneType.Battlefield);
        addCardToZone("Mountain", owner, ZoneType.Battlefield);
        Card attacker;
        Card blocker;
        if (name.equals("tusker")) {
            attacker = cast(game, "Avalanche Tusker", owner, null, 5);
            for (int turn = 0; turn < 4; turn++) nextTurn(game);
            blocker = addCardToZone("Wall of Stone", defender, ZoneType.Battlefield);
            game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, owner, 5);
            game.getPhaseHandler().setCombat(new Combat(owner));
            game.getCombat().addAttacker(attacker, defender);
            CombatUtil.checkDeclaredAttacker(game, attacker, game.getCombat(), true);
            settle(game);
        } else if (name.equals("sled")) {
            attacker = cast(game, "Auriok Siege Sled", owner, null, 6);
            blocker = addCardToZone("Bronze Sable", defender, ZoneType.Battlefield);
            activate(game, attacker, owner, blocker, 1, ApiType.MustBlock);
        } else {
            attacker = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
            blocker = addCardToZone("Runeclaw Bear", defender, ZoneType.Battlefield);
            Card spell = addCardToZone(name.equals("hunt") ? "Hunt Down" : "Monstrous Step", owner, ZoneType.Hand);
            SpellAbility ability = spell.getFirstSpellAbility();
            ability.getTargets().add(attacker);
            ability.getSubAbility().getTargets().add(blocker);
            pay(game, ability, owner, name.equals("hunt") ? 1 : 5);
        }
        assertEquals(blocker.getMustBlockCards().size(), 1, "Requirement exists before copying");
        assertSame(blocker.getMustBlockCards().get(0), attacker);
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copy = copier.makeCopy();
        Card mapped = find(copy, blocker.getId());
        assertEquals(mapped.getMustBlockCards().size(), 1, "Active forced-block requirement copied");
        assertSame(mapped.getMustBlockCards().get(0), find(copy, attacker.getId()), "Attacker reference belongs to copy");
        if (name.equals("tusker")) {
            assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(copy)));
            settle(copy);
        } else nextTurn(copy);
        assertTrue(mapped.getMustBlockCards().isEmpty(), "Copied requirement expires at its deadline");
        assertEquals(blocker.getMustBlockCards().size(), 1, "Source requirement remains active");
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), "Selected cleanup commands mapped");
        if (name.equals("tusker")) {
            assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
            settle(game);
        } else nextTurn(game);
        assertTrue(blocker.getMustBlockCards().isEmpty(), "Source expires independently");
    }
    private void shield(String name, boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) addCardToZone("Plains", owner, ZoneType.Battlefield);
        for (int i = 0; i < 4; i++) addCardToZone("Mountain", owner, ZoneType.Battlefield);
        boolean creature = name.equals("creature");
        GameEntity target = creature ? addCardToZone("Serra Angel", owner, ZoneType.Battlefield) : owner;
        cast(game, "Mending Hands", owner, target, 1);
        if (name.equals("stacked")) cast(game, "Mending Hands", owner, target, 1);
        if (name.equals("partial")) cast(game, "Lightning Bolt", owner, target, 1);
        int amount = name.equals("stacked") ? 8 : name.equals("partial") ? 1 : 4;
        assertEquals(target.getPreventNextDamageTotalShields(), amount, "Native shield quota before copying");
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copy = copier.makeCopy();
        Player copiedOwner = copy.getPlayers().get(0);
        GameEntity mapped = creature ? find(copy, ((Card) target).getId()) : copiedOwner;
        assertEquals(mapped.getPreventNextDamageTotalShields(), amount, "Active shield quota copied");
        nextTurn(copy);
        assertEquals(mapped.getPreventNextDamageTotalShields(), 0, "Copied shield expires at turn end");
        assertEquals(target.getPreventNextDamageTotalShields(), amount, "Source shield remains active");
        cast(copy, "Lightning Bolt", copiedOwner, mapped, 1);
        if (creature) assertEquals(((Card) mapped).getDamage(), 3, "Expired shield does not prevent damage");
        else assertEquals(copiedOwner.getLife(), 37);
        cast(game, "Lightning Bolt", owner, target, 1);
        assertEquals(target.getPreventNextDamageTotalShields(), Math.max(0, amount - 3));
        if (creature) assertEquals(((Card) target).getDamage(), 0, "Source shield prevents damage");
        else assertEquals(owner.getLife(), 40 - Math.max(0, 3 - amount));
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), "Selected cleanup commands mapped");
        nextTurn(game);
        assertEquals(target.getPreventNextDamageTotalShields(), 0);
        int sourceLife = owner.getLife();
        cast(game, "Lightning Bolt", owner, target, 1);
        if (creature) assertEquals(((Card) target).getDamage(), 3);
        else assertEquals(owner.getLife(), sourceLife - 3);
    }
    @Test public void huntWithStack() { block("hunt", true); }
    @Test public void huntWithoutStack() { block("hunt", false); }
    @Test public void stepWithStack() { block("step", true); }
    @Test public void stepWithoutStack() { block("step", false); }
    @Test public void sledWithStack() { block("sled", true); }
    @Test public void sledWithoutStack() { block("sled", false); }
    @Test public void tuskerWithStack() { block("tusker", true); }
    @Test public void tuskerWithoutStack() { block("tusker", false); }
    @Test public void playerWithStack() { shield("player", true); }
    @Test public void playerWithoutStack() { shield("player", false); }
    @Test public void creatureWithStack() { shield("creature", true); }
    @Test public void creatureWithoutStack() { shield("creature", false); }
    @Test public void partialWithStack() { shield("partial", true); }
    @Test public void partialWithoutStack() { shield("partial", false); }
    @Test public void stackedWithStack() { shield("stacked", true); }
    @Test public void stackedWithoutStack() { shield("stacked", false); }
}
