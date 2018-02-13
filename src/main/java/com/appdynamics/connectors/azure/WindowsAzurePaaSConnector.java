/*
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */
package com.appdynamics.connectors.azure;

import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.models.DeploymentSlot;
import com.singularity.ee.agent.resolver.AgentResolutionEncoder;
import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IConnector;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.api.InvalidObjectException;
import com.singularity.ee.connectors.entity.api.IAccount;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IImage;
import com.singularity.ee.connectors.entity.api.IImageStore;
import com.singularity.ee.connectors.entity.api.IMachine;
import com.singularity.ee.connectors.entity.api.IMachineDescriptor;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_PORT_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.DEFAULT_CONTROLLER_PORT_VALUE;

public class WindowsAzurePaaSConnector implements IConnector {

    private IControllerServices controllerServices;

    private final Logger logger = Logger.getLogger(WindowsAzurePaaSConnector.class.getName());

    @Override
    public IMachine createMachine(IComputeCenter computeCenter, IImage image, IMachineDescriptor machineDescriptor)
            throws InvalidObjectException, ConnectorException {

        ComputeManagementClient connector = null;
        try {
            connector = ConnectorLocator.getInstance().getConnector(computeCenter, controllerServices);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to create connector", e);
            throw new ConnectorException("Unable to create connector" + e);
        }

        //Validate the credentials
        AzureActions.validate(connector);

        try {
            AzureActions.createInstance(connector, computeCenter, image, machineDescriptor, controllerServices);

            String roleInstanceName = Utils.getRoleInstanceName(machineDescriptor.getProperties(), controllerServices);

            AgentResolutionEncoder agentResolutionEncoder = getAgentResolutionEncoder(computeCenter);
            IMachine machine = controllerServices.createMachineInstance(roleInstanceName,
                    agentResolutionEncoder.getUniqueHostIdentifier(), computeCenter, machineDescriptor, image,
                    getAgentPort());

            logger.info("Windows Azure machine instance created successfully on Controller.");
            return machine;
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e.getMessage(), e);
        }
    }

    private AgentResolutionEncoder getAgentResolutionEncoder(IComputeCenter iComputeCenter) throws ConnectorException {
        IAccount account = iComputeCenter.getAccount();

        String controllerHost = null;
        try {
            controllerHost = System.getProperty(CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY, InetAddress
                    .getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new ConnectorException(e);
        }

        int controllerPort = Integer.getInteger(CONTROLLER_SERVICES_PORT_PROPERTY_KEY, DEFAULT_CONTROLLER_PORT_VALUE);
        return new AgentResolutionEncoder(controllerHost, controllerPort,
                account.getName(), "");
    }

    @Override
    public void setControllerServices(IControllerServices controllerServices) {
        this.controllerServices = controllerServices;
    }

    @Override
    public void terminateMachine(IMachine machine) throws InvalidObjectException, ConnectorException {
        IImage image = machine.getImage();
        IMachineDescriptor machineDescriptor = machine.getMachineDescriptor();
        IComputeCenter computeCenter = machine.getComputeCenter();

        String roleInstanceName = Utils.getRoleInstanceName(machineDescriptor.getProperties(), controllerServices);
        String hostedServiceName = Utils.getHostedServiceName(image.getProperties(), controllerServices);

        ComputeManagementClient connector = null;
        try {
            connector = ConnectorLocator.getInstance().getConnector(computeCenter, controllerServices);
        } catch (Exception e) {
            throw new ConnectorException("Unable to create connector" + e);
        }

        DeploymentSlot deploymentSlot = Utils.getDeploymentSlot(image.getProperties(), controllerServices);
        String deploymentName = hostedServiceName + "-" + deploymentSlot.name();
        AzureActions.deleteInstance(connector, hostedServiceName, deploymentName, roleInstanceName);
    }

    @Override
    public void validate(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException {

        // The property file entites are only uploaded once the compute center is registered. The file bytes are not set yet
        // when the Controller tries to validate the imagestore properties before the computeCenter is registered.

        // do nothing
    }

    @Override
    public void validate(IImageStore imageStore) throws InvalidObjectException, ConnectorException {
        // The property file entites are only uploaded once the compute center is registered. The file bytes are not set yet
        // when the Controller tries to validate the imagestore properties before the computeCenter is registered.

        // do nothing
    }

    @Override
    public void refreshMachineState(IMachine machine) throws InvalidObjectException, ConnectorException {

        IImage image = machine.getImage();
        IMachineDescriptor machineDescriptor = machine.getMachineDescriptor();
        IComputeCenter computeCenter = machine.getComputeCenter();

        String roleInstanceName = Utils.getRoleInstanceName(machineDescriptor.getProperties(), controllerServices);
        String hostedServiceName = Utils.getHostedServiceName(image.getProperties(), controllerServices);

        ComputeManagementClient connector = null;
        try {
            connector = ConnectorLocator.getInstance().getConnector(computeCenter, controllerServices);
        } catch (Exception e) {
            throw new ConnectorException("Unable to create connector" + e);
        }

        DeploymentSlot deploymentSlot = Utils.getDeploymentSlot(image.getProperties(), controllerServices);
        String deploymentName = hostedServiceName + "-" + deploymentSlot.name();
        AzureActions.updateMachineState(machine, connector, hostedServiceName, deploymentName, roleInstanceName);
    }

    @Override
    public void restartMachine(IMachine machine) throws ConnectorException {
        IImage image = machine.getImage();
        IMachineDescriptor machineDescriptor = machine.getMachineDescriptor();
        IComputeCenter computeCenter = machine.getComputeCenter();

        String roleInstanceName = Utils.getRoleInstanceName(machineDescriptor.getProperties(), controllerServices);
        String hostedServiceName = Utils.getHostedServiceName(image.getProperties(), controllerServices);

        ComputeManagementClient connector = null;
        try {
            connector = ConnectorLocator.getInstance().getConnector(computeCenter, controllerServices);
        } catch (Exception e) {
            throw new ConnectorException("Unable to create connector" + e);
        }

        DeploymentSlot deploymentSlot = Utils.getDeploymentSlot(image.getProperties(), controllerServices);
        String deploymentName = hostedServiceName + "-" + deploymentSlot.name();

        AzureActions.restartInstance(connector, hostedServiceName, deploymentName, roleInstanceName);
    }

    @Override
    public void validate(IImage image) throws InvalidObjectException, ConnectorException {
    }

    @Override
    public void unconfigure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException {
    }

    @Override
    public void unconfigure(IImageStore imageStore) throws InvalidObjectException, ConnectorException {
    }

    @Override
    public void unconfigure(IImage image) throws InvalidObjectException, ConnectorException {
    }

    @Override
    public void configure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

    @Override
    public void configure(IImageStore imageStore) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

    @Override
    public void configure(IImage image) throws InvalidObjectException, ConnectorException {
        // do onothing
    }

    @Override
    public void deleteImage(IImage image) throws InvalidObjectException, ConnectorException {
    }

    @Override
    public int getAgentPort() {
        return controllerServices.getDefaultAgentPort();
    }

    @Override
    public void refreshImageState(IImage image) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

}
