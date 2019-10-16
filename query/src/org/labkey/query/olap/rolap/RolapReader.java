/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.query.olap.rolap;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Created by matthew on 8/16/14.
 *
 * Read monrdrian schema xml.  Although we have no reason or intention to reimplement Mondrian functionality,
 * we don't really need Mondrian to implement the CountDistinct API.  Parsing the mondrian schema xml
 * allows us to duplicate that functionality while better integrating with arbitrary SQL filters and joins.
 */

public class RolapReader
{
//    static Logger _log = Logger.getLogger(RolapReader.class);

    private Document _document;
    private String schemaName;
    private final Map<String,String> schemaAnnotations = new TreeMap<>();
    private final Map<String,RolapCubeDef> cubeDefinitions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);


    public RolapReader(File f) throws SAXException, ParserConfigurationException, IOException
    {
        loadDocument(f);
        parse();
    }


    public RolapReader(Reader r) throws IOException
    {
        loadDocument(r);
        parse();
    }


    public String getSchemaName()
    {
        return schemaName;
    }

    public List<RolapCubeDef> getCubes()
    {
        return new ArrayList<>(cubeDefinitions.values());
    }


    public Map<String,String> getAnnotations()
    {
        return Collections.unmodifiableMap(schemaAnnotations);
    }


    private void loadDocument(File file) throws SAXException, ParserConfigurationException, IOException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        _document = db.parse(file);
    }


    private void loadDocument(Reader reader) throws IOException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        try
        {
            DocumentBuilder db = dbf.newDocumentBuilder();
            _document = db.parse(new InputSource(reader));
        }
        catch (SAXException|ParserConfigurationException x)
        {
            throw new IOException(x);
        }
    }


    // TODO, it would be great to give informative errors if the XML is not well formed
    // CONSIDER: use xsd? or validate by hand?
    void validate()
    {
    }


    private RolapCubeDef _currentCube = null;
    private RolapCubeDef.DimensionDef _currentDim = null;
    private RolapCubeDef.HierarchyDef _currentHier = null;
    private RolapCubeDef.LevelDef _currentLevel = null;

    private void parse()
    {
        Node schema = _document.getFirstChild();
        if (!"Schema".equals(schema.getNodeName()))
            throw new ConfigurationException("Expected to find Schema element at root of document");

        schemaName = getStringAttribute(schema,"name");
        if (isBlank(schemaName))
            throw new ConfigurationException("Schema element should have 'name' attribute");

        for (Node node : it(schema.getChildNodes()))
        {
            switch (node.getNodeName())
            {
                default: continue;
                case "Annotations":
                {
                    // just so as not to give the impression Annotation applies to some cubes and not others
                    if (0 < cubeDefinitions.size())
                        throw new ConfigurationException("Annotations element should appear before Cube elements");
                    parseAnnotations(node,schemaAnnotations);
                    break;
                }
                case "Cube":
                {
                    parseCube(node);
                    break;
                }
            }
        }

        validate();
    }


    private void parseCube(Node cubeNode)
    {
        String cubeName = getStringAttribute(cubeNode,"name");
        if (isBlank(cubeName))
            throw new ConfigurationException("Cube element should have 'name' attribute");

        _currentCube = new RolapCubeDef(schemaName, cubeName);

        for (Node node : it(cubeNode.getChildNodes()))
        {
            switch (node.getNodeName())
            {
                default: continue;
                case "Dimension":
                {
                    parseDimension(node, null);
                    break;
                }
                case "DimensionUsage":
                {
                    String source = getStringAttribute(node, "source");
                    String name = getStringAttribute(node, "name");
                    Node schema = _document.getFirstChild();
                    for (Node d : it(schema.getChildNodes()))
                    {
                        if ("Dimension".equals(d.getNodeName()) && source.equals(getStringAttribute(d,"name")))
                        {
                            parseDimension(d, name);
                        }
                    }
                }
                case "Measure":
                {
                    parseMeasure(node);
                    break;
                }
                case "Annotations":
                {
                    parseAnnotations(node,_currentCube.annotations);
                    break;
                }
                case "Table":
                {
                    _currentCube.factTable = parseJoinOrTable(node);
                    break;
                }
            }
        }

        if (null != _currentCube.getAnnotations().get("experimentalUseInnerJoins"))
        {
            if (Boolean.TRUE == ConvertUtils.convert(_currentCube.getAnnotations().get("experimentalUseInnerJoins"), Boolean.class))
                _currentCube.useOuterJoin = false;
        }

        _currentCube.validate();

        if (cubeDefinitions.containsKey(_currentCube.name))
            throw new IllegalArgumentException("Duplicate cube name found " + _currentCube.name);
        cubeDefinitions.put(_currentCube.name, _currentCube);
        _currentCube = null;
    }


    private void parseMeasure(Node measureNode)
    {
        RolapCubeDef.MeasureDef m = new RolapCubeDef.MeasureDef();
        m.name = getStringAttribute(measureNode,"name");
        m.columnExpression = getStringAttribute(measureNode, "column");
        m.aggregator = getStringAttribute(measureNode, "aggregator");
        parseAnnotations(measureNode, m.annotations);
        _currentCube.measures.add(m);
    }


    private void parseAnnotations(Node node, Map<String,String> map /* OUT */)
    {
        for (Node a : it(node.getChildNodes()))
        {
            if ("Annotations".equals(a.getNodeName()))
                parseAnnotations(a, map);
            if (!"Annotation".equals(a.getNodeName()))
                continue;
            String key = getStringAttribute(a,"name");
            String value = a.getTextContent();
            map.put(key,value);
        }
    }


    private void parseDimension(Node dimNode, @Nullable String name)
    {
        _currentDim = new RolapCubeDef.DimensionDef();

        _currentDim.cube = _currentCube;
        _currentDim.name = StringUtils.defaultString(name, getStringAttribute(dimNode, "name"));
        _currentDim.foreignKey = getStringAttribute(dimNode,"foreignKey");

        for (Node node : it(dimNode.getChildNodes()))
        {
            if (!"Hierarchy".equals(node.getNodeName()))
                continue;
            parseHierarchy(node);
        }
        _currentCube.dimensions.add(_currentDim);
        _currentDim = null;
    }


    private void parseHierarchy(Node hierNode)
    {
        _currentHier = new RolapCubeDef.HierarchyDef();

        _currentHier.cube = _currentCube;
        _currentHier.dimension = _currentDim;
        _currentHier.name = getStringAttribute(hierNode,"name");
        if (StringUtils.isEmpty(_currentHier.name))
            _currentHier.name = _currentDim.name;
        _currentHier.primaryKey = getStringAttribute(hierNode,"primaryKey");
        _currentHier.primaryKeyTable = getStringAttribute(hierNode, "primaryKeyTable");
        _currentHier.hasAll = getBooleanAttribute(hierNode, "hasAll", true);

        for (Node node : it(hierNode.getChildNodes()))
        {
            switch (node.getNodeName())
            {
                default:
                    continue;
                case "Level":
                    parseLevel(node);
                    break;
                case "Table":
                case "Join":
                    _currentHier.join = parseJoinOrTable(node);
                    break;
            }
        }
        RolapCubeDef.LevelDef lowest = _currentHier.levels.get(_currentHier.levels.size()-1);
        lowest.isLeaf = true;

        _currentDim.hierarchies.add(_currentHier);
        _currentHier = null;
    }


    private RolapCubeDef.JoinOrTable parseJoinOrTable(Node table)
    {
        RolapCubeDef.JoinOrTable t = new RolapCubeDef.JoinOrTable();

        t.cube = _currentCube;

        if ("Table".equals(table.getNodeName()))
        {
            t.tableName = getStringAttribute(table, "name");
            t.schemaName = getStringAttribute(table, "schema");
        }
        else if ("Join".equals(table.getNodeName()))
        {
            t.leftKey = getStringAttribute(table, "leftKey");
            t.leftAlias = getStringAttribute(table, "leftAlias");
            t.rightKey = getStringAttribute(table, "rightKey");
            t.rightAlias = getStringAttribute(table, "rightAlias");
            for (Node node : it(table.getChildNodes()))
            {
                switch (node.getNodeName())
                {
                    default:
                        continue;
                    case "Table":
                    case "Join":
                        RolapCubeDef.JoinOrTable childTable = parseJoinOrTable(node);
                        if (null == t.left)
                            t.left = childTable;
                        else if (null == t.right)
                            t.right = childTable;
                        else
                            throw new IllegalArgumentException("Found more than two table or join under <Join>");
                        break;
                }
            }
        }
        return t;
    }


    private void parseLevel(Node levelNode)
    {
        _currentLevel = new RolapCubeDef.LevelDef();

        _currentLevel.cube = _currentCube;
        _currentLevel.hierarchy = _currentHier;
        _currentLevel.name = getStringAttribute(levelNode,"name");
        _currentLevel.table = getStringAttribute(levelNode,"table");
        _currentLevel.keyColumn = getStringAttribute(levelNode, "column");
        _currentLevel.nameColumn = getStringAttribute(levelNode, "nameColumn");
        _currentLevel.ordinalColumn = getStringAttribute(levelNode, "ordinalColumn");
        _currentLevel.uniqueMembers = getBooleanAttribute(levelNode, "uniqueMembers", false);
        _currentLevel.keyType = getStringAttribute(levelNode,"type");

        for (Node node : it(levelNode.getChildNodes()))
        {
            switch (node.getNodeName())
            {
                default:
                    continue;
                case "KeyExpression":
                case "NameExpression":
                case "OrdinalExpression":
                {
                    for (Node sql : it(node.getChildNodes()))
                    {
                        if (!"SQL".equals(sql.getNodeName()))
                            continue;
                        String expr = sql.getTextContent();
                        switch (node.getNodeName())
                        {
                            case "KeyExpression":
                                _currentLevel.keyExpression = expr;
                                break;
                            case "NameExpression":
                                _currentLevel.nameExpression = expr;
                                break;
                            case "OrdinalExpression":
                                _currentLevel.ordinalExpression = expr;
                                break;
                            default:
                        }
                    }
                    break;
                }
                case "Property":
                {
                    RolapCubeDef.PropertyDef p = new RolapCubeDef.PropertyDef();
                    p.name = getStringAttribute(node,"name");
                    p.columnExpression = getStringAttribute(node,"column");
                    p.type = getStringAttribute(node,"type");
                    p.dependsOnLevelValue = getBooleanAttribute(node,"dependsOnLevelValue",true);
                    _currentLevel.properties.add(p);
                }
            }
        }

        _currentHier.levels.add(_currentLevel);
        _currentLevel = null;
    }


    private String getStringAttribute(Node n, String name)
    {
        Node attr = n.getAttributes().getNamedItem(name);
        if (null == attr) return null;
        return attr.getNodeValue();
    }


    private boolean getBooleanAttribute(Node n, String name, boolean def)
    {
        Node attr = n.getAttributes().getNamedItem(name);
        if (null == attr)
            return def;
        String s = attr.getNodeValue();
        if ("true".equals(s))
            return true;
        else if ("false".equals(s))
            return false;
        throw new IllegalArgumentException("Illegal boolean value for attribute: " + name + "=" + s);
    }


    private static Iterable<Node> it(NodeList nl)
    {
        return new _Iterable(nl);

    }
    private static class _Iterable implements Iterable<Node>
    {
        final NodeList _nl;

        _Iterable(NodeList nl)
        {
            _nl = nl;
        }

        @Override
        @NotNull
        public Iterator<Node> iterator()
        {
            return new Iterator<>()
            {
                int i=-1;

                @Override
                public boolean hasNext()
                {
                    return i<_nl.getLength()-1;
                }

                @Override
                public Node next()
                {
                    ++i;
                    return _nl.item(i);
                }

                @Override
                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }



    public static class RolapTest extends Assert
    {
        @Test
        public void parse() throws Exception
        {
            Module argos = ModuleLoader.getInstance().getModule("argos");
            if (null != argos)
            {
                try (
                    InputStream is = argos.getResourceStream("olap/Argos.xml");
                    Reader r = new InputStreamReader(is)
                )
                {
                    new RolapReader(r);
                }
            }
            Module cds = ModuleLoader.getInstance().getModule("cds");
            if (null != cds)
            {
                try (
                        InputStream is = cds.getResourceStream("olap/CDS.xml");
                        Reader r = new InputStreamReader(is)
                )
                {
                    new RolapReader(r);
                }
            }
        }
    }
}

