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

import java.util.HashMap;
import java.util.Map;

import org.soyatec.windowsazure.management.ServiceManagementRest;

import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IProperty;

public class ConnectorLocator
{
	private static final ConnectorLocator INSTANCE = new ConnectorLocator();

	private final Map<String, WindowsAzurePaaSProvider> usernameComputeServiceCtx = new HashMap<String, WindowsAzurePaaSProvider>();

	private final Object connectorLock = new Object();

	private ConnectorLocator()
	{

	}

	public static ConnectorLocator getInstance()
	{
		return INSTANCE;
	}

	public WindowsAzurePaaSProvider getConnector(IComputeCenter computeCenter,IControllerServices controllerServices, String imageName) throws Exception
	{
			
		IProperty[] properties = computeCenter.getProperties();
		String subscriptionId = Utils.getSubscriptionId(properties, controllerServices);
		String keyPass = Utils.getKeyPass(properties, controllerServices);
		String alias = Utils.getAlias(properties, controllerServices);
		
		String strKeyStore = Utils.getKeyStore(properties, controllerServices, imageName); 
		String strTrustStore = Utils.getTrustStore(properties, controllerServices, imageName);
		
		synchronized (connectorLock)
		{			
			WindowsAzurePaaSProvider provider = usernameComputeServiceCtx.get(subscriptionId);

			if (provider == null)
			{
				ServiceManagementRest serviceManagementRest = null;

				
				try
				{										
					serviceManagementRest = new ServiceManagementRest(subscriptionId, strKeyStore, keyPass, strTrustStore, keyPass, alias);
				}
				catch (Exception e)
				{
					// TODO: throw exception
				}
				
				provider = new WindowsAzurePaaSProvider(serviceManagementRest, strKeyStore, strTrustStore, keyPass);
				
				usernameComputeServiceCtx.put(subscriptionId, provider);

			}

			return provider;
		}
	}	
	
}
