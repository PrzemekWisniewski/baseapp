package com.base.processors;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.Deal;
import com.getbase.models.Stage;
import com.getbase.services.StagesService;
import com.getbase.sync.Meta;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Created by przemek on 19.10.2016.
 */

@Component
@Slf4j
public class DealProcessor extends AbstractProcessor {

    private final String accManager;

    @Autowired
    public DealProcessor(Client client, @Value("${flow.userAccManager}") String accManager) {
        super(client);
        this.accManager = accManager;
    }

    public boolean process(final Meta meta, final Deal deal) {
        MDC.put("deal", deal.getId()
                .toString());
        String syncEventType = meta.getSync()
                .getEventType();

        log.info("processing deal, sync event received: '{}'", syncEventType);

        if ("updated".equals(syncEventType)) {
            try {
                if (isDealWon(deal)) {
                    Contact relatedContact = client.contacts()
                            .get(deal.getContactId());
                    log.info("checking contact {} for reassigning", relatedContact.getId());
                    if (isOwnerASalesRep(relatedContact.getOwnerId())) {
                        log.info("contact is to be reassigned.");
                        return reassignContact(relatedContact);
                    }
                }
            } finally {
                MDC.remove("deal");
            }
        }
        return false;
    }

    private boolean isDealWon(Deal deal) {
        Optional<Stage> result = client.stages()
                .list(inactiveStage())
                .stream()
                .filter(getStagePredicate(deal))
                .findAny();
        log.info("is deal {} won: {}", result.get()
                .getId(), result.isPresent());
        return result.isPresent();
    }

    private StagesService.SearchCriteria inactiveStage() {
        return new StagesService.SearchCriteria().active(false);
    }

    private Predicate<Stage> getStagePredicate(Deal deal) {
        return stage -> deal.getStageId()
                .equals(stage.getId()) && "won".equals(stage.getCategory());
    }

    private boolean reassignContact(Contact relatedContact) {
        relatedContact.setOwnerId(new Long(accManager));
        try {
            client.contacts()
                    .update(relatedContact);
            log.info("contact {} was assigned to user: {}, and updated successfully", relatedContact.getId(),
                    relatedContact.getOwnerId());
            return true;
        } catch (Exception e) {
            log.info("reassigning contact {} failed, {}", relatedContact.getId(), e.getMessage());
        }
        return false;
    }

}
