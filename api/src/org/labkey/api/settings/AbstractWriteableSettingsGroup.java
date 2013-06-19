/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.api.settings;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Aug 2, 2008
 * Time: 11:36:03 AM
 */
public abstract class AbstractWriteableSettingsGroup extends AbstractSettingsGroup
{
    public final static String AUDIT_EVENT_TYPE = "AppPropsEvent";
    public final static String AUDIT_PROP_DIFF = "AppPropsDiff";

    protected PropertyManager.PropertyMap _properties = null;
    protected PropertyManager.PropertyMap _oldProps = null;

    protected abstract String getType();

    protected void makeWriteable(Container c)
    {
        _properties = getWriteableProperties(c);
        _oldProps = getWriteableProperties(c);
    }

    protected PropertyManager.PropertyMap getWriteableProperties(Container c)
    {
        return PropertyManager.getWritableProperties(SITE_CONFIG_USER, c, getGroupName(), true);
    }

    protected Map<String, String> getProperties()
    {
        return _properties;
    }

    public Map<String, String> getOldProperties()
    {
        return _oldProps;
    }

    protected void save()
    {
        PropertyManager.saveProperties(_properties);
    }

    protected void storeBooleanValue(String name, boolean value)
    {
        storeStringValue(name, value ? "TRUE" : "FALSE");
    }

    protected void storeIntValue(String name, int value)
    {
        storeStringValue(name, Integer.toString(value));
    }

    protected void storeStringValue(String name, @Nullable String value)
    {
        if (value == null)
        {
            value = "";
        }

        _properties.put(name, value);
    }

    public void writeAuditLogEvent(User user, Map<String,String> oldProps)
    {
        String diff = genDiffHtml(oldProps);

        if (null != diff)
        {
            String domainUri = ensureAuditLogDomainAndProps(user);
            AuditLogEvent event = new AuditLogEvent();
            event.setCreatedBy(user);
            event.setContainerId(ContainerManager.getRoot().getId());
            event.setComment("The " + getType() + " were changed (see details).");
            event.setEventType(AUDIT_EVENT_TYPE);

            Map<String,Object> map = new HashMap<>();
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
        String oldValue;
        String newValue;

        for(String key : _properties.keySet())
        {
            if(key.equals(AppPropsImpl.LOOK_AND_FEEL_REVISION))
                continue;

            if(!(_properties.get(key).equalsIgnoreCase(oldProps.get(key))))
            {
                propsChanged = true;
                oldValue = oldProps.get(key);
                newValue = _properties.get(key);

                //obscure password properties
                if(AppPropsImpl.MASCOT_USERPASSWORD_PROP.equals(key) || AppPropsImpl.NETWORK_DRIVE_PASSWORD.equals(key))
                {
                    oldValue = obscureValue(oldValue);
                    newValue = obscureValue(newValue);
                }

                html.append("<tr><td class='labkey-form-label'>");
                html.append(PageFlowUtil.filter(ColumnInfo.labelFromName(key)));
                html.append("</td><td>");
                html.append(PageFlowUtil.filter(oldValue));
                html.append("&nbsp;&raquo;&nbsp;");
                html.append(PageFlowUtil.filter(newValue));
                html.append("</td></tr>");
            }
        }

        html.append("</html>");

        return propsChanged ? html.toString() : null;
    }

    protected static String obscureValue(String value)
    {
        if(null == value || value.length() == 0)
            return "";
        else
            return "*******"; //used fixed number to obscure num characters
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
