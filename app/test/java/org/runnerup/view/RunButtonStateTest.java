package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.runnerup.R;
import org.runnerup.view.RunActivity.ButtonState;

public class RunButtonStateTest {
  @Test
  public void recordingState() {
    ButtonState s = RunActivity.buttonState(false);
    assertEquals(org.runnerup.common.R.string.Pause, s.leftText);
    assertEquals(R.drawable.ic_pause, s.leftIcon);
    assertEquals(org.runnerup.common.R.string.NextLap, s.rightText);
    assertEquals(R.drawable.ic_skip_next, s.rightIcon);
    assertFalse(s.rightIsError);
  }

  @Test
  public void pausedState() {
    ButtonState s = RunActivity.buttonState(true);
    assertEquals(org.runnerup.common.R.string.Resume, s.leftText);
    assertEquals(R.drawable.ic_play_arrow, s.leftIcon);
    assertEquals(org.runnerup.common.R.string.Stop, s.rightText);
    assertEquals(R.drawable.ic_stop, s.rightIcon);
    assertTrue(s.rightIsError);
  }
}
