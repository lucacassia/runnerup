package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import org.junit.Test;
import org.runnerup.tracker.Tracker;

public class WorkoutTickFeedbackTest {

  static class CountingFeedback extends Feedback {
    int emitCount = 0;

    @Override
    public boolean equals(Feedback other) {
      return this == other;
    }

    @Override
    public void emit(Workout w, Context ctx) {
      emitCount++;
    }
  }

  private Workout createWorkoutWithStepCountdown(CountingFeedback feedback) {
    Workout workout = new Workout();
    workout.tracker = mock(Tracker.class);

    Step step = new Step();
    step.intensity = Intensity.ACTIVE;
    step.durationType = Dimension.TIME;
    step.durationValue = 1000;

    IntervalTrigger trigger = new IntervalTrigger();
    trigger.scope = Scope.STEP;
    trigger.dimension = Dimension.TIME;
    trigger.first = 1;
    trigger.interval = 1;
    trigger.triggerAction.add(feedback);
    step.triggers.add(trigger);

    workout.steps.add(step);
    return workout;
  }

  @Test
  public void feedbackReemitsOnEveryTickWithoutTts() {
    CountingFeedback feedback = new CountingFeedback();
    Workout workout = createWorkoutWithStepCountdown(feedback);

    when(workout.tracker.getTimeMs()).thenReturn(1000L);
    workout.onStart(Scope.ACTIVITY, workout);

    when(workout.tracker.getTimeMs()).thenReturn(2000L);
    workout.onTick();
    assertEquals(1, feedback.emitCount);

    when(workout.tracker.getTimeMs()).thenReturn(3000L);
    workout.onTick();
    assertEquals(2, feedback.emitCount);
  }
}
