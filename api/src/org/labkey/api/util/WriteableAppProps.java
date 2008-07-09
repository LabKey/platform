/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.util;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.security.User;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Dec 6, 2006
 */
public class WriteableAppProps extends AppProps
{
    public final static String AUDIT_EVENT_TYPE = "AppPropsEvent";
    public final static String AUDIT_PROP_DIFF = "AppPropsDiff";

    private final PropertyManager.PropertyMap _properties;
    private final PropertyManager.PropertyMap _oldProps;

    public WriteableAppProps() throws SQLException
    {
        _properties = PropertyManager.getWritableSiteConfigProperties();
        _oldProps = PropertyManager.getWritableSiteConfigProperties();
    }

    protected Map<String, String> getProperties()
    {
        return _properties;
    }

    public Map<String,String> getOldProperties()
    {
        return _oldProps;
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

    public void setPerlPipelineEnabled(boolean cluster)
    {
        storeBooleanValue(PIPELINE_PERL_CLUSTER_PROP, cluster);
    }

    public void setPipelineToolsDir(String toolsDir)
    {
        storeStringValue(PIPELINE_TOOLS_DIR_PROP, toolsDir);
    }

    /**
     * Sets the database prop.properties for the value of SequestServer
     * @param sequestServer   name of sequest HTTP server.
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

    public static void incrementLookAndFeelRevisionAndSave() throws SQLException
    {
        WriteableAppProps app = AppProps.getWriteableInstance();
        app.incrementLookAndFeelRevision();
        app.save();
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

    public void writeAuditLogEvent(User user, Map<String,String> oldProps)
    {
        String diff = genDiffHtml(oldProps);
        if(null != diff)
        {
            String domainUri = ensureAuditLogDomainAndProps(user);
            AuditLogEvent event = new AuditLogEvent();
            event.setCreatedBy(user.getUserId());
            event.setComment("The site settings were changed (see details).");
            event.setEventType(AUDIT_EVENT_TYPE);

            Map<String,Object> map = new HashMap<String,Object>();
            map.put(AUDIT_PROP_DIFF, diff);

            AuditLogService.get().addEvent(event, map, domainUri);
        }
    }

    public String genDiffHtml(Map<String,String> oldProps)
    {
        //since this is a fixed membership map, we just need to run
        //one of the map's keys and compare values, noting what has changed
        boolean propsChanged = false;
        StringBuilder html = new StringBuilder("<table>");

        for(String key : _properties.keySet())
        {
            if(key.equals("logoRevision"))
                continue;

            if(!(_properties.get(key).equalsIgnoreCase(oldProps.get(key))))
            {
                propsChanged = true;
                html.append("<tr><td class='ms-searchform'>");
                html.append(PageFlowUtil.filter(ColumnInfo.captionFromName(key)));
                html.append("</td><td>");
                html.append(PageFlowUtil.filter(oldProps.get(key)));
                html.append("&nbsp;&raquo;&nbsp;");
                html.append(PageFlowUtil.filter(_properties.get(key)));
                html.append("</td></tr>");
            }
        }

        html.append("</html>");

        return propsChanged ? html.toString() : null;
    }

    protected String ensureAuditLogDomainAndProps(User user)
    {
        AuditLogService.I svc = AuditLogService.get();
        String domainUri = svc.getDomainURI(AUDIT_EVENT_TYPE);
        Container c = ContainerManager.getSharedContainer();

        try
        {
            Domain domain = PropertyService.get().getDomain(c, domainUri);
            //if domain has not yet been created, create it
            if (domain == null)
            {
                domain = PropertyService.get().createDomain(c, domainUri, AUDIT_EVENT_TYPE + "Domain");
                domain.save(user);
                domain = PropertyService.get().getDomain(c, domainUri);
            }

            //if diff property has not yet been created, create it
            if(null == domain.getPropertyByName(AUDIT_PROP_DIFF))
            {
                DomainProperty prop = domain.addProperty();
                prop.setType(PropertyService.get().getType(c, PropertyType.STRING.getXmlName()));
                prop.setName(AUDIT_PROP_DIFF);
                prop.setPropertyURI(AuditLogService.get().getPropertyURI(AUDIT_EVENT_TYPE, AUDIT_PROP_DIFF));
                domain.save(user);
            }
        }
        catch (ChangePropertyDescriptorException e)
        {
            Logger.getLogger(WriteableAppProps.class).error(e);
        }

        return domainUri;
    }
}
