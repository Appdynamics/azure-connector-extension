package com.singularity.ee.connectors.azure.paas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.soyatec.windowsazure.management.DeploymentSlotType;
import org.soyatec.windowsazure.management.ServiceManagementRest;

import com.singularity.ee.util.clock.ClockUtils;

public class WindowsAzurePaaSProvider
{

	private final ServiceManagementRest serviceManagementRest;

	private final String keyStore;
	private final String trustStore;
	private final String keyPass;

	/*
	 * so the timestamps are neccessary because it takes several minutes for azure to update its status. i.e. if an instance
	 * is created, it takes ~2min for the instance to be reflected on the webconsole & rest api. If we attempt to
	 * update/terminate machines during this state, an error will occur
	 */
	private Map<AzureRoleInstance, Long> instanceUpdatedTime = new ConcurrentHashMap<AzureRoleInstance, Long>();

	public WindowsAzurePaaSProvider(ServiceManagementRest serviceManagementRest, String keyStore, String trustStore,
			String keyPass)
	{
		this.keyPass = keyPass;
		this.keyStore = keyStore;
		this.trustStore = trustStore;
		this.serviceManagementRest = serviceManagementRest;
	}

	public ServiceManagementRest getServiceManagement()
	{
		return serviceManagementRest;
	}

	public long getTimeStamp(String serviceName, DeploymentSlotType slotType, String roleInstanceName)
	{
		AzureRoleInstance key = new AzureRoleInstance(serviceName, slotType, roleInstanceName);

		if (instanceUpdatedTime.containsKey(key))
		{
			return instanceUpdatedTime.get(key);
		}

		// the time stamp is refreshed everytime the program restarts. so there maybe needs to reenter the timestamp data.
		long currentTime = ClockUtils.getCurrentTime();
		instanceUpdatedTime.put(key, currentTime);
		return 0;
	}

	public void updateInstanceTimeStamp(String serviceName, DeploymentSlotType slotType, String roleInstanceName)
	{
		AzureRoleInstance key = new AzureRoleInstance(serviceName, slotType, roleInstanceName);

		instanceUpdatedTime.put(key, ClockUtils.getCurrentTime());
	}

	public void addNewInstance(String serviceName, DeploymentSlotType slotType, String roleInstanceName)
	{
		AzureRoleInstance key = new AzureRoleInstance(serviceName, slotType, roleInstanceName);

		instanceUpdatedTime.put(key, ClockUtils.getCurrentTime());
	}

	public void deleteRoleInstance(String serviceName, DeploymentSlotType slotType, String roleInstanceName)
	{
		AzureRoleInstance key = new AzureRoleInstance(serviceName, slotType, roleInstanceName);

		instanceUpdatedTime.remove(key);
	}

}
