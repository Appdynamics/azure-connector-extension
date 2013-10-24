openstack-connector-extension
===========================

##Use Case

##Directory Structure

<table><tbody>
<tr>
<th align="left"> File/Folder </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> lib </td>
<td class='confluenceTd'> Contains third-party project references </td>
</tr>
<tr>
<td class='confluenceTd'> src </td>
<td class='confluenceTd'> Contains source code to the openstack connector extension </td>
</tr>
<tr>
<td class='confluenceTd'> dist </td>
<td class='confluenceTd'> Only obtained when using ant. Run 'ant build' to get binaries. Run 'ant package' to get the distributable .zip file </td>
</tr>
<tr>
<td class='confluenceTd'> build.xml </td>
<td class='confluenceTd'> Ant build script to package the project (required only if changing Java code) </td>
</tr>
</tbody>
</table>

##Installation

1. Clone the openstack-connector-extension from GitHub
2. Run 'ant package' from the cloned openstack-connector-extension directory
3. Download the file openstack-connector.zip located in the 'dist' directory into \<controller install dir\>/lib/connectors
4. Unzip the downloaded file
5. Restart the Controller
6. Go to the controller dashboard on the browser. Under Setup->My Preferences->Advanced Features enable "Show Cloud Auto-Scaling features" if it is not enabled. 
7. On the controller dashboard click "Cloud Auto-Scaling" and configure the compute cloud and the image.

Click Compute Cloud->Register Compute Cloud. Refer to the image below

![alt tag](https://raw.github.com/Appdynamics/openstack-connector-extension/master/OpenStack%20Compute%20Fields.png?login=rvasanda&token=7f509b16f54234b256e7a4b9a5fbaf6b)

Click Image->Register Image. Refer to the image below

![alt tag](https://raw.github.com/Appdynamics/openstack-connector-extension/master/OpenStack%20Compute%20Image.png?login=rvasanda&token=115e990ccc6ba6ae95c294affc71fe51)

##Contributing

Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/openstack-connector-extension).

##Community

Find out more in the [AppSphere] community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).

