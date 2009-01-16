/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.module.ModuleFileResource;
import org.labkey.api.data.Container;
import org.w3c.dom.Node;
import org.w3c.dom.Document;

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
public class ModuleQueryDef extends ModuleFileResource
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

    public ModuleQueryDef(File sqlFile, String schemaName)
    {
        super(sqlFile);

        _schemaName = schemaName;
        _name = getNameFromFile(sqlFile);

        //load the sql from the sqlFile
        try
        {
            _sql = getFileContents();
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }

        //meta-data file is optional
        File metaFile = new File(sqlFile.getParentFile(), _name + META_FILE_EXTENSION);
        addAssociatedFile(metaFile);
        if(metaFile.exists())
        {
            try
            {
                loadMetadata(parseFile(metaFile));
            }
            catch(Exception e)
            {
                _log.warn("Unable to load meta-data from module query file " + metaFile.getAbsolutePath(), e);
            }
        }
    }

    protected static String getNameFromFile(File sqlFile)
    {
        String fileName = sqlFile.getName();
        return fileName.substring(0, fileName.length() - FILE_EXTENSION.length());
    }


    protected void loadMetadata(Document doc)
    {
        Node docElem = doc.getDocumentElement();

        if(!docElem.getNodeName().equalsIgnoreCase("query"))
            return;

        _hidden = Boolean.parseBoolean(DOMUtil.getAttributeValue(docElem, "hidden", "false"));
        _schemaVersion = Double.parseDouble(DOMUtil.getAttributeValue(docElem, "schemaVersion", "0"));

        //description
        Node node = DOMUtil.getFirstChildNodeWithName(docElem, "description");
        if(null != node)
            _description = DOMUtil.getNodeText(node);

        node = DOMUtil.getFirstChildNodeWithName(docElem, "metadata");
        if(null != node)
        {
            Node root = DOMUtil.getFirstChildElement(node);
            if(null != root)
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