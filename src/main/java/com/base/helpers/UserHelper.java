package com.base.helpers;

import com.getbase.Client;
import com.getbase.models.User;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by przemek on 03/11/16.
 */

@Component
@Slf4j
public class UserHelper {

    private final Client client;

    @Autowired
    public UserHelper(@NonNull final Client client) {
        this.client = client;
    }

    public boolean isSalesRep(final Long ownerId) {
        final User user = getCurrentUser(ownerId);
        final String usersStatus = user.getStatus();
        boolean isUserSalesRep = null != user.getEmail() && user.getEmail()
                .contains("_salesrep@");
        log.info("userId: {}, user's status: {}, and user is a sales rep: {}", user.getId(),
                 usersStatus, isUserSalesRep);

        return "active".equals(usersStatus) && isUserSalesRep;
    }

    private User getCurrentUser(final Long id) {
        return client.users()
                .get(id);
    }

}
