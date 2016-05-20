package com.emc.ecs.serviceBroker.service;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static com.emc.ecs.common.Fixtures.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import com.emc.ecs.serviceBroker.EcsManagementClientException;
import com.emc.ecs.serviceBroker.EcsService;
import com.emc.ecs.serviceBroker.config.BrokerConfig;
import com.emc.ecs.serviceBroker.config.CatalogConfig;
import com.emc.ecs.serviceBroker.repository.ServiceInstance;
import com.emc.ecs.serviceBroker.repository.ServiceInstanceRepository;

@RunWith(MockitoJUnitRunner.class)
public class EcsServiceInstanceServiceTest {

    @Mock
    private EcsService ecs;

    @Mock
    private ServiceInstanceRepository repository;

    @Mock
    private BrokerConfig broker;

    @Mock
    private CatalogConfig catalog;

    @Autowired
    @InjectMocks
    EcsServiceInstanceService instanceService;

    @Test
    public void testCreateNamespaceService()
	    throws EcsManagementClientException, IOException, JAXBException {
	when(catalog.findServiceDefinition(SERVICE_ID))
		.thenReturn(namespaceServiceFixture());

	when(ecs.namespaceExists(NAMESPACE)).thenReturn(false).thenReturn(true);

	Map<String, Object> params = new HashMap<>();
	instanceService.createServiceInstance(
		namespaceCreateRequestFixture(params));

	verify(repository).save(any(ServiceInstance.class));
	verify(ecs, times(2)).namespaceExists(NAMESPACE);
	verify(ecs, times(1)).createNamespace(NAMESPACE, SERVICE_ID, PLAN_ID1,
		params);
    }

    @Test
    public void testDeleteNamespaceService()
	    throws EcsManagementClientException {
	when(catalog.findServiceDefinition(SERVICE_ID))
		.thenReturn(namespaceServiceFixture());

	instanceService
		.deleteServiceInstance(namespaceDeleteRequestFixture());

	verify(repository, times(1)).delete(NAMESPACE);
	verify(ecs, times(1)).deleteNamespace(NAMESPACE);
    }

    @Test
    public void testChangeNamespaceService()
	    throws IOException, JAXBException, EcsManagementClientException {
	Map<String, Object> params = new HashMap<>();

	when(catalog.findServiceDefinition(SERVICE_ID))
		.thenReturn(namespaceServiceFixture());
	when(repository.find(NAMESPACE))
		.thenReturn(new ServiceInstance(namespaceCreateRequestFixture(params)));

	instanceService.updateServiceInstance(
		namespaceUpdateRequestFixture(params));

	verify(repository, times(1)).find(NAMESPACE);
	verify(repository, times(1)).delete(NAMESPACE);
	verify(repository, times(1)).save(any(ServiceInstance.class));
	verify(ecs, times(1)).changeNamespacePlan(NAMESPACE, SERVICE_ID,
		PLAN_ID1, params);
    }
}