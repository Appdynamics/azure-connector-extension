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
