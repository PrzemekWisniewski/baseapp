package com.base.processors;

import com.getbase.Client;
import com.getbase.models.User;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by przemek on 26/10/16.
 */
@Slf4j
public class AbstractProcessor {

    protected final Client client;

    public AbstractProcessor(Client client) {
        this.client = client;
    }

    protected boolean isOwnerASalesRep(Long ownerId) {
        User user = getCurrentUser(ownerId);
        String usersStatus = user.getStatus();
        boolean isUserSalesRep = null != user.getEmail() && user.getEmail()
                .contains("_salesrep@");
        log.info("userId: {}, user's status: {}, and user is a sales rep: {}", user.getId(),
                usersStatus, isUserSalesRep);

        return "active".equals(usersStatus) && isUserSalesRep;
    }

    private User getCurrentUser(Long id) {
        return client.users()
                .get(id);
    }
}
