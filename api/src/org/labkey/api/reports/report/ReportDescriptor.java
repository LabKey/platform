/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.query.xml.ReportDescriptorDocument;
import org.labkey.query.xml.ReportPropertyList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 4, 2006
 */
public class ReportDescriptor extends Entity implements SecurableResource
{
    private static final Logger _log = Logger.getLogger(ReportDescriptor.class);
    public static final String TYPE = "reportDescriptor";
    public static final int FLAG_INHERITABLE = 0x01;

    private String _reportKey;
    private Integer _owner;
    private int _flags;

    protected Map<String, Object> _props = new LinkedHashMap<String, Object>();

    public enum Prop implements ReportProperty
    {
        descriptorType,
        reportId,
        reportType,
        reportName,
        reportDescription,
        filterParam,
        schemaName,
        queryName,
        viewName,
        dataRegionName,
        redirectUrl,
        cached,
    }

    public ReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    public interface ReportProperty
    {
        // marker
    }

    public void setReportId(ReportIdentifier reportId)
    {
        setProperty(Prop.reportId, reportId.toString());
    }

    public ReportIdentifier getReportId()
    {
        return ReportService.get().getReportIdentifier(getProperty(Prop.reportId));
    }

    public void setReportKey(String key){_reportKey = key;}
    public String getReportKey(){return _reportKey;}

    /**
     * Specify the type of report associated with this descriptor, valid types must
     * be registered/obtained through ReportService.registerReport(String type)
     */
    public void setReportType(String reportType)
    {
        setProperty(Prop.reportType, reportType);
    }

    public String getReportType()
    {
        return getProperty(Prop.reportType);
    }

    public void setReportName(String label)
    {
        setProperty(Prop.reportName, label);
    }

    public String getReportName()
    {
        return getProperty(Prop.reportName);
    }

    public void setReportDescription(String desc)
    {
        setProperty(Prop.reportDescription, desc);
    }

    public String getReportDescription()
    {
        return getProperty(Prop.reportDescription);
    }

    public void setProperty(String key, String value){_props.put(key, value);}
    public void setProperties(List<Pair<String,String>> props)
    {
        init(props.toArray(new Pair[props.size()]));
    }

    public String getProperty(String key){return (String)_props.get(key);}

    public Map<String, Object> getProperties() {return Collections.unmodifiableMap(_props);}

    public void setProperty(ReportProperty prop, String value)
    {
        _props.put(prop.toString(), value);
    }

    public void setProperty(ReportProperty prop, int value)
    {
        _props.put(prop.toString(), String.valueOf(value));
    }

    public void setProperty(ReportProperty prop, boolean value)
    {
        _props.put(prop.toString(), String.valueOf(value));
    }

    public String getProperty(ReportProperty prop)
    {
        Object o = _props.get(prop.toString());
        if (o != null)
        {
            if (o instanceof String)
                return (String)o;
            throw new IllegalStateException("Property value for: " + prop.toString() + " is not a String");
        }
        return null;
    }

    public void setDescriptorType(String type)
    {
        setProperty(Prop.descriptorType, type);
    }

    public String getDescriptorType()
    {
        return getProperty(Prop.descriptorType);
    }

    public Integer getOwner(){return _owner;}

    public void setOwner(Integer owner){_owner = owner;}

    public void initFromQueryString(String queryString)
    {
        init(PageFlowUtil.fromQueryString(queryString));
    }

    public int getFlags()
    {
        return _flags;
    }

    public void setFlags(int flags)
    {
        _flags = flags;
    }

/*
    protected void init(Map<String,String> props)
    {
        _props.putAll(props);
    }
*/

    protected void init(Pair<String, String>[] params)
    {
        Map<String, Object> m = mapFromQueryString(params);

        for (Map.Entry<String,Object> entry : m.entrySet())
        {
            _props.put(entry.getKey(), entry.getValue());
        }
    }

    public String toQueryString()
    {
        final StringBuffer sb = new StringBuffer();
        String strAnd = "";
        for (Map.Entry entry : _props.entrySet())
        {
            sb.append(strAnd);
            if (null == entry.getKey())
                continue;

            Object v = entry.getValue();
            if (v instanceof List)
            {
                String delim = "";
                for (String value : ((List<String>)entry.getValue()))
                {
                    sb.append(delim);
                    encode(sb, (String)entry.getKey(), value);
                    delim = "&";
                }
            }
            else
                encode(sb, (String)entry.getKey(), String.valueOf(v));

            strAnd = "&";
        }
        return sb.toString();
    }

    private void encode(final StringBuffer sb, String key, Object value)
    {
        sb.append(PageFlowUtil.encode(key));
        sb.append('=');
        sb.append(PageFlowUtil.encode(String.valueOf(value)));
    }

    protected static ReportDescriptor create(List<Pair<String,String>> props)
    {
        String type = null;
        for (Pair<String, String> param : props)
        {
            if (Prop.descriptorType.toString().equals(param.getKey()))
            {
                type = param.getValue();
                break;
            }
        }
        ReportDescriptor descriptor = ReportService.get().createDescriptorInstance(type);
        if (descriptor != null)
            descriptor.init(props.toArray(new Pair[0]));

        return descriptor;
    }

    private Map<String, Object> mapFromQueryString(Pair<String, String>[] pairs)
    {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (Pair<String, String> p : pairs)
        {
            if (isArrayType(p.getKey()))
            {
                final Object o = m.get(p.getKey());
                if (o instanceof List)
                {
                    final List l = (List)o;
                    if (!l.contains(p.getValue()))
                        l.add(p.getValue());
                }
                else
                {
                    final List<String> list = new ArrayList<String>();
                    list.add(p.getValue());
                    m.put(p.getKey(), list);
                }
            }
            else if (!StringUtils.isEmpty(p.getValue()))
                m.put(p.getKey(), p.getValue());
        }
        return m;
    }

    public boolean isArrayType(String prop)
    {
        return false;
    }

    /**
     * Builds an XML representation of this descriptor
     * @return
     */
    private ReportDescriptorDocument getDescriptorDocument()
    {
        ReportDescriptorDocument doc = ReportDescriptorDocument.Factory.newInstance();
        ReportDescriptorDocument.ReportDescriptor descriptor = doc.addNewReportDescriptor();

        descriptor.setDescriptorType(getDescriptorType());
        descriptor.setReportName(getReportName());
        descriptor.setReportKey(getReportKey());

        ReportPropertyList props = descriptor.addNewProperties();
        for (Map.Entry<String, Object> entry : _props.entrySet())
        {
            final Object value = entry.getValue();
            if (value instanceof List)
            {
                for (Object item : ((List)value))
                {
                    ReportPropertyList.Prop prop = props.addNewProp();

                    prop.setName(entry.getKey());
                    prop.setStringValue(String.valueOf(item));
                }
            }
            else if (value != null)
            {
                ReportPropertyList.Prop prop = props.addNewProp();

                prop.setName(entry.getKey());
                prop.setStringValue(String.valueOf(value));
            }
        }
        return doc;
    }

    public void serialize(VirtualFile dir, String filename) throws IOException
    {
        ReportDescriptorDocument doc = getDescriptorDocument();
        dir.saveXmlBean(filename, doc);
    }

    public String serialize() throws IOException
    {
        ReportDescriptorDocument doc = getDescriptorDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try
        {
            XmlBeansUtil.validateXmlDocument(doc);
            doc.save(output, XmlBeansUtil.getDefaultSaveOptions());
            return output.toString();
        }
        catch (XmlValidationException e)
        {
            // This is likely a code problem -- propagate it up so we log to mothership
            throw new RuntimeException(e);
        }
        finally
        {
            output.close();
        }
    }

    public static ReportDescriptor createFromXML(String xmlString) throws IOException
    {
        List<Pair<String,String>> props = createPropsFromXML(xmlString);
        return create(props);
    }

    public static ReportDescriptor createFromXML(File file) throws IOException, XmlValidationException
    {
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            options.setLoadSubstituteNamespaces(Collections.singletonMap("", "http://labkey.org/query/xml"));

            ReportDescriptorDocument doc = ReportDescriptorDocument.Factory.parse(file, options);
            XmlBeansUtil.validateXmlDocument(doc);
            ReportDescriptorDocument.ReportDescriptor d = doc.getReportDescriptor();

            ReportDescriptor descriptor = ReportService.get().createDescriptorInstance(d.getDescriptorType());
            if (descriptor != null)
            {
                descriptor.setReportName(d.getReportName());
                descriptor.setReportKey(d.getReportKey());
                List<Pair<String, String>> props = new ArrayList<Pair<String, String>>();

                for (ReportPropertyList.Prop prop : d.getProperties().getPropArray())
                {
                    props.add(new Pair<String, String>(prop.getName(), prop.getStringValue()));
                }

                descriptor.init(props.toArray(new Pair[props.size()]));

                return descriptor;
            }

            return null;
        }
        catch (XmlException e)
        {
            throw new IOException(e.getMessage());
        }
    }

    public static List<Pair<String, String>> createPropsFromXML(String xmlString) throws IOException
    {
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            options.setLoadSubstituteNamespaces(Collections.singletonMap("", "http://labkey.org/query/xml"));

            ReportDescriptorDocument doc = ReportDescriptorDocument.Factory.parse(xmlString, options);
            ReportDescriptorDocument.ReportDescriptor d = doc.getReportDescriptor();

            List<Pair<String, String>> props = new ArrayList<Pair<String, String>>();
            for (ReportPropertyList.Prop prop : d.getProperties().getPropArray())
                props.add(new Pair<String, String>(prop.getName(), prop.getStringValue()));

            return props;
        }
        catch (XmlException e)
        {
            throw new IOException(e.getMessage());
        }
    }

    public void updatePolicy(ViewContext context, MutableSecurityPolicy policy)
    {
        assert policy.getResource() == this;
        SecurityManager.savePolicy(policy);
    }

    public boolean canRead(User u)
    {
        return org.labkey.api.security.SecurityManager.getPolicy(this).hasPermission(u, ReadPermission.class);
    }

    public boolean canEdit(ViewContext context)
    {
        if (isInherited(context.getContainer()))
            return false;
        if (getOwner() != null && !getOwner().equals(context.getUser().getUserId()))
            return false;
        if (context.hasPermission(ACL.PERM_ADMIN))
            return true;
        if (getCreatedBy() != 0)
            return (getCreatedBy() == context.getUser().getUserId());
        return false;
    }

    public boolean isInherited(Container c)
    {
        if (null != getReportId())
        {
            if ((getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0)
            {
                return !c.getId().equals(getContainerId());
            }
        }
        return false;
    }

    @NotNull
    public String getResourceId()
    {
        return getEntityId();
    }

    @NotNull
    public String getResourceName()
    {
        return getReportName();
    }

    @NotNull
    public String getResourceDescription()
    {
        return getReportDescription();
    }

    @NotNull
    public Set<Class<? extends Permission>> getRelevantPermissions()
    {
        return RoleManager.BasicPermissions;
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getCoreModule();
    }

    public SecurableResource getParentResource()
    {
        return getResourceContainer();
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        return Collections.emptyList();
    }

    @NotNull
    public Container getResourceContainer()
    {
        return lookupContainer();
    }

    public boolean mayInheritPolicy()
    {
        return true;
    }
}
