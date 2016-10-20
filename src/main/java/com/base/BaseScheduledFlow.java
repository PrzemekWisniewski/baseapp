package com.base;

import com.base.processors.ContactProcessor;
import com.base.processors.DealProcessor;
import com.base.util.BaseAppUtil;
import com.getbase.Client;
import com.getbase.models.*;
import com.getbase.sync.Sync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Created by przemek on 19.10.2016.
 */

@Service
@Slf4j
class BaseScheduledFlow {

    private final Client client;

    @Autowired
    private DealProcessor dealProcessor;

    @Autowired
    private ContactProcessor contactProcessor;

    public BaseScheduledFlow() {
        log.info("BaseScheduledFlow service launching...");
        this.client = new BaseAppUtil().baseClient();
    }


    @Scheduled(fixedDelay = 15000)
    public void process() {
        Sync sync = new Sync(client, "abc321");
        sync.subscribe(Account.class, (meta, account) -> true)
                .subscribe(Address.class, (meta, address) -> true)
                .subscribe(AssociatedContact.class, (meta, associatedContact) -> true)
                .subscribe(Contact.class, contactProcessor::process)
                .subscribe(Deal.class, dealProcessor::process)
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

}
