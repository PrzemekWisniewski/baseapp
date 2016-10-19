import com.getbase.Client
import com.getbase.Configuration
import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.models.Stage
import com.getbase.models.User
import com.getbase.services.StagesService
import groovy.util.logging.Slf4j
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import static java.lang.System.getProperty
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

/**
 * Created by przemek on 06.10.2016.
 */

@Slf4j
@Requires({ getProperty("integrationTests") == "true" })
class BusinessScenariosTests extends Specification {

    @Shared
    User userSalesRep, userAccManager

    @Shared
    Contact createdContact

    final static String personContactName = 'person assigned to a manager'
    final static String testContactName = 'Foo Contact'

    def setupSpec() {

        def client = getClient()

        userSalesRep = client
                .users()
                .get(getUser('userSalesRep'))

        userAccManager = client
                .users()
                .get(getUser('userAccManager'))

        assert client
                .contacts()
                .list([name: testContactName])
                .isEmpty()
    }

    def cleanupSpec() {
        client
                .deals()
                .list([contact_id: createdContact.id])*.id
                .each { id -> client.deals().delete(id) }

        client
                .contacts()
                .delete(createdContact.id)


        Contact tempContact = client
                .contacts()
                .list([name: personContactName])[0]

        if (!tempContact)
            client
                    .contacts()
                    .delete(tempContact.id)
    }

    Client getClient() {

        new Client(new Configuration.Builder()
                .accessToken(token)
                .build())
    }

    def getToken() {
        def token = System.getProperty("BASECRM_ACCESS_TOKEN")
        assert token
        token
    }

    def getUser(def property) {
        def user = convertUserId(System.getProperty(property))
        assert user
        user
    }


    def convertUserId(String userId) {
        assert userId
        return new Long(userId)
    }

    def createContact(name, ownerId, isOrganization) {
        Contact contact = client
                .contacts()
                .create(new Contact(name: name, ownerId: ownerId, isOrganization: isOrganization))
        log.info("createdContact: {}", contact)
        contact
    }

    def getDealsStageCategory(long dealStageId, boolean active) {
        Optional<Stage> result = client.stages()
                .list(new StagesService.SearchCriteria().active(active))
                .stream()
                .filter { stage -> stage.id == dealStageId }
                .findAny();

        assert result.present
        result.get().category
    }

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
        Deal createdDeal = getDealsByContactId(createdContact.id)[0]
        Stage stage = client.stages().list([name: "Won"])[0]
        createdDeal.stageId = stage.id
        client.deals().update(createdDeal);
        await().timeout(30, SECONDS).pollDelay(20, SECONDS).atLeast(20, SECONDS).until {
            getDealsByContactId(createdContact.id)[0].stageId == stage.id
        }
        Contact reassigned = client.contacts().get(createdContact.id)

        then:
        "active" == userAccManager.status
        userAccManager.id == reassigned.ownerId
        userSalesRep.id != reassigned.ownerId
    }

    def "deal should not be created neither when the contacts owning user is not a sales rep or contact is not an organization"() {
        when:
        Contact person = createContact(personContactName, userAccManager.id, false)
        await().atMost(30, SECONDS).until {
            null != person.id
        }

        Deal shouldBeNull = getDealsByContactId(person.id)[0]

        then:
        !shouldBeNull

    }

    List<Deal> getDealsByContactId(Long id) {
        assert client
        client.deals().list([contact_id: id])
    }

}
