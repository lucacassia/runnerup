package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.junit.Test;

public class WorkoutOrderTest {

  private static File tempDir() throws IOException {
    return Files.createTempDirectory("workout-order").toFile();
  }

  private static void writeRaw(File orderFile, String content) throws IOException {
    try (FileWriter w = new FileWriter(orderFile)) {
      w.write(content);
    }
  }

  @Test
  public void applyWithFullOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"c\",\"a\"]");
    List<String> result =
        WorkoutOrder.apply(Arrays.asList("a.json", "b.json", "c.json"), orderFile);
    assertEquals(Arrays.asList("c.json", "a.json", "b.json"), result);
  }

  @Test
  public void applyWithPartialOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"b\"]");
    List<String> result =
        WorkoutOrder.apply(Arrays.asList("a.json", "b.json", "c.json"), orderFile);
    assertEquals(Arrays.asList("b.json", "a.json", "c.json"), result);
  }

  @Test
  public void applyIgnoresDanglingOrderNames() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"zzz\",\"a\"]");
    List<String> result = WorkoutOrder.apply(Arrays.asList("a.json"), orderFile);
    assertEquals(Collections.singletonList("a.json"), result);
  }

  @Test
  public void applyWithMissingOrderFilePreservesInputOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    List<String> result = WorkoutOrder.apply(Arrays.asList("a.json", "b.json"), orderFile);
    assertEquals(Arrays.asList("a.json", "b.json"), result);
  }

  @Test
  public void applyKeepsUnrankedFilesInInputRelativeOrder() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "[\"b\"]");
    List<String> result =
        WorkoutOrder.apply(Arrays.asList("c.json", "a.json", "b.json"), orderFile);
    assertEquals(Arrays.asList("b.json", "c.json", "a.json"), result);
  }

  @Test
  public void writeAndLoadRoundTrip() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b", "c"));
    assertEquals(Arrays.asList("a", "b", "c"), WorkoutOrder.load(orderFile));
  }

  @Test
  public void replaceUpdatesEntry() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b", "c"));
    WorkoutOrder.replace(orderFile, "b", "renamed");
    assertEquals(Arrays.asList("a", "renamed", "c"), WorkoutOrder.load(orderFile));
  }

  @Test
  public void replaceUnknownNameDoesNotCreateFile() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.replace(orderFile, "nope", "other");
    assertFalse(orderFile.exists());
  }

  @Test
  public void removeDropsEntry() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b", "c"));
    WorkoutOrder.remove(orderFile, "b");
    assertEquals(Arrays.asList("a", "c"), WorkoutOrder.load(orderFile));
  }

  @Test
  public void removeUnknownNameDoesNotCreateFile() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.remove(orderFile, "nope");
    assertFalse(orderFile.exists());
  }

  @Test
  public void loadUnparseableFileReturnsEmpty() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    writeRaw(orderFile, "{not json");
    assertTrue(WorkoutOrder.load(orderFile).isEmpty());
  }

  @Test
  public void jsonArrayIsUsableSerialization() throws Exception {
    File dir = tempDir();
    File orderFile = new File(dir, ".order");
    WorkoutOrder.write(orderFile, Arrays.asList("a", "b"));
    assertEquals("[\"a\",\"b\"]", new JSONArray(WorkoutOrder.load(orderFile)).toString());
  }
}
