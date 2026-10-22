package com.hyped.app.account;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyped.app.account.application.AccountService;
import com.hyped.app.notification.api.ReminderTickController;
import com.hyped.app.notification.application.ReminderDeliveryService;
import com.hyped.app.room.application.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class AccountCleanupTickTest {
    @Test
    void retriesAccountCleanupWhenPushFails() {
        var reminders = mock(ReminderDeliveryService.class);
        when(reminders.tick()).thenThrow(new IllegalStateException("unavailable"));
        var accounts = mock(AccountService.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("reminders", reminders);
        beans.addBean("accounts", accounts);
        var controller = new ReminderTickController(beans.getBeanProvider(ReminderDeliveryService.class),
                mock(RoomService.class), beans.getBeanProvider(AccountService.class));

        assertThrows(IllegalStateException.class, controller::tick);
        verify(accounts).tick();
    }

    @Test
    void retriesAccountCleanupWhenRoomPurgeFailsWithPushDisabled() {
        var rooms = mock(RoomService.class);
        doThrow(new IllegalStateException("unavailable")).when(rooms).purgeEligible();
        var accounts = mock(AccountService.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("accounts", accounts);
        var controller = new ReminderTickController(beans.getBeanProvider(ReminderDeliveryService.class),
                rooms, beans.getBeanProvider(AccountService.class));

        assertThrows(IllegalStateException.class, controller::tick);
        verify(accounts).tick();
    }
}
