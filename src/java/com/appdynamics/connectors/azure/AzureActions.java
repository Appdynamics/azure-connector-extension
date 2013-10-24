package com.appdynamics.connectors.azure;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.soyatec.windowsazure.blob.io.BlobFileStream;
import org.soyatec.windowsazure.management.Deployment;
import org.soyatec.windowsazure.management.DeploymentSlotType;
import org.soyatec.windowsazure.management.ServiceManagementRest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class AzureActions
{

	public static final int MAX_INSTANCE_ALLOWED = 10;

	public static String increaseInstanceCount(String roleName, DeploymentSlotType type, String serviceName,
			ServiceManagementRest serviceManagementRest) throws Exception
	{
		return changeInstanceCount(roleName, type, serviceName, "increase", serviceManagementRest);
	}

	public static String decreaseInstanceCount(String roleName, DeploymentSlotType type, String serviceName,
			ServiceManagementRest serviceManagementRest) throws Exception
	{
		return changeInstanceCount(roleName, type, serviceName, "decrease", serviceManagementRest);
	}

	/**
	 * In azure the role instance number can only be changed by editing the deployment configuration file.
	 * 
	 * @param roleName
	 * @param slotType
	 * @param serviceName
	 * @param action
	 * @param serviceManagementRest
	 * @return
	 * @throws Exception
	 */
	private static String changeInstanceCount(String roleName, DeploymentSlotType slotType, String serviceName,
			String action, ServiceManagementRest serviceManagementRest) throws Exception
	{
		Deployment deployment = serviceManagementRest.getDeployment(serviceName, slotType);

		String xmlConfig = deployment.getConfiguration();

		InputStream inXml = new ByteArrayInputStream(xmlConfig.getBytes("UTF-16"));

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
		Document doc = documentBuilder.parse(inXml);
		doc.getDocumentElement().normalize();

		NodeList list = doc.getElementsByTagName("Role");

		for (int i = 0; i < list.getLength(); i++)
		{
			Node node = list.item(i);

			if (node.getNodeType() == Node.ELEMENT_NODE && roleName.equals(((Element) node).getAttribute("name")))
			{
				Element element = (Element) node;
				String instanceCount = ((Element) element.getElementsByTagName("Instances").item(0)).getAttribute("count");

				int iCount = Integer.parseInt(instanceCount);

				if (action.equals("increase"))
				{
					if (iCount != MAX_INSTANCE_ALLOWED)
					{
						iCount++;
					}
					else
					{
						throw new Exception("The number of instances in Hosted Service:" + serviceName
								+ " Deployment Slot Type:" + slotType.toString() + " Role Name:" + roleName
								+ " has reached the maximum number allowed (" + MAX_INSTANCE_ALLOWED + ")");
					}
				}
				else
				{
					if (iCount > 1)
					{
						iCount--;
					}
					else
					{
						// TODO:check if this will be thrown appropriately..
						throw new Exception("The number of instances in Hosted Service:" + serviceName
								+ " Deployment Slot Type:" + slotType.toString() + " Role Name:" + roleName
								+ " has reached the minimum number allowed (1)");
					}
				}

				((Element) element.getElementsByTagName("Instances").item(0))
						.setAttribute("count", Integer.toString(iCount));

				Transformer transformer = TransformerFactory.newInstance().newTransformer();

				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				StreamResult result = new StreamResult(new StringWriter());

				OutputFormat format = new OutputFormat(doc);
				format.setEncoding("UTF-8");

				FileOutputStream fos = new FileOutputStream("output.xml");
				OutputFormat of = new OutputFormat("XML", "utf-8", true);
				XMLSerializer serializer = new XMLSerializer(fos, of);
				serializer.serialize(doc);
				DOMSource source = new DOMSource(doc);
				transformer.transform(source, result);
				BlobFileStream bi = new BlobFileStream(new File("output.xml"));

				AzureAsyncCallBack callback = new AzureAsyncCallBack();

				serviceManagementRest.changeDeploymentConfiguration(serviceName, slotType, bi, callback);

				return Integer.toString(iCount);
				// return "";

			}
		}

		return "";
	}
}
