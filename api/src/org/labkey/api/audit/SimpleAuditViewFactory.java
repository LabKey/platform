/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.api.audit;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.ContainerUser;

import javax.mail.MessagingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/**
 * User: Karl Lum
 * Date: Oct 23, 2007
 */
public abstract class SimpleAuditViewFactory implements AuditLogService.AuditViewFactory
{
    private static final Logger _log = Logger.getLogger(SimpleAuditViewFactory.class);

    public static final String OLD_RECORD_PROP_NAME = "oldRecordMap";
    public static final String OLD_RECORD_PROP_CAPTION = "Old Record Map";
    public static final String NEW_RECORD_PROP_NAME = "newRecordMap";
    public static final String NEW_RECORD_PROP_CAPTION = "New Record Map";

    public String getName()
    {
        return getEventType();
    }

    public String getDescription()
    {
        return getName();
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        return Collections.emptyList();
    }

    public void setupTable(FilteredTable table, UserSchema schema)
    {
        // set the filter for the audit view type
        table.addCondition(table.getRealTable().getColumn("EventType"), getEventType());
    }

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
    }

    private static Object _82decodeObject(String s) throws IOException
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return null;

        try
        {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());
            InputStream isBase64 = javax.mail.internet.MimeUtility.decode(byteArrayInputStream, "base64");
            InputStream isCompressed = new InflaterInputStream(isBase64);
            ObjectInputStream ois = new ObjectInputStream(isCompressed);
            return ois.readObject();
        }
        catch (MessagingException x)
        {
            throw new IOException(x.getMessage());
        }
        catch (ClassNotFoundException x)
        {
            throw new IOException(x.getMessage());
        }
    }

    public static Map<String, String> _safeDecodeFromDataMap(String properties)
    {
        try
        {
            // try to filter out non-encoded values ('&' is not in the base64 character set)
            if (properties != null && !properties.contains("&"))
            {
                Object o = _82decodeObject(properties);
                if (Map.class.isAssignableFrom(o.getClass()))
                    return (Map<String, String>) o;
            }
        }
        catch (IOException e)
        {
            _log.debug("unable to decode object : " + properties);
        }
        return Collections.emptyMap();
    }

    public static Map<String, String> decodeFromDataMap(String properties)
    {
        try
        {
            if (properties != null)
            {
                return PageFlowUtil.mapFromQueryString(properties);
            }
            return Collections.emptyMap();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static int MAX_FIELD_SIZE = 4000;

    public static String encodeForDataMap(Map<String, Object> properties)
    {
        if (properties == null) return null;

        Map<String,String> stringMap = new HashMap<>();
        for (Map.Entry<String,Object> entry :  properties.entrySet())
        {
            Object value = entry.getValue();
            stringMap.put(entry.getKey(), value == null ? null : value.toString());
        }
        return encodeForDataMap(stringMap, true);
    }

    // helper to encode map information into a form that can be saved into an ontology column,
    // if validate size is set, the returned String will be guaranteed to fit into the field.
    //
    public static String encodeForDataMap(Map<String, String> properties, boolean validateSize)
    {
        try
        {
            String data = PageFlowUtil.toQueryString(properties.entrySet());
            if (data == null)
                return null;

            int count = 0;

            while (validateSize && data.length() > MAX_FIELD_SIZE)
            {
                _truncateEntry(properties, (data.length() - MAX_FIELD_SIZE));
                data = PageFlowUtil.toQueryString(properties.entrySet());
                if (count++ > 4)
                    break;
            }

            // if the overall size couldn't be reduced by truncating the largest entries, just
            // start reducing the overall size of the map
            if (validateSize && data.length() > MAX_FIELD_SIZE)
            {
                List<Map.Entry<String, String>> newProps = new ArrayList<>();
                newProps.addAll(properties.entrySet());
                int newSize = Math.max(1, newProps.size());

                while (data.length() > MAX_FIELD_SIZE)
                {
                    newSize = Math.max(1, newSize - 10);
                    if (newSize == 1)
                        break;
                    List<Map.Entry<String, String>> a = newProps.subList(0, newSize);
                    data = PageFlowUtil.toQueryString(a);
                }
            }

            return data;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    protected static void _truncateEntry(Map<String, String> properties, int diff)
    {
        diff = diff * 13 / 10;
        diff = Math.max(diff, 200);

        int max = 0;
        String largest = null;

        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            if (entry.getValue() != null && entry.getValue().length() > max)
            {
                max = entry.getValue().length();
                largest = entry.getKey();
            }
        }

        if (largest != null && max > diff)
        {
            String newValue = properties.get(largest).substring(0, max - diff) + "...";
            properties.put(largest, newValue);
        }
        else
            properties.put(largest, "contents too large to display");
    }

    protected boolean ensureProperties(User user, Domain domain, PropertyInfo[] properties) throws ChangePropertyDescriptorException
    {
        if (domain != null)
        {
            boolean dirty = false;

            Map<String, DomainProperty> existingProps = new HashMap<>();

            for (DomainProperty dp : domain.getProperties())
            {
                existingProps.put(dp.getName(), dp);
            }

            for (PropertyInfo pInfo : properties)
            {
                DomainProperty prop = existingProps.remove(pInfo.name);

                if (prop == null)
                {
                    dirty = true;
                    prop = domain.addProperty();
                    prop.setLabel(pInfo.label);
                    prop.setName(pInfo.name);
                    prop.setType(PropertyService.get().getType(domain.getContainer(), pInfo.type.getXmlName()));
                    prop.setPropertyURI(AuditLogService.get().getPropertyURI(getEventType(), pInfo.name));
                }
            }

            // remove orphaned properties
            for (DomainProperty dp : existingProps.values())
            {
                dirty = true;
                OntologyManager.removePropertyDescriptorFromDomain(dp);
            }

            if (dirty)
                domain.save(user);

            return dirty;
        }
        return false;
    }

    public static class PropertyInfo
    {
        private String name;
        private String label;
        private PropertyType type;

        public PropertyInfo(String name, String label, PropertyType type)
        {
            this.name = name;
            this.label = label;
            this.type = type;
        }
    }
}
