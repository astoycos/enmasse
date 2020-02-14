/*
 * Copyright 2018-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.isolated;

import static io.enmasse.systemtest.TestTag.ISOLATED;
import static io.enmasse.systemtest.TestTag.NON_PR;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.transport.DeliveryState.DeliveryStateType;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressBuilder;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AddressSpaceBuilder;
import io.enmasse.admin.model.v1.AddressPlan;
import io.enmasse.admin.model.v1.AddressSpacePlan;
import io.enmasse.admin.model.v1.AddressSpacePlanBuilder;
import io.enmasse.admin.model.v1.BrokeredInfraConfig;
import io.enmasse.admin.model.v1.BrokeredInfraConfigBuilder;
import io.enmasse.admin.model.v1.BrokeredInfraConfigSpecAdminBuilder;
import io.enmasse.admin.model.v1.BrokeredInfraConfigSpecBrokerBuilder;
import io.enmasse.admin.model.v1.ResourceAllowance;
import io.enmasse.admin.model.v1.ResourceRequest;
import io.enmasse.admin.model.v1.StandardInfraConfig;
import io.enmasse.admin.model.v1.StandardInfraConfigBuilder;
import io.enmasse.admin.model.v1.StandardInfraConfigSpecAdminBuilder;
import io.enmasse.admin.model.v1.StandardInfraConfigSpecBrokerBuilder;
import io.enmasse.admin.model.v1.StandardInfraConfigSpecRouterBuilder;
import io.enmasse.systemtest.UserCredentials;
import io.enmasse.systemtest.amqp.AmqpClient;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.bases.isolated.ITestBaseIsolated;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.model.address.AddressType;
import io.enmasse.systemtest.model.addressplan.DestinationPlan;
import io.enmasse.systemtest.model.addressspace.AddressSpacePlans;
import io.enmasse.systemtest.model.addressspace.AddressSpaceType;
import io.enmasse.systemtest.platform.KubeCMDClient;
import io.enmasse.systemtest.shared.standard.QueueTest;
import io.enmasse.systemtest.time.TimeoutBudget;
import io.enmasse.systemtest.utils.AddressUtils;
import io.enmasse.systemtest.utils.PlanUtils;
import io.enmasse.systemtest.utils.TestUtils;
import io.fabric8.kubernetes.api.model.Pod;

@Tag(ISOLATED)
class CommonTest extends TestBase implements ITestBaseIsolated {
    private static Logger log = CustomLogger.getLogger();

    @Test
    void testAccessLogs() throws Exception {
        AddressSpace standard = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("mystandard")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.STANDARD.toString())
                .withPlan(AddressSpacePlans.STANDARD_UNLIMITED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        resourcesManager.createAddressSpace(standard);

        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(standard.getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(standard, "test-queue"))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("test-queue")
                .withPlan(DestinationPlan.STANDARD_SMALL_QUEUE)
                .endSpec()
                .build();
        resourcesManager.setAddresses(dest);

        TimeoutBudget budget = new TimeoutBudget(5, TimeUnit.MINUTES);
        List<Pod> unready;
        do {
            unready = new ArrayList<>(kubernetes.listPods());
            unready.removeIf(p -> TestUtils.isPodReady(p, true));

            if (!unready.isEmpty()) {
                Thread.sleep(1000L);
            }
        } while (!unready.isEmpty() && budget.timeLeft() > 0);

        if (!unready.isEmpty()) {
            fail(String.format(" %d pod(s) still unready", unready.size()));
        }

        Multimap<String, String> podsContainersWithNoLog = HashMultimap.create();

        kubernetes.listPods().stream().filter(pod -> !pod.getMetadata().getName().contains("none-authservice")).forEach(pod -> kubernetes.getContainersFromPod(pod.getMetadata().getName()).forEach(container -> {
            String podName = pod.getMetadata().getName();
            String containerName = container.getName();
            log.info("Getting log from pod: {}, for container: {}", podName, containerName);
            String podlog = kubernetes.getLog(podName, containerName);

            // Retry - diagnostic code to help understand a sporadic Ci failure.
            if (podlog.isEmpty()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("(Retry) Getting log from pod: {}, for container: {}", podName, containerName);
                podlog = kubernetes.getLog(podName, containerName);
            }

            if (podlog.isEmpty()) {
                podsContainersWithNoLog.put(podName, containerName);
            }

        }));

        if (!podsContainersWithNoLog.isEmpty()) {
            String podContainerNames = podsContainersWithNoLog.entries().stream().map(e -> String.format("%s-%s", e.getKey(), e.getValue())).collect(Collectors.joining(","));
            fail(String.format("%d pod container(s) had unexpectedly empty logs : %s ", podsContainersWithNoLog.size(), podContainerNames));
        }
    }

    @Test
    void testRestartComponents() throws Exception {
        List<Label> labels = new LinkedList<>();
        labels.add(new Label("name", "standard-authservice"));
        labels.add(new Label("name", "address-space-controller"));
        labels.add(new Label("name", "enmasse-operator"));

        UserCredentials user = new UserCredentials("frantisek", "dobrota");
        AddressSpace brokered = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("space-restart-brokered")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.BROKERED.toString())
                .withPlan(AddressSpacePlans.BROKERED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        AddressSpace standard = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("space-restart-standard")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.STANDARD.toString())
                .withPlan(AddressSpacePlans.STANDARD_UNLIMITED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        isolatedResourcesManager.createAddressSpaceList(standard, brokered);
        resourcesManager.createOrUpdateUser(brokered, user);
        resourcesManager.createOrUpdateUser(standard, user);

        List<Address> brokeredAddresses = AddressUtils.getAllBrokeredAddresses(brokered);
        List<Address> standardAddresses = AddressUtils.getAllStandardAddresses(standard);

        resourcesManager.setAddresses(brokeredAddresses.toArray(new Address[0]));
        resourcesManager.setAddresses(standardAddresses.toArray(new Address[0]));

        getClientUtils().assertCanConnect(brokered, user, brokeredAddresses, resourcesManager);
        getClientUtils().assertCanConnect(standard, user, standardAddresses, resourcesManager);

        log.info("------------------------------------------------------------");
        log.info("------------------- Start with restarting -------------------");
        log.info("------------------------------------------------------------");

        List<Pod> pods = kubernetes.listPods();
        int runningPodsBefore = pods.size();
        log.info("Number of running pods before restarting any: {}", runningPodsBefore);
        for (Label label : labels) {
            log.info("Restarting {}", label.labelValue);
            KubeCMDClient.deletePodByLabel(label.getLabelName(), label.getLabelValue());
            Thread.sleep(30_000);
            TestUtils.waitForExpectedReadyPods(kubernetes, kubernetes.getInfraNamespace(), runningPodsBefore, new TimeoutBudget(10, TimeUnit.MINUTES));
            assertSystemWorks(brokered, standard, user, brokeredAddresses, standardAddresses);
        }

        log.info("Restarting whole enmasse");
        KubeCMDClient.deletePodByLabel("app", kubernetes.getEnmasseAppLabel());
        Thread.sleep(180_000);
        TestUtils.waitForExpectedReadyPods(kubernetes, kubernetes.getInfraNamespace(), runningPodsBefore, new TimeoutBudget(10, TimeUnit.MINUTES));
        AddressUtils.waitForDestinationsReady(new TimeoutBudget(10, TimeUnit.MINUTES),
                standardAddresses.toArray(new Address[0]));
        assertSystemWorks(brokered, standard, user, brokeredAddresses, standardAddresses);

        //TODO: Uncomment when #2127 will be fixedy

//            Pod qdrouter = pods.stream().filter(pod -> pod.getMetadata().getName().contains("qdrouter")).collect(Collectors.toList()).get(0);
//            kubernetes.deletePod(environment.namespace(), qdrouter.getMetadata().getName());
//            assertSystemWorks(brokered, standard, user, brokeredAddresses, standardAddresses);
    }

    //https://github.com/EnMasseProject/enmasse/issues/3098
    @Test
    void testRestartAdminComponent() throws Exception {
        List<Label> labels = new LinkedList<>();
        labels.add(new Label("name", "admin"));

        UserCredentials user = new UserCredentials("frantisek", "dobrota");
        AddressSpace brokered = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("space-restart-brokered")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.BROKERED.toString())
                .withPlan(AddressSpacePlans.BROKERED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        AddressSpace standard = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("space-restart-standard")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.STANDARD.toString())
                .withPlan(AddressSpacePlans.STANDARD_UNLIMITED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        isolatedResourcesManager.createAddressSpaceList(standard, brokered);
        resourcesManager.createOrUpdateUser(brokered, user);
        resourcesManager.createOrUpdateUser(standard, user);

        List<Address> brokeredAddresses = Collections.singletonList(
                new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(brokered.getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(brokered, "test-queue"))
                        .endMetadata()
                        .withNewSpec()
                        .withType("queue")
                        .withAddress("test-queue")
                        .withPlan(DestinationPlan.BROKERED_QUEUE)
                        .endSpec()
                        .build());

        List<Address> standardAddresses = Arrays.asList(
                new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(standard.getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(standard, "test-queue"))
                        .endMetadata()
                        .withNewSpec()
                        .withType("queue")
                        .withAddress("test-queue")
                        .withPlan(DestinationPlan.STANDARD_SMALL_QUEUE)
                        .endSpec()
                        .build(),

                new AddressBuilder()
                        .withNewMetadata()
                        .withNamespace(standard.getMetadata().getNamespace())
                        .withName(AddressUtils.generateAddressMetadataName(standard, "test-queue-sharded"))
                        .endMetadata()
                        .withNewSpec()
                        .withType("queue")
                        .withAddress("test-queue-sharded")
                        .withPlan(DestinationPlan.STANDARD_LARGE_QUEUE)
                        .endSpec()
                        .build());


        resourcesManager.setAddresses(brokeredAddresses.toArray(new Address[0]));
        resourcesManager.setAddresses(standardAddresses.toArray(new Address[0]));

        getClientUtils().assertCanConnect(brokered, user, brokeredAddresses, resourcesManager);
        getClientUtils().assertCanConnect(standard, user, standardAddresses, resourcesManager);

        log.info("Sending messages before admin pod restart");

        for (Address addr : brokeredAddresses) {
            getClientUtils().sendDurableMessages(resourcesManager, brokered, addr, user, 15);
        }

        for (Address addr : standardAddresses) {
            getClientUtils().sendDurableMessages(resourcesManager, standard, addr, user, 15);
        }

        log.info("------------------------------------------------------------");
        log.info("------------------- Start with restarting -------------------");
        log.info("------------------------------------------------------------");

        List<Pod> pods = kubernetes.listPods();
        int runningPodsBefore = pods.size();
        log.info("Number of running pods before restarting any: {}", runningPodsBefore);

        for (Label label : labels) {
            log.info("Restarting {}", label.labelValue);
            KubeCMDClient.deletePodByLabel(label.getLabelName(), label.getLabelValue());
            Thread.sleep(30_000);
            TestUtils.waitForExpectedReadyPods(kubernetes, kubernetes.getInfraNamespace(), runningPodsBefore, new TimeoutBudget(10, TimeUnit.MINUTES));
            assertSystemWorks(brokered, standard, user, brokeredAddresses, standardAddresses);
        }

        log.info("Receiving messages after admin pod restart");

        for (Address addr : brokeredAddresses) {
            getClientUtils().receiveDurableMessages(resourcesManager, brokered, addr, user, 15);
        }

        for (Address addr : standardAddresses) {
            getClientUtils().receiveDurableMessages(resourcesManager, standard, addr, user, 15);
        }

    }

    @Test
    void testMonitoringTools() throws Exception {
        AddressSpace standard = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("standard-space-monitor")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.STANDARD.toString())
                .withPlan(AddressSpacePlans.STANDARD_UNLIMITED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        resourcesManager.createAddressSpace(standard);
        resourcesManager.createOrUpdateUser(standard, new UserCredentials("jenda", "cenda"));
        resourcesManager.setAddresses(AddressUtils.getAllStandardAddresses(standard).toArray(new Address[0]));

        String qdRouterName = TestUtils.listRunningPods(kubernetes, standard).stream()
                .filter(pod -> pod.getMetadata().getName().contains("qdrouter"))
                .collect(Collectors.toList()).get(0).getMetadata().getName();
        assertTrue(KubeCMDClient.runQDstat(kubernetes.getInfraNamespace(), qdRouterName, "-c", "--sasl-username=jenda", "--sasl-password=cenda").getRetCode());
        assertTrue(KubeCMDClient.runQDstat(kubernetes.getInfraNamespace(), qdRouterName, "-a", "--sasl-username=jenda", "--sasl-password=cenda").getRetCode());
        assertTrue(KubeCMDClient.runQDstat(kubernetes.getInfraNamespace(), qdRouterName, "-l", "--sasl-username=jenda", "--sasl-password=cenda").getRetCode());
    }

    @Test
    @Tag(NON_PR)
    void testMessagingDuringRestartComponents() throws Exception {
        List<Label> labels = new LinkedList<>();
        labels.add(new Label("name", "address-space-controller"));
        labels.add(new Label("name", "enmasse-operator"));

        UserCredentials user = new UserCredentials("frantisek", "dobrota");
        AddressSpace standard = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("addr-space-restart-standard")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.STANDARD.toString())
                .withPlan(AddressSpacePlans.STANDARD_UNLIMITED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        AddressSpace brokered = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("addr-space-restart-brokered")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.BROKERED.toString())
                .withPlan(AddressSpacePlans.BROKERED)
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();
        isolatedResourcesManager.createAddressSpaceList(standard, brokered);
        resourcesManager.createOrUpdateUser(brokered, user);
        resourcesManager.createOrUpdateUser(standard, user);

        List<Address> brokeredAddresses = AddressUtils.getAllBrokeredAddresses(brokered);
        List<Address> standardAddresses = AddressUtils.getAllStandardAddresses(standard);

        resourcesManager.setAddresses(brokeredAddresses.toArray(new Address[0]));
        resourcesManager.setAddresses(standardAddresses.toArray(new Address[0]));

        getClientUtils().assertCanConnect(brokered, user, brokeredAddresses, resourcesManager);
        getClientUtils().assertCanConnect(standard, user, standardAddresses, resourcesManager);

        log.info("------------------------------------------------------------");
        log.info("------------------- Start with restarting -------------------");
        log.info("------------------------------------------------------------");

        List<Pod> pods = kubernetes.listPods();
        int runningPodsBefore = pods.size();
        log.info("Number of running pods before restarting any: {}", runningPodsBefore);

        for (Address addr : brokeredAddresses) {
            log.info("Starting messaging in address {} and address space {}", addr.getSpec().getAddress(), brokered.getMetadata().getName());
            for (Label label : labels) {
                getClientUtils().assertCanConnect(brokered, user, brokeredAddresses, resourcesManager);
                doMessagingDuringRestart(label, runningPodsBefore, user, brokered, addr);
                getClientUtils().assertCanConnect(brokered, user, brokeredAddresses, resourcesManager);
            }
        }

        for (Address addr : standardAddresses) {
            log.info("Starting messaging in address {} and address space {}", addr.getSpec().getAddress(), standard.getMetadata().getName());
            for (Label label : labels) {
                getClientUtils().assertCanConnect(standard, user, standardAddresses, resourcesManager);
                doMessagingDuringRestart(label, runningPodsBefore, user, standard, addr);
                getClientUtils().assertCanConnect(standard, user, standardAddresses, resourcesManager);
            }
        }
    }

    @Test
    @Tag(NON_PR)
    public void testFullBrokerKeepsOperableStandard() throws Exception {
        StandardInfraConfig testInfra = new StandardInfraConfigBuilder()
                .withNewMetadata()
                .withName("infra-keeps-operable-s")
                .endMetadata()
                .withNewSpec()
                .withBroker(new StandardInfraConfigSpecBrokerBuilder()
                        .withAddressFullPolicy("FAIL")
                        .withNewResources()
                        .withMemory("512Mi")
                        .withStorage("512Mi")
                        .endResources()
                        .withGlobalMaxSize("10Mb")
                        .build())
                .withRouter(new StandardInfraConfigSpecRouterBuilder()
                        .withNewResources()
                        .withMemory("256Mi")
                        .endResources()
                        .withMinReplicas(1)
                        .withLinkCapacity(250)
                        .build())
                .withAdmin(new StandardInfraConfigSpecAdminBuilder()
                        .withNewResources()
                        .withMemory("512Mi")
                        .endResources()
                        .build())
                .endSpec()
                .build();
        resourcesManager.createInfraConfig(testInfra);

        AddressPlan exampleAddressPlan = PlanUtils.createAddressPlanObject("test-queue-keeps-operable", AddressType.QUEUE,
                Arrays.asList(new ResourceRequest("broker", 0.5), new ResourceRequest("router", 0.5)));

        resourcesManager.createAddressPlan(exampleAddressPlan);

        AddressSpacePlan exampleSpacePlan = new AddressSpacePlanBuilder()
                .withNewMetadata()
                .withName("test-plan-keeps-operable")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withAddressSpaceType(AddressSpaceType.STANDARD.toString())
                .withInfraConfigRef(testInfra.getMetadata().getName())
                .withResourceLimits(Arrays.asList(
                        new ResourceAllowance("broker", 1.0),
                        new ResourceAllowance("router", 1.0),
                        new ResourceAllowance("aggregate", 2.0))
                        .stream().collect(Collectors.toMap(ResourceAllowance::getName, ResourceAllowance::getMax)))
                .withAddressPlans(Arrays.asList(exampleAddressPlan.getMetadata().getName()))
                .endSpec()
                .build();
        resourcesManager.createAddressSpacePlan(exampleSpacePlan);

        AddressSpace exampleAddressSpace = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("test-address-space-keeps-operable")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.STANDARD.toString())
                .withPlan(exampleSpacePlan.getMetadata().getName())
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();

        resourcesManager.createAddressSpace(exampleAddressSpace);

        doTestBrokerKeepsOperableOnFullAddress(exampleAddressPlan, exampleAddressSpace);

    }

    @Test
    @Tag(NON_PR)
    public void testFullBrokerKeepsOperableBrokered() throws Exception {
        BrokeredInfraConfig testInfra = new BrokeredInfraConfigBuilder()
                .withNewMetadata()
                .withName("infra-keeps-operable-b")
                .endMetadata()
                .withNewSpec()
                .withBroker(new BrokeredInfraConfigSpecBrokerBuilder()
                        .withAddressFullPolicy("FAIL")
                        .withNewResources()
                        .withMemory("512Mi")
                        .withStorage("512Mi")
                        .endResources()
                        .withGlobalMaxSize("10Mb")
                        .build())
                .withAdmin(new BrokeredInfraConfigSpecAdminBuilder()
                        .withNewResources()
                        .withMemory("512Mi")
                        .endResources()
                        .build())
                .endSpec()
                .build();
        resourcesManager.createInfraConfig(testInfra);

        AddressPlan exampleAddressPlan = PlanUtils.createAddressPlanObject("test-queue-keeps-operable", AddressType.QUEUE,
                Arrays.asList(new ResourceRequest("broker", 0.5)));

        resourcesManager.createAddressPlan(exampleAddressPlan);

        AddressSpacePlan exampleSpacePlan = new AddressSpacePlanBuilder()
                .withNewMetadata()
                .withName("test-plan-keeps-operable")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withAddressSpaceType(AddressSpaceType.BROKERED.toString())
                .withInfraConfigRef(testInfra.getMetadata().getName())
                .withResourceLimits(Arrays.asList(
                        new ResourceAllowance("broker", 1.0))
                        .stream().collect(Collectors.toMap(ResourceAllowance::getName, ResourceAllowance::getMax)))
                .withAddressPlans(Arrays.asList(exampleAddressPlan.getMetadata().getName()))
                .endSpec()
                .build();
        resourcesManager.createAddressSpacePlan(exampleSpacePlan);

        AddressSpace exampleAddressSpace = new AddressSpaceBuilder()
                .withNewMetadata()
                .withName("test-address-space-keeps-operable")
                .withNamespace(kubernetes.getInfraNamespace())
                .endMetadata()
                .withNewSpec()
                .withType(AddressSpaceType.BROKERED.toString())
                .withPlan(exampleSpacePlan.getMetadata().getName())
                .withNewAuthenticationService()
                .withName("standard-authservice")
                .endAuthenticationService()
                .endSpec()
                .build();

        resourcesManager.createAddressSpace(exampleAddressSpace);

        doTestBrokerKeepsOperableOnFullAddress(exampleAddressPlan, exampleAddressSpace);

    }

    private void doTestBrokerKeepsOperableOnFullAddress(AddressPlan addressPlan, AddressSpace addressSpace) throws Exception {
        UserCredentials user = new UserCredentials("dummy", "supersecure");
        resourcesManager.createOrUpdateUser(addressSpace, user);

        var address1 = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(kubernetes.getInfraNamespace())
                .withName(AddressUtils.generateAddressMetadataName(addressSpace, "example-queue-1"))
                .endMetadata()
                .withNewSpec()
                .withType(AddressType.QUEUE.toString())
                .withAddress("example-queue-1")
                .withPlan(addressPlan.getMetadata().getName())
                .endSpec()
                .build();

        var address2 = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(kubernetes.getInfraNamespace())
                .withName(AddressUtils.generateAddressMetadataName(addressSpace, "example-queue-2"))
                .endMetadata()
                .withNewSpec()
                .withType(AddressType.QUEUE.toString())
                .withAddress("example-queue-2")
                .withPlan(addressPlan.getMetadata().getName())
                .endSpec()
                .build();

        resourcesManager.setAddresses(address1, address2);

        AmqpClient client = getAmqpClientFactory().createQueueClient(addressSpace);
        client.getConnectOptions().setCredentials(user);
        boolean full = false;
        byte[] bytes = new byte[1024 * 1000];
        Random random = new Random();
        int messagesSent = 0;
        TimeoutBudget timeout = new TimeoutBudget(180, TimeUnit.SECONDS);

        do {
            Message message = Message.Factory.create();
            random.nextBytes(bytes);
            message.setBody(new AmqpValue(new Data(new Binary(bytes))));
            message.setAddress(address1.getSpec().getAddress());
            message.setDurable(true);
            try {
                var deliveries = client.sendMessage(address1.getSpec().getAddress(), message).get(5, TimeUnit.SECONDS);

                assertThat(deliveries, hasSize(1));
                var state = deliveries.get(0).getRemoteState();
                if (state.getType() == DeliveryStateType.Modified || state.getType() == DeliveryStateType.Rejected) {
                    full = true;
                    log.info("broker is full after sending {} messages", messagesSent);
                }
                messagesSent++;

                if (timeout.timeoutExpired()) {
                    log.info("Delivery state is {}", state.getType());
                    Assertions.fail("Timeout waiting for remote broker to become full, probably error in test env configuration");
                }
            } catch (Exception e) {
                full = true;
                log.info("broker is full after sending {} messages", messagesSent);
            }
        } while(!full);

        resourcesManager.deleteAddresses(address1);
        AddressUtils.waitForAddressDeleted(address1, new TimeoutBudget(1, TimeUnit.MINUTES));

        QueueTest.runQueueTest(client, address2, messagesSent/2);
    }

    /////////////////////////////////////////////////////////////////////
    // help methods
    /////////////////////////////////////////////////////////////////////

    private void assertSystemWorks(AddressSpace brokered, AddressSpace standard, UserCredentials existingUser,
                                   List<Address> brAddresses, List<Address> stAddresses) throws Exception {
        log.info("Check if system works");
        getClientUtils().assertCanConnect(standard, existingUser, stAddresses, resourcesManager);
        getClientUtils().assertCanConnect(brokered, existingUser, brAddresses, resourcesManager);
        resourcesManager.getAddressSpace(brokered.getMetadata().getName());
        resourcesManager.getAddressSpace(standard.getMetadata().getName());
        resourcesManager.createOrUpdateUser(brokered, new UserCredentials("jenda", "cenda"));
        resourcesManager.createOrUpdateUser(standard, new UserCredentials("jura", "fura"));
    }

    private void doMessagingDuringRestart(Label label, int runningPodsBefore, UserCredentials user, AddressSpace space, Address addr) throws Exception {
        long sleepMillis = 500;
        log.info("Starting messaging");
        AddressType addressType = AddressType.getEnum(addr.getSpec().getType());
        AmqpClient client = getAmqpClientFactory().createAddressClient(space, addressType);
        client.getConnectOptions().setCredentials(user);

        var stopSend = new CompletableFuture<>();

        var recvFut = client.recvMessagesWithStatus(addr.getSpec().getAddress(), msg -> {
            log.info("Message received");
            return false;
        });


        var sendFut = client.sendMessages(addr.getSpec().getAddress(),
                Stream.generate(() -> UUID.randomUUID().toString()).limit(1000).collect(Collectors.toList()),
                msg -> {
                    if (stopSend.isDone()) {
                        return true;
                    }
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        log.error("Error waiting between sends", e);
                        stopSend.completeExceptionally(e);
                        return true;
                    }
                    return false;
                });

        log.info("Restarting {}", label.labelValue);
        KubeCMDClient.deletePodByLabel(label.getLabelName(), label.getLabelValue());
        Thread.sleep(30_000);
        TestUtils.waitForExpectedReadyPods(kubernetes, kubernetes.getInfraNamespace(), runningPodsBefore, new TimeoutBudget(10, TimeUnit.MINUTES));
        if (stopSend.isCompletedExceptionally()) {
            stopSend.get();
        }
        stopSend.complete(new Object());
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            log.error("Error waiting between stop sender and receiver", e);
        }
        recvFut.closeGracefully();

        int received = recvFut.getResult().get(10, TimeUnit.SECONDS).size();
        int sent = sendFut.get(10, TimeUnit.SECONDS);
        assertEquals(sent, received, "Missmatch between messages sent and received");
    }

    private static class Label {
        String labelName;
        String labelValue;

        Label(String labelName, String labelValue) {
            this.labelName = labelName;
            this.labelValue = labelValue;
        }

        String getLabelName() {
            return labelName;
        }

        String getLabelValue() {
            return labelValue;
        }
    }

}

