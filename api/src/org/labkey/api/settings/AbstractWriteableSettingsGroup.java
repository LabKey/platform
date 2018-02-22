/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider.SiteSettingsAuditEvent;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.Map;

/**
 * Subclass of {@link AbstractSettingsGroup} that allows mutating the property values.
 * User: adam
 * Date: Aug 2, 2008
 */
public abstract class AbstractWriteableSettingsGroup extends AbstractSettingsGroup
{
    private PropertyMap _properties = null;
    private PropertyMap _oldProps = null;

    protected abstract String getType();

    protected void makeWriteable(Container c)
    {
        _properties = getWriteableProperties(c);
        _oldProps = getWriteableProperties(c);
    }

    protected PropertyMap getWriteableProperties(Container c)
    {
        return PropertyManager.getWritableProperties(getPropertyConfigUser(), c, getGroupName(), true);
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
        _properties.save();
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

    // Clear out a single property... should then inherit this value from parent
    public void remove(String name)
    {
        _properties.remove(name);
    }

    public void writeAuditLogEvent(Container c, User user)
    {
        String diff = genDiffHtml(getOldProperties());

        if (null != diff)
        {
            SiteSettingsAuditEvent event = new SiteSettingsAuditEvent(c.getId(), "The " + getType() + " were changed (see details).");

            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setChanges(diff);

            AuditLogService.get().addEvent(user, event);
        }
    }

    protected boolean isPasswordProperty(String propName)
    {
        return false;
    }

    public boolean hasDiffHtml(Map<String,String> oldProps)
    {
        String hasDiff = genDiffHtml(oldProps);
        return hasDiff != null;
    }

    private String genDiffHtml(Map<String,String> oldProps)
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
                if(isPasswordProperty(key))
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

        html.append("</table>");

        return propsChanged ? html.toString() : null;
    }

    private static String obscureValue(String value)
    {
        if(null == value || value.length() == 0)
            return "";
        else
            return "*******"; //use fixed number to obscure num characters
    }
}
