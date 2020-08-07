package com.emc.ecs.servicebroker.service;

import com.emc.ecs.management.sdk.*;
import com.emc.ecs.management.sdk.model.BaseUrl;
import com.emc.ecs.management.sdk.model.BaseUrlInfo;
import com.emc.ecs.management.sdk.model.DataServiceReplicationGroup;
import com.emc.ecs.management.sdk.model.ObjectBucketCreate;
import com.emc.ecs.management.sdk.model.UserSecretKey;
import com.emc.ecs.servicebroker.EcsManagementClientException;
import com.emc.ecs.servicebroker.config.BrokerConfig;
import com.emc.ecs.servicebroker.config.CatalogConfig;
import com.emc.ecs.servicebroker.model.PlanProxy;
import com.emc.ecs.servicebroker.model.ReclaimPolicy;
import com.emc.ecs.servicebroker.model.ServiceDefinitionProxy;
import com.emc.ecs.servicebroker.repository.BucketWipeFactory;
import com.emc.ecs.servicebroker.repository.ServiceInstance;
import com.emc.ecs.servicebroker.repository.ServiceInstanceRepository;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.emc.ecs.common.Fixtures.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ReplicationGroupAction.class, BucketAction.class,
    ObjectUserAction.class, ObjectUserSecretAction.class,
    BaseUrlAction.class, BucketQuotaAction.class,
    BucketRetentionAction.class, NamespaceAction.class,
    NamespaceQuotaAction.class, NamespaceRetentionAction.class,
    BucketAclAction.class, NFSExportAction.class, ObjectUserMapAction.class})

public class ReclaimPolicyTests {
    private static final String BASE_URL = "base-url";
    private static final String REPOSITORY = "repository";
    private static final String USER = "user";
    private static final String HTTP = "http://";
    private static final String _9020 = ":9020";
    private static final String CREATE = "create";
    private static final String DELETE = "delete";

    @Mock
    private Connection connection;

    @Mock
    private BrokerConfig broker;

    @Mock
    private CatalogConfig catalog;

    @Mock
    private BucketWipeFactory bucketWipeFactory;

    @Autowired
    @InjectMocks
    private EcsService ecs;

    ServiceInstanceRepository instanceRepo;
    BucketInstanceWorkflow workflow;

    @Before
    public void setUp() {
        when(broker.getPrefix()).thenReturn(PREFIX);
        when(broker.getReplicationGroup()).thenReturn(RG_NAME);
        when(broker.getNamespace()).thenReturn(NAMESPACE);
        when(broker.getRepositoryUser()).thenReturn(USER);
        when(broker.getRepositoryBucket()).thenReturn(REPOSITORY);

        instanceRepo = mock(ServiceInstanceRepository.class);
        workflow = new BucketInstanceWorkflow(instanceRepo, ecs);
    }

    @Test
    public void testReclaimPolicyExtraction() {
        assertEquals(ReclaimPolicy.Fail, ReclaimPolicy.getReclaimPolicy(null));
        assertEquals(ReclaimPolicy.Detach, ReclaimPolicy.getReclaimPolicy(Collections.singletonMap(RECLAIM_POLICY, ReclaimPolicy.Detach)));
        assertEquals(ReclaimPolicy.Delete, ReclaimPolicy.getReclaimPolicy(Collections.singletonMap(RECLAIM_POLICY, ReclaimPolicy.Delete)));
        assertEquals(ReclaimPolicy.Fail, ReclaimPolicy.getReclaimPolicy(Collections.singletonMap(RECLAIM_POLICY, ReclaimPolicy.Fail)));
    }

    @Test
    public void testAllowedReclaimPolicyExtraction() {
        Map<String, Object> params = new HashMap<>();

        List<ReclaimPolicy> result = ReclaimPolicy.getAllowedReclaimPolicies(null);
        assertThat(result, CoreMatchers.hasItems(ReclaimPolicy.Fail));

        params.put(ALLOWED_RECLAIM_POLICIES, "Delete, Fail");
        result = ReclaimPolicy.getAllowedReclaimPolicies(params);
        assertThat(result, CoreMatchers.hasItems(ReclaimPolicy.Delete, ReclaimPolicy.Fail));

        params.put(ALLOWED_RECLAIM_POLICIES, "Delete,Fail");
        result = ReclaimPolicy.getAllowedReclaimPolicies(params);
        assertThat(result, CoreMatchers.hasItems(ReclaimPolicy.Delete, ReclaimPolicy.Fail));

        params.put(ALLOWED_RECLAIM_POLICIES, "Delete, Fail, Detach");
        result = ReclaimPolicy.getAllowedReclaimPolicies(params);
        assertThat(result, CoreMatchers.hasItems(ReclaimPolicy.Delete, ReclaimPolicy.Fail, ReclaimPolicy.Detach));


        params.put(ALLOWED_RECLAIM_POLICIES, "Detach");
        result = ReclaimPolicy.getAllowedReclaimPolicies(params);
        assertThat(result, CoreMatchers.hasItems(ReclaimPolicy.Detach));
    }

    @Test
    public void testReclaimPolicyValidation() {
        Map<String, Object> params = new HashMap<>();

        // With No Defined Allowed
        assertTrue(ReclaimPolicy.isPolicyAllowed(params));

        params.put(RECLAIM_POLICY, "Fail");
        assertTrue(ReclaimPolicy.isPolicyAllowed(params));

        params.put(RECLAIM_POLICY, "Delete");
        assertFalse(ReclaimPolicy.isPolicyAllowed(params));

        params.put(RECLAIM_POLICY, "Detach");
        assertFalse(ReclaimPolicy.isPolicyAllowed(params));

        // With Different Allowed defined
        params.put(ALLOWED_RECLAIM_POLICIES, "Detach, Fail");
        params.put(RECLAIM_POLICY, "Detach");
        assertTrue(ReclaimPolicy.isPolicyAllowed(params));

        params.put(ALLOWED_RECLAIM_POLICIES, "Fail,Detach");
        params.put(RECLAIM_POLICY, "Detach");
        assertTrue(ReclaimPolicy.isPolicyAllowed(params));

        params.put(ALLOWED_RECLAIM_POLICIES, "Detach, Fail");
        params.put(RECLAIM_POLICY, "Delete");
        assertFalse(ReclaimPolicy.isPolicyAllowed(params));

        params.put(ALLOWED_RECLAIM_POLICIES, "Detach, Fail");
        params.put(RECLAIM_POLICY, "Delete");
        assertFalse(ReclaimPolicy.isPolicyAllowed(params));
    }

    @Test
    public void createBucketWithDefaultReclaimPolicies() throws Exception {
        setupCreateBucketTest();

        ServiceDefinitionProxy service = bucketServiceFixture();
        service.setServiceSettings(new HashMap<>());

        PlanProxy plan = service.findPlan(BUCKET_PLAN_ID1);
        plan.setServiceSettings(new HashMap<>());

        Map<String, Object> params = new HashMap<>();
        ecs.createBucket(BUCKET_NAME, service, plan, params);
    }

    @Test
    public void createBucketWithAllowedReclaimPolicies() throws Exception {
        setupCreateBucketTest();

        ServiceDefinitionProxy service = bucketServiceFixture();
        service.setServiceSettings(new HashMap<>());

        PlanProxy plan = service.findPlan(BUCKET_PLAN_ID1);
        plan.setServiceSettings(new HashMap<>());

        Map<String, Object> params = new HashMap<>();

        params.put(ALLOWED_RECLAIM_POLICIES, "Delete, Detach");
        params.put(RECLAIM_POLICY, ReclaimPolicy.Delete);
        ecs.createBucket(BUCKET_NAME, service, plan, params);

        params.put(ALLOWED_RECLAIM_POLICIES, "Detach,Fail");
        params.put(RECLAIM_POLICY, ReclaimPolicy.Fail);
        ecs.createBucket(BUCKET_NAME, service, plan, params);

        params.put(ALLOWED_RECLAIM_POLICIES, "Delete, Detach");
        params.put(RECLAIM_POLICY, ReclaimPolicy.Detach);
        ecs.createBucket(BUCKET_NAME, service, plan, params);
    }

    @Test
    public void createBucketWithWrongReclaimPolicies() throws Exception {
        setupCreateBucketTest();

        ServiceDefinitionProxy service = bucketServiceFixture();
        PlanProxy plan = service.findPlan(BUCKET_PLAN_ID1);

        Map<String, Object> params = new HashMap<>();

        try {
            service.getServiceSettings().put(ALLOWED_RECLAIM_POLICIES, "Detach");
            params.put(RECLAIM_POLICY, ReclaimPolicy.Delete);
            ecs.createBucket(BUCKET_NAME, service, plan, params);
            fail("Expected Exception");
        }catch(Exception ignore) {
        }

        try {
            service.getServiceSettings().put(ALLOWED_RECLAIM_POLICIES, "Delete");
            params.put(RECLAIM_POLICY, ReclaimPolicy.Fail);
            ecs.createBucket(BUCKET_NAME, service, plan, params);
            fail("Expected Exception");
        }catch(Exception ignore) {
        }

        try {
            service.getServiceSettings().put(ALLOWED_RECLAIM_POLICIES, "Fail");
            params.put(RECLAIM_POLICY, ReclaimPolicy.Detach);
            ecs.createBucket(BUCKET_NAME, service, plan, params);
            fail("Expected Exception");
        }catch(Exception ignore) {
        }
    }

    @Test
    public void deleteBucketWithNoReclaimPolicy() throws Exception {
        ecs = mock(EcsService.class);
        workflow = new BucketInstanceWorkflow(instanceRepo, ecs);

        ServiceInstance instance = serviceInstanceFixture();
        when(instanceRepo.find(instance.getServiceInstanceId())).thenReturn(instance);

        workflow.delete(instance.getServiceInstanceId());

        verify(ecs, times(1)).deleteBucket(instance.getServiceInstanceId());
        verify(ecs, times(0)).wipeAndDeleteBucket(instance.getServiceInstanceId());
    }

    private void setupInitTest() throws EcsManagementClientException {
        DataServiceReplicationGroup rg = new DataServiceReplicationGroup();
        rg.setName(RG_NAME);
        rg.setId(RG_ID);
        UserSecretKey secretKey = new UserSecretKey();

        secretKey.setSecretKey(TEST);
        PowerMockito.mockStatic(BucketAction.class);
        when(BucketAction.exists(connection, REPO_BUCKET, NAMESPACE))
            .thenReturn(true);

        PowerMockito.mockStatic(ReplicationGroupAction.class);
        when(ReplicationGroupAction.list(connection))
            .thenReturn(Collections.singletonList(rg));

        PowerMockito.mockStatic(ObjectUserAction.class);
        when(ObjectUserAction.exists(connection, REPO_USER, NAMESPACE))
            .thenReturn(true);

        PowerMockito.mockStatic(ObjectUserSecretAction.class);
        when(ObjectUserSecretAction.list(connection, REPO_USER))
            .thenReturn(Collections.singletonList(secretKey));
    }

    private void setupBaseUrlTest(String name, boolean namespaceInHost) throws EcsManagementClientException {
        PowerMockito.mockStatic(BaseUrlAction.class);
        BaseUrl baseUrl = new BaseUrl();
        baseUrl.setId(BASE_URL_ID);
        baseUrl.setName(name);
        when(BaseUrlAction.list(same(connection)))
            .thenReturn(Collections.singletonList(baseUrl));

        BaseUrlInfo baseUrlInfo = new BaseUrlInfo();
        baseUrlInfo.setId(BASE_URL_ID);
        baseUrlInfo.setName(name);
        baseUrlInfo.setNamespaceInHost(namespaceInHost);
        baseUrlInfo.setBaseurl(BASE_URL);
        when(BaseUrlAction.get(connection, BASE_URL_ID))
            .thenReturn(baseUrlInfo);
    }

    private void setupCreateBucketTest() throws Exception {
        PowerMockito.mockStatic(BucketAction.class);
        PowerMockito.doNothing().when(BucketAction.class, CREATE,
            same(connection), any(ObjectBucketCreate.class));
    }
}
