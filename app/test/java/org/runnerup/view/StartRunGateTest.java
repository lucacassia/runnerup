package org.runnerup.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StartRunGateTest {

  @Test
  public void nonGpsSportAlwaysEnabled() {
    assertTrue(StartFragment.startRunEnabled(true, false, false, false, false));
    assertTrue(StartFragment.startRunEnabled(true, false, false, false, true));
    assertTrue(StartFragment.startRunEnabled(true, true, true, true, true));
  }

  @Test
  public void gpsSportRequiresStartedLoggingFixedConnected() {
    assertFalse(StartFragment.startRunEnabled(false, false, true, true, true));
    assertFalse(StartFragment.startRunEnabled(false, true, false, true, true));
    assertFalse(StartFragment.startRunEnabled(false, true, true, false, true));
    assertFalse(StartFragment.startRunEnabled(false, true, true, true, false));
    assertTrue(StartFragment.startRunEnabled(false, true, true, true, true));
  }
}
