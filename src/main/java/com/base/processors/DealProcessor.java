package com.base.processors;

import com.base.util.BaseAppUtil;
import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.Stage;
import com.getbase.models.User;
import com.getbase.services.StagesService;
import com.getbase.sync.Meta;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.function.Predicate;

import static java.lang.System.getProperty;

/**
 * Created by przemek on 19.10.2016.
 */

@Component
@Slf4j
public class DealProcessor {

    private Client client;

    @PostConstruct
    void initClient() {
        this.client = new BaseAppUtil().baseClient();
    }

    public boolean process(final Meta meta, final Deal deal) {
        MDC.put("deal", deal.getId().toString());
        String syncEventType = meta.getSync().getEventType();
        boolean isDealUpdated = "updated".equals(syncEventType);

        if (isDealUpdated) {
            log.info("processing deal, sync event received: '{}'", syncEventType);
            if (isDealWon(deal)) {
                Contact relatedContact = client.contacts().get(deal.getContactId());
                log.info("checking contact {} for reassigning", relatedContact.getId());
                if (isCurrentOwnerASalesRepUser(relatedContact.getOwnerId())) {
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

    private boolean isCurrentOwnerASalesRepUser(Long ownerId) {
        User currentUser = getCurrentUser(ownerId);
        String usersStatus = currentUser.getStatus();
        boolean isUserSalesRep = currentUser.getEmail().contains("_salesrep@");
        log.info("isUserASalesRepUser, userId: {}, user's status: {}, and user is a sales rep: {}", currentUser.getId(), usersStatus, isUserSalesRep);

        return "active".equals(usersStatus) && isUserSalesRep;
    }

    private User getCurrentUser(Long id) {
        return client.users().get(id);
    }

    private void reassignContact(Contact relatedContact) {
        relatedContact.setOwnerId(new Long(getProperty("userAccManager")));
        relatedContact = client.contacts().update(relatedContact);
        log.info("contact {} was assigned to user: {}, and updated successfully: {}", relatedContact.getId(), relatedContact.getOwnerId(), relatedContact != null);
    }

}
