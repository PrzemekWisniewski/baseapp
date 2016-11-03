package com.base.processors;

import com.base.helpers.DealHelper;
import com.getbase.Client;
import com.getbase.models.Contact;
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
public class ContactProcessor {

    private final DealHelper dealHelper;

    @Autowired
    public ContactProcessor(@NonNull final DealHelper dealHelper) {
        this.dealHelper = dealHelper;
    }

    public boolean process(final Meta meta, final Contact contact) {
        final String syncEventType = meta.getSync()
                .getEventType();
        log.info("processing contact: {}, sync event received: '{}'", contact.getId(), syncEventType);

        if ("created".equals(syncEventType)) {
            MDC.put("contact", contact.getId()
                    .toString());
            try {
                return dealHelper.dealCreated(contact);
            } finally {
                MDC.remove("contact");
            }
        }
        return false;
    }

}
