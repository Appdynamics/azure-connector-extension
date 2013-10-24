package com.appdynamics.connectors;

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
