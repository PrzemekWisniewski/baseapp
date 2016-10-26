import com.getbase.Client
import com.getbase.Configuration
import com.getbase.models.Contact
import com.getbase.models.Deal
import com.getbase.models.Stage
import com.getbase.models.User
import groovy.util.logging.Slf4j
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import static java.lang.System.getProperty

/**
 * Created by przemek on 25/10/16.
 */

@Slf4j
@Requires({ getProperty("integrationTests") == "true" })
abstract class TestSetup extends Specification {

    @Shared
    User userSalesRep, userAccManager

    @Shared
    Contact createdContact

    final static String personContactName = 'person assigned to a manager'
    final static String testContactName = 'Foo Contact'

    def setupSpec() {

        def client = getClient()

        userSalesRep = client.users()
                .get(getUser('userSalesRep'))

        checkIfUserActive(userSalesRep);
        assert userSalesRep.email.contains("_salesrep@")

        userAccManager = client.users()
                .get(getUser('userAccManager'))

        checkIfUserActive(userAccManager);
        assert userAccManager.email.contains("_accmanager@")

        assert client.contacts()
                .list([name: testContactName])
                .isEmpty()
    }

    def cleanupSpec() {
        client.deals()
                .list([contact_id: createdContact.id])*.id
                .each { id -> client.deals().delete(id) }

        client.contacts()
                .delete(createdContact.id)

        client.contacts()
                .list([name: personContactName])*.id
                .each { id ->
            client.contacts().delete(id)
        }
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

    def getDealsStageCategory(long dealStageId) {
        Optional<Stage> result = client.stages()
                .list([active: true])
                .stream()
                .filter { stage -> stage.id == dealStageId }
                .findAny();

        assert result.present
        result.get().category
    }

    List<Deal> getDealsByContactId(Long id) {
        assert client
        client.deals().list([contact_id: id])
    }

    def checkIfUserActive(user) {
        assert "active" == user.status
    }
}
