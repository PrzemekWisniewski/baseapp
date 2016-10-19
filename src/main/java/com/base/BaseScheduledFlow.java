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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Created by przemek on 19.10.2016.
 */

@Service
@Slf4j
public class BaseScheduledFlow {

    private Client client;
    private String deviceUUID;

    public BaseScheduledFlow() {
        log.info("BaseScheduledFlow service launching...");
        deviceUUID = getProperty("deviceUUID");
        this.client = new Client(new Configuration.Builder()
                .accessToken(getProperty("BASECRM_ACCESS_TOKEN"))
                .build());
    }

    @Scheduled(fixedDelay = 15000)
    public void reportCurrentTime() {
        Sync sync = new Sync(client, deviceUUID);
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

        clearSync(meta);
        MDC.clear();
        return true;
    }

    private void clearSync(Meta meta) {
        client
                .sync()
                .ack(deviceUUID, Arrays.asList(meta.getSync().getAckKey()));
    }

    private boolean verifyContact(final Contact contact) {
        boolean isContactAnOrganization = contact.getIsOrganization();
        log.info("verifying contact. contact is a company: {}", isContactAnOrganization);

        return verifyNoExistingDealsOnContact(contact)
                && isCurrentOwnerASalesRepUser(getCurrentUser(contact.getOwnerId()))
                && contact.getIsOrganization();

    }

    private User getCurrentUser(Long id) {
        return client.users().get(id);
    }

    private boolean verifyNoExistingDealsOnContact(final Contact contact) {
        List<Deal> deals = client.deals()
                .list(new DealsService.SearchCriteria().contactId(contact.getId()));

        boolean noExistingDeals = deals.isEmpty();
        log.info("verifyNoExistingDealsOnContact {}", noExistingDeals);

        return noExistingDeals;
    }

    private boolean isCurrentOwnerASalesRepUser(final User user) {
        String usersStatus = user.getStatus();
        boolean isUserSalesRep = user.getEmail().contains("_salesrep@");
        log.info("isCurrentOwnerASalesRepUser, userId: {}, user's status: {}, and user is a sales rep: {}", user.getId(), usersStatus, isUserSalesRep);

        return "active".equals(usersStatus) && isUserSalesRep;
    }

    private boolean createDeal(final Contact contact) {
        final Deal newDeal = new Deal();
        newDeal.setOwnerId(contact.getOwnerId());
        newDeal.setContactId(contact.getId());

        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        String dealName = contact.getName() + " " + now.format(formatter);
        newDeal.setName(dealName);

        Deal createdDeal = client.deals().create(newDeal);
        log.info("created deal with id: {}, name: {}, owned by: {}, assigned to contact: {}", createdDeal.getId(), dealName, contact.getOwnerId(), contact.getId());

        return null != createdDeal;
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
                if (isCurrentOwnerASalesRepUser(getCurrentUser(relatedContact.getOwnerId()))) {
                    log.info("contact is to be reassigned.");
                    relatedContact.setOwnerId(convertId(getProperty("userAccManager")));
                    relatedContact = client.contacts().update(relatedContact);
                    log.info("contact {} was assigned to user: {}, and updated successfully: {}", relatedContact.getId(), relatedContact.getOwnerId(), relatedContact != null);
                }
            }
        } else {
            log.info("processing deal, but received sync event != updated. Nothing to do.");
        }
        clearSync(meta);
        MDC.clear();
        return true;
    }

    private boolean isDealWon(Deal deal) {

        Optional<Stage> result = client.stages()
                .list(new StagesService.SearchCriteria().active(false))
                .stream()
                .filter(stage -> deal.getStageId().equals(stage.getId()) && "won".equals(stage.getCategory()))
                .findAny();
        log.info("is deal won: {}", result.isPresent());
        return result.isPresent();
    }

    private String getProperty(String key) {
        String value = System.getProperty(key);
        if (null == value) {
            throw new MissingRequiredPropertiesException();
        }
        return value;
    }

    private Long convertId(String id) {
        return new Long(id);
    }


}
