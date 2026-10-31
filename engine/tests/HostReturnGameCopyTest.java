package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntity;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.card.CardCopyService;
import forge.game.card.CounterEnumType;
import forge.game.player.PlayerController;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Exiled permanents return only in the world whose host left. */
public class HostReturnGameCopyTest extends AITest {
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
        ability.setActivatingPlayer(player);
        if (target instanceof Card c) { ability.getTargets().add(c); assertTrue(ability.canTarget(c)); assertTrue(c.canBeTargetedBy(ability)); }
        else if (target instanceof Player p) { ability.getTargets().add(p); assertTrue(p.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
        return find(game, card.getId());
    }
    private void activate(Game game, Card card, Player player, GameEntity target, int mana, ApiType api) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(player);
        if (target instanceof Card c) { ability.getTargets().add(c); assertTrue(ability.canTarget(c)); assertTrue(c.canBeTargetedBy(ability)); }
        else if (target instanceof Player p) { ability.getTargets().add(p); assertTrue(p.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
    }
    private void nextTurn(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
    }
    private Player prepare(Game game) {
        for (Player p : game.getPlayers()) {
            for (int i = 0; i < 10; i++) addCardToZone("Plains", p, ZoneType.Battlefield);
            for (int i = 0; i < 4; i++) addCardToZone("Island", p, ZoneType.Battlefield);
            for (int i = 0; i < 4; i++) addCardToZone("Swamp", p, ZoneType.Battlefield);
            addCardToZone("Forest", p, ZoneType.Battlefield);
        }
        return game.getPlayers().get(0);
    }
    private Card inZone(Game game, int id, ZoneType zone) {
        return game.getCardsIn(zone).stream().filter(c -> c.getId() == id).findFirst().orElse(null);
    }
    private Card lagrella(Game game, Player owner, List<Card> targets) {
        PlayerController original = owner.getController();
        owner.dangerouslySetController(new PlayerControllerAi(game, owner, owner.getLobbyPlayer()) {
            @Override public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : orderSimultaneousSa(abilities)) {
                    if (ability.getApi() == ApiType.ChangeZone && ability.hasParam("ReturnAbility")) {
                        ability.resetTargets();
                        for (Card target : targets) {
                            assertTrue(ability.canTarget(target), "Fixed legal Lagrella target");
                            ability.getTargets().add(target);
                        }
                        assertTrue(ability.isTargetNumberValid());
                        assertTrue(ComputerUtil.playStack(ability, owner, game));
                    } else super.orderAndPlaySimultaneousSa(List.of(ability));
                }
            }
        });
        try { return cast(game, "Lagrella, the Magpie", owner, null, 3); }
        finally { owner.dangerouslySetController(original); }
    }
    private void witness(String kind, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        List<Card> targets = new ArrayList<>();
        if (kind.equals("hand")) targets.add(addCardToZone("Lightning Bolt", game.getPlayers().get(1), ZoneType.Hand));
        else if (kind.equals("token")) {
            Player opponent = game.getPlayers().get(1);
            cast(game, "Raise the Alarm", opponent, null, 2);
            targets.addAll(opponent.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList());
            assertEquals(targets.size(), 2);
        } else {
            if (kind.equals("lagrella")) targets.add(addCardToZone("Runeclaw Bear", owner, ZoneType.Battlefield));
            int seats = kind.equals("grasp") || kind.equals("lagrella") ? 4 : 2;
            for (int seat = 1; seat < seats; seat++)
                targets.add(addCardToZone("Grizzly Bears", game.getPlayers().get(seat), ZoneType.Battlefield));
        }
        String hostName = kind.equals("hand") ? "Kitesail Freebooter" : kind.equals("priest") ? "Banisher Priest"
                : kind.equals("grasp") ? "Grasp of Fate" : "Banishing Light";
        Card host = kind.equals("lagrella") ? lagrella(game, owner, targets)
                : cast(game, hostName, owner, null, kind.equals("hand") ? 2 : 3);
        int tokenSurvivors = kind.equals("token") ? 1 : 0;
        if (kind.equals("token")) {
            assertEquals(game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 1L);
            assertTrue(host.getUntilLeavesBattlefield().isEmpty());
        } else {
            for (Card target : targets) assertNotNull(inZone(game, target.getId(), ZoneType.Exile), "Initial exile");
            assertEquals(host.getUntilLeavesBattlefield().size(), targets.size());
        }
        if (kind.equals("early")) {
            cast(game, "Pull from Eternity", owner, inZone(game, targets.get(0).getId(), ZoneType.Exile), 1);
            assertTrue(host.getUntilLeavesBattlefield().isEmpty());
        }
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copied = copier.makeCopy();
        Card mappedHost = inZone(copied, host.getId(), ZoneType.Battlefield);
        String removal = kind.equals("priest") || kind.equals("hand") || kind.equals("lagrella") ? "Unsummon"
                : kind.equals("bounce") ? "Disperse" : "Disenchant";
        int mana = removal.equals("Unsummon") ? 1 : 2;
        cast(copied, removal, copied.getPlayers().get(0), mappedHost, mana);
        assertReturns(copied, targets, kind, tokenSurvivors);
        assertNotNull(inZone(game, host.getId(), ZoneType.Battlefield));
        if (!kind.equals("token") && !kind.equals("early"))
            for (Card target : targets) assertNotNull(inZone(game, target.getId(), ZoneType.Exile), "Independent source exile");
        cast(game, removal, owner, host, mana);
        assertReturns(game, targets, kind, tokenSurvivors);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), copier.getUnmappedSnapshotCommands().toString());
    }
    private void assertReturns(Game game, List<Card> targets, String kind, int tokens) {
        if (kind.equals("token")) {
            assertEquals(game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), (long) tokens);
            return;
        }
        ZoneType destination = kind.equals("hand") ? ZoneType.Hand : kind.equals("early") ? ZoneType.Graveyard : ZoneType.Battlefield;
        for (Card target : targets) {
            Card returned = inZone(game, target.getId(), destination);
            assertNotNull(returned, "Expected return destination " + destination);
            assertSame(returned.getOwner(), game.getPlayer(target.getOwner().getId()));
            if (destination == ZoneType.Battlefield) assertSame(returned.getController(), returned.getOwner());
            if (kind.equals("lagrella")) assertEquals(returned.getCounters(CounterEnumType.P1P1), returned.getOwner() == game.getPlayers().get(0) ? 2 : 0);
        }
    }
    private void links(boolean history, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card target = addCardToZone("Grizzly Bears", game.getPlayers().get(1), ZoneType.Battlefield);
        Card host = cast(game, "Banishing Light", owner, null, 3);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copied = copier.makeCopy();
        Card mapped = copier.find(host);
        if (history) {
            mapped = CardCopyService.getLKICopyForGame(host, new IEntityMap() {
                @Override public Game getGame() { return copied; }
                @Override public GameObject map(GameObject object) { return copier.find(object); }
            });
            assertNotSame(mapped, copier.find(host));
            assertTrue(mapped.isLKI());
        }
        assertEquals(mapped.getUntilLeavesBattlefield().size(), 1);
        assertSame(mapped.getUntilLeavesBattlefield().get(0), inZone(copied, target.getId(), ZoneType.Exile));
        mapped.removeUntilLeavesBattlefield(mapped.getUntilLeavesBattlefield().get(0));
        assertEquals(host.getUntilLeavesBattlefield().size(), 1, "Independent linked-card collection");
    }
    private void pending(boolean hostGone) {
        Game game = pod(); Player owner = prepare(game);
        Card target = addCardToZone("Grizzly Bears", game.getPlayers().get(1), ZoneType.Battlefield);
        Card host;
        if (hostGone) {
            host = addCardToZone("Banishing Light", owner, ZoneType.Hand);
            SpellAbility spell = host.getFirstSpellAbility(); spell.setActivatingPlayer(owner);
            assertTrue(ComputerUtil.playStack(spell, owner, game));
            game.getStack().resolveStack();
            game.getAction().checkStateEffects(true); game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            assertEquals(game.getStack().peekAbility().getApi(), ApiType.ChangeZone);
            host = inZone(game, host.getId(), ZoneType.Battlefield);
        } else host = cast(game, "Banishing Light", owner, null, 3);
        Card removal = addCardToZone("Disenchant", owner, ZoneType.Hand);
        SpellAbility spell = removal.getFirstSpellAbility(); spell.setActivatingPlayer(owner);
        assertTrue(spell.canTarget(host)); spell.getTargets().add(host);
        assertTrue(ComputerUtil.playStack(spell, owner, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        if (hostGone) { game.getStack().resolveStack(); assertEquals(game.getStack().size(), 1); }
        GameCopier copier = new GameCopier(game).withStack(true); Game copied = copier.makeCopy();
        assertEquals(copied.getStack().size(), 1);
        copied.getStack().resolveStack(); settle(copied);
        assertNotNull(inZone(copied, target.getId(), ZoneType.Battlefield));
        if (hostGone) assertEquals(inZone(copied, target.getId(), ZoneType.Battlefield).getGameTimestamp(), target.getGameTimestamp(), "No exile/return when host already left");
        else assertNotNull(inZone(game, target.getId(), ZoneType.Exile));
        game.getStack().resolveStack(); settle(game);
        assertNotNull(inZone(game, target.getId(), ZoneType.Battlefield));
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), copier.getUnmappedSnapshotCommands().toString());
    }
    @Test public void destroyStack() { witness("destroy", true); }
    @Test public void destroyNoStack() { witness("destroy", false); }
    @Test public void bounceStack() { witness("bounce", true); }
    @Test public void bounceNoStack() { witness("bounce", false); }
    @Test public void priestStack() { witness("priest", true); }
    @Test public void priestNoStack() { witness("priest", false); }
    @Test public void graspStack() { witness("grasp", true); }
    @Test public void graspNoStack() { witness("grasp", false); }
    @Test public void handStack() { witness("hand", true); }
    @Test public void handNoStack() { witness("hand", false); }
    @Test public void lagrellaStack() { witness("lagrella", true); }
    @Test public void lagrellaNoStack() { witness("lagrella", false); }
    @Test public void earlyStack() { witness("early", true); }
    @Test public void earlyNoStack() { witness("early", false); }
    @Test public void tokenStack() { witness("token", true); }
    @Test public void tokenNoStack() { witness("token", false); }
    @Test public void liveLinksStack() { links(false, true); }
    @Test public void liveLinksNoStack() { links(false, false); }
    @Test public void historyLinksStack() { links(true, true); }
    @Test public void historyLinksNoStack() { links(true, false); }
    @Test public void pendingRemoval() { pending(false); }
    @Test public void hostGoneBeforeTrigger() { pending(true); }
}
