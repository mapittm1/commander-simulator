package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordCollection;
import forge.game.keyword.KeywordInterface;
import forge.game.keyword.SimpleKeyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Optional method signatures cannot break otherwise usable keyword callbacks. */
public class CommanderKeywordMetadataFailureTest extends AITest {
    public static final class MissingSignature { }
    public static final class OpaqueSignatureKeyword extends SimpleKeyword {
        public OpaqueSignatureKeyword() { initialize("Flying", Keyword.FLYING, ""); }
        public MissingSignature unusedOptionalMethod() { return null; }
    }
    private KeywordInterface isolatedKeyword() throws ReflectiveOperationException {
        final String keywordName = OpaqueSignatureKeyword.class.getName();
        final String missingName = MissingSignature.class.getName();
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader loader = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(missingName)) throw new ClassNotFoundException(name);
                if (!name.equals(keywordName)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try (InputStream input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readNBytes(65537);
                            if (bytes.length > 65536) throw new ClassNotFoundException("Fixture class too large");
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException error) { throw new ClassNotFoundException(name, error); }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Class<?> type = loader.loadClass(keywordName);
        boolean unavailable = false;
        try { type.getMethod("getReplacements"); }
        catch (LinkageError expected) {
            unavailable = true;
            assertTrue(expected.getMessage().contains("MissingSignature"));
        }
        assertTrue(unavailable, "The fixture must actually make class metadata unavailable");
        return (KeywordInterface) type.getConstructor().newInstance();
    }
    private Player actor(Game game) { return game.getPlayers().getFirst(); }
    private Card land(Game game) { return actor(game).getCardsIn(ZoneType.Battlefield).getFirst(); }
    private Game setup() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("metadata-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Opaque metadata"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        return game;
    }
    private void check(int depth) throws ReflectiveOperationException {
        Game game = setup();
        KeywordInterface keyword = isolatedKeyword();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
            keyword = keyword.copy(land(game), true);
        }
        Card card = land(game); keyword.setHostCard(card);
        KeywordCollection collection = new KeywordCollection(); collection.insert(keyword);
        card.getCurrentState().setCachedKeywords(collection);
        assertTrue(card.getStaticAbilities().isEmpty()); assertTrue(card.canTap());
        StaticAbility ban = new StaticAbility("Mode$ CantBeActivated | AffectedZone$ Battlefield | ValidCard$ Card.Self | ValidSA$ Activated", card, card.getCurrentState());
        keyword.getStaticAbilities().add(ban);
        assertTrue(card.getStaticAbilities().contains(ban));
        ReplacementEffect prevention = ReplacementHandler.parseReplacement("Event$ Tap | ActiveZones$ Battlefield | ValidCard$ Card.Self | Layer$ CantHappen", card, true);
        keyword.getReplacements().add(prevention);
        assertFalse(card.canTap(), "Opaque metadata must not hide inherited replacements");
        assertTrue(card.getReplacementEffects().contains(prevention));
        keyword.getStaticAbilities().clear(); keyword.getReplacements().clear();
        assertTrue(card.getStaticAbilities().isEmpty()); assertTrue(card.canTap());
    }
    @Test public void opaqueMetadataDepth0() throws ReflectiveOperationException { check(0); }
    @Test public void opaqueMetadataDepth1() throws ReflectiveOperationException { check(1); }
    @Test public void opaqueMetadataDepth2() throws ReflectiveOperationException { check(2); }
}
