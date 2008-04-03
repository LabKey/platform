package org.labkey.api.util;

import org.labkey.api.data.PropertyManager;

import java.sql.SQLException;
import java.util.Map;
import java.util.Date;
import java.net.URISyntaxException;

/**
 * User: jeckels
 * Date: Dec 6, 2006
 */
public class WriteableAppProps extends AppProps
{
    private final PropertyManager.PropertyMap _properties;

    public WriteableAppProps() throws SQLException
    {
        _properties = PropertyManager.getWritableSiteConfigProperties();
    }

    protected Map getProperties()
    {
        return _properties;
    }

    public void setAdminOnlyMessage(String adminOnlyMessage)
    {
        storeStringValue(ADMIN_ONLY_MESSAGE, adminOnlyMessage);
    }

    public void setSSLPort(int sslPort)
    {
        storeIntValue(SSL_PORT, sslPort);
    }

    public void setSystemMaintenanceInterval(String systemMaintenanceInterval)
    {
        storeStringValue(SYSTEM_MAINTENANCE_INTERVAL, systemMaintenanceInterval);
    }

    public void setSystemMaintenanceTime(Date time)
    {
        String parsedTime = SystemMaintenance.formatSystemMaintenanceTime(time);
        storeStringValue(SYSTEM_MAINTENANCE_TIME, parsedTime);
    }

    public void setMemoryUsageDumpInterval(int memoryUsageDumpInterval)
    {
        storeIntValue(MEMORY_USAGE_DUMP_INTERVAL, memoryUsageDumpInterval);
    }

    public void setFolderDisplayMode(FolderDisplayMode folderDisplayMode)
    {
        storeStringValue(FOLDER_DISPLAY_MODE, folderDisplayMode.toString());
    }

    public void setNavigationBarWidth(String width)
    {
        storeStringValue(NAVIGATION_BAR_WIDTH, width);
    }

    public void setMascotServer(String mascotServer)
    {
        storeStringValue(MASCOT_SERVER_PROP, mascotServer);
    }

    public void setMascotUserAccount(String mascotUserAccount)
    {
        storeStringValue(MASCOT_USERACCOUNT_PROP, mascotUserAccount);
    }

    public void setMascotUserPassword(String mascotUserPassword)
    {
        storeStringValue(MASCOT_USERPASSWORD_PROP, mascotUserPassword);
    }

    public void setMascotHTTPProxy(String mascotHTTPProxy)
    {
        storeStringValue(MASCOT_HTTPPROXY_PROP, mascotHTTPProxy);
    }

    public void setThemeFont(String themeFont)
    {
        storeStringValue(THEME_FONT_PROP, themeFont);
    }

    public void setExceptionReportingLevel(ExceptionReportingLevel level)
    {
        storeStringValue(EXCEPTION_REPORTING_LEVEL, level.toString());
    }

    public void setUsageReportingLevel(UsageReportingLevel level)
    {
        storeStringValue(USAGE_REPORTING_LEVEL, level.toString());
    }


    private void storeBooleanValue(String name, boolean value)
    {
        storeStringValue(name, value ? "TRUE" : "FALSE");
    }

    private void storeIntValue(String name, int value)
    {
        storeStringValue(name, Integer.toString(value));
    }

    private void storeStringValue(String name, String value)
    {
        if (value == null)
        {
            value = "";
        }

        _properties.put(name, value);
    }

    public void setCompanyName(String companyName)
    {
        storeStringValue(COMPANY_NAME_PROP, companyName);
    }

    public void setDefaultDomain(String defaultDomain)
    {
        storeStringValue(DEFAULT_DOMAIN_PROP, defaultDomain);
    }

    public void setDefaultLsidAuthority(String defaultLsidAuthority)
    {
        storeStringValue(DEFAULT_LSID_AUTHORITY_PROP, defaultLsidAuthority);
    }

    public void setThemeName(String themeName)
    {
        storeStringValue(THEME_NAME_PROP, themeName);
    }

    public void setLDAPDomain(String ldapDomain)
    {
        storeStringValue(LDAP_DOMAIN_PROP, ldapDomain);
    }

    public void setLDAPPrincipalTemplate(String ldapPrincipalTemplate)
    {
        storeStringValue(LDAP_PRINCIPALS_TEMPLATE_PROP, ldapPrincipalTemplate);
    }

    public void setLDAPServers(String ldapServers)
    {
        storeStringValue(LDAP_SERVERS_PROP, ldapServers);
    }
    
    public void setLDAPAuthentication(boolean LDAPAuthentication)
    {
        storeBooleanValue(LDAP_AUTHENTICATION, LDAPAuthentication);
    }

    public void setLogoHref(String logoHref)
    {
        storeStringValue(LOGO_HREF_PROP, logoHref);
    }

    public void setReportAProblemPath(String reportAProblemPath)
    {
        storeStringValue(REPORT_A_PROBLEM_PATH_PROP, reportAProblemPath);
    }

    public void setBaseServerUrl(String baseServerUrl) throws URISyntaxException
    {
        setBaseServerUrlAttributes(baseServerUrl);

        storeStringValue(BASE_SERVER_URL_PROP, baseServerUrl);
    }

    public void setSystemDescription(String systemDescription)
    {
        storeStringValue(SYSTEM_DESCRIPTION_PROP, systemDescription);
    }

    public void setSystemEmailAddresses(String systemEmailAddress)
    {
        storeStringValue(SYSTEM_EMAIL_ADDRESS_PROP, systemEmailAddress);
    }

    public void setSystemShortName(String systemShortName)
    {
        storeStringValue(SYSTEM_SHORT_NAME_PROP, systemShortName);
    }

    public void setPipelineCluster(boolean cluster)
    {
        storeBooleanValue(PIPELINE_CLUSTER_PROP, cluster);
    }

    public void setPipelineToolsDir(String toolsDir)
    {
        storeStringValue(PIPELINE_TOOLS_DIR_PROP, toolsDir);
    }

    /**
     * Sets the database prop.properties for the value of SequestServer
     * @param sequestServer   name of sequest HTTP server.
     * @throws SQLException
     */
    public void setSequestServer(String sequestServer)
    {
        storeStringValue(SEQUEST_SERVER_PROP, sequestServer);
    }

    public void setPipelineFTPHost(String pipelineFTPHost)
    {
        storeStringValue(PIPELINE_FTPHOST_PROP, pipelineFTPHost);
    }

    public void setPipelineFTPPort(String pipelineFTPPort)
    {
        storeStringValue(PIPELINE_FTPPORT_PROP, pipelineFTPPort);
    }

    public void setPipelineFTPSecure(boolean pipelineFTPSecure)
    {
        storeBooleanValue(PIPELINE_FTPSECURE_PROP, pipelineFTPSecure);
    }

    public void setSSLRequired(boolean sslRequired)
    {
        storeBooleanValue(SSL_REQUIRED, sslRequired);
    }

    public void setUserRequestedAdminOnlyMode(boolean adminOnlyMode)
    {
        storeBooleanValue(USER_REQUESTED_ADMIN_ONLY_MODE, adminOnlyMode);
    }

    public void incrementLookAndFeelRevision()
    {
        storeIntValue(LOOK_AND_FEEL_REVISION, getLookAndFeelRevision() + 1);
    }

    public void setNetworkDriveLetter(String letter)
    {
        storeStringValue(NETWORK_DRIVE_LETTER, letter);
    }

    public void setNetworkDrivePath(String path)
    {
        storeStringValue(NETWORK_DRIVE_PATH, path);
    }

    public void setNetworkDriveUser(String user)
    {
        storeStringValue(NETWORK_DRIVE_USER, user);
    }

    public void setNetworkDrivePassword(String password)
    {
        storeStringValue(NETWORK_DRIVE_PASSWORD, password);
    }

    public void save() throws SQLException
    {
        PropertyManager.saveProperties(_properties);
    }

    public void setCaBIGEnabled(boolean enabled)
    {
        storeBooleanValue(CABIG_ENABLED, enabled);
    }

    public void setMicroarrayFeatureExtractionServer(String name)
    {
        storeStringValue(MICROARRAY_FEATURE_EXTRACTION_SERVER_PROP, name);
    }
}
