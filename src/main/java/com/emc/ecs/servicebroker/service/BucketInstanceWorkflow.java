package com.emc.ecs.servicebroker.service;

import com.emc.ecs.servicebroker.model.PlanProxy;
import com.emc.ecs.servicebroker.model.ServiceDefinitionProxy;
import com.emc.ecs.servicebroker.model.ReclaimPolicy;
import com.emc.ecs.servicebroker.repository.ServiceInstance;
import com.emc.ecs.servicebroker.repository.ServiceInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.servicebroker.exception.ServiceBrokerException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BucketInstanceWorkflow extends InstanceWorkflowImpl {
    private static final Logger logger = LoggerFactory.getLogger(BucketInstanceWorkflow.class);

    BucketInstanceWorkflow(ServiceInstanceRepository repo, EcsService ecs) {
        super(repo, ecs);
    }

    @Override
    public Map<String, Object> changePlan(String instanceId, ServiceDefinitionProxy service, PlanProxy plan, Map<String, Object> parameters) {
        try {
            ServiceInstance instance = instanceRepository.find(instanceId);
            if (instance == null) {
                throw new ServiceInstanceDoesNotExistException(instanceId);
            }

            return ecs.changeBucketPlan(instance.getName(), service, plan, parameters);
        } catch (IOException e) {
            throw new ServiceBrokerException(e);
        }
    }
    @Override
    public CompletableFuture delete(String id) {
        try {
            ServiceInstance instance = instanceRepository.find(id);
            if (instance.getReferences().size() > 1) {
                removeInstanceFromReferences(instance, id);

                return null;
            } else {
                ReclaimPolicy reclaimPolicy = ReclaimPolicy.getReclaimPolicy(instance.getServiceSettings());

                switch(reclaimPolicy) {
                    case Fail:
                        logger.info("Reclaim Policy is {} for bucket {}, attempting to delete bucket", reclaimPolicy, ecs.prefix(instance.getName()));
                        ecs.deleteBucket(instance.getName());
                        return null;
                    case Detach:
                        logger.info("Reclaim Policy is {} for bucket {}, Not Deleting Bucket", reclaimPolicy, ecs.prefix(instance.getName()));
                        return null;
                    case Delete:
                        logger.info("Reclaim Policy is {} for bucket {}, Wiping and Deleting bucket", reclaimPolicy, ecs.prefix(instance.getName()));
                        return ecs.wipeAndDeleteBucket(instance.getName());
                    default:
                        throw new ServiceBrokerException("ReclaimPolicy "+reclaimPolicy+" not supported");
                }
            }
        } catch (IOException e) {
            throw new ServiceBrokerException(e);
        }
    }

    private void removeInstanceFromReferences(ServiceInstance instance, String id) throws IOException {
        for (String refId : instance.getReferences()) {
            if (!refId.equals(id)) {
                ServiceInstance ref = instanceRepository.find(refId);
                Set<String> references = ref.getReferences()
                    .stream()
                    .filter((String i) -> ! i.equals(id))
                    .collect(Collectors.toSet());
                ref.setReferences(references);
                instanceRepository.save(ref);
            }
        }
    }

    @Override
    public ServiceInstance create(String id, ServiceDefinitionProxy service, PlanProxy plan,
                                  Map<String, Object> parameters) {
        ServiceInstance instance = getServiceInstance(parameters);
        Map<String, Object> serviceSettings = ecs.createBucket(id, instance.getName(), service, plan, parameters);

        instance.setServiceSettings(serviceSettings);

        return instance;
    }
}
