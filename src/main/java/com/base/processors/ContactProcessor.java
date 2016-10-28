package com.base.processors;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.services.DealsService;
import com.getbase.sync.Meta;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by przemek on 19.10.2016.
 */
@Component
@Slf4j
public class ContactProcessor extends AbstractProcessor {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ContactProcessor(Client client) {
        super(client);
    }


    public boolean process(final Meta meta, final Contact contact) {
        MDC.put("contact", contact.getId()
                .toString());
        final String syncEventType = meta.getSync()
                .getEventType();
        log.info("processing contact, sync event received: '{}'", syncEventType);

        if ("created".equals(syncEventType)) {
            try {
                log.info("contact with id: {} is to be verified", contact.getId());
                if (verifyContact(contact)) {
                    return createDeal(contact);
                }
            } finally {
                MDC.remove("contact");
            }
        }

        return false;
    }

    private boolean verifyContact(final Contact contact) {
        boolean isContactAnOrganization = contact.getIsOrganization();
        log.info("verifying contact. contact is a company: {}", isContactAnOrganization);

        return noExistingDeals(contact)
                && isOwnerASalesRep(contact.getOwnerId())
                && isContactAnOrganization;

    }

    private boolean noExistingDeals(final Contact contact) {
        List<Deal> deals = getDealsByContactId(contact);

        boolean noExistingDeals = deals.isEmpty();
        log.info("noExistingDeals {}", noExistingDeals);

        return noExistingDeals;
    }

    private List<Deal> getDealsByContactId(Contact contact) {
        return client.deals()
                .list(new DealsService.SearchCriteria().contactId(contact.getId()));
    }


    private boolean createDeal(final Contact contact) {
        final Deal newDeal = new Deal();
        newDeal.setOwnerId(contact.getOwnerId());
        newDeal.setContactId(contact.getId());

        String dealName = getDealName(contact.getName());
        newDeal.setName(dealName);

        try {
            Deal createdDeal = client.deals()
                    .create(newDeal);
            log.info("created deal with id: {}, name: {}, owned by: {}, assigned to contact: {}",
                    createdDeal.getId(), dealName, contact.getOwnerId(), contact.getId());
            return true;
        } catch (Exception e) {
            log.info("exception occured when creating deal {}, exception message: {}, contact id: {}," +
                    "", dealName, e, contact.getId());
        }
        return false;
    }

    private String getDealName(String name) {
        LocalDate now = LocalDate.now();
        return name + " " + now.format(formatter);
    }
}
