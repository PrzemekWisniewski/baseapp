package com.base.helpers;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.Stage;
import com.getbase.services.StagesService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Created by przemek on 03/11/16.
 */

@Component
@Slf4j
public class ContactHelper {

    private final Client client;
    private final UserHelper userHelper;
    private final String accManager;

    @Autowired
    public ContactHelper(@NonNull final Client client,
                         @NonNull final UserHelper userHelper,
                         @Value("${flow.userAccManager}") String accManager) {
        this.client = client;
        this.userHelper = userHelper;
        this.accManager = accManager;
    }

    public boolean contactReassigned(final Deal deal) {
        if (isDealWon(deal)) {
            final Contact relatedContact = client.contacts()
                    .get(deal.getContactId());
            log.info("checking contact: {} for reassigning", relatedContact.getId());
            if (userHelper.isSalesRep(relatedContact.getOwnerId())) {
                log.info("contact is to be reassigned.");
                return reassignContact(relatedContact);
            }
        }
        return false;
    }

    private boolean reassignContact(final Contact relatedContact) {
        relatedContact.setOwnerId(new Long(accManager));
        try {
            client.contacts()
                    .update(relatedContact);
            log.info("contact {} was assigned to user: {}, and updated successfully", relatedContact
                    .getId(), relatedContact.getOwnerId());
            return true;
        } catch (Exception e) {
            log.info("reassigning contact {} failed, {}", relatedContact.getId(), e);
        }
        return false;
    }

    private boolean isDealWon(final Deal deal) {
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

    private Predicate<Stage> getStagePredicate(final Deal deal) {
        return stage -> deal.getStageId()
                .equals(stage.getId()) && "won".equals(stage.getCategory());
    }

}
