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
import org.apache.xerces.util.DOMUtil;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 4, 2006
 */
public class ReportDescriptor extends Entity
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
        init(props.toArray(new Pair[0]));
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
        for (Map.Entry entry : m.entrySet())
        {
            _props.put((String)entry.getKey(), entry.getValue());
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
                    final List list = new ArrayList<String>();
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
     * Creates an xml representation of this descriptor prior to serialization.
     * Subclasses can override.
     */
    protected Document toXML()
    {
        Document doc;
        try
        {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        }
        catch (ParserConfigurationException e)
        {
            return null;
        }
        doc.appendChild(doc.createElement("ReportDescriptor"));

        Element elRoot = doc.getDocumentElement();
        elRoot.setAttribute(Prop.descriptorType.toString(), getDescriptorType());
        elRoot.setAttribute(Prop.reportName.toString(), getReportName());

        Element props = doc.createElement("Properties");
        elRoot.appendChild(props);

        for (Map.Entry<String, Object> entry : _props.entrySet())
        {
            final Object value = entry.getValue();
            if (value instanceof List)
            {
                for (Object item : ((List)value))
                    createPropNode(entry.getKey(), item.toString(), doc, props);
            }
            else if (value != null)
                createPropNode(entry.getKey(), value.toString(), doc, props);
        }
        return doc;
    }

    private void createPropNode(String name, String value, Document doc, Element parent)
    {
        final Element prop = doc.createElement("Prop");

        prop.setAttribute("name", name);
        if (RReportDescriptor.Prop.script.toString().equals(name))
            prop.appendChild(doc.createCDATASection(value));
        else
            prop.appendChild(doc.createTextNode(value));

        parent.appendChild(prop);
    }

    public String serialize()
    {
        try {
            final Document doc = toXML();
            StringWriter writer = new StringWriter();
            XMLSerializer serializer = new XMLSerializer(writer, new OutputFormat(doc));

            serializer.asDOMSerializer();
            serializer.serialize(doc);

            return writer.toString();
        }
        catch (Exception e)
        {
            _log.error(e);
        }
        return null;
    }

    public static ReportDescriptor createFromXML(String xmlString)
    {
        List<Pair<String,String>> props = createPropsFromXML(xmlString);
        return create(props);
    }

    public static List<Pair<String, String>> createPropsFromXML(String xmlString)
    {
        try
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document doc = db.parse(new ByteArrayInputStream(xmlString.getBytes("UTF-8")));
            if (doc != null)
            {
                Element root = DOMUtil.getFirstChildElement(doc);

                List<Pair<String,String>> props = new ArrayList<Pair<String,String>>();

                Element propsElem = DOMUtil.getFirstChildElement(root);
                if(null == propsElem)
                    return null;

                //need to iterate elements here, not just child nodes, as \r\n get parsed into text nodes
                //and module-based reports will likely have those between property elements
                for (Element propElem = DOMUtil.getFirstChildElement(propsElem); null != propElem; propElem = DOMUtil.getNextSiblingElement(propElem))
                {
                    final String key = propElem.getAttribute("name");
                    String value = "";
                    if (RReportDescriptor.Prop.script.toString().equals(key))
                    {
                        Node cdata = propElem.getFirstChild();
                        if (cdata != null && CDATASection.class.isAssignableFrom(cdata.getClass()))
                        {
                            value = ((CDATASection)cdata).getWholeText();
                        }
                    }
                    else
                        value = DOMUtil.getChildText(propElem);

                    props.add(new Pair(key, value));
                }
                return props;
            }
        }
        catch (Exception e)
        {
            _log.error("An error occurred parsing the report xml", e);
            return null;
        }
        return null;
    }

    public ACL getACL()
    {
        String entityId = getEntityId();
        if (null == entityId)
            return null;

        final Container c = ContainerManager.getForId(getContainerId());
        return org.labkey.api.security.SecurityManager.getACL(c, entityId);
    }

    public void updateACL(ViewContext context, ACL acl)
    {
        final Container c = ContainerManager.getForId(getContainerId());
        org.labkey.api.security.SecurityManager.updateACL(c, getEntityId(), acl);
    }

    public int getPermissions(User u)
    {
        final Container c = ContainerManager.getForId(getContainerId());
        if (!c.hasPermission(u, ACL.PERM_READ))
            return 0;
        ACL acl = getACL();
        if (acl == null || acl.isEmpty())
            return ACL.PERM_READ;
        return acl.getPermissions(u);
    }

    public boolean canRead(User u)
    {
        return (getPermissions(u) & ACL.PERM_READ) != 0;
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
}
