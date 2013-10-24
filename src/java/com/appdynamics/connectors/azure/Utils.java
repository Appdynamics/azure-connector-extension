package com.appdynamics.connectors.azure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.soyatec.windowsazure.management.DeploymentSlotType;

import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IFileProperty;
import com.singularity.ee.connectors.entity.api.IProperty;
import com.singularity.ee.connectors.entity.api.PropertyType;

public class Utils
{
	private static final String HOSTED_SERVICE_NAME = "Hosted Service Name";
	private static final String DEPLOYMENT_SLOT_TYPE = "Deployment Slot Type";
	private static final String ROLE_NAME = "Role Name";
	private static final String SERVICE_TYPE = "Service Type";

	private static final String SUBSCRIPTION_ID = "Subscription Id";
	private static final String KEY_PASS = "Key Pass";
	private static final String CERTIFICATE_ALIAS = "Certificate Alias";

	public Utils()
	{
		
	}

	public static void main(String[] str) throws Exception
	{
		File pfxCert = new File("test.txt");

		if (pfxCert.exists())
		{
			pfxCert.delete();
		}

		FileOutputStream in = new FileOutputStream(pfxCert);

		in.write("dsafsd".getBytes());
		in.flush();
		in.close();
		
	}
	
	public static String getServiceName(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, HOSTED_SERVICE_NAME));
	}

	public static DeploymentSlotType getDeploymentSlot(IProperty[] properties, IControllerServices controllerServices)
	{
		String slotType = getValue(controllerServices.getStringPropertyValueByName(properties, DEPLOYMENT_SLOT_TYPE));

		return DeploymentSlotType.valueOf(slotType);
	}

	public static String getRoleName(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, ROLE_NAME));
	}

	public static String getServiceType(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, SERVICE_TYPE));
	}

	public static String getSubscriptionId(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, SUBSCRIPTION_ID));
	}

	public static byte[] getPfx(IProperty[] properties, IControllerServices controllerServices)
	{
		byte[] pfx = null;
		for (IProperty i : properties)
		{
			if (i.getDefinition().getName().equals("Personal Information Exchange Certificate"))
			{
				pfx = ((IFileProperty) i).getFileBytes();
				break;
			}
		}
		return pfx;
	}

	public static byte[] getCertificate(IProperty[] properties, IControllerServices controllerServices)
	{
		byte[] cer = null;
		for (IProperty i : properties)
		{
			if (i.getDefinition().getType() == PropertyType.FILE)
			{
				if (i.getDefinition().getName().equals("Azure Certificate"))
				{
					cer = ((IFileProperty) i).getFileBytes();
					break;
				}
			}
		}
		return cer;
	}

	public static String getKeyPass(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, KEY_PASS));
	}

	public static String getKeyStore(IProperty[] properties, IControllerServices controllerServices, String imageName)
			throws Exception
	{

		String keyStoreLoc = imageName + File.separator + imageName + ".keystore";
		File keyStore = new File(keyStoreLoc);

		if (keyStore.exists())
		{
			return keyStoreLoc;
		}

		return convertPfxToKeyStore(Utils.getPfx(properties, controllerServices),
				Utils.getKeyPass(properties, controllerServices), Utils.getAlias(properties, controllerServices), imageName);
	}

	public static String getTrustStore(IProperty[] properties, IControllerServices controllerServices, String imageName)
			throws Exception
	{
		String trustStoreLoc = imageName + File.separator + imageName + ".trustcacerts";
		File trustStore = new File(trustStoreLoc);

		if (trustStore.exists())
		{
			return trustStoreLoc;
		}

		return convertCertificateToTrustCACert(Utils.getCertificate(properties, controllerServices),
				Utils.getKeyPass(properties, controllerServices), Utils.getAlias(properties, controllerServices), imageName);
	}

	public static String getAlias(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, CERTIFICATE_ALIAS));
	}

	//folder gets created in the Glassfish domains/domain1/config folder.
	public static void createImageFolder(String imageName) throws ConnectorException
	{
		try
		{
			File imageDir = new File( imageName);
			if (imageDir.exists())
			{
				imageDir.delete();
			}

			imageDir.mkdir();
		}
		catch (Exception e)
		{
			throw new ConnectorException("Error trying to create certificate folder for " + imageName + e, e);
		}
	}

	public static void deleteImageFiles(String imageName)
	{
		File dir = new File( imageName);

		if (dir.exists())
		{
			dir.delete();
		}

	}

	public static String convertPfxToKeyStore(byte[] pfxFile, String keyPass, String alias, String imageName)
			throws Exception
	{

		File imageDir = new File( imageName);
		
		if (!imageDir.exists())
		{
			imageDir.mkdir();
		}


		String imageFileName =  imageName + File.separator + imageName;
		
		String pfxLoc = imageFileName + ".pfx";

		File pfxCert = new File(pfxLoc);

		if (pfxCert.exists())
		{
			pfxCert.delete();
		}

		FileOutputStream in = new FileOutputStream(pfxCert);

		in.write(pfxFile);
		in.flush();
		in.close();

		String keystoreFile = imageFileName + ".keystore";

		File fileOut = new File(keystoreFile);

		if (fileOut.exists())
		{
			fileOut.delete();
		}

		if (!pfxCert.canRead())
		{
			throw new Exception("Unable to access input keystore: " + pfxCert.getPath());
		}
		if (fileOut.exists() && !fileOut.canWrite())
		{
			throw new Exception("Output file is not writable: " + fileOut.getPath());
		}
		KeyStore kspkcs12 = KeyStore.getInstance("pkcs12");
		KeyStore ksjks = KeyStore.getInstance("jks");

		char[] inphrase = keyPass.toCharArray();
		char[] outphrase = keyPass.toCharArray();

		kspkcs12.load(new FileInputStream(pfxCert), inphrase);
		ksjks.load((fileOut.exists()) ? new FileInputStream(fileOut) : null, outphrase);

		Enumeration eAliases = kspkcs12.aliases();

		int n = 0;
		List<String> list = new ArrayList<String>();
		if (!eAliases.hasMoreElements())
		{
			throw new Exception("Certificate is not valid. It does not contain any alias.");
		}
		while (eAliases.hasMoreElements())
		{
			String strAlias = (String) eAliases.nextElement();
			if (kspkcs12.isKeyEntry(strAlias))
			{
				Key key = (Key) kspkcs12.getKey(strAlias, inphrase);
				java.security.cert.Certificate[] chain = kspkcs12.getCertificateChain(strAlias);

				if (alias != null)
					strAlias = alias;

				ksjks.setKeyEntry(strAlias, key, outphrase, chain);
				list.add(strAlias);
			}
		}

		OutputStream out = new FileOutputStream(fileOut);
		ksjks.store(out, outphrase);
		out.close();

		return keystoreFile;
	}

	public static String convertCertificateToTrustCACert(byte[] pfxFile, String storePass, String alias, String imageName)
			throws Exception
	{
		try
		{
			File imageDir = new File( imageName);
			
			if (!imageDir.exists())
			{
				imageDir.mkdir();
			}
			
			String imageFileName = imageName + File.separator + imageName;
			String pfxLoc = imageFileName + ".cer";

			File pfxCert = new File(pfxLoc);

			if (pfxCert.exists())
			{
				pfxCert.delete();
			}

			FileOutputStream in = new FileOutputStream(pfxCert);

			in.write(pfxFile);
			in.flush();
			in.close();

			String fileOutLoc = pfxLoc + ".trustcacerts";

			File fileOut = new File(fileOutLoc);

			if (fileOut.exists())
			{
				fileOut.delete();
			}

			String cmd = "keytool -import -trustcacerts -noprompt -keystore " + fileOut + " -storepass " + storePass
					+ " -alias " + alias + " -file " + pfxLoc;

			Runtime rt = Runtime.getRuntime();
			Process pr = rt.exec(cmd);

			int exitVal = pr.waitFor();

			return fileOutLoc;
		}
		catch (Exception e)
		{
			throw new Exception(e.getMessage());
		}

	}

	public static KeyManager getKeyManager(String certificate, String password) throws Exception
	{
		try
		{
			File keyFile = new File(certificate);
			char[] pass = password.toCharArray();

			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(new FileInputStream(keyFile), pass);

			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
			keyManagerFactory.init(ks, pass);

			KeyManager[] keyManager = keyManagerFactory.getKeyManagers();

			for (KeyManager k : keyManager)
			{
				if (k instanceof X509KeyManager)
				{
					return k;
				}
			}

			throw new Exception("Could not initialize X509 Key");
		}
		catch (Exception e)
		{
			throw new Exception("Could not initialize X509 Key");
		}

	}

	private static String getValue(String value)
	{
		return (value == null || value.trim().length() == 0) ? null : value.trim();
	}
}
