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
package org.labkey.api.module;

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.CustomView;
import org.labkey.api.util.DOMUtil;
import org.labkey.common.util.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.apache.log4j.Logger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/*
* User: Dave
* Date: Jan 14, 2009
* Time: 12:43:18 PM
*/

/**
 * A bean that represents the a custom view definition stored
 * in a module resource file. This is separate from ModuleCustomView
 * because that class cannot be cached, as it must hold a reference
 * to the source QueryDef, which holds a reference to the QueryView,
 * etc., etc.
 */
public class ModuleCustomViewDef
{
    public static final String FILE_EXTENSION = ".qview.xml";

    private File _sourceFile;
    private String _name;
    private List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> _colList = new ArrayList<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>>();
    private boolean _hidden = false;
    private List<Pair<String,String>> _filters;
    private List<String> _sorts;
    private long _lastModified;
    private String _customIconUrl;

    public ModuleCustomViewDef(File sourceFile)
    {
        _sourceFile = sourceFile;
        _lastModified = sourceFile.lastModified();

        String fileName = _sourceFile.getName();
        assert fileName.length() > FILE_EXTENSION.length();
        _name = fileName.substring(0, fileName.length() - FILE_EXTENSION.length());

        loadDefinition();
    }

    public boolean isStale()
    {
        return _sourceFile.lastModified() != _lastModified;
    }

    public File getSourceFile()
    {
        return _sourceFile;
    }

    public String getName()
    {
        return _name;
    }

    public List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> getColList()
    {
        return _colList;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public List<Pair<String, String>> getFilters()
    {
        return _filters;
    }

    public List<String> getSorts()
    {
        return _sorts;
    }

    public long getLastModified()
    {
        return _lastModified;
    }

    public String getCustomIconUrl()
    {
        return _customIconUrl;
    }

    protected void loadDefinition()
    {
        try
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document doc = db.parse(_sourceFile);
            if(null == doc)
                throw new IllegalStateException("Custom view definition file " + _sourceFile.getPath() + " contianed an empty document!");

            //load hidden attribute
            _hidden = Boolean.parseBoolean(DOMUtil.getAttributeValue(doc.getDocumentElement(), "hidden", "false"));

            _customIconUrl = DOMUtil.getAttributeValue(doc.getDocumentElement(), "customIconUrl", null);

            //load the columns
            _colList = loadColumns(doc);

            //load the filters
            _filters = loadFilters(doc);

            //load the sorts
            _sorts = loadSorts(doc);

        }
        catch(Exception e)
        {
            Logger.getLogger(ModuleCustomView.class).warn("Unable to load custom view definition from file "
                    + _sourceFile.getPath(), e);
        }
    }

    protected List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> loadColumns(Document doc)
    {
        List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> ret = new ArrayList<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>>();

        //load the column elements
        NodeList nodes = doc.getElementsByTagName("column");
        for(int idx = 0; idx < nodes.getLength(); ++idx)
        {
            Node node = nodes.item(idx);
            FieldKey fieldKey = getFieldKey(node);
            if(null == fieldKey)
                continue;

            //load any column properties that might be there
            Map<CustomView.ColumnProperty,String> props = new HashMap<CustomView.ColumnProperty,String>();
            Node propsNode = DOMUtil.getFirstChildNodeWithName(node, "properties");
            if(null != propsNode)
            {
                List<Node> propsNodes = DOMUtil.getChildNodesWithName(propsNode, "property");
                for(Node propNode : propsNodes)
                {
                    CustomView.ColumnProperty colProp = CustomView.ColumnProperty.valueOf(DOMUtil.getAttributeValue(propNode, "name", null));
                    if(null == colProp)
                        continue;

                    props.put(colProp, DOMUtil.getAttributeValue(propNode, "value", null));
                }
            }

            ret.add(Pair.of(fieldKey, props));
        }

        return ret;
    }

    protected List<Pair<String,String>> loadFilters(Document doc)
    {
        NodeList nodes = doc.getElementsByTagName("filters");
        if(null == nodes || nodes.getLength() == 0)
            return null;

        List<Pair<String,String>> ret = new ArrayList<Pair<String,String>>();
        Node filtersRoot = nodes.item(0);
        nodes = filtersRoot.getChildNodes();
        for(int idx = 0; idx < nodes.getLength(); ++idx)
        {
            Node filterNode = nodes.item(idx);
            if(!filterNode.getNodeName().equalsIgnoreCase("filter"))
                    continue;

            String colName = DOMUtil.getAttributeValue(filterNode, "column", null);
            if(null == colName)
                continue;

            String oper = DOMUtil.getAttributeValue(filterNode, "operator", "eq");
            String value = DOMUtil.getAttributeValue(filterNode, "value", null);

            ret.add(new Pair<String,String>(colName + "~" + oper, value));
        }

        return ret;
    }

    protected FieldKey getFieldKey(Node columnNode)
    {
        String nameAttr = DOMUtil.getAttributeValue(columnNode, "name", null);
        return null == nameAttr ? null : FieldKey.fromString(nameAttr);
    }

    protected List<String> loadSorts(Document doc)
    {
        List<Node> sortsNodes = DOMUtil.getChildNodesWithName(doc.getDocumentElement(), "sorts");
        if(null == sortsNodes || sortsNodes.size() == 0)
            return null;

        List<Node> sortNodes = DOMUtil.getChildNodesWithName(sortsNodes.get(0), "sort");
        List<String> ret = new ArrayList<String>();
        for(Node node : sortNodes)
        {
            String colName = DOMUtil.getAttributeValue(node, "column");
            if(null == colName)
                continue;

            boolean descending = Boolean.parseBoolean(DOMUtil.getAttributeValue(node, "descending", "false"));
            ret.add(descending ? "-" + colName : colName);
        }
        return ret;
    }
}