/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;

public class ModuleQueryMetadataDef
{
    private static final Logger LOG = LogHelper.getLogger(ModuleQueryMetadataDef.class, "Query metadata warnings");

    private String _name;
    private final Path _path;
    private QueryDef.ParsedMetadata _queryMetaData = QueryDef.createParsedMetadata(null);
    private String _description;
    private boolean _hidden = false;

    public ModuleQueryMetadataDef(Resource resource)
    {
        _name = resource.getName();
        _path = resource.getPath();

        if (_name.endsWith(ModuleQueryDef.META_FILE_EXTENSION))
        {
            _name = _name.substring(0, _name.length() - ModuleQueryDef.META_FILE_EXTENSION.length());
        }

        try
        {
            // Check in case the file has disappeared from disk
            if (resource.isFile())
            {
                Document doc = parseFile(resource);
                Node docElem = doc.getDocumentElement();

                // Figure out the root element name, stripping off any namespace prefix
                String rootElementName = docElem.getNodeName();
                if (rootElementName.contains(":"))
                {
                    rootElementName = rootElementName.substring(rootElementName.indexOf(":") + 1);
                }

                // We really expect it to be a query.xsd document, but check if it's a tableInfo.xsd document instead.
                if (rootElementName.equalsIgnoreCase("tables"))
                {
                    // Just apply the tableInfo metadata directly
                    _queryMetaData = QueryDef.createParsedMetadata(PageFlowUtil.convertNodeToXml(docElem));
                }
                else if (rootElementName.equalsIgnoreCase("query"))
                {
                    _name = DOMUtil.getAttributeValue(docElem, "name", _name);
                    _hidden = Boolean.parseBoolean(DOMUtil.getAttributeValue(docElem, "hidden", "false"));

                    //description
                    Node node = DOMUtil.getFirstChildNodeWithName(docElem, "description");
                    if (null != node)
                        _description = DOMUtil.getNodeText(node);

                    node = DOMUtil.getFirstChildNodeWithName(docElem, "metadata");
                    if (null != node)
                    {
                        Node root = DOMUtil.getFirstChildElement(node);
                        if (null != root)
                            _queryMetaData = QueryDef.createParsedMetadata(PageFlowUtil.convertNodeToXml(root));
                    }
                }
                else
                {
                    LOG.warn("Query metadata XML does not have <query> or <tables> as its root element, its contents will be ignored: {}", resource);
                }
            }
            else
            {
                _queryMetaData = QueryDef.createParsedMetadata(null);
            }
        }
        catch (IOException | TransformerException | ParserConfigurationException | SAXException e)
        {
            LOG.warn("Unable to load meta-data from module query file {}", resource, e);
        }
    }

    protected Document parseFile(Resource r) throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilder db = XmlBeansUtil.DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();

        return db.parse(r.getInputStream());
    }

    public String getName()
    {
        return _name;
    }

    public Path getPath()
    {
        return _path;
    }

    public String getQueryMetaData()
    {
        return _queryMetaData.getXml();
    }

    public String getDescription()
    {
        return _description;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public QueryDef toQueryDef(Container container, SchemaKey schemaPath)
    {
        QueryDef ret = new QueryDef();
        ret.setContainer(container.getId());
        ret.setName(getName());
        ret.setDescription(getDescription());
        ret.setSchemaPath(schemaPath);
        ret.setParsedMetadata(_queryMetaData);
        if (isHidden())
            ret.setFlags(QueryManager.FLAG_HIDDEN);

        return ret;
    }
}
