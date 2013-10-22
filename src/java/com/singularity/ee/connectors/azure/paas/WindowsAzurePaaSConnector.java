package com.singularity.ee.connectors.azure.paas;

import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_PORT_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.DEFAULT_CONTROLLER_PORT_VALUE;

import java.net.InetAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.soyatec.windowsazure.management.Deployment;
import org.soyatec.windowsazure.management.DeploymentSlotType;
import org.soyatec.windowsazure.management.HostedService;
import org.soyatec.windowsazure.management.InstanceStatus;
import org.soyatec.windowsazure.management.Role;
import org.soyatec.windowsazure.management.RoleInstance;
import org.soyatec.windowsazure.management.ServiceManagementRest;

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
import com.singularity.ee.connectors.entity.api.IProperty;
import com.singularity.ee.connectors.entity.api.MachineState;
import com.singularity.ee.util.clock.ClockUtils;

/**
 * 
 * The WindowsAzurePaaSConnector make use of the Azure4j libray as a api wrapper to perform machine instance orchestration.
 * 
 * Validate ComputeCenter and ImageStore: The property file entites are only uploaded once the compute center is registered.
 * The file bytes are not set yet when the Controller tries to validate the computeCenter properties before the computeCenter
 * is registered;
 * 
 * Machine Termination: Because of Azure's PaaS structure, it doesn't give us much control over it's machine/role instances.
 * We can only increase/decrease the role instance numbers. We cannot terminate specific machines. So if there's machine_2
 * and machine_3, machine_3 will always be terminated before machine_2 by order of number. For terminate machine to work
 * properly the connector need access outside of its domain...
 * 
 * Certificate/PFX file problems. The certificate files are hardcoded and saved onto the harddrive. There might be naming issues
 * problems with this......
 * 
 * 
 * 
 */
public class WindowsAzurePaaSConnector implements IConnector
{

	private IControllerServices controllerServices;

	private static final Object counterLock = new Object();

	private static volatile long counter;

	private final Logger logger = Logger.getLogger(WindowsAzurePaaSConnector.class.getName());

	@Override
	public IMachine createMachine(IComputeCenter computeCenter, IImage image, IMachineDescriptor machineDescriptor)
			throws InvalidObjectException, ConnectorException
	{
		boolean succeeded = false;
		Exception createFailureRootCause = null;

		RoleInstance roleInstance = null;

		IProperty[] macProps = machineDescriptor.getProperties();

		WindowsAzurePaaSProvider connector = null;

		try
		{

			connector = ConnectorLocator.getInstance().getConnector(computeCenter, controllerServices, image.getName());
		}
		catch (Exception e)
		{
			throw new ConnectorException("Unable to validate conenctor properties" + e);
		}

		try
		{
			String controllerHost = System.getProperty(CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY, InetAddress
					.getLocalHost().getHostAddress());

			int controllerPort = Integer.getInteger(CONTROLLER_SERVICES_PORT_PROPERTY_KEY, DEFAULT_CONTROLLER_PORT_VALUE);

			IAccount account = computeCenter.getAccount();
			String accountName = account.getName();
			String accountAccessKey = account.getAccessKey();

			AgentResolutionEncoder agentResolutionEncoder = null;

			try
			{
				agentResolutionEncoder = new AgentResolutionEncoder(controllerHost, controllerPort, accountName,
						accountAccessKey);
			}
			catch (Exception e)
			{
				throw new ConnectorException("Failed to initiate AgentResolutionEncoder");
			}
			roleInstance = incrementInstanceCount(image, macProps, connector);

			IMachine machine = controllerServices.createMachineInstance(roleInstance.getInstanceName(),
					agentResolutionEncoder.getUniqueHostIdentifier(), computeCenter, machineDescriptor, image,
					getAgentPort());

			logger.info("Windows Azure machine instace created successfully on Controller.");
			succeeded = true;
			return machine;
		}
		catch (Exception e)
		{
			createFailureRootCause = e;
			throw new ConnectorException(e.getMessage(), e);
		}
		finally
		{
			if (!succeeded && roleInstance != null)
			{
				try
				{
					String serviceName = Utils.getServiceName(image.getProperties(), controllerServices);
					DeploymentSlotType slotType = Utils.getDeploymentSlot(image.getProperties(), controllerServices);
					String roleName = Utils.getRoleName(image.getProperties(), controllerServices);

					AzureActions.decreaseInstanceCount(roleName, slotType, serviceName, connector.getServiceManagement());
				}
				catch (Exception e)
				{
					throw new ConnectorException("Machine create failed, but terminate failed as well! "
							+ "We have an orphan Azure VM instance with id: " + roleInstance.getInstanceName()
							+ " that must be shut down manually. Root cause for machine " + "create failure is following: ",
							createFailureRootCause);
				}
			}
		}
	}

	private RoleInstance incrementInstanceCount(IImage image, IProperty[] macProps, WindowsAzurePaaSProvider connector)
			throws Exception, ConnectorException
	{
		String serviceName = Utils.getServiceName(image.getProperties(), controllerServices);
		DeploymentSlotType slotType = Utils.getDeploymentSlot(image.getProperties(), controllerServices);
		String roleName = Utils.getRoleName(image.getProperties(), controllerServices);

		try
		{
			ServiceManagementRest serviceManagementRest = connector.getServiceManagement();

			List<HostedService> hostedServices = serviceManagementRest.listHostedServices();

			for (HostedService h : hostedServices)
			{

				if (h.getName().toLowerCase().equals(serviceName.toLowerCase()))
				{
					String instanceName = "";

					Deployment deployment = serviceManagementRest.getDeployment(serviceName, slotType);

					if (deployment == null)
					{
						throw new ConnectorException("The hosted service:" + serviceName + " Deployment Slot Type:"
								+ slotType.toString() + " does not exist");
					}
					else
					{

						List<Role> roles = deployment.getRoles();
						boolean found = false;
						for (Role r : roles)
						{
							if (r.getRoleName().equals(roleName))
							{
								found = true;
								break;
							}
						}

						if (!found)
						{
							throw new ConnectorException("The hosted service:" + serviceName + " Deployment Slot Type:"
									+ slotType.toString() + " Role Name:" + roleName + " does not exist");
						}
						else if (found)
						{
							String count = AzureActions.increaseInstanceCount(roleName, slotType, serviceName,
									serviceManagementRest);

							instanceName = roleName + "_IN_" + (Integer.parseInt(count) - 1);
						}

					}

					logger.info("Windows Azure instance created. Name: " + instanceName);
					connector.addNewInstance(serviceName, slotType, instanceName);
					return new RoleInstance(roleName, instanceName, InstanceStatus.Initializing);
				}
			}

			throw new Exception("Could not find Azure hosted Service:" + serviceName);
		}
		catch (Exception e)
		{
			throw e;
		}
	}

	@Override
	public void setControllerServices(IControllerServices controllerServices)
	{
		this.controllerServices = controllerServices;
	}

	@Override
	public void terminateMachine(IMachine machine) throws InvalidObjectException, ConnectorException
	{
		try
		{

			WindowsAzurePaaSProvider connector = ConnectorLocator.getInstance().getConnector(machine.getComputeCenter(),
					controllerServices, machine.getImage().getName());

			IImage image = machine.getImage();
			String serviceName = Utils.getServiceName(image.getProperties(), controllerServices);
			DeploymentSlotType slotType = Utils.getDeploymentSlot(image.getProperties(), controllerServices);
			String roleName = Utils.getRoleName(image.getProperties(), controllerServices);
			String instanceName = machine.getName();

			List<RoleInstance> roleInstances = connector.getServiceManagement().getDeployment(serviceName, slotType)
					.getRoleInstances();

			for (RoleInstance r : roleInstances)
			{
				if (r.getInstanceName().equalsIgnoreCase(instanceName))
				{
					AzureActions.decreaseInstanceCount(roleName, slotType, serviceName, connector.getServiceManagement());
					connector.updateInstanceTimeStamp(serviceName, slotType, instanceName);
					return;
				}
			}

			// if machine is not found, terminate it right away
			machine.setState(MachineState.STOPPED);

		}
		catch (Exception e)
		{
			throw new ConnectorException("Azure VM Machine terminate failed: " + machine.getName(), e);
		}

	}

	@Override
	public void validate(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
		// The property file entites are only uploaded once the compute center is registered. The file bytes are not set yet
		// when the Controller tries to validate the computeCenter properties before the computeCenter is registered;

		// do nothing
	}

	@Override
	public void validate(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		// The property file entites are only uploaded once the compute center is registered. The file bytes are not set yet
		// when the Controller tries to validate the imagestore properties before the computeCenter is registered.

		// do nothing
	}

	@Override
	public void refreshMachineState(IMachine machine) throws InvalidObjectException, ConnectorException
	{

		String instanceName = machine.getName();
		IImage image = machine.getImage();

		String serviceName = Utils.getServiceName(image.getProperties(), controllerServices);
		DeploymentSlotType slotType = Utils.getDeploymentSlot(image.getProperties(), controllerServices);

		MachineState currentState = machine.getState();

		// during starting and restarting the machine
		WindowsAzurePaaSProvider connector = null;
		try
		{
			connector = ConnectorLocator.getInstance().getConnector(machine.getComputeCenter(), controllerServices,
					image.getName());

			long currentTime = ClockUtils.getCurrentTime();
			long timeStamp = connector.getTimeStamp(serviceName, slotType, instanceName);
			/*
			 * only update the status after ~2:30min after a previous action has been performed. It takes sometime for azure
			 * to update its status. For example if an instance is created, it takes about ~2min for azure to reflect its
			 * existence.
			 */
			if ((currentTime - timeStamp) < 150000.00)
			{
				return;
			}
		}
		catch (Exception e)
		{
			throw new ConnectorException("Unable to validate connector properties for refresh machine", e);
		}

		if (currentState == MachineState.STARTING)
		{
			List<RoleInstance> instances = connector.getServiceManagement().getDeployment(serviceName, slotType)
					.getRoleInstances();

			InstanceStatus status = null;

			for (RoleInstance r : instances)
			{
				if (r.getInstanceName().equalsIgnoreCase(instanceName))
				{
					status = r.getInstanceStatus();
					break;

				}
			}

			if (status == null)
			{
				machine.setState(MachineState.STOPPED);
				return;
			}

			if (status == InstanceStatus.Suspended)
			{
				AzureAsyncCallBack callback = new AzureAsyncCallBack();

				connector.getServiceManagement().rebootRoleInstance(serviceName, slotType, instanceName, callback);
			}
			else if (status == InstanceStatus.Ready)
			{
				try
				{
					String ipAddress = null;
					
					logger.info("Retrieved end point: " + ipAddress);

					if (ipAddress == null || ipAddress.isEmpty() || ipAddress.length() == 0)
					{
						ipAddress = connector.getServiceManagement().getDeployment(serviceName, slotType).getUrl();
					}

					String currentIpAddress = machine.getIpAddress();

					if (ipAddress != null && !currentIpAddress.equals(ipAddress))
					{
						machine.setIpAddress(ipAddress);
					}

					machine.setState(MachineState.STARTED);
				}
				catch (Exception e)
				{
					throw new ConnectorException("Error while retrieving machine instance ip addresses. " + e.getMessage());
				}
			}
		}
		else if (currentState == MachineState.STOPPING)
		{
			List<RoleInstance> instances = null;
			try
			{
				instances = connector.getServiceManagement().getDeployment(serviceName, slotType).getRoleInstances();
			}
			catch (Exception e)
			{
				logger.log(Level.FINE, "Exception occurred while checking machine "
						+ "state on STOPPING instance. Assume instance is STOPPED.", e);
			}

			InstanceStatus status = null;

			for (RoleInstance r : instances)
			{
				if (r.getInstanceName().equalsIgnoreCase(instanceName))
				{
					status = r.getInstanceStatus();
		
					break;

				}
			}

			if (status == null)
			{
				machine.setState(MachineState.STOPPED);
			}
			else if (status == InstanceStatus.Suspended || status == InstanceStatus.Stopped)
			{
				machine.setState(MachineState.STOPPED);
			}
			else if (status == InstanceStatus.Unknown)
			{
				machine.setState(MachineState.STOPPED);
			}
			else if (status == InstanceStatus.Ready)
			{
				machine.setState(MachineState.STARTED);
			}
			else if (status == InstanceStatus.Initializing || status == InstanceStatus.Busy)
			{
				machine.setState(MachineState.STARTING);
			}
		}
		else if (currentState == MachineState.STARTED)
		{
			List<RoleInstance> instances = connector.getServiceManagement().getDeployment(serviceName, slotType)
					.getRoleInstances();

			InstanceStatus status = null;

			for (RoleInstance r : instances)
			{
				if (r.getInstanceName().equalsIgnoreCase(instanceName))
				{
					status = r.getInstanceStatus();
					break;

				}
			}

			if (status == null)
			{
				machine.setState(MachineState.STOPPED);
			}
			else if (status == InstanceStatus.Stopping)
			{
				machine.setState(MachineState.STOPPING);
			}
			else if (status == InstanceStatus.Initializing || status == InstanceStatus.Busy)
			{
				machine.setState(MachineState.STARTING);
			}

		}
	}

	@Override
	public void restartMachine(IMachine machine)
	{
		WindowsAzurePaaSProvider connector = null;

		try
		{
			connector = ConnectorLocator.getInstance().getConnector(machine.getComputeCenter(), controllerServices,
					machine.getImage().getName());
		}
		catch (Exception e)
		{
		}

		IImage image = machine.getImage();
		String serviceName = Utils.getServiceName(image.getProperties(), controllerServices);
		DeploymentSlotType slotType = Utils.getDeploymentSlot(image.getProperties(), controllerServices);
		String roleInstanceName = machine.getName();

		AzureAsyncCallBack callback = new AzureAsyncCallBack();

		connector.getServiceManagement().rebootRoleInstance(serviceName, slotType, roleInstanceName, callback);

		// set machine to starting..
		machine.setState(MachineState.STARTING);

		connector.updateInstanceTimeStamp(serviceName, slotType, roleInstanceName);
	}

	@Override
	public void validate(IImage image) throws InvalidObjectException, ConnectorException
	{
		Utils.createImageFolder(image.getName());
	}

	@Override
	public void unconfigure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
	}

	@Override
	public void unconfigure(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
	}

	@Override
	public void unconfigure(IImage image) throws InvalidObjectException, ConnectorException
	{
	}

	@Override
	public void configure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void configure(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void configure(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do onothing
	}

	@Override
	public void deleteImage(IImage image) throws InvalidObjectException, ConnectorException
	{
	}

	@Override
	public int getAgentPort()
	{
		return controllerServices.getDefaultAgentPort();
	}

	@Override
	public void refreshImageState(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

}
