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
        given:
        createdContact = createContact(testContactName, userSalesRep.id, true)

        when:
        await().atMost(40, SECONDS).until {
            !getDealsByContactId(createdContact.id).isEmpty()
        }
        Deal deal = getDealsByContactId(createdContact.id)[0]

        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("dd/MM/yyyy")
        String dealCreationDate = dtfOut.print(deal.createdAt)

        then:
        deal.name
        "${createdContact.name} ${dealCreationDate}" == deal.name
        deal.contactId == createdContact.id
        "incoming" == getDealsStageCategory(deal.stageId)
    }

    def "won deal should be assigned to account manager user and contact reassigned from sales rep to acc manager user"() {
        when:
        Deal createdDeal = getDealsByContactId(createdContact.id)[0]
        Stage stage = client.stages().list([name: "Won"])[0]
        createdDeal.stageId = stage.id
        client.deals().update(createdDeal);

        then:
        await().timeout(40, SECONDS).pollDelay(20, SECONDS).until {
            client.contacts().get(createdContact.id)?.ownerId == userAccManager.id
        }
    }

    def "deal should not be created neither when the contacts owning user is not a sales rep or contact is not an organization"() {
        when:
        Contact person = createContact(personContactName, userAccManager.id, false)
        await().timeout(60, SECONDS).pollDelay(20, SECONDS).until {
            null != person.id
        }

        then:
        getDealsByContactId(person.id).isEmpty()

        when:
        person.isOrganization = true
        person.ownerId = userSalesRep.id
        client.contacts().update(person);

        then:
        await().timeout(60, SECONDS).pollDelay(20, SECONDS).until {
            client.contacts().get(person.id)?.ownerId == userSalesRep.id
        }
        getDealsByContactId(person.id).isEmpty()
    }


}
