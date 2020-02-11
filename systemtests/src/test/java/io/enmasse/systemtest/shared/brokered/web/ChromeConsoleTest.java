/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.shared.brokered.web;

import io.enmasse.address.model.AddressBuilder;
import io.enmasse.systemtest.bases.shared.ITestSharedBrokered;
import io.enmasse.systemtest.bases.web.ConsoleTest;
import io.enmasse.systemtest.messagingclients.ExternalClients;
import io.enmasse.systemtest.model.address.AddressType;
import io.enmasse.systemtest.selenium.SeleniumChrome;
import io.enmasse.systemtest.utils.AddressUtils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.enmasse.systemtest.TestTag.NON_PR;

@Tag(NON_PR)
@SeleniumChrome
class ChromeConsoleTest extends ConsoleTest implements ITestSharedBrokered {

    @Test
    void testCreateDeleteQueue() throws Exception {
        doTestCreateDeleteAddress(getSharedAddressSpace(), new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "test-queue"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("test-queue")
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build());
    }

    @Test
    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
    void testCreateDeleteTopic() throws Exception {
        doTestCreateDeleteAddress(getSharedAddressSpace(), new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "test-topic"))
                .endMetadata()
                .withNewSpec()
                .withType("topic")
                .withAddress("test-topic")
                .withPlan(getDefaultPlan(AddressType.TOPIC))
                .endSpec()
                .build());
    }


    @Test
    @ExternalClients
    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
    void testPurgeAddress() throws Exception {
        doTestPurgeMessages(getSharedAddressSpace());
    }

    //    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testCreateDeleteTopic() throws Exception {
//        doTestCreateDeleteAddress(new AddressBuilder()
//                .withNewMetadata()
//                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
//                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "test-topic"))
//                .endMetadata()
//                .withNewSpec()
//                .withType("topic")
//                .withAddress("test-topic")
//                .withPlan(getDefaultPlan(AddressType.TOPIC))
//                .endSpec()
//                .build());
//    }
//
//    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testPurgeAddress() throws Exception {
//        doTestPurgeMessages(new AddressBuilder()
//                .withNewMetadata()
//                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
//                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "purge-queue"))
//                .endMetadata()
//                .withNewSpec()
//                .withType("queue")
//                .withAddress("purge-queue")
//                .withPlan(getDefaultPlan(AddressType.QUEUE))
//                .endSpec()
//                .build());
//    }

    @Test
    void testFilterAddressesByType() throws Exception {
        doTestFilterAddressesByType(getSharedAddressSpace());
    }

    @Test
    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
    void testFilterAddressesByName() throws Exception {
        doTestFilterAddressesByName(getSharedAddressSpace());
    }

//    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testDeleteFilteredAddress() throws Exception {
//        doTestDeleteFilteredAddress();
//    }
//
//    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testFilterAddressWithRegexSymbols() throws Exception {
//        doTestFilterAddressWithRegexSymbols();
//    }
//
//    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testRegexAlertBehavesConsistently() throws Exception {
//        doTestRegexAlertBehavesConsistently();
//    }

    @Test
    void testSortAddressesByName() throws Exception {
        doTestSortAddressesByName(getSharedAddressSpace());
    }

//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testSortConnectionsBySenders() throws Exception {
//        doTestSortConnectionsBySenders();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testSortConnectionsByReceivers() throws Exception {
//        doTestSortConnectionsByReceivers();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testFilterConnectionsByEncrypted() throws Exception {
//        doTestFilterConnectionsByEncrypted();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testFilterConnectionsByUser() throws Exception {
//        doTestFilterConnectionsByUser();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testFilterConnectionsByHostname() throws Exception {
//        doTestFilterConnectionsByHostname();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testSortConnectionsByHostname() throws Exception {
//        doTestSortConnectionsByHostname();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testFilterConnectionsByContainerId() throws Exception {
//        doTestFilterConnectionsByContainerId();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testSortConnectionsByContainerId() throws Exception {
//        doTestSortConnectionsByContainerId();
//    }
//
//    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testMessagesMetrics() throws Exception {
//        doTestMessagesMetrics();
//    }
//
//    @Test
//    @ExternalClients
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testClientsMetrics() throws Exception {
//        doTestClientsMetrics();
//    }
//
//    @Test()
//    void testCannotOpenConsolePage() {
//        assertThrows(IllegalAccessException.class,
//                () -> doTestCanOpenConsolePage(new UserCredentials("nonexistsUser", "pepaPa555"), false));
//    }
//
//    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testCanOpenConsolePage() throws Exception {
//        doTestCanOpenConsolePage(clusterUser, true);
//    }
//
//    @Test
//    @Disabled("Only few chrome tests are enabled, rest functionality is covered by firefox")
//    void testCreateAddressWithSpecialCharsShowsErrorMessage() throws Exception {
//        doTestCreateAddressWithSpecialCharsShowsErrorMessage();
//    }
//
//    @Test
//    @Disabled("Only a few chrome tests are enabled, rest of functionality is covered by firefox")
//    void testCreateAddressWithSymbolsAt61stCharIndex() throws Exception {
//        doTestCreateAddressWithSymbolsAt61stCharIndex(
//                new AddressBuilder()
//                        .withNewMetadata()
//                        .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue10charhere-10charhere-10charhere-10charhere-10charhere-1"))
//                        .endMetadata()
//                        .withNewSpec()
//                        .withType("queue")
//                        .withAddress("test-queue1")
//                        .withPlan(getDefaultPlan(AddressType.QUEUE))
//                        .endSpec()
//                        .build(),
//                new AddressBuilder()
//                        .withNewMetadata()
//                        .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "queue10charhere-10charhere-10charhere-10charhere-10charhere.1"))
//                        .endMetadata()
//                        .withNewSpec()
//                        .withType("queue")
//                        .withAddress("test-queue2")
//                        .withPlan(getDefaultPlan(AddressType.QUEUE))
//                        .endSpec()
//                        .build());
//    }
//
//    @Test
//    @Disabled("Only a few chrome tests are enabled, rest of functionality is covered by firefox")
//    void testAddressWithValidPlanOnly() throws Exception {
//        doTestAddressWithValidPlanOnly();
//    }
}
