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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/*
* User: Dave
* Date: Jan 9, 2009
* Time: 4:37:30 PM
*/
public class ModuleCustomView implements CustomView
{
    public static final String FILE_EXTENSION = ".qview.xml";

    private QueryDefinition _queryDef;
    private File _sourceFile;
    private String _name;
    private List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> _colList = new ArrayList<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>>();
    private boolean _hidden = false;
    private List<Pair<String,String>> _filters;
    private List<String> _sorts;
    private boolean _loaded = false;
    private long _lastModified;
    private String _customIconUrl;

    public ModuleCustomView(QueryDefinition queryDef, File sourceFile)
    {
        _sourceFile = sourceFile;
        _lastModified = sourceFile.lastModified();
        _queryDef = queryDef;

        String fileName = _sourceFile.getName();
        assert fileName.length() > FILE_EXTENSION.length();
        _name = fileName.substring(0, fileName.length() - FILE_EXTENSION.length());
    }

    public boolean isStale()
    {
        return _sourceFile.lastModified() != _lastModified;
    }

    public QueryDefinition getQueryDefinition()
    {
        return _queryDef;
    }

    public String getName()
    {
        return _name;
    }

    public User getOwner()
    {
        //module-based reports have no owner
        return null;
    }

    public Container getContainer()
    {
        //module-based reports have no explicit container
        return null;
    }

    public boolean canInherit()
    {
        return false;
    }

    public void setCanInherit(boolean f)
    {
        throw new UnsupportedOperationException("Module-based custom views cannot inherit");
    }

    public boolean isHidden()
    {
        loadDefinition();
        return _hidden;
    }

    public void setIsHidden(boolean f)
    {
        throw new UnsupportedOperationException("Module-based custom views cannot be set to hidden. " +
                "To suppress a module-based view, use Customize Folder to deactivate the module in this current folder.");
    }

    public boolean isEditable()
    {
        //module custom views are not updatable
        return false;
    }

    public String getCustomIconUrl()
    {
        return _customIconUrl;
    }

    public List<FieldKey> getColumns()
    {
        List<FieldKey> ret = new ArrayList<FieldKey>();
        for(Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>> entry : getColumnProperties())
        {
            ret.add(entry.getKey());
        }
        return ret;
    }

    public List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> getColumnProperties()
    {
        loadDefinition();
        return _colList;
    }

    public void setColumns(List<FieldKey> columns)
    {
        throw new UnsupportedOperationException("Can't set columns on a module-based custom view!");
    }

    public void setColumnProperties(List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> list)
    {
        throw new UnsupportedOperationException("Can't set column properties on a module-based custom view!");
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        loadDefinition();
        if(null != _filters)
        {
            for(Pair<String,String> filter : _filters)
            {
                url.addParameter(dataRegionName + "." + filter.first, filter.second);
            }
        }

        String sortParam = buildSortParamValue();
        if(null != sortParam)
            url.addParameter(dataRegionName + ".sort", sortParam);
    }

    protected String buildSortParamValue()
    {
        if(null == _sorts)
            return null;

        StringBuilder sortParam = new StringBuilder();
        String sep = "";
        for(String sort : _sorts)
        {
            sortParam.append(sep);
            sortParam.append(sort);
            sep = ",";
        }
        return sortParam.toString();
    }

    public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
    {
        throw new UnsupportedOperationException("Can't set the filter or sort of a module-based custom view!");
    }

    public String getFilter()
    {
        loadDefinition();
        if(null == _filters)
            return null;

        StringBuilder ret = new StringBuilder();
        for(Pair<String,String> filter : _filters)
        {
            ret.append(filter.first);
            ret.append("=");
            ret.append(filter.second);
        }

        return ret.toString();
    }

    public void setFilter(String filter)
    {
        throw new UnsupportedOperationException("Can't set filter on a module-based custom view!");
    }

    public String getContainerFilterName()
    {
        loadDefinition();
        if(null == _filters)
            return null;

        for(Pair<String,String> filter : _filters)
        {
            if(filter.first.startsWith("containerFilterName~"))
                return filter.second;
        }
        return null;
    }

    public boolean hasFilterOrSort()
    {
        loadDefinition();
        return (null != _filters && _filters.size() > 0);
    }

    public void save(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException("Can't save a module-based custom view!");
    }

    public void delete(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException("Can't delete a module-based custom view!");
    }

    /**
     * Loads the custom view definition from the source file
     */
    protected void loadDefinition()
    {
        if(_loaded)
            return;

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

            _loaded = true;
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
            Map<CustomView.ColumnProperty,String> props = new HashMap<ColumnProperty,String>();
            Node propsNode = DOMUtil.getFirstChildNodeWithName(node, "properties");
            if(null != propsNode)
            {
                List<Node> propsNodes = DOMUtil.getChildNodesWithName(propsNode, "property");
                for(Node propNode : propsNodes)
                {
                    ColumnProperty colProp = ColumnProperty.valueOf(DOMUtil.getAttributeValue(propNode, "name", null));
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