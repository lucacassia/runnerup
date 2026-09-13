package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class FavoritesStoreTest {

  private final Map<String, String> store = new HashMap<>();
  private FavoritesStore favorites;

  @Before
  public void setUp() {
    SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getString(anyString(), anyString()))
        .thenAnswer(
            i -> {
              String key = i.getArgument(0);
              return store.containsKey(key) ? store.get(key) : i.getArgument(1);
            });
    SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
    when(editor.putString(anyString(), anyString()))
        .thenAnswer(
            i -> {
              store.put(i.getArgument(0), i.getArgument(1));
              return editor;
            });
    when(editor.remove(anyString()))
        .thenAnswer(
            i -> {
              store.remove(i.getArgument(0));
              return editor;
            });
    doAnswer(i -> null).when(editor).apply();
    when(prefs.edit()).thenReturn(editor);
    favorites = new FavoritesStore(prefs, "favorites", "lastUsed");
  }

  @Test
  public void defaultEmpty() {
    assertTrue(favorites.getFavorites().isEmpty());
    assertNull(favorites.getLastUsed());
    assertTrue(favorites.getPins().isEmpty());
  }

  @Test
  public void toggleAddsAndRemoves() {
    assertTrue(favorites.toggleFavorite("w1"));
    assertTrue(favorites.isFavorite("w1"));
    assertEquals(List.of("w1"), favorites.getFavorites());
    assertFalse(favorites.toggleFavorite("w1"));
    assertTrue(favorites.getFavorites().isEmpty());
  }

  @Test
  public void setLastUsedThenPins() {
    favorites.setLastUsed("w1");
    assertEquals("w1", favorites.getLastUsed());
    assertEquals(List.of("w1"), favorites.getPins());
  }

  @Test
  public void pinsDedupeFavoritesForLastUsed() {
    favorites.setLastUsed("w1");
    favorites.addFavorite("w1");
    favorites.addFavorite("w2");
    assertEquals(Arrays.asList("w1", "w2"), favorites.getPins());
  }

  @Test
  public void cleanupDeletedKeepsFavorites() {
    favorites.setLastUsed("w1");
    favorites.addFavorite("w1");
    favorites.cleanupDeleted("w1");
    assertEquals("w1", favorites.getLastUsed());
  }

  @Test
  public void cleanupDeletedClearsUnfavorite() {
    favorites.setLastUsed("w1");
    favorites.cleanupDeleted("w1");
    assertNull(favorites.getLastUsed());
  }

  @Test
  public void renameMovesFavoritesAndLastUsed() {
    favorites.setLastUsed("w1");
    favorites.addFavorite("w1");
    favorites.rename("w1", "w1b");
    assertEquals("w1b", favorites.getLastUsed());
    assertEquals(List.of("w1b"), favorites.getFavorites());
  }
}
