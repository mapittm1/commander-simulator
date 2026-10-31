package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.card.CardCopyService;
import forge.game.combat.CombatUtil;
import forge.game.staticability.StaticAbilityCantAttackBlock;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntity;
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

/** Detained creatures must retain native activation restrictions in copied games. */
public class DetainGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("expiry" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Keyword expiry"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 20; i++)
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
    private void lands(Game game) {
        for (Player player : game.getPlayers()) for (String name : List.of("Island", "Plains", "Forest", "Mountain"))
            for (int i = 0; i < 8; i++) addCardToZone(name, player, ZoneType.Battlefield);
    }
    private SpellAbility action(Card card, Player owner) {
        SpellAbility ability = card.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        if (ability.usesTargeting()) {
            Card target = owner.getGame().getPlayers().get(0).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getType().isLand()).findFirst().orElseThrow();
            assertTrue(ability.canTarget(target)); ability.getTargets().add(target);
        }
        return ability;
    }
    private Card seed(Game game, String kind) {
        lands(game); nextTurn(game); Player opponent = game.getPlayers().get(1);
        if (kind.equals("haste")) cast(game, "Fervor", opponent, null, 3);
        Card target = cast(game, kind.equals("artifact") ? "Icy Manipulator" : "Azure Mage", opponent, null, kind.equals("artifact") ? 4 : 2);
        if (kind.equals("mass")) cast(game, "Llanowar Elves", opponent, null, 1);
        for (int i = 0; i < (kind.equals("haste") ? 3 : 7); i++) nextTurn(game);
        assertTrue(action(target, opponent).canPlay(), "Positive admission before detain");
        return target;
    }
    private void decree(Game game, Player owner, List<Card> targets) {
        Card spell = addCardToZone("Lyev Decree", owner, ZoneType.Hand);
        SpellAbility ability = spell.getFirstSpellAbility(); ability.setActivatingPlayer(owner);
        for (Card target : targets) { assertTrue(ability.canTarget(target)); ability.getTargets().add(target); }
        pay(game, ability, owner, 2);
    }
    private void restricted(Game game, Card target, boolean detained) {
        assertEquals(target.isDetained(), detained);
        assertEquals(target.getView().isDetained(), detained);
        assertEquals(action(target, target.getController()).canPlay(), !detained, "Native activation admission");
        if (target.isCreature()) {
            Player defender = game.getPlayers().get(0);
            assertEquals(StaticAbilityCantAttackBlock.cantAttack(target, defender), detained, "Detain attack restriction");
            assertEquals(StaticAbilityCantAttackBlock.cantBlock(target), detained, "Detain block restriction");
            boolean ready = !target.isTapped() && !target.isPhasedOut();
            assertEquals(CombatUtil.canAttack(target, defender), !detained && ready && !CombatUtil.isAttackerSick(target, defender), "Native attack eligibility");
            assertEquals(CombatUtil.canBlock(target), !detained && ready, "Native block eligibility");
        }
    }
    private void scenario(String kind, boolean withStack) {
        Game game = pod(); Card target = seed(game, kind); Player owner = game.getPlayers().get(0);
        Card elf = kind.equals("mass") ? game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Llanowar Elves")).findFirst().orElseThrow() : null;
        if (kind.equals("artifact")) cast(game, "Lyev Skyknight", owner, null, 3);
        else if (kind.equals("mass")) decree(game, owner, List.of(target, elf));
        else cast(game, "Inaction Injunction", owner, target, 2);
        restricted(game, target, true); if (elf != null) restricted(game, elf, true);
        if (kind.equals("same")) cast(game, "Inaction Injunction", owner, target, 2);
        if (kind.equals("different") || kind.equals("control")) {
            nextTurn(game); nextTurn(game);
            if (kind.equals("different")) cast(game, "Inaction Injunction", game.getPlayers().get(2), target, 2);
            else cast(game, "Act of Treason", game.getPlayers().get(2), target, 3);
        }
        if (kind.equals("blink")) {
            cast(game, "Cloudshift", game.getPlayers().get(1), target, 1);
            target = find(game, target.getId()); assertFalse(target.isDetained());
            assertTrue(action(target, target.getController()).canPlay());
        }
        if (kind.equals("extra")) cast(game, "Time Warp", owner, owner, 5);
        if (kind.equals("expired")) for (int i = 0; i < 4; i++) nextTurn(game);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        Card mapped = find(copy, target.getId());
        assertEquals(mapped.isFirstTurnControlled(), target.isFirstTurnControlled(), "Raw control-age flag must not be replaced by haste-adjusted sickness");
        if (kind.equals("mappedHistory")) {
            Card historical = CardCopyService.getLKICopyForGame(target, new IEntityMap() {
                @Override public Game getGame() { return copy; }
                @Override public GameObject map(GameObject object) { return copier.find(object); }
            });
            assertTrue(historical.isLKI()); assertTrue(historical.isDetained());
            historical.removeDetainedBy(copy.getPlayers().get(0)); assertFalse(historical.isDetained());
            assertTrue(mapped.isDetained()); assertTrue(target.isDetained());
        } else if (kind.equals("blink") || kind.equals("expired")) {
            assertFalse(mapped.isDetained()); assertTrue(action(mapped, mapped.getController()).canPlay());
            for (int i = 0; i < 4; i++) nextTurn(copy);
            assertFalse(find(copy, mapped.getId()).isDetained());
        } else {
            restricted(copy, mapped, true);
            if (elf != null) restricted(copy, find(copy, elf.getId()), true);
            int steps = kind.equals("extra") ? 1 : kind.equals("different") || kind.equals("control") ? 2 : 4;
            for (int i = 0; i < steps; i++) {
                nextTurn(copy);
                if (i < steps - 1) restricted(copy, mapped, true);
            }
            if (kind.equals("different")) {
                restricted(copy, mapped, true);
                nextTurn(copy); restricted(copy, mapped, true); nextTurn(copy);
            }
            if (kind.equals("control")) assertSame(mapped.getController(), copy.getPlayers().get(1));
            assertTrue(target.isDetained(), "Independent source membership and deadline");
            assertFalse(action(target, target.getController()).canPlay());
            if (kind.equals("control") || kind.equals("extra")) {
                for (int i = 0; i < steps; i++) nextTurn(game);
                restricted(game, target, false);
                assertEquals(mapped.isSick(), target.isSick()); assertEquals(mapped.isTapped(), target.isTapped());
                assertEquals(CombatUtil.canAttack(mapped, copy.getPlayers().get(0)), CombatUtil.canAttack(target, game.getPlayers().get(0)));
                assertEquals(CombatUtil.canBlock(mapped), CombatUtil.canBlock(target));
                if (kind.equals("extra")) {
                    assertSame(copy.getPhaseHandler().getPlayerTurn(), copy.getPlayers().get(0));
                    assertTrue(copy.getPhaseHandler().getPlayerTurn().isExtraTurn());
                }
            }
            restricted(copy, mapped, false); if (elf != null) restricted(copy, find(copy, elf.getId()), false);
        }
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), copier.getUnmappedSnapshotCommands().toString());
    }
    private void originalHistory(boolean expired) {
        Game game = pod(); Card target = seed(game, "activation"); Player owner = game.getPlayers().get(0);
        cast(game, "Inaction Injunction", owner, target, 2);
        if (expired) for (int i = 0; i < 4; i++) nextTurn(game);
        Card lki = CardCopyService.getLKICopy(target);
        assertEquals(lki.isDetained(), !expired); assertEquals(lki.getView().isDetained(), !expired);
        if (!expired) {
            lki.removeDetainedBy(owner); assertFalse(lki.isDetained()); assertTrue(target.isDetained());
        }
    }
    @Test public void activationStack() { scenario("activation", true); }
    @Test public void activationNoStack() { scenario("activation", false); }
    @Test public void combatStack() { scenario("combat", true); }
    @Test public void combatNoStack() { scenario("combat", false); }
    @Test public void massStack() { scenario("mass", true); }
    @Test public void massNoStack() { scenario("mass", false); }
    @Test public void artifactStack() { scenario("artifact", true); }
    @Test public void artifactNoStack() { scenario("artifact", false); }
    @Test public void sameStack() { scenario("same", true); }
    @Test public void sameNoStack() { scenario("same", false); }
    @Test public void differentStack() { scenario("different", true); }
    @Test public void differentNoStack() { scenario("different", false); }
    @Test public void blinkStack() { scenario("blink", true); }
    @Test public void blinkNoStack() { scenario("blink", false); }
    @Test public void controlStack() { scenario("control", true); }
    @Test public void controlNoStack() { scenario("control", false); }
    @Test public void extraStack() { scenario("extra", true); }
    @Test public void extraNoStack() { scenario("extra", false); }
    @Test public void mappedHistoryStack() { scenario("mappedHistory", true); }
    @Test public void mappedHistoryNoStack() { scenario("mappedHistory", false); }
    @Test public void expiredStack() { scenario("expired", true); }
    @Test public void expiredNoStack() { scenario("expired", false); }
    @Test public void originalHistoryActive() { originalHistory(false); }
    @Test public void originalHistoryExpired() { originalHistory(true); }
    private SpellAbility ping(Card card, Game game) {
        SpellAbility ability = card.getSpellAbilities().stream().filter(a -> a.isActivatedAbility() && a.getApi() == ApiType.DealDamage).findFirst().orElseThrow();
        ability.setActivatingPlayer(card.getController());
        assertTrue(ability.canTarget(game.getPlayers().get(0))); ability.getTargets().add(game.getPlayers().get(0)); return ability;
    }
    private void hasteRemoval(boolean withStack) {
        Game game = pod(); lands(game); nextTurn(game); Player owner = game.getPlayers().get(1);
        Card fervor = cast(game, "Fervor", owner, null, 3); Card wizard = cast(game, "Prodigal Pyromancer", owner, null, 3);
        assertTrue(wizard.isFirstTurnControlled()); assertTrue(wizard.hasKeyword("Haste")); assertTrue(ping(wizard, game).canPlay());
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        cast(game, "Naturalize", game.getPlayers().get(0), fervor, 2);
        assertFalse(wizard.hasKeyword("Haste")); assertFalse(ping(wizard, game).canPlay(), "Original tap restriction after haste loss");
        Card mapped = find(copy, wizard.getId()); assertTrue(mapped.hasKeyword("Haste")); assertTrue(ping(mapped, copy).canPlay());
        cast(copy, "Naturalize", copy.getPlayers().get(0), find(copy, fervor.getId()), 2);
        assertFalse(mapped.hasKeyword("Haste"));
        assertFalse(ping(mapped, copy).canPlay(), "Copied newly controlled creature cannot tap after losing haste");
        assertTrue(mapped.isFirstTurnControlled()); assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void historicalControlAge(boolean fresh) {
        Game game = pod(); Card mage = seed(game, fresh ? "haste" : "activation");
        Card history = CardCopyService.getLKICopy(mage);
        assertEquals(history.isFirstTurnControlled(), mage.isFirstTurnControlled(), "LKI raw control-age flag");
    }
    @Test public void hasteDetainStack() { scenario("haste", true); }
    @Test public void hasteDetainNoStack() { scenario("haste", false); }
    @Test public void hasteRemovalStack() { hasteRemoval(true); }
    @Test public void hasteRemovalNoStack() { hasteRemoval(false); }
    @Test public void historicalControlAgeFresh() { historicalControlAge(true); }
    @Test public void historicalControlAgeEstablished() { historicalControlAge(false); }
}
