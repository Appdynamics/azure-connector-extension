package com.appdynamics.connectors.azure;

import com.microsoft.windowsazure.management.compute.models.ConfigurationSetTypes;

public enum AzureOsImage {

    Windows_Server_2012_R2_Datacenter("a699494373c04fc0bc8f2bb1389d6106__Windows-Server-2012-R2-201407.01-en.us-127GB.vhd", "Windows Server 2012 R2 Datacenter", "Windows"),
    Windows_Server_2012_Datacenter("a699494373c04fc0bc8f2bb1389d6106__Windows-Server-2012-Datacenter-201407.01-en.us-127GB.vhd", "Windows Server 2012 Datacenter", "Windows"),
    Windows_Server_2008_R2_SP1("a699494373c04fc0bc8f2bb1389d6106__Win2K8R2SP1-Datacenter-201407.01-en.us-127GB.vhd", "Windows Server 2008 R2 SP1", "Windows"),
    SUSE_Linux_Enterprise_Server_11_SP3("b4590d9e3ed742e4a1d46e5424aa335e__SUSE-Linux-Enterprise-Server-11-SP3-v203", "SUSE Linux Enterprise Server 11 SP", "Linux"),
    SUSE_Linux_Enterprise_Server_11_SP3_Premium("b4590d9e3ed742e4a1d46e5424aa335e__SUSE-Linux-Enterprise-Server-11-SP3-Prio-v203", "SUSE Linux Enterprise Server 11 SP3 (Premium Image)", "Linux"),
    SUSE_Linux_Enterprise_Server_11_SP3_SAP_Cloud_Appliance_Library("b4590d9e3ed742e4a1d46e5424aa335e__SUSE-Linux-Enterprise-Server-11-SP3-SAP-CAL-v105", "SUSE Linux Enterprise Server 11 SP3 for SAP Cloud Appliance Library", "Linux"),
    Ubuntu_Server_12_04_LTS("b39f27a8b8c64d52b05eac6a62ebad85__Ubuntu-12_04_4-LTS-amd64-server-20140717-en-us-30GB", "Ubuntu Server 12.04 LTS", "Linux"),
    Ubuntu_Server_14_04_LTS("b39f27a8b8c64d52b05eac6a62ebad85__Ubuntu-14_04-LTS-amd64-server-20140724-en-us-30GB", "Ubuntu Server 14.04 LTS", "Linux"),
    OpenLogic("5112500ae3b842c8b9c604889f8753c3__OpenLogic-CentOS-65-20140606", "OpenLogic", "Linux"),
    Oracle_Linux_6_4_0_0_0("c290a6b031d841e09f2da759bbabe71f__Oracle-Linux-6", "Oracle Linux 6.4.0.0.0", "Linux"),
    DreamFactory_1_6("3422a428aaf14529884165693cbb90d3__DreamFactory_1.6.10-3_-_Ubuntu_14.04", "DreamFactory 1.6", "Linux"),
    eXo_Platform_Express_4("3422a428aaf14529884165693cbb90d3__eXo_Platform_Express_4.0.6-4_-_Ubuntu_14.04", "eXo Platform Express 4", "Linux");

    private String name;
    private String label;
    private String osFamily;
    private String configSetType;

    AzureOsImage(String name, String label, String osFamily) {
        this.name = name;
        this.label = label;
        this.osFamily = osFamily;
        if ("Windows".equals(osFamily)) {
            this.configSetType = ConfigurationSetTypes.WINDOWSPROVISIONINGCONFIGURATION;
        } else {
            this.configSetType = ConfigurationSetTypes.LINUXPROVISIONINGCONFIGURATION;
        }
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getOsFamily() {
        return osFamily;
    }

    public String getConfigSetType() {
        return configSetType;
    }


    public static AzureOsImage getImage(String label) {
        for (AzureOsImage azureOsImage : AzureOsImage.values()) {
            if (azureOsImage.getLabel().equals(label)) {
                return azureOsImage;
            }
        }
        throw new IllegalArgumentException("No OS Image found for Label :[" + label + "]");
    }
}
