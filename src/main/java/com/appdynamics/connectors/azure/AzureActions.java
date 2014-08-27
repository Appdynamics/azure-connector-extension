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
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.models.ConfigurationSet;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.DeploymentSlot;
import com.microsoft.windowsazure.management.compute.models.HostedServiceCreateParameters;
import com.microsoft.windowsazure.management.compute.models.OSVirtualHardDisk;
import com.microsoft.windowsazure.management.compute.models.Role;
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.VirtualHardDiskHostCaching;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineRoleType;
import com.microsoft.windowsazure.management.storage.StorageManagementClient;
import com.microsoft.windowsazure.management.storage.StorageManagementService;
import com.microsoft.windowsazure.management.storage.models.StorageAccountCreateParameters;
import com.microsoft.windowsazure.management.storage.models.StorageAccountGetKeysResponse;
import com.microsoft.windowsazure.storage.CloudStorageAccount;
import com.microsoft.windowsazure.storage.blob.CloudBlobClient;
import com.microsoft.windowsazure.storage.blob.CloudBlobContainer;
import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IImage;
import com.singularity.ee.connectors.entity.api.IMachine;
import com.singularity.ee.connectors.entity.api.IMachineDescriptor;
import com.singularity.ee.connectors.entity.api.IProperty;
import com.singularity.ee.connectors.entity.api.MachineState;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.xml.sax.SAXException;

public class AzureActions {

    private static final Logger logger = Logger.getLogger(AzureActions.class.getName());
    private static String storageContainer = "vhd-store";

    public static void createInstance(ComputeManagementClient connector, IComputeCenter computeCenter, IImage image, IMachineDescriptor machineDescriptor, IControllerServices controllerServices) throws ConnectorException, URISyntaxException {

        IProperty[] properties = computeCenter.getProperties();
        String subscriptionId = Utils.getSubscriptionId(properties, controllerServices);
        String keyStoreLocation = Utils.getKeyStoreLocation(properties, computeCenter.getName());
        String keyStorePassword = Utils.getKeyStorePassword(properties, controllerServices);
        String hostedServiceName = Utils.getHostedServiceName(image.getProperties(), controllerServices);
        StorageManagementClient storageManagementClient = createStorageManagementClient(subscriptionId, keyStoreLocation, keyStorePassword);
        try {
            storageManagementClient.getStorageAccountsOperations().get(hostedServiceName);
            logger.log(Level.FINER, "Storage account found, continuing with the VM creation");
        } catch (IOException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e);
        } catch (ServiceException e) {
            if (e.getMessage().contains("ResourceNotFound")) {
                logger.log(Level.FINER, "Storage account not found, creating storage account");
                createStorageAccount(storageManagementClient, image, controllerServices);
            } else {
                logger.log(Level.WARNING, e.getMessage(), e);
                throw new ConnectorException(e);
            }
        } catch (ParserConfigurationException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e);
        } catch (SAXException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e);
        } catch (URISyntaxException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e);
        }

        createAzureInstance(connector, image, machineDescriptor, controllerServices);
    }

    private static void createVirtualMachines(ComputeManagementClient computeClient, String hostedServiceName, String deploymentName, String roleInstanceName, AzureOsImage azureOsImage, String size, String adminUserName, String adminUserPassword) throws URISyntaxException, ConnectorException {
        int random = (int) (Math.random() * 100);
        URI mediaLinkUriValue = new URI("http://" + hostedServiceName + ".blob.core.windows.net/" + storageContainer + "/" + roleInstanceName + random + ".vhd");
        String osVHarddiskName = roleInstanceName + "-oshdname" + random;
        String operatingSystemName = "Windows";

        ArrayList<ConfigurationSet> configList = getConfigurationSets(hostedServiceName, roleInstanceName, azureOsImage, adminUserName, adminUserPassword);

        OSVirtualHardDisk oSVirtualHardDisk = getOsVirtualHardDisk(azureOsImage, mediaLinkUriValue, osVHarddiskName, operatingSystemName);

        VirtualMachineCreateParameters createParameters = new VirtualMachineCreateParameters();
        createParameters.setRoleName(roleInstanceName);
        createParameters.setRoleSize(size);
        createParameters.setProvisionGuestAgent(true);
        createParameters.setConfigurationSets(configList);
        createParameters.setOSVirtualHardDisk(oSVirtualHardDisk);

        try {
            computeClient.getVirtualMachinesOperations().create(hostedServiceName, deploymentName, createParameters);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Unable to create virtual machine", e);
            throw new ConnectorException("Unable to create virtual machine", e);
        } catch (ExecutionException e) {
            logger.log(Level.WARNING, "Unable to create virtual machine", e);
            throw new ConnectorException("Unable to create virtual machine", e);
        } catch (ServiceException e) {
            logger.log(Level.WARNING, "Unable to create virtual machine", e);
            throw new ConnectorException("Unable to create virtual machine", e);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to create virtual machine", e);
            throw new ConnectorException("Unable to create virtual machine", e);
        } catch (ParserConfigurationException e) {
            logger.log(Level.WARNING, "Unable to create virtual machine", e);
            throw new ConnectorException("Unable to create virtual machine", e);
        } catch (SAXException e) {
            logger.log(Level.WARNING, "Unable to create virtual machine", e);
            throw new ConnectorException("Unable to create virtual machine", e);
        } catch (TransformerException e) {
            logger.log(Level.WARNING, "Unable to create virtual machine", e);
            throw new ConnectorException("Unable to create virtual machine", e);
        }
    }

    private static OSVirtualHardDisk getOsVirtualHardDisk(AzureOsImage azureOsImage, URI mediaLinkUriValue, String osVHarddiskName, String operatingSystemName) {
        OSVirtualHardDisk oSVirtualHardDisk = new OSVirtualHardDisk();
        oSVirtualHardDisk.setName(osVHarddiskName);
        oSVirtualHardDisk.setHostCaching(VirtualHardDiskHostCaching.ReadWrite);
        oSVirtualHardDisk.setOperatingSystem(operatingSystemName);
        oSVirtualHardDisk.setMediaLink(mediaLinkUriValue);
        oSVirtualHardDisk.setSourceImageName(azureOsImage.getName());
        return oSVirtualHardDisk;
    }

    private static ArrayList<ConfigurationSet> getConfigurationSets(String hostedServiceName, String roleInstanceName, AzureOsImage azureOsImage, String adminUserName, String adminUserPassword) {
        ArrayList<ConfigurationSet> configList = new ArrayList<ConfigurationSet>();
        ConfigurationSet configSet = new ConfigurationSet();
        configSet.setConfigurationSetType(azureOsImage.getConfigSetType());
        configSet.setComputerName(roleInstanceName);
        configSet.setHostName(hostedServiceName + ".cloudapp.net");
        configSet.setAdminPassword(adminUserPassword);
        configSet.setAdminUserName(adminUserName);
        configSet.setUserName(adminUserName);
        configSet.setUserPassword(adminUserPassword);
        configSet.setEnableAutomaticUpdates(true);
        configList.add(configSet);
        return configList;
    }

    private static void createAzureInstance(ComputeManagementClient computeClient, IImage image, IMachineDescriptor machineDescriptor, IControllerServices controllerServices) throws ConnectorException, URISyntaxException {

        String roleInstanceName = Utils.getRoleInstanceName(machineDescriptor.getProperties(), controllerServices);
        String osImage = Utils.getOsImage(machineDescriptor.getProperties(), controllerServices);

        AzureOsImage azureOsImage = AzureOsImage.getImage(osImage);

        String size = Utils.getSize(machineDescriptor.getProperties(), controllerServices);
        String adminUserName = Utils.getAdminUserName(machineDescriptor.getProperties(), controllerServices);
        String adminUserPassword = Utils.getAdminUserPassword(machineDescriptor.getProperties(), controllerServices);

        String hostedServiceName = Utils.getHostedServiceName(image.getProperties(), controllerServices);

        createHostedService(computeClient, image, controllerServices);

        DeploymentSlot deploymentSlot = Utils.getDeploymentSlot(image.getProperties(), controllerServices);

        String deploymentName = hostedServiceName + "-" + deploymentSlot.name();
        if (!isDeploymentPresent(computeClient, hostedServiceName, deploymentName)) {
            ArrayList<Role> roleList = createRoleList(hostedServiceName, roleInstanceName, azureOsImage, size, adminUserName, adminUserPassword);
            createVMDeployment(computeClient, roleList, deploymentSlot, hostedServiceName);
        } else {
            createVirtualMachines(computeClient, hostedServiceName, deploymentName, roleInstanceName, azureOsImage, size, adminUserName, adminUserPassword);
        }


    }

    private static boolean isDeploymentPresent(ComputeManagementClient computeClient, String hostedServiceName, String deploymentName) throws ConnectorException {
        boolean deploymentPresent = false;
        try {
            computeClient.getDeploymentsOperations().getByName(hostedServiceName, deploymentName);
            deploymentPresent = true;
        } catch (ServiceException e) {
            if (e.getMessage().contains("ResourceNotFound")) {
                deploymentPresent = false;
            } else {
                logger.log(Level.WARNING, "Unable to get VM deployment state", e);
                throw new ConnectorException("Unable to get VM deployment state", e);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to get VM deployment state", e);
            throw new ConnectorException("Unable to get VM deployment state", e);
        }
        return deploymentPresent;
    }

    private static void createStorageAccount(StorageManagementClient storageManagementClient, IImage image, IControllerServices controllerServices) throws ConnectorException {
        String location = Utils.getLocation(image.getProperties(), controllerServices);
        String storageAccountName = Utils.getHostedServiceName(image.getProperties(), controllerServices);

        StorageAccountCreateParameters createParameters = new StorageAccountCreateParameters();
        createParameters.setName(storageAccountName);
        createParameters.setLabel(storageAccountName);
        createParameters.setLocation(location);

        try {
            logger.log(Level.FINER, "Creating storage account");
            storageManagementClient.getStorageAccountsOperations().create(createParameters);

            StorageAccountGetKeysResponse storageAccountGetKeysResponse = storageManagementClient.getStorageAccountsOperations().getKeys(storageAccountName);
            String storageAccountKey = storageAccountGetKeysResponse.getPrimaryKey();
            CloudBlobClient blobClient = createBlobClient(storageAccountName, storageAccountKey);
            CloudBlobContainer container = blobClient.getContainerReference(storageContainer);
            container.createIfNotExists();

            //make sure it created and available, otherwise vm deployment will fail with storage/container still creating
            boolean found = false;
            while (!found) {
                Iterable<CloudBlobContainer> listContainerResult = blobClient.listContainers(storageContainer);
                for (CloudBlobContainer item : listContainerResult) {
                    if (item.getName().contains(storageContainer)) {
                        found = true;
                    }
                }

                if (!found) {
                    Thread.sleep(1000 * 30);
                } else {
                    Thread.sleep(1000 * 120);
                }
            }
            logger.log(Level.FINER, "Created storage account successfully");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to create storage account", e);
            throw new ConnectorException("Unable to create storage account", e);
        }
    }

    private static void createHostedService(ComputeManagementClient connector, IImage image, IControllerServices controllerServices) throws ConnectorException {

        String hostedServiceName = Utils.getHostedServiceName(image.getProperties(), controllerServices);

        try {
            connector.getHostedServicesOperations().get(hostedServiceName);
            logger.log(Level.FINER, "Hosted service found, continuing with the VM creation");
        } catch (ServiceException e) {
            if (e.getMessage().contains("ResourceNotFound")) {
                logger.log(Level.FINER, "Hosted service not found, creating hosted service");
                HostedServiceCreateParameters createParameters = new HostedServiceCreateParameters();
                createParameters.setLabel(hostedServiceName);
                createParameters.setServiceName(hostedServiceName);
                createParameters.setDescription(hostedServiceName);
                String location = Utils.getLocation(image.getProperties(), controllerServices);
                createParameters.setLocation(location);
                try {
                    connector.getHostedServicesOperations().create(createParameters);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Unable to create hosted service", ex);
                    throw new ConnectorException("Unable to create hosted service", ex);
                }
            } else {
                logger.log(Level.WARNING, e.getMessage(), e);
                throw new ConnectorException(e);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e);
        }
    }

    private static ArrayList<Role> createRoleList(String hostedServiceName, String roleInstanceName, AzureOsImage azureOsImage, String size, String adminUserName, String adminUserPassword) throws URISyntaxException {
        int random = (int) (Math.random() * 100);
        ArrayList<Role> roleList = new ArrayList<Role>();
        Role role = new Role();
        URI mediaLinkUriValue = new URI("http://" + hostedServiceName + ".blob.core.windows.net/" + storageContainer + "/" + roleInstanceName + random + ".vhd");
        String osVHarddiskName = roleInstanceName + "-oshdname" + random;
        String operatingSystemName = "Windows";

        ArrayList<ConfigurationSet> configList = getConfigurationSets(hostedServiceName, roleInstanceName, azureOsImage, adminUserName, adminUserPassword);

        OSVirtualHardDisk oSVirtualHardDisk = getOsVirtualHardDisk(azureOsImage, mediaLinkUriValue, osVHarddiskName, operatingSystemName);

        role.setRoleName(roleInstanceName);
        role.setRoleType(VirtualMachineRoleType.PersistentVMRole.toString());
        role.setRoleSize(size);
        role.setProvisionGuestAgent(true);
        role.setConfigurationSets(configList);
        role.setOSVirtualHardDisk(oSVirtualHardDisk);
        roleList.add(role);
        return roleList;
    }

    private static void createVMDeployment(ComputeManagementClient computeClient, ArrayList<Role> roleList, DeploymentSlot deploymentSlot, String hostedServiceName) throws ConnectorException {
        try {
            computeClient.getDeploymentsOperations().getByName(hostedServiceName, hostedServiceName);
            logger.log(Level.FINER, "Deployment found, continuing with the VM creation");
        } catch (ServiceException e) {
            if (e.getMessage().contains("ResourceNotFound")) {
                logger.log(Level.FINER, "Deployment not found, creating deployment");
                VirtualMachineCreateDeploymentParameters deploymentParameters = new VirtualMachineCreateDeploymentParameters();
                deploymentParameters.setDeploymentSlot(deploymentSlot);
                String deploymentName = hostedServiceName + "-" + deploymentSlot.name();
                deploymentParameters.setName(deploymentName);
                deploymentParameters.setLabel(deploymentName);
                deploymentParameters.setRoles(roleList);

                try {
                    computeClient.getVirtualMachinesOperations().createDeployment(hostedServiceName, deploymentParameters);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Unable to create VM deployment", ex);
                    throw new ConnectorException("Unable to create hosted Unable to create VM deployment", ex);
                }
            } else {
                logger.log(Level.WARNING, e.getMessage(), e);
                throw new ConnectorException(e);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e);
        }
    }

    private static CloudBlobClient createBlobClient(String storageAccountName, String storageAccountKey) throws InvalidKeyException, URISyntaxException {
        String storageconnectionstring = "DefaultEndpointsProtocol=http;AccountName=" + storageAccountName + ";AccountKey=" + storageAccountKey;
        CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageconnectionstring);
        return storageAccount.createCloudBlobClient();
    }

    private static StorageManagementClient createStorageManagementClient(String subscriptionId, String keyStoreLocation, String keyStorePassword) throws ConnectorException {
        Configuration config = ConnectorLocator.getInstance().createConfiguration(subscriptionId, keyStoreLocation, keyStorePassword);
        return StorageManagementService.create(config);
    }

    public static void deleteInstance(ComputeManagementClient connector, String hostedServiceName, String deploymentName, String instanceName) throws ConnectorException {
        DeploymentGetResponse deployment = null;
        try {
            deployment = connector.getDeploymentsOperations().getByName(hostedServiceName, deploymentName);
            ArrayList<RoleInstance> roleInstances = deployment.getRoleInstances();
            if (roleInstances != null && roleInstances.size() > 1) {
                logger.log(Level.FINER, "Deleting the machine " + instanceName);
                connector.getVirtualMachinesOperations().delete(hostedServiceName, deploymentName, instanceName, true);
            } else if (roleInstances != null && roleInstances.size() == 1 && roleInstances.get(0).getInstanceName().equals(instanceName)) {
                logger.log(Level.FINER, "Only one instance found on the deployment. Deleting the deployment.");
                connector.getDeploymentsOperations().deleteByName(hostedServiceName, deploymentName, true);
            } else {
                logger.log(Level.FINER, "Instance [" + instanceName + "] not found. Removing it from the controller.");
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to delete VM instance", e);
            throw new ConnectorException("Unable to delete VM instance", e);
        } catch (ServiceException e) {
            logger.log(Level.WARNING, "Unable to delete VM instance", e);
            throw new ConnectorException("Unable to delete VM instance", e);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Unable to delete VM instance", e);
            throw new ConnectorException("Unable to delete VM instance", e);
        } catch (ExecutionException e) {
            logger.log(Level.WARNING, "Unable to delete VM instance", e);
            throw new ConnectorException("Unable to delete VM instance", e);
        } catch (ParserConfigurationException e) {
            logger.log(Level.WARNING, "Unable to delete VM instance", e);
            throw new ConnectorException("Unable to delete VM instance", e);
        } catch (SAXException e) {
            logger.log(Level.WARNING, "Unable to delete VM instance", e);
            throw new ConnectorException("Unable to delete VM instance", e);
        } catch (URISyntaxException e) {
            logger.log(Level.WARNING, "Unable to delete VM instance", e);
            throw new ConnectorException("Unable to delete VM instance", e);
        }
    }

    public static void restartInstance(ComputeManagementClient connector, String hostedServiceName, String deploymentName, String instanceName) throws ConnectorException {
        try {
            connector.getVirtualMachinesOperations().restart(hostedServiceName, deploymentName, instanceName);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to restart VM instance", e);
            throw new ConnectorException("Unable to restart VM instance", e);
        } catch (ServiceException e) {
            logger.log(Level.WARNING, "Unable to restart VM instance", e);
            throw new ConnectorException("Unable to restart VM instance", e);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Unable to restart VM instance", e);
            throw new ConnectorException("Unable to restart VM instance", e);
        } catch (ExecutionException e) {
            logger.log(Level.WARNING, "Unable to restart VM instance", e);
            throw new ConnectorException("Unable to restart VM instance", e);
        }
    }

    public static void validate(ComputeManagementClient connector) throws ConnectorException {
        try {
            connector.getHostedServicesOperations().checkNameAvailability("dummy");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Credentials validation failed", e);
            throw new ConnectorException("Credentials validation failed", e);
        } catch (ServiceException e) {
            logger.log(Level.WARNING, "Credentials validation failed", e);
            throw new ConnectorException("Credentials validation failed", e);
        } catch (ParserConfigurationException e) {
            logger.log(Level.WARNING, "Credentials validation failed", e);
            throw new ConnectorException("Credentials validation failed", e);
        } catch (SAXException e) {
            logger.log(Level.WARNING, "Credentials validation failed", e);
            throw new ConnectorException("Credentials validation failed", e);
        }
    }

    public static void updateMachineState(IMachine machine, ComputeManagementClient connector, String hostedServiceName, String deploymentName, String roleInstanceName) throws ConnectorException {
        DeploymentGetResponse deployment = null;
        try {
            deployment = connector.getDeploymentsOperations().getByName(hostedServiceName, deploymentName);
            ArrayList<RoleInstance> roleInstances = deployment.getRoleInstances();
            boolean instanceFound = false;
            for (RoleInstance roleInstance : roleInstances) {
                if (roleInstanceName.equals(roleInstance.getInstanceName())) {
                    setMachineStatus(machine, roleInstance, hostedServiceName);
                    instanceFound = true;
                }
            }
            if (!instanceFound) {
                machine.setState(MachineState.STOPPED);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Update VM state failed", e);
            throw new ConnectorException("Update VM state failed", e);
        } catch (ServiceException e) {
            if (e.getMessage().contains("ResourceNotFound")) {
                machine.setState(MachineState.STOPPED);
            } else {
                logger.log(Level.WARNING, "Update VM state failed", e);
                throw new ConnectorException("Update VM state failed", e);
            }

        } catch (ParserConfigurationException e) {
            logger.log(Level.WARNING, "Update VM state failed", e);
            throw new ConnectorException("Update VM state failed", e);
        } catch (SAXException e) {
            logger.log(Level.WARNING, "Update VM state failed", e);
            throw new ConnectorException("Update VM state failed", e);
        } catch (URISyntaxException e) {
            logger.log(Level.WARNING, "Update VM state failed", e);
            throw new ConnectorException("Update VM state failed", e);
        }
    }

    private static void setMachineStatus(IMachine machine, RoleInstance roleInstance, String hostedServiceName) {
        if ("ReadyRole".equals(roleInstance.getInstanceStatus())) {
            machine.setState(MachineState.STARTED);
            machine.setIpAddress(hostedServiceName + ".cloudapp.net");
        } else if ("CreatingVM".equals(roleInstance.getInstanceStatus()) ||
                "StartingVM".equals(roleInstance.getInstanceStatus()) ||
                "CreatingRole".equals(roleInstance.getInstanceStatus()) ||
                "StartingRole".equals(roleInstance.getInstanceStatus())) {
            machine.setState(MachineState.STARTING);
        } else if ("StoppingRole".equals(roleInstance.getInstanceStatus()) ||
                "StoppingVM".equals(roleInstance.getInstanceStatus()) ||
                "DeletingVM".equals(roleInstance.getInstanceStatus())) {
            machine.setState(MachineState.STOPPING);
        } else if ("StoppedVM".equals(roleInstance.getInstanceStatus())) {
            machine.setState(MachineState.STOPPED);
        }
    }
}