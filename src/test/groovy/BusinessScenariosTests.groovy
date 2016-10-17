import com.getbase.Client
import com.getbase.Configuration
import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.models.Stage
import com.getbase.models.User
import com.getbase.services.ContactsService
import com.getbase.services.DealsService
import com.getbase.services.StagesService
import groovy.util.logging.Slf4j
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import static org.awaitility.Awaitility.await

/**
 * Created by przemek on 06.10.2016.
 */

@Slf4j
@Requires({ System.getProperty("integrationTests").equals("true") })
class BusinessScenariosTests extends Specification {

    @Shared
    def client

    @Shared
    User userSalesRep, userAccManager

    @Shared
    Contact createdContact

    @Shared
    String personContactName = 'person assigned to a manager'

    def setupSpec() {
        client = new Client(new Configuration.Builder()
                .accessToken(System.getProperty("BASECRM_ACCESS_TOKEN"))
                .build())

        userSalesRep = client
                .users()
                .get(convertUserId(System.getProperty("userSalesRep")))

        userAccManager = client
                .users()
                .get(convertUserId(System.getProperty("userAccManager")))

        def testContactName = 'Foo Contact'

        assert client
                .contacts()
                .list(new ContactsService.SearchCriteria().name(testContactName))
                .size() == 0

        createdContact = client.contacts().create(new Contact(name: testContactName, ownerId: userSalesRep.id, isOrganization: true))
        await().until {
            client
                    .contacts()
                    .list(new ContactsService.SearchCriteria().name(createdContact.name))
                    .size() == 1
        }
    }

    def convertUserId(String userId) {
        assert userId
        return new Long(userId)
    }

    def cleanupSpec() {
        client
                .deals()
                .list(new DealsService.SearchCriteria().contactId((Long) createdContact.id))*.id
                .each {
            id ->
                println(id)
                client.deals().delete(id)
        }

        await().until {
            client
                    .deals()
                    .list(new DealsService.SearchCriteria().contactId((Long) createdContact.id))
                    .size() == 0
        }

        client
                .contacts()
                .delete(createdContact.id)


        Contact tempContact = client
                .contacts()
                .list(new ContactsService.SearchCriteria().name((String) personContactName))[0]

        if (null != tempContact) {
            client
                    .contacts()
                    .delete(tempContact.id)
        }
    }

    def getDealsStageCategory(long dealStageId, boolean active) {
        Optional<Stage> result = client.stages()
                .list(new StagesService.SearchCriteria().active(active))
                .stream()
                .filter { stage -> stage.getId().equals(dealStageId) }
                .findAny();

        assert result.present
        result.get().category
    }

    def "new deal in incoming stage should be created automatically when new contact organization is assigned to a sales rep"() {
        when:
        await().sleep(40000)
        Deal deal = client.deals().list(new DealsService.SearchCriteria().contactId((Long) createdContact.id))[0]
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("dd/MM/yyyy")
        String dealCreationDate = dtfOut.print(deal.createdAt)

        then:
        createdContact.isOrganization
        deal.name
        "${createdContact.name} ${dealCreationDate}" == deal.name
        deal.contactId == createdContact.id
        "incoming" == getDealsStageCategory(deal.stageId, true)
        "active" == userSalesRep.status
        userSalesRep.email.contains("_salesrep@")
        !userSalesRep.email.contains("_accmanager@")
    }

    def "won deal should be assigned to account manager user and contact reassigned from sales rep to acc manager user"() {
        when:
        Deal createdDeal = client.deals().list(new DealsService.SearchCriteria().contactId(createdContact.id))[0]
        Stage stage = client.stages().list(new StagesService.SearchCriteria().name("Won"))[0]
        createdDeal.stageId = stage.id
        client.deals().update(createdDeal);
        await().sleep(40000)
        Contact reassigned = client.contacts().get(createdContact.id)

        then:
        "active" == userAccManager.status
        userAccManager.id == reassigned.ownerId
        userSalesRep.id != reassigned.ownerId
    }

    def "deal should not be created neither when the contacts owning user is not a sales rep or contact is not an organization"() {
        when:
        Contact person = client.contacts().create(new Contact(name: personContactName, isOrganization: false, ownerId: userAccManager.id))
        await().sleep(15000)

        Deal shouldBeNull = client.deals().list(new DealsService.SearchCriteria().contactId(person.id))[0]

        then:
        assert shouldBeNull == null

    }

}
