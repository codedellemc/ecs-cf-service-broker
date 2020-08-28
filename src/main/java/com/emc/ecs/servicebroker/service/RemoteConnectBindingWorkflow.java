package com.emc.ecs.servicebroker.service;

import com.emc.ecs.servicebroker.exception.EcsManagementClientException;
import com.emc.ecs.servicebroker.repository.ServiceInstance;
import com.emc.ecs.servicebroker.repository.ServiceInstanceRepository;
import org.springframework.cloud.servicebroker.exception.ServiceBrokerException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingExistsException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RemoteConnectBindingWorkflow extends BindingWorkflowImpl {
    RemoteConnectBindingWorkflow(ServiceInstanceRepository instanceRepo, EcsService ecs) {
        super(instanceRepo, ecs);
    }

    @Override
    public void checkIfUserExists() throws EcsManagementClientException, IOException {
        ServiceInstance instance = instanceRepository.find(instanceId);
        if (instance == null)
            throw new ServiceInstanceDoesNotExistException(instanceId);
        if (instance.remoteConnectionKeyExists(bindingId))
            throw new ServiceInstanceBindingExistsException(instanceId, bindingId);
    }

    @Override
    public String createBindingUser() throws ServiceBrokerException, IOException, JAXBException {
        ServiceInstance instance = instanceRepository.find(instanceId);
        if (instance == null)
            throw new ServiceInstanceDoesNotExistException(instanceId);

        String secretKey = instance.addRemoteConnectionKey(bindingId);
        instanceRepository.save(instance);
        return secretKey;
    }

    @Override
    public Map<String, Object> getCredentials(String secretKey, Map<String, Object> parameters)
            throws IOException, EcsManagementClientException {
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("accessKey", bindingId);
        credentials.put("secretKey", secretKey);
        credentials.put("instanceId", instanceId);
        return credentials;
    }

    @Override
    public void removeBinding()
            throws EcsManagementClientException, IOException, JAXBException {
        ServiceInstance instance = instanceRepository.find(instanceId);
        if (instance == null)
            throw new ServiceInstanceDoesNotExistException(instanceId);
        instance.removeRemoteConnectionKey(bindingId);
        instanceRepository.save(instance);
    }

}
