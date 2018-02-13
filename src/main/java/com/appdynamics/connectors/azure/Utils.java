/*
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */
package com.appdynamics.connectors.azure;

import com.microsoft.windowsazure.management.compute.models.DeploymentSlot;
import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IFileProperty;
import com.singularity.ee.connectors.entity.api.IProperty;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Utils {
    private static final String HOSTED_SERVICE_NAME = "Hosted Service Name";
    private static final String DEPLOYMENT_SLOT_TYPE = "Deployment Slot Type";

    private static final String SUBSCRIPTION_ID = "Subscription Id";
    private static final String KEY_STORE = "Key Store";
    private static final String KEY_STORE_PASSWORD = "Key Store Password";

    private static final String ROLE_INSTANCE_NAME = "Role Instance Name";
    private static final String OS_IMAGE = "OS Image";
    private static final String SIZE = "size";
    private static final String ADMIN_USER_NAME = "Admin User Name";
    private static final String ADMIN_USER_PASSWORD = "Admin User Password";

    private static final String LOCATION = "Location";

    private static final Logger logger = Logger.getLogger(Utils.class.getName());

    public Utils() {

    }

    public static String getHostedServiceName(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, HOSTED_SERVICE_NAME));
    }

    public static DeploymentSlot getDeploymentSlot(IProperty[] properties, IControllerServices controllerServices) {
        String slotType = getValue(controllerServices.getStringPropertyValueByName(properties, DEPLOYMENT_SLOT_TYPE));
        return DeploymentSlot.valueOf(slotType);
    }

    public static String getRoleInstanceName(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, ROLE_INSTANCE_NAME));
    }

    public static String getOsImage(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, OS_IMAGE));
    }

    public static String getSize(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, SIZE));
    }

    public static String getAdminUserName(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, ADMIN_USER_NAME));
    }

    public static String getAdminUserPassword(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, ADMIN_USER_PASSWORD));
    }

    public static String getSubscriptionId(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, SUBSCRIPTION_ID));
    }

    public static String getLocation(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, LOCATION));
    }

    public static String getKeyStoreLocation(IProperty[] properties, String computeCenterName) throws ConnectorException {
        byte[] jks = null;
        for (IProperty i : properties) {
            if (i.getDefinition().getName().equals(KEY_STORE)) {
                jks = ((IFileProperty) i).getFileBytes();
                break;
            }
        }

        String keyStoreLocation = saveKeyStore(jks, computeCenterName);
        return keyStoreLocation;
    }

    public static String getKeyStorePassword(IProperty[] properties, IControllerServices controllerServices) {
        return getValue(controllerServices.getStringPropertyValueByName(properties, KEY_STORE_PASSWORD));
    }

    //folder gets created in the Glassfish domains/domain1/config folder.
    public static String saveKeyStore(byte[] jksFile, String computeCenterName) throws ConnectorException {

        File computeClodDir = new File("azure" + File.separator + computeCenterName);

        if (!computeClodDir.exists()) {
            computeClodDir.mkdirs();
        }

        String imageFileName = "azure" + File.separator + computeCenterName + File.separator + computeCenterName;

        String jksLoc = imageFileName + ".jks";

        File jksCert = new File(jksLoc);

        if (jksCert.exists()) {
            jksCert.delete();
        }

        FileOutputStream in = null;
        try {
            in = new FileOutputStream(jksCert);
            in.write(jksFile);
            in.flush();
            in.close();
        } catch (FileNotFoundException e) {
            logger.log(Level.WARNING, "Java Key Store file not found", e);
            throw new ConnectorException("Java Key Store file not found", e);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error saving Java Key Store file", e);
            throw new ConnectorException("Error saving Java Key Store file", e);
        }

        return jksCert.getAbsolutePath();
    }

    private static String getValue(String value) {
        return (value == null || value.trim().length() == 0) ? null : value.trim();
    }
}