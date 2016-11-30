import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.models.Stage
import groovy.util.logging.Slf4j
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

/**
 * Created by przemek on 06.10.2016.
 */

@Slf4j
class BusinessScenariosTests extends TestSetup {

    def "new deal in incoming stage should be created automatically when new contact organization is assigned to a sales rep"() {
        when:
        createdContact = createContact(testContactName, userSalesRep.id, ContactType.ORGANIZATION)
        await().atMost(40, SECONDS).until {
            !getDealsByContactId(createdContact.id).isEmpty()
        }
        Deal deal = getDealsByContactId(createdContact.id).first()

        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("dd/MM/yyyy")
        String dealCreationDate = dtfOut.print(deal.createdAt)

        then:
        "${createdContact.name} ${dealCreationDate}" == deal.name
        deal.contactId == createdContact.id
        "incoming" == getDealsStageCategory(deal.stageId)
    }

    def "won deal should be assigned to account manager user and contact reassigned from sales rep to acc manager user"() {
        when:
        Deal createdDeal = getDealsByContactId(createdContact.id).first()
        Stage stage = client.stages().list([name: "Won"]).first()
        createdDeal.stageId = stage.id
        client.deals().update(createdDeal);

        then:
        await().timeout(60, SECONDS).pollDelay(20, SECONDS).until {
            client.contacts().get(createdContact.id)?.ownerId == userAccManager.id
        }
    }

    def "deal should not be created when the contact is not an organization"() {
        when:
        Contact person = createContact(personContactName, userSalesRep.id, ContactType.NON_ORGANIZATION)

        then:
        await().timeout(60, SECONDS).pollDelay(20, SECONDS).until {
            getDealsByContactId(person.id).isEmpty()
        }
    }

    def "deal should not be created when the contacts owning user is not a sales rep"() {
        when:
        Contact person = createContact(personContactName, userAccManager.id, ContactType.ORGANIZATION)

        then:
        await().timeout(60, SECONDS).pollDelay(20, SECONDS).until {
            getDealsByContactId(person.id).isEmpty()
        }
    }

}
