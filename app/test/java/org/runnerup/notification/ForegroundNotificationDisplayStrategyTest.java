package org.runnerup.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import org.junit.Test;

public class ForegroundNotificationDisplayStrategyTest {

  @Test
  public void cancelRemovesNotificationWhenServiceIsNotInForeground() {
    Service service = mock(Service.class);
    NotificationManager notificationManager = mock(NotificationManager.class);
    when(service.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager);
    ForegroundNotificationDisplayStrategy strategy =
        new ForegroundNotificationDisplayStrategy(service);

    strategy.cancel(1);

    verify(notificationManager).cancel(1);
  }
}
