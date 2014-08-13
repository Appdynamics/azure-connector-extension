/**
 * Copyright 2013 AppDynamics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.appdynamics.connectors.azure;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.DefaultBuilder;
import com.microsoft.windowsazure.core.utils.KeyStoreType;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementService;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;
import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IProperty;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectorLocator {

    private static final Logger logger = Logger.getLogger(ConnectorLocator.class.getName());

    private static final ConnectorLocator INSTANCE = new ConnectorLocator();

    private final Map<String, ComputeManagementClient> subscriptionIdVsComputeClient = new HashMap<String, ComputeManagementClient>();

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private ConnectorLocator() {

    }

    public static ConnectorLocator getInstance() {
        return INSTANCE;
    }

    public ComputeManagementClient getConnector(IComputeCenter computeCenter, IControllerServices controllerServices) throws Exception {

        String computeCenterName = computeCenter.getName();
        IProperty[] properties = computeCenter.getProperties();
        String subscriptionId = Utils.getSubscriptionId(properties, controllerServices);
        String keyStoreLocation = Utils.getKeyStoreLocation(properties, computeCenterName);
        String keyStorePassword = Utils.getKeyStorePassword(properties, controllerServices);

        ComputeManagementClient computeManagementClient = getComputeManagementClient(subscriptionId);

        if (computeManagementClient == null) {
            computeManagementClient = setComputeManagementClient(subscriptionId, keyStoreLocation, keyStorePassword);
        }
        return computeManagementClient;
    }

    private ComputeManagementClient setComputeManagementClient(String subscriptionId, String keyStoreLocation, String keyStorePassword) throws ConnectorException {
        rwLock.writeLock().lock();
        try {
            ComputeManagementClient computeManagementClient = createComputeManagementClient(subscriptionId, keyStoreLocation, keyStorePassword);
            subscriptionIdVsComputeClient.put(subscriptionId, computeManagementClient);
            return computeManagementClient;
        } finally {
            rwLock.writeLock().unlock();
        }

    }

    private ComputeManagementClient createComputeManagementClient(String subscriptionId, String keyStoreLocation, String keyStorePassword) throws ConnectorException {
        Configuration config = createConfiguration(subscriptionId, keyStoreLocation, keyStorePassword);
        return ComputeManagementService.create(config);
    }

    public Configuration createConfiguration(String subscriptionId, String keyStoreLocation, String keyStorePassword) throws ConnectorException {
        try {
            DefaultBuilder builder = new DefaultBuilder();
            //As Controller is unable to load service classes, loading them manually
            new com.microsoft.windowsazure.core.pipeline.apache.Exports().register(builder);
            new com.microsoft.windowsazure.core.pipeline.jersey.Exports().register(builder);
            new com.microsoft.windowsazure.core.utils.Exports().register(builder);
            new com.microsoft.windowsazure.credentials.Exports().register(builder);
            new com.microsoft.windowsazure.management.configuration.Exports().register(builder);
            new com.microsoft.windowsazure.management.Exports().register(builder);
            new com.microsoft.windowsazure.management.compute.Exports().register(builder);
            new com.microsoft.windowsazure.management.storage.Exports().register(builder);

            Configuration configuration = ManagementConfiguration.configure(null, new Configuration(builder), null, subscriptionId, keyStoreLocation, keyStorePassword, KeyStoreType.jks);
            return configuration;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to create Azure Management Configuration", e);
            throw new ConnectorException("Unable to create Azure Management Configuration", e);
        }
    }

    private ComputeManagementClient getComputeManagementClient(String subscriptionId) {
        rwLock.readLock().lock();
        try {
            return subscriptionIdVsComputeClient.get(subscriptionId);
        } finally {
            rwLock.readLock().unlock();
        }
    }
}