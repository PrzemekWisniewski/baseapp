package com.base.helpers;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.services.DealsService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by przemek on 03/11/16.
 */

@Component
@Slf4j
public class DealHelper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Client client;
    private final UserHelper userHelper;

    @Autowired
    public DealHelper(@NonNull final Client client,
                      @NonNull final UserHelper userHelper) {
        this.client = client;
        this.userHelper = userHelper;
    }

    public boolean dealCreated(final Contact contact) {
        log.info("contact with id: {} is to be verified", contact.getId());

        if (verifyContact(contact)) {
            return createDeal(contact);
        }
        return false;
    }

    private boolean verifyContact(final Contact contact) {
        log.info("contact is a company: {}", contact.getIsOrganization());

        return contact.getIsOrganization()
                && noExistingDeals(contact)
                && userHelper.isSalesRep(contact.getOwnerId());
    }

    private boolean noExistingDeals(final Contact contact) {
        List<Deal> deals = getDealsByContactId(contact);
        log.info("noExistingDeals: {}", deals.isEmpty());

        return deals.isEmpty();
    }

    private List<Deal> getDealsByContactId(final Contact contact) {
        return client.deals()
                .list(new DealsService.SearchCriteria().contactId(contact.getId()));
    }

    private boolean createDeal(final Contact contact) {
        final Deal newDeal = new Deal();
        newDeal.setOwnerId(contact.getOwnerId());
        newDeal.setContactId(contact.getId());

        final String dealName = getDealName(contact.getName());
        newDeal.setName(dealName);

        try {
            final Deal createdDeal = client.deals()
                    .create(newDeal);
            log.info("created deal with id: {}, name: {}, owned by: {}, assigned to contact: {}",
                     createdDeal.getId(), dealName, contact.getOwnerId(), contact.getId());
            return true;
        } catch (Exception e) {
            log.info("exception occured while creating deal {}, exception message: {}, contact id: {}",
                     dealName, e, contact.getId());
        }
        return false;
    }

    private String getDealName(final String name) {
        final LocalDate now = LocalDate.now();
        return name + " " + now.format(FORMATTER);
    }

}
