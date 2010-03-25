/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.query;

import org.apache.commons.io.IOUtils;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.ResourceRef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.data.Container;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

/*
* User: Dave
* Date: Jan 16, 2009
* Time: 11:44:38 AM
*/

/**
 * Bean that represents a query definition that is defined in file(s) in a module.
 * This is separate from ModuleCustomQueryDefinition so that it can be cached and
 * used for multiple containers.
 */
public class ModuleQueryDef extends ResourceRef
{
    public static final String FILE_EXTENSION = ".sql";
    public static final String META_FILE_EXTENSION = ".query.xml";

    private String _name;
    private String _schemaName;
    private boolean _hidden = false;
    private String _sql;
    private String _queryMetaData;
    private String _description;
    private double _schemaVersion;

    public ModuleQueryDef(Resource r, String schemaName)
    {
        super(r);

        _schemaName = schemaName;
        _name = getNameFromFile();

        //load the sql from the sqlFile
        try
        {
            _sql = IOUtils.toString(r.getInputStream());
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }

        //meta-data file is optional
        Resource parent = r.parent();
        if (parent != null)
        {
            Resource metadataResource = parent.find(_name + META_FILE_EXTENSION);
            if (metadataResource != null)
            {
                ResourceRef metadataRef = new ResourceRef(metadataResource);
                addDependency(metadataRef);
                try
                {
                    loadMetadata(parseFile(metadataResource));
                }
                catch (Exception e)
                {
                    _log.warn("Unable to load meta-data from module query file " + metadataResource.getPath(), e);
                }
            }
        }
    }

    protected String getNameFromFile()
    {
        String name = getResource().getName();
        return name.substring(0, name.length() - FILE_EXTENSION.length());
    }

    protected Document parseFile(Resource r) throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        DocumentBuilder db = dbf.newDocumentBuilder();

        return db.parse(r.getInputStream());
    }

    protected void loadMetadata(Document doc) throws TransformerException, IOException
    {
        Node docElem = doc.getDocumentElement();

        if (!docElem.getNodeName().equalsIgnoreCase("query"))
            return;

        _hidden = Boolean.parseBoolean(DOMUtil.getAttributeValue(docElem, "hidden", "false"));
        _schemaVersion = Double.parseDouble(DOMUtil.getAttributeValue(docElem, "schemaVersion", "0"));

        //description
        Node node = DOMUtil.getFirstChildNodeWithName(docElem, "description");
        if (null != node)
            _description = DOMUtil.getNodeText(node);

        node = DOMUtil.getFirstChildNodeWithName(docElem, "metadata");
        if (null != node)
        {
            Node root = DOMUtil.getFirstChildElement(node);
            if (null != root)
                _queryMetaData = PageFlowUtil.convertNodeToXml(root);
        }
    }

    public String getName()
    {
        return _name;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public String getSql()
    {
        return _sql;
    }

    public String getQueryMetaData()
    {
        return _queryMetaData;
    }

    public String getDescription()
    {
        return _description;
    }

    public double getSchemaVersion()
    {
        return _schemaVersion;
    }

    public QueryDef toQueryDef(Container container)
    {
        QueryDef ret = new QueryDef();
        ret.setContainer(container.getId());
        ret.setName(getName());
        ret.setSchema(getSchemaName());
        ret.setSql(getSql());
        ret.setDescription(getDescription());
        ret.setSchemaVersion(getSchemaVersion());
        ret.setMetaData(getQueryMetaData());
        if(isHidden())
            ret.setFlags(QueryManager.FLAG_HIDDEN);

        return ret;
    }
}