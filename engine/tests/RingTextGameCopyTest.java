package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntity;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.card.CardCopyService;
import forge.game.player.PlayerController;
import java.util.Collection;
import java.util.Map;
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

/** Ring designations and edited text belong to their own game world. */
public class RingTextGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("expiry" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Keyword expiry"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 12; i++)
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
    private Player prepare(Game game) {
        Player owner = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 12; i++) addCardToZone("Island", player, ZoneType.Battlefield);
            for (int i = 0; i < 4; i++) addCardToZone("Plains", player, ZoneType.Battlefield);
            for (int i = 0; i < 3; i++) addCardToZone("Mountain", player, ZoneType.Battlefield);
        }
        return owner;
    }
    private Game snapshot(Game game, boolean withStack) {
        return new GameCopier(game).withStack(withStack).makeCopy();
    }
    private void assertTypeEdit(Card card, boolean typeLine, boolean edited) {
        if (typeLine) {
            assertTrue(card.getType().hasSubtype(edited ? "Island" : "Forest"));
            assertFalse(card.getType().hasSubtype(edited ? "Forest" : "Island"));
            assertEquals(card.getManaAbilities().size(), 1);
            assertEquals(card.getManaAbilities().get(0).getManaPart().getOrigProduced(), edited ? "U" : "G");
        } else {
            assertTrue(card.hasKeyword(edited ? "Landwalk:Island" : "Landwalk:Forest"));
            assertFalse(card.hasKeyword(edited ? "Landwalk:Forest" : "Landwalk:Island"));
        }
    }
    private void words(String kind, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        if (kind.equals("type") || kind.equals("typeLine") || kind.equals("typeOriginal")) {
            boolean typeLine = kind.equals("typeLine");
            Card dryad = addCardToZone(typeLine ? "Forest" : "Shanodin Dryads", owner, ZoneType.Battlefield);
            PlayerController original = owner.getController();
            owner.dangerouslySetController(new PlayerControllerAi(game, owner, owner.getLobbyPlayer()) {
                @Override public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> choices,
                        SpellAbility sa, String title, Map<String,Object> params) {
                    return choices.stream().filter(a -> a.hasParam("ChangeTypeWord")).findFirst().orElseThrow();
                }
                @Override public String chooseSomeType(String kind, SpellAbility sa,
                        Collection<String> validTypes, boolean optional) {
                    return validTypes.contains("Forest") ? "Forest" : "Island";
                }
            });
            try { cast(game, "Whim of Volrath", owner, dryad, 1); }
            finally { owner.dangerouslySetController(original); }
            assertEquals(dryad.getChangedTextTypeWords().get("Forest"), "Island");
            assertTypeEdit(dryad, typeLine, true);
            if (kind.equals("typeOriginal")) {
                nextTurn(game);
                assertTrue(dryad.getChangedTextTypeWords().isEmpty());
                assertTypeEdit(dryad, false, false);
                return;
            }
            Game copy = snapshot(game, withStack); Card mapped = find(copy, dryad.getId());
            assertEquals(mapped.getChangedTextTypeWords(), dryad.getChangedTextTypeWords(), "Type text map copied");
            assertTypeEdit(mapped, typeLine, true);
            nextTurn(copy);
            assertTrue(mapped.getChangedTextTypeWords().isEmpty());
            assertTypeEdit(mapped, typeLine, false);
            assertTypeEdit(dryad, typeLine, true);
            nextTurn(game); assertTypeEdit(dryad, typeLine, false);
            return;
        }
        Card honor = cast(game, "Honor of the Pure", owner, null, 2);
        Card white = addCardToZone("Silvercoat Lion", owner, ZoneType.Battlefield);
        Card blue = addCardToZone("Coral Merfolk", owner, ZoneType.Battlefield);
        // The standard AI chooses the prominent hand color, then a different legal color.
        addCardToZone("Savannah Lions", owner, ZoneType.Hand);
        cast(game, kind.equals("permanent") ? "Sleight of Mind" : "Whim of Volrath", owner, honor, 1);
        assertEquals(honor.getChangedTextColorWords().get("White"), "Blue");
        assertEquals(white.getNetPower(), 2); assertEquals(blue.getNetPower(), 3);
        Game copy = snapshot(game, withStack); Card mapped = find(copy, honor.getId());
        assertEquals(mapped.getChangedTextColorWords(), honor.getChangedTextColorWords(), "Color text map copied");
        assertEquals(find(copy, white.getId()).getNetPower(), 2); assertEquals(find(copy, blue.getId()).getNetPower(), 3);
        nextTurn(copy);
        boolean permanent = kind.equals("permanent");
        assertEquals(mapped.getChangedTextColorWords().isEmpty(), !permanent);
        assertEquals(find(copy, white.getId()).getNetPower(), permanent ? 2 : 3);
        assertEquals(find(copy, blue.getId()).getNetPower(), permanent ? 3 : 2);
        assertEquals(white.getNetPower(), 2, "Original color edit remains active");
        nextTurn(game); assertEquals(white.getNetPower(), permanent ? 2 : 3);
    }
    private void exchange(String kind, boolean withStack) {
        Game game = pod(); Player owner = prepare(game); Player opponent = game.getPlayers().get(1);
        String firstName = kind.equals("static") ? "Merfolk Sovereign" : kind.equals("trigger") ? "Wall of Omens"
                : kind.equals("replacement") ? "Cho-Manno, Revolutionary" : "Serra Angel";
        String secondName = kind.equals("keywords") || kind.equals("blink") ? "Royal Assassin" : "Grizzly Bears";
        Card first = addCardToZone(firstName, owner, ZoneType.Battlefield);
        Card second = addCardToZone(secondName, opponent, ZoneType.Battlefield);
        Card host = cast(game, "Exchange of Words", owner, null, 3); settle(game);
        Card friendlyMerfolk = null; Card enemyMerfolk = null;
        if (kind.equals("static")) {
            friendlyMerfolk = addCardToZone("Merfolk of the Pearl Trident", owner, ZoneType.Battlefield);
            enemyMerfolk = addCardToZone("Merfolk of the Pearl Trident", opponent, ZoneType.Battlefield);
            settle(game); assertEquals(friendlyMerfolk.getNetPower(), 1); assertEquals(enemyMerfolk.getNetPower(), 2);
        }
        if (kind.equals("blink")) {
            cast(game, "Cloudshift", opponent, second, 1); second = find(game, second.getId());
            second.addRemembered("new-incarnation");
        }
        if (kind.equals("keywords") || kind.equals("blink")) assertFalse(first.hasKeyword("Flying"));
        if (kind.equals("trigger")) assertEquals(first.getTriggers().size(), 0);
        Game copy = snapshot(game, withStack); Card mappedFirst = find(copy, first.getId());
        Card mappedSecond = find(copy, second.getId()); Player copiedOwner = copy.getPlayers().get(0);
        cast(copy, "Disenchant", copiedOwner, find(copy, host.getId()), 2);
        if (kind.equals("static")) {
            assertEquals(find(copy, friendlyMerfolk.getId()).getNetPower(), 2, "Copied original static text restored");
            assertEquals(find(copy, enemyMerfolk.getId()).getNetPower(), 1);
            assertEquals(friendlyMerfolk.getNetPower(), 1); assertEquals(enemyMerfolk.getNetPower(), 2);
        } else if (kind.equals("trigger")) {
            assertEquals(mappedFirst.getTriggers().size(), 1, "Copied ETB trigger restored without replay");
            assertEquals(first.getTriggers().size(), 0);
            int library = copiedOwner.getCardsIn(ZoneType.Library).size();
            cast(copy, "Cloudshift", copiedOwner, mappedFirst, 1);
            assertEquals(copiedOwner.getCardsIn(ZoneType.Library).size(), library - 1, "Returned Wall draws normally");
        } else if (kind.equals("replacement")) {
            cast(copy, "Lightning Bolt", copiedOwner, mappedFirst, 1);
            assertEquals(mappedFirst.getDamage(), 0); assertEquals(mappedFirst.getZone().getZoneType(), ZoneType.Battlefield);
            cast(game, "Lightning Bolt", owner, first, 1);
            assertEquals(find(game, first.getId()).getZone().getZoneType(), ZoneType.Graveyard);
        } else {
            assertTrue(mappedFirst.hasKeyword("Flying"), "Copied original keywords restored");
            assertFalse(first.hasKeyword("Flying"), "Original text exchange remains active");
            assertFalse(mappedSecond.hasKeyword("Flying"));
            if (kind.equals("blink")) assertTrue(mappedSecond.isRemembered("new-incarnation"));
        }
    }
    private void ring(int level, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card bearer = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        for (int i = 0; i < level; i++) cast(game, "Birthday Escape", owner, null, 1);
        assertEquals(owner.getNumRingTemptedYou(), level); assertSame(owner.getRingBearer(), bearer);
        Card ring = owner.getTheRing(); assertNotNull(ring);
        assertEquals(ring.getTriggers().size(), Math.min(level, 4) - 1);
        Game copy = snapshot(game, withStack); Player player = copy.getPlayers().get(0);
        assertEquals(player.getNumRingTemptedYou(), level, "Temptation count copied");
        assertSame(player.getRingBearer(), find(copy, bearer.getId()), "Bearer reference mapped");
        assertSame(player.getTheRing(), find(copy, ring.getId()), "Existing Ring effect reused");
        assertTrue(player.getRingBearer().isRingBearer()); assertTrue(player.getRingBearer().getType().isLegendary());
        cast(copy, "Birthday Escape", player, null, 1);
        assertEquals(player.getNumRingTemptedYou(), level + 1);
        assertEquals(player.getTheRing().getId(), ring.getId());
        assertEquals(player.getTheRing().getTriggers().size(), Math.min(level + 1, 4) - 1);
        assertEquals(player.getCardsIn(ZoneType.Command).stream().filter(c -> c.getName().equals("The Ring")).count(), 1L);
        assertEquals(owner.getNumRingTemptedYou(), level, "Original progression unchanged");
    }
    private void bearerLoss(String action, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card bearer = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        cast(game, "Birthday Escape", owner, null, 1);
        Game copy = snapshot(game, withStack); Player player = copy.getPlayers().get(0);
        assertSame(player.getRingBearer(), find(copy, bearer.getId()), "Active designation copied");
        if (action.equals("leave")) cast(copy, "Unsummon", player, player.getRingBearer(), 1);
        else {
            nextTurn(copy); Player thief = copy.getPlayers().get(1);
            cast(copy, "Act of Treason", thief, player.getRingBearer(), 3);
        }
        assertNull(player.getRingBearer(), "Copied bearer designation lost");
        assertFalse(find(copy, bearer.getId()).isRingBearer());
        assertSame(owner.getRingBearer(), bearer); assertTrue(bearer.isRingBearer());
        assertEquals(player.getNumRingTemptedYou(), 1);
    }
    private Game formerOwnerSetup() {
        Game game = pod(); Player first = prepare(game); Player second = game.getPlayers().get(1);
        Card old = addCardToZone("Wall of Stone", first, ZoneType.Battlefield);
        cast(game, "Birthday Escape", first, null, 1);
        nextTurn(game);
        cast(game, "Control Magic", second, old, 4);
        assertNull(first.getRingBearer());
        cast(game, "Birthday Escape", second, null, 1); assertSame(second.getRingBearer(), old);
        for (int i = 0; i < 3; i++) nextTurn(game);
        Card current = cast(game, "Ornithopter", first, null, 0);
        cast(game, "Birthday Escape", first, null, 1); assertSame(first.getRingBearer(), current);
        return game;
    }
    private void formerOwner(boolean copyWorld, boolean withStack) {
        Game source = formerOwnerSetup(); Game game = copyWorld ? snapshot(source, withStack) : source;
        Player first = game.getPlayers().get(0); Player second = game.getPlayers().get(1);
        assertNotNull(first.getRingBearer(), "First player's newer bearer is present");
        assertNotNull(second.getRingBearer(), "Second player's bearer is present");
        Card newer = first.getRingBearer();
        cast(game, "Cloudshift", second, second.getRingBearer(), 1);
        assertSame(first.getRingBearer(), newer, "Stale former-owner callback cannot clear the newer bearer");
        assertTrue(newer.isRingBearer()); assertNull(second.getRingBearer());
        if (copyWorld) assertNotNull(source.getPlayers().get(1).getRingBearer());
    }
    @Test public void designationIsNotACopiableValueButLkiRetainsIt() {
        Game game = pod(); Player owner = prepare(game);
        Card bearer = addCardToZone("Wall of Stone", owner, ZoneType.Battlefield);
        cast(game, "Birthday Escape", owner, null, 1);
        Card clone = cast(game, "Clone", owner, null, 4);
        assertFalse(clone.isRingBearer(), "Paid Clone does not inherit the designation");
        assertFalse(clone.getType().isLegendary());
        assertTrue(CardCopyService.getLKICopy(bearer).isRingBearer(), "Historical state retains the designation");
    }
    @Test public void staleFormerOwnerInOriginalGame() { formerOwner(false, false); }
    @Test public void words_temporary_stack() { words("temporary", true); }
    @Test public void words_temporary_nostack() { words("temporary", false); }
    @Test public void words_permanent_stack() { words("permanent", true); }
    @Test public void words_permanent_nostack() { words("permanent", false); }
    @Test public void words_type_stack() { words("type", true); }
    @Test public void words_type_nostack() { words("type", false); }
    @Test public void exchange_keywords_stack() { exchange("keywords", true); }
    @Test public void exchange_keywords_nostack() { exchange("keywords", false); }
    @Test public void exchange_static_stack() { exchange("static", true); }
    @Test public void exchange_static_nostack() { exchange("static", false); }
    @Test public void exchange_trigger_stack() { exchange("trigger", true); }
    @Test public void exchange_trigger_nostack() { exchange("trigger", false); }
    @Test public void exchange_replacement_stack() { exchange("replacement", true); }
    @Test public void exchange_replacement_nostack() { exchange("replacement", false); }
    @Test public void exchange_blink_stack() { exchange("blink", true); }
    @Test public void exchange_blink_nostack() { exchange("blink", false); }
    @Test public void ring_1_stack() { ring(1, true); }
    @Test public void ring_1_nostack() { ring(1, false); }
    @Test public void ring_2_stack() { ring(2, true); }
    @Test public void ring_2_nostack() { ring(2, false); }
    @Test public void ring_3_stack() { ring(3, true); }
    @Test public void ring_3_nostack() { ring(3, false); }
    @Test public void ring_4_stack() { ring(4, true); }
    @Test public void ring_4_nostack() { ring(4, false); }
    @Test public void bearer_leave_stack() { bearerLoss("leave", true); }
    @Test public void bearer_leave_nostack() { bearerLoss("leave", false); }
    @Test public void bearer_control_stack() { bearerLoss("control", true); }
    @Test public void bearer_control_nostack() { bearerLoss("control", false); }
    @Test public void formerOwner_stack() { formerOwner(true, true); }
    @Test public void formerOwner_nostack() { formerOwner(true, false); }
    @Test public void mandatoryExchangeCannotUseAnOpposingHexproofCreature() {
        Game game = pod(); Player owner = prepare(game);
        Card angel = addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        Card bogle = addCardToZone("Slippery Bogle", game.getPlayers().get(1), ZoneType.Battlefield);
        cast(game, "Exchange of Words", owner, null, 3);
        assertTrue(angel.hasKeyword("Flying"), "No legal two-target exchange exists");
        assertFalse(bogle.hasKeyword("Flying"));
    }
    @Test public void words_typeLine_stack() { words("typeLine", true); }
    @Test public void words_typeLine_nostack() { words("typeLine", false); }
    @Test public void typeWordExpiryInOriginalGame() { words("typeOriginal", false); }
    private void ringCombat(boolean blocked, boolean withStack) {
        Game source = pod(); Player owner = prepare(source); Player defender = source.getPlayers().get(1);
        source.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 1);
        Card bearer = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        for (int i = 0; i < (blocked ? 3 : 4); i++) cast(source, "Birthday Escape", owner, null, 1);
        Card blocker = null;
        if (blocked) {
            addCardToZone("Forest", owner, ZoneType.Battlefield);
            blocker = addCardToZone("Wall of Omens", defender, ZoneType.Battlefield);
            Card hunt = addCardToZone("Hunt Down", owner, ZoneType.Hand);
            hunt.getFirstSpellAbility().getTargets().add(bearer);
            hunt.getFirstSpellAbility().getSubAbility().getTargets().add(blocker);
            pay(source, hunt.getFirstSpellAbility(), owner, 1);
        }
        Game game = snapshot(source, withStack); Player player = game.getPlayers().get(0);
        Card attacker = find(game, bearer.getId());
        int library = player.getCardsIn(ZoneType.Library).size(); int hand = player.getCardsIn(ZoneType.Hand).size();
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, player, 1);
        game.getPhaseHandler().setCombat(new Combat(player));
        game.getCombat().addAttacker(attacker, game.getPlayers().get(1));
        CombatUtil.checkDeclaredAttacker(game, attacker, game.getCombat(), true);
        settle(game);
        assertEquals(player.getCardsIn(ZoneType.Library).size(), library - 1, "Copied Ring attack draws");
        assertEquals(player.getCardsIn(ZoneType.Hand).size(), hand, "Copied Ring attack discards");
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game))); settle(game);
        if (blocked) {
            assertEquals(find(game, blocker.getId()).getZone().getZoneType(), ZoneType.Graveyard,
                    "Copied Ring block trigger sacrifices the surviving blocker at combat end");
            assertEquals(blocker.getZone().getZoneType(), ZoneType.Battlefield);
        } else {
            assertEquals(game.getPlayers().get(1).getLife(), 35, "Two combat damage plus Ring loss");
            assertEquals(game.getPlayers().get(2).getLife(), 37);
            assertEquals(game.getPlayers().get(3).getLife(), 37);
        }
        assertEquals(owner.getCardsIn(ZoneType.Library).size(), library, "Original library unchanged");
        for (Player original : source.getPlayers()) assertEquals(original.getLife(), 40);
    }
    @Test public void ringCombatHit_stack() { ringCombat(false, true); }
    @Test public void ringCombatHit_nostack() { ringCombat(false, false); }
    @Test public void ringCombatBlock_stack() { ringCombat(true, true); }
    @Test public void ringCombatBlock_nostack() { ringCombat(true, false); }
}
