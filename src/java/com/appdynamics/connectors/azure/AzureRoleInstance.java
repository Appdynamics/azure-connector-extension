package com.appdynamics.connectors.azure;

import org.soyatec.windowsazure.management.DeploymentSlotType;


public class AzureRoleInstance
{

	private String serviceName;
	private DeploymentSlotType slotType;
	private String roleInstanceName;
	
	public AzureRoleInstance(String serviceName, DeploymentSlotType slotType, String roleInstanceName)
	{
		this.serviceName = serviceName;
		this.slotType = slotType;
		this.roleInstanceName = roleInstanceName;
	}
	
	@Override
	public int hashCode()
	{
		return (serviceName + slotType.toString() + roleInstanceName).hashCode();
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (o instanceof AzureRoleInstance)
		{
			AzureRoleInstance a = (AzureRoleInstance) o;
			
			return serviceName.equalsIgnoreCase(a.serviceName) && slotType == a.slotType 
					&& roleInstanceName.equalsIgnoreCase(a.roleInstanceName);
		}
		
		return false;
	}
	
}
