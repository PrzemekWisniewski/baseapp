package com.base.processors;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.User;
import com.getbase.services.DealsService;
import com.getbase.sync.Meta;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.base.util.BaseAppUtil.baseClient;

/**
 * Created by przemek on 19.10.2016.
 */
@Component
@Slf4j
public class ContactProcessor {

    private Client client;

    @PostConstruct
    void initClient() {
        this.client = baseClient();
    }


    public boolean process(final Meta meta, final Contact contact) {
        MDC.put("contact", contact.getId().toString());
        final String syncEventType = meta.getSync().getEventType();
        boolean isContactCreated = "created".equals(syncEventType);

        if (isContactCreated) {
            log.info("processing contact, sync event received: '{}'", syncEventType);
            log.info("contact with id: {} is to be verified", contact.getId());
            if (verifyContact(contact)) {
                createDeal(contact);
            }
        } else {
            log.info("processing contact, received sync event != 'created'. Nothing to do");
        }

        MDC.clear();
        return true;
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

    private boolean isOwnerASalesRep(Long ownerId) {
        User user = getCurrentUser(ownerId);
        String usersStatus = user.getStatus();
        boolean isUserSalesRep = user.getEmail().contains("_salesrep@");
        log.info("isOwnerASalesRep, userId: {}, user's status: {}, and user is a sales rep: {}", user.getId(), usersStatus, isUserSalesRep);

        return "active".equals(usersStatus) && isUserSalesRep;
    }

    private User getCurrentUser(Long id) {
        return client.users().get(id);
    }

    private void createDeal(final Contact contact) {
        final Deal newDeal = new Deal();
        newDeal.setOwnerId(contact.getOwnerId());
        newDeal.setContactId(contact.getId());

        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        String dealName = contact.getName() + " " + now.format(formatter);
        newDeal.setName(dealName);

        Deal createdDeal = client.deals().create(newDeal);
        log.info("created deal with id: {}, name: {}, owned by: {}, assigned to contact: {}", createdDeal.getId(), dealName, contact.getOwnerId(), contact.getId());

    }

}
