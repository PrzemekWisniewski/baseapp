package com.base;

import com.getbase.Client;
import com.getbase.Configuration;
import com.getbase.models.*;
import com.getbase.services.DealsService;
import com.getbase.services.StagesService;
import com.getbase.sync.Meta;
import com.getbase.sync.Sync;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.env.MissingRequiredPropertiesException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Created by przemek on 19.10.2016.
 */

@Service
@Slf4j
class BaseScheduledFlow {

    private Client client;

    public BaseScheduledFlow() {
        log.info("BaseScheduledFlow service launching...");
        this.client = new Client(new Configuration.Builder()
                .accessToken(getProperty("BASECRM_ACCESS_TOKEN"))
                .build());
    }

    @Scheduled(fixedDelay = 15000)
    public void process() {
        Sync sync = new Sync(client, "abc321");
        sync.subscribe(Account.class, (meta, account) -> true)
                .subscribe(Address.class, (meta, address) -> true)
                .subscribe(AssociatedContact.class, (meta, associatedContact) -> true)
                .subscribe(Contact.class, this::processContact)
                .subscribe(Deal.class, this::processDeal)
                .subscribe(LossReason.class, (meta, lossReason) -> true)
                .subscribe(Note.class, (meta, note) -> true)
                .subscribe(Pipeline.class, (meta, pipeline) -> true)
                .subscribe(Source.class, (meta, source) -> true)
                .subscribe(Stage.class, (meta, stage) -> true)
                .subscribe(Tag.class, (meta, tag) -> true)
                .subscribe(Task.class, (meta, task) -> true)
                .subscribe(User.class, (meta, user) -> true)
                .subscribe(Lead.class, (meta, lead) -> true)
                .fetch();
    }

    private boolean processContact(final Meta meta, final Contact contact) {
        MDC.put("contact", contact.getId().toString());
        final String syncEventType = meta.getSync().getEventType();
        boolean isContactCreated = "created".equals(syncEventType);

        if (isContactCreated) {
            log.info("processing contact, sync event received: '{}'", syncEventType);
            if ("created".equals(syncEventType)) {
                log.info("contact with id: {} is to be verified", contact.getId());
                if (verifyContact(contact)) {
                    createDeal(contact);
                }
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
                && isCurrentOwnerASalesRepUser(contact)
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

    private boolean isCurrentOwnerASalesRepUser(final User user) {
        String usersStatus = user.getStatus();
        boolean isUserSalesRep = user.getEmail().contains("_salesrep@");
        log.info("isCurrentOwnerASalesRepUser, userId: {}, user's status: {}, and user is a sales rep: {}", user.getId(), usersStatus, isUserSalesRep);

        return "active".equals(usersStatus) && isUserSalesRep;
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

    private boolean processDeal(final Meta meta, final Deal deal) {
        MDC.put("deal", deal.getId().toString());
        String syncEventType = meta.getSync().getEventType();
        boolean isDealUpdated = "updated".equals(syncEventType);

        if (isDealUpdated) {
            log.info("processing deal, sync event received: '{}'", syncEventType);
            if (isDealWon(deal)) {
                Contact relatedContact = client.contacts().get(deal.getContactId());
                log.info("checking contact {} for reassigning", relatedContact.getId());
                if (isCurrentOwnerASalesRepUser(relatedContact)) {
                    log.info("contact is to be reassigned.");
                    reassignContact(relatedContact);
                }
            }
        } else {
            log.info("processing deal, but received sync event != updated. Nothing to do.");
        }
        MDC.clear();
        return true;
    }

    private void reassignContact(Contact relatedContact) {
        relatedContact.setOwnerId(new Long(getProperty("userAccManager")));
        relatedContact = client.contacts().update(relatedContact);
        log.info("contact {} was assigned to user: {}, and updated successfully: {}", relatedContact.getId(), relatedContact.getOwnerId(), relatedContact != null);
    }

    private boolean isCurrentOwnerASalesRepUser(Contact relatedContact) {
        return isCurrentOwnerASalesRepUser(getCurrentUser(relatedContact.getOwnerId()));
    }

    private User getCurrentUser(Long id) {
        return client.users().get(id);
    }

    private boolean isDealWon(Deal deal) {

        Optional<Stage> result = client.stages()
                .list(inactiveStage())
                .stream()
                .filter(getStagePredicate(deal))
                .findAny();
        log.info("is deal won: {}", result.isPresent());
        return result.isPresent();
    }

    private StagesService.SearchCriteria inactiveStage() {
        return new StagesService.SearchCriteria().active(false);
    }

    private Predicate<Stage> getStagePredicate(Deal deal) {
        return stage -> deal.getStageId().equals(stage.getId()) && "won".equals(stage.getCategory());
    }

    private String getProperty(String key) {
        String value = System.getProperty(key);
        if (null == value) {
            throw new MissingRequiredPropertiesException();
        }
        return value;
    }

}
