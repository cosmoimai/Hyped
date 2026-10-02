package com.hyped.app.invitation.application;

import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomStatus;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class InvitationGuard {
    private final UserAccountRepository accounts;
    private final Clock clock;

    public InvitationGuard(UserAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Global order: account(s) by UUID, room, memberships, invitation. Never acquire an account after a room. */
    public void lockActor(UserId actor) {
        var account = accounts.findByIdForUpdate(actor)
                .orElseThrow(() -> new InvitationException(401, "ACCESS_TOKEN_INVALID"));
        if (account.status() != AccountStatus.ACTIVE && account.status() != AccountStatus.LOCKED) {
            throw new InvitationException(403, "ACCOUNT_" + account.status().name());
        }
    }

    public void requireActive(Room room) {
        if (room.status() != RoomStatus.ACTIVE || !room.eventAt().isAfter(clock.instant())) {
            throw new InvitationException(410, "ROOM_ENDED");
        }
    }
}
