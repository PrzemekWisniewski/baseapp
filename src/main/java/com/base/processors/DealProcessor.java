package com.base.processors;

import com.base.helpers.ContactHelper;
import com.getbase.Client;
import com.getbase.models.Deal;
import com.getbase.sync.Meta;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Created by przemek on 19.10.2016.
 */

@Component
@Slf4j
public class DealProcessor {

    private final ContactHelper contactHelper;

    @Autowired
    public DealProcessor(@NonNull final ContactHelper contactHelper) {
        this.contactHelper = contactHelper;
    }

    public boolean process(final Meta meta, final Deal deal) {
        String syncEventType = meta.getSync()
                .getEventType();

        log.info("processing deal: {}, sync event received: '{}'", deal.getId(), syncEventType);

        if ("updated".equals(syncEventType)) {
            MDC.put("deal", deal.getId()
                    .toString());
            try {
                return contactHelper.contactReassigned(deal);
            } finally {
                MDC.remove("deal");
            }
        }
        return false;
    }

}
