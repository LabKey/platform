package org.labkey.api.audit;

import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DataView;

import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 23, 2007
 */
public abstract class SimpleAuditViewFactory implements AuditLogService.AuditViewFactory
{
    public String getName()
    {
        return getEventType();
    }

    public String getDescription()
    {
        return null;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        return Collections.emptyList();
    }

    public void setupTable(TableInfo table)
    {
    }

    public void setupView(DataView view)
    {
        
    }

    public static Map<String, String> decodeFromDataMap(String properties)
    {
        try {
            if (properties != null)
            {
                Object o = PageFlowUtil.decodeObject(properties);
                if (Map.class.isAssignableFrom(o.getClass()))
                    return (Map<String, String>)o;
            }
            return Collections.emptyMap();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static int MAX_FIELD_SIZE = 4000;
    // helper to encode map information into a form that can be saved into an ontology column,
    // if validate size is set, the returned String will be guaranteed to fit into the field.
    //
    public static String encodeForDataMap(Map<String, String> properties, boolean validateSize)
    {
        try {
            String data = PageFlowUtil.encodeObject(properties);
            int count = 0;

            while (validateSize && data.length() > MAX_FIELD_SIZE)
            {
                _truncateEntry(properties, (data.length() - MAX_FIELD_SIZE));
                data = PageFlowUtil.encodeObject(properties);
                if (count++ > 4) 
                    break;
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
            if (entry.getValue().length() > max)
            {
                max = entry.getValue().length();
                largest = entry.getKey();
            }
        }

        if (largest != null && max > diff)
        {
            String newValue = properties.get(largest).substring(0, max-diff) + "...";
            properties.put(largest, newValue);
        }
        else
            properties.put(largest, "contents too large to display");
    }

    protected static String _encode(Map<String, String> properties)
    {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            XMLEncoder enc = new XMLEncoder(new BufferedOutputStream(baos));
            enc.writeObject(properties);
            enc.close();

            return baos.toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    protected void ensureProperties(User user, Domain domain, PropertyInfo[] properties) throws Exception
    {
        if (domain != null)
        {
            try {
                Map<String, DomainProperty> existingProps = new HashMap<String, DomainProperty>();
                for (DomainProperty dp : domain.getProperties())
                {
                    existingProps.put(dp.getName(), dp);
                }

                for (PropertyInfo pInfo : properties)
                {
                    DomainProperty prop = existingProps.remove(pInfo.name);
                    if (prop == null)
                    {
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
                    try {
                        OntologyManager.deletePropertyDescriptor(dp.getPropertyDescriptor());
                    }
                    catch (SQLException se)
                    {
                    }
                }
            }
            finally
            {
                domain.save(user);
            }
        }
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
