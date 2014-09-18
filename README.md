Windows Azure Connector Extension
=================================

##Use Case

Elastically grow/shrink instances into cloud/virtualized environments. There are four use cases for the connector. 

First, if the Controller detects that the load on the machine instances hosting an application is too high, the azure-connector-extension may be used to automate creation of new virtual machines to host that application. The end goal is to reduce the load across the application by horizontally scaling up application machine instances.

Second, if the Controller detects that the load on the machine instances hosting an application is below some minimum threshold, the azure-connector-extension may be used to terminate virtual machines running that application. The end goal is to save power/usage costs without sacrificing application performance by horizontally scaling down application machine instances.

Third, if the Controller detects that a machine instance has terminated unexpectedly when the connector refreshes an application machine state, the azure-connector-extension may be used to create a replacement virtual machine to replace the terminated application machine instance. This is known as our failover feature.

Lastly, the azure-connector-extension may be used to stage migration of an application from a physical to virtual infrastructure. Or the azure-connector-extension may be used to add additional virtual capacity to an application to augment a preexisting physical infrastructure hosting the application.   

##Directory Structure

<table><tbody>
<tr>
<th align="left"> File/Folder </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> src </td>
<td class='confluenceTd'> Contains source code to the azure connector extension </td>
</tr>
<tr>
<td class='confluenceTd'> target </td>
<td class='confluenceTd'> Only obtained when using maven. Run 'maven clean install' to get distributable .zip file </td>
</tr>
<tr>
<td class='confluenceTd'> pom.xml </td>
<td class='confluenceTd'> maven script file (required only if changing Java code) </td>
</tr>
</tbody>
</table>

##Generating Required Keys

1. Create a Java keystore file using <br>
keytool -genkeypair -alias appdAlias -keyalg RSA -keystore KeyStore.jks -keysize 2048 -storepass "keypass"
2. Create a Certificate file using keystore. We have to upload this certificate to Azure.<br>
keytool -v -export -file AppDCert.cer -keystore KeyStore.jks -alias appdAlias

<b>Note:</b> This version of the connector is not compatible with the prvious version. If you already have a azure connector in \<controller install dir\>/lib/connectors, dont replace the jars. Keep both the folders or remove the old azure folder.

##Installation

1. Clone the azure-connector-extension from GitHub
2. Run 'maven clean install' from the cloned azure-connector-extension directory
3. Download the file azure-connector_v1.zip located in the 'dist' directory into \<controller install dir\>/lib/connectors
4. Unzip the downloaded file
5. Restart the Controller
6. Go to the controller dashboard on the browser. Under Setup->My Preferences->Advanced Features enable "Show Cloud Auto-Scaling features" if it is not enabled. 
7. On the controller dashboard click "Cloud Auto-Scaling" and configure the compute cloud and the image.

Click Compute Cloud->Register Compute Cloud. Refer to the image below

![alt tag](https://raw.github.com/Appdynamics/azure-connector-extension/master/azure_compute_cloud.png)

Upload .jks file as 'Key Store'

Click Image->Register Image. Refer to the image below

![alt tag](https://raw.github.com/Appdynamics/azure-connector-extension/master/azure_image.png)

To launch an instance click the image created in the above step and click on Launch Instance. Refer to the image below

![alt tag](https://raw.github.com/Appdynamics/azure-connector-extension/master/azure_launch_instance.png)

##Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

##Community

Find out more in the [AppSphere](http://appsphere.appdynamics.com/t5/eXchange/Windows-Azure-Cloud-Connector-Extension/idi-p/5493) community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:help@appdynamics.com).

