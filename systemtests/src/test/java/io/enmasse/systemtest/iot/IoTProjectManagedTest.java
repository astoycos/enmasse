/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.iot;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.iot.model.v1.IoTConfigBuilder;
import io.enmasse.iot.model.v1.IoTProject;
import io.enmasse.systemtest.AddressSpacePlans;
import io.enmasse.systemtest.AddressSpaceType;
import io.enmasse.systemtest.AddressType;
import io.enmasse.systemtest.DestinationPlan;
import io.enmasse.systemtest.TestTag;
import io.enmasse.systemtest.ability.ITestBaseStandard;
import io.enmasse.systemtest.bases.IoTTestBase;
import io.enmasse.systemtest.utils.AddressUtils;
import io.enmasse.systemtest.utils.IoTUtils;
import io.enmasse.user.model.v1.Operation;
import io.enmasse.user.model.v1.User;
import io.enmasse.user.model.v1.UserAuthorization;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.enmasse.user.model.v1.Operation.recv;
import static io.enmasse.user.model.v1.Operation.send;
import static java.util.Arrays.asList;
import static java.util.EnumSet.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.jupiter.api.Assertions.*;

@Tag(TestTag.sharedIot)
@Tag(TestTag.smoke)
class IoTProjectManagedTest extends IoTTestBase implements ITestBaseStandard {

    @Test
    void testCreate() throws Exception {
        createIoTConfig(new IoTConfigBuilder()
                .withNewMetadata()
                .withName("default")
                .endMetadata()
                .withNewSpec()
                .withEnableDefaultRoutes(false)
                .withNewServices()
                .withNewDeviceRegistry()
                .withNewFile()
                .endFile()
                .endDeviceRegistry()
                .endServices()
                .endSpec()
                .build());

        String addressSpaceName = "managed-address-space";

        IoTProject project = IoTUtils.getBasicIoTProjectObject("iot-project-managed", addressSpaceName, this.iotProjectNamespace);

        createIoTProject(project);// waiting until ready

        var iotProjectApiClient = kubernetes.getIoTProjectClient(project.getMetadata().getNamespace());
        IoTProject created = iotProjectApiClient.withName(project.getMetadata().getName()).get();

        assertNotNull(created);
        assertEquals(iotProjectNamespace, created.getMetadata().getNamespace());
        assertEquals(project.getMetadata().getName(), created.getMetadata().getName());
        assertEquals(
                project.getSpec().getDownstreamStrategy().getManagedStrategy().getAddressSpace().getName(),
                created.getSpec().getDownstreamStrategy().getManagedStrategy().getAddressSpace().getName());

        assertManaged(created);

    }

    private static void assertAddressType (final Address address, final AddressType type, final String plan) {
        assertEquals(type.toString(), address.getSpec().getType());
        assertEquals(plan, address.getSpec().getPlan());
    }

    private void assertManaged(IoTProject project) throws Exception {
        // address space s
        AddressSpace addressSpace = getAddressSpace(iotProjectNamespace, project.getSpec().getDownstreamStrategy().getManagedStrategy().getAddressSpace().getName());
        assertEquals(project.getSpec().getDownstreamStrategy().getManagedStrategy().getAddressSpace().getName(), addressSpace.getMetadata().getName());
        assertEquals(AddressSpaceType.STANDARD.toString(), addressSpace.getSpec().getType());
        assertEquals(AddressSpacePlans.STANDARD_SMALL, addressSpace.getSpec().getPlan());

        // addresses
        // {event/control/telemetry}/"project-namespace"."project-name"
        final String addressSuffix = "/" + project.getMetadata().getNamespace() + "." + project.getMetadata().getName();
        final List<Address> addresses = AddressUtils.getAddresses(addressSpace);
        assertEquals(5, addresses.size());
        assertEquals(5, addresses.stream()
                .map(Address::getMetadata)
                .map(ObjectMeta::getOwnerReferences)
                .flatMap(List::stream)
                .filter(reference -> isOwner(project, reference))
                .count());

        int correctAddressesCounter = 0;
        for (Address address : addresses) {

            final String addressName = address.getSpec().getAddress();

            if (addressName.equals(IOT_ADDRESS_EVENT + addressSuffix)) {

                assertAddressType(address, AddressType.QUEUE, DestinationPlan.STANDARD_SMALL_QUEUE);
                correctAddressesCounter++;

            } else if (addressName.equals(IOT_ADDRESS_CONTROL + addressSuffix)
                    || addressName.equals(IOT_ADDRESS_TELEMETRY + addressSuffix)
                    || addressName.equals(IOT_ADDRESS_COMMAND + addressSuffix)
                    || addressName.equals(IOT_ADDRESS_COMMAND_RESPONSE + addressSuffix)) {

                assertAddressType(address, AddressType.ANYCAST, DestinationPlan.STANDARD_SMALL_ANYCAST);
                correctAddressesCounter++;

            }

        }
        assertEquals(5, correctAddressesCounter, "There are incorrect IoT addresses " + addresses);

        // username "adapter"
        // name "project-address-space"+".adapter"
        final User user = getUser(addressSpace, "adapter");
        assertNotNull(user);
        assertEquals(1, user.getMetadata().getOwnerReferences().size());
        assertTrue(isOwner(project, user.getMetadata().getOwnerReferences().get(0)));

        final List<UserAuthorization> authorizations = user.getSpec().getAuthorization();

        assertThat(authorizations, hasSize(3));

        assertThat(authorizations, containsInAnyOrder(
                asList(
                        assertAdapterAuthorization( of(send), expandAddresses(addressSuffix, IOT_ADDRESS_TELEMETRY, IOT_ADDRESS_EVENT, IOT_ADDRESS_COMMAND_RESPONSE)),
                        assertAdapterAuthorization(of(recv), expandAddresses(addressSuffix, IOT_ADDRESS_COMMAND)),
                        assertAdapterAuthorization( of(recv, send), expandAddresses(addressSuffix, IOT_ADDRESS_CONTROL)))));
    }

    /**
     * Assert an authorization entry.
     *
     * @param operations The expected operations.
     * @param addresses The expected addresses.
     * @return A matcher, asserting the entry.
     */
    private static Matcher<UserAuthorization> assertAdapterAuthorization(final Set<Operation> operations, final Set<String> addresses) {

        return allOf(asList(

                hasProperty("operations", containsInAnyOrder(operations.toArray(Operation[]::new))),
                hasProperty("addresses", containsInAnyOrder(addresses.toArray(String[]::new)))

        ));

    }

    /**
     * Expand addresses to match ACLs.
     *
     * @param addressSuffix The "suffix" (tenant) of the address.
     * @return A set of all addresses.
     */
    private static Set<String> expandAddresses(final String addressSuffix, final String... baseAddresses) {

        return Arrays

                .stream(baseAddresses)

                .flatMap(address -> {
                    return Stream.of(
                            address + addressSuffix,
                            address + addressSuffix + "/*");
                })

                .collect(Collectors.toSet());

    }

    private boolean isOwner(IoTProject project, OwnerReference ownerReference) {
        return ownerReference.getKind().equals(IoTProject.KIND) && project.getMetadata().getName().equals(ownerReference.getName());
    }

}
