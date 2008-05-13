/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.OntologyType;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.apache.log4j.Priority;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlCursor;

import javax.xml.namespace.QName;
import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: phussey
 * Date: Sep 19, 2005
 * Time: 11:09:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class TableXmlUtils
{
    private static Logger _log = Logger.getLogger(TableXmlUtils.class);

    public static TablesDocument getXmlDocumentFromMetaData(String dbSchemaName, boolean bFull) throws Exception
    {

        DbSchema dbSchema = DbSchema.createFromMetaData(dbSchemaName);
        TablesDocument xmlTablesDoc = TablesDocument.Factory.newInstance();
        TablesDocument.Tables xmlTables = xmlTablesDoc.addNewTables();
        SchemaTableInfo[] tableInfos = dbSchema.getTables();

        for (SchemaTableInfo tableInfo : tableInfos)
        {
            TableType xmlTable = xmlTables.addNewTable();
            tableInfo.copyToXml(xmlTable, bFull);
        }

        return xmlTablesDoc;
    }

    public static void loadMapsFromTableXml(String dbSchemaName, Map tableNameMap, Map columnNameMap)
    {
        InputStream xmlStream = null;

        try
        {

            xmlStream = DbSchema.getSchemaXmlStream(dbSchemaName);
            if (null == xmlStream)
            {
                _log.debug("No xml tables doc found for " + dbSchemaName);
                return;
            }

            TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlStream);
            // todo:  cache these tableDocs so DbSchema.get doesn't have to reload them

            TableType[] ts = tablesDoc.getTables().getTableArray();
            ColumnType[] cs;

            Map ttempMap = new CaseInsensitiveHashMap(ts.length);
            Map ctempMap;
            for (int i = 0; i < ts.length; i++)
            {
                ttempMap.put(ts[i].getTableName(), ts[i].getTableName());
                cs = ts[i].getColumns().getColumnArray();
                ctempMap = new CaseInsensitiveHashMap(cs.length);
                for (int j = 0; j < cs.length; j++)
                    ctempMap.put(cs[j].getColumnName(), cs[j].getColumnName());

                columnNameMap.putAll(ctempMap);
            }
            tableNameMap.putAll(ttempMap);
        }
        catch (Exception e)
        {
            _log.log(Level.ERROR, "Exception loading schema " + dbSchemaName, e);
            return;
        }
        finally
        {
            try
            {
                if (null != xmlStream) xmlStream.close();
            }
            catch (Exception x)
            {
            }
        }
        return;
    }

    public static TablesDocument getMergedXmlDocument(String dbSchemaName) throws Exception
    {

        TablesDocument tablesDocMetaData = null;
        TablesDocument tablesDocFromXml = null;
        TablesDocument tablesDocMerged = null;
        InputStream xmlStream = null;

        try
        {
            tablesDocMetaData = getXmlDocumentFromMetaData(dbSchemaName, true);
            xmlStream = DbSchema.getSchemaXmlStream(dbSchemaName);
            if (null != xmlStream)
                tablesDocFromXml = TablesDocument.Factory.parse(xmlStream);

            tablesDocMerged = TablesDocument.Factory.newInstance();
            tablesDocMerged.addNewTables();

            if ((null != tablesDocMetaData) && (null != tablesDocFromXml))
            {
                String result = compareTableDocuments(tablesDocMetaData, tablesDocFromXml, true, true, tablesDocMerged);
            }
        }
        catch (XmlException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        finally
        {
            try
            {
                if (null != xmlStream) xmlStream.close();
            }
            catch (Exception x)
            {
            }
        }
        XmlCursor cursor = tablesDocMerged.newCursor();
        if (cursor.toFirstChild())
        {
          cursor.setAttributeText(new QName("http://www.w3.org/2001/XMLSchema-instance","schemaLocation"),
                  "http://cpas.fhcrc.org/data/xml ..\\..\\..\\..\\schemas\\tableInfo.xsd");
        }
        return tablesDocMerged;
    }


    public static String compareXmlToMetaData(String dbSchemaName, boolean bFull, boolean bCaseSensitive)
    {
        String result = null;

        InputStream xmlStream = null;
        try
        {
            TablesDocument tablesDocMetaData = null;
            TablesDocument tablesDocFromXml = null;

            tablesDocMetaData = getXmlDocumentFromMetaData(dbSchemaName, false);
            xmlStream = DbSchema.getSchemaXmlStream(dbSchemaName);
            if (null != xmlStream)
                tablesDocFromXml = TablesDocument.Factory.parse(xmlStream);

            if ((null != tablesDocMetaData) && (null != tablesDocFromXml))
            {
                result = compareTableDocuments(tablesDocMetaData, tablesDocFromXml, bFull, bCaseSensitive, null);
            }

            result += compareOldToNewNames(dbSchemaName);

        }
        catch (Exception e)
        {
            _log.log(Priority.ERROR, "Exception loading schema " + dbSchemaName, e);
            return "+++ ERROR:  Exception " + e.getMessage();
        }
        finally
        {
            try
            {
                if (null != xmlStream) xmlStream.close();
            }
            catch (Exception x)
            {
            }
        }
        if (null!=result && result.equals(""))
            return null;
        return result;

    }

    public static String compareTableDocuments(TablesDocument dbTablesDoc,
                                               TablesDocument xmlTablesDoc,
                                               boolean bFull,
                                               boolean bCaseSensitive,
                                               TablesDocument mergedTablesDoc)
    {
        StringBuffer sbOut = new StringBuffer();
        boolean merge = (null != mergedTablesDoc);
        boolean bCopyTargetNode = false;
        TableType[] dbTables =null;
        TableType[] xmlTables =null;
        TableType mt = null;
        ColumnType mc = null;
        String xmlTableName =null;
        String xmlTableType;
        String dbTableName;
        String dbColName;
        String xmlColName;
        int idt;
        int idc;
        ColumnType[] dbCols;
        ColumnType[] xmlCols;
        SortedMap<String, Integer> mXmlTableOrdinals =null;
        SortedMap<String, Integer> mXmlColOrdinals =null;
        SortedMap<String, Integer> mDbTableOrdinals =null;
        SortedMap<String, Integer> mDbColOrdinals =null;

        try
        {
            dbTables = dbTablesDoc.getTables().getTableArray();
            xmlTables = xmlTablesDoc.getTables().getTableArray();

            mXmlTableOrdinals = new TreeMap<String, Integer>();
            for (int i = 0; i < xmlTables.length; i++)
            {
                xmlTableName = xmlTables[i].getTableName();
                if (mXmlTableOrdinals.containsKey(xmlTableName.toLowerCase()))
                    sbOut.append("ERROR:  TableName " + xmlTableName + " duplicated in Xml. <BR/>");
                else
                    mXmlTableOrdinals.put(xmlTableName.toLowerCase(), new Integer(i));
            }

            mDbTableOrdinals = new TreeMap<String, Integer>();
            for (int i = 0; i < dbTables.length; i++)
            {
                dbTableName = dbTables[i].getTableName();
                mDbTableOrdinals.put(dbTableName.toLowerCase(), new Integer(i));
            }

            for (int ixt = 0; ixt < xmlTables.length; ixt++)
            {

               xmlTableName = xmlTables[ixt].getTableName();
               xmlTableType = xmlTables[ixt].getTableDbType();

                if (!(mDbTableOrdinals.containsKey(xmlTableName.toLowerCase())))
                {
                    if (!xmlTableType.equals("NOT_IN_DB"))
                        sbOut.append("<BR/>ERROR:  TableName " + xmlTableName + " type " + xmlTableType +
                                " found in Xml but not in database. <BR/>");
                    else
                    {
                        if (merge)
                        {
                            //copy XML-only node to end of table array
                            int size = mergedTablesDoc.getTables().getTableArray().length;
                            mergedTablesDoc.getTables().addNewTable();
                            mt = (TableType) xmlTables[ixt].copy();
                            mergedTablesDoc.getTables().setTableArray(size, mt);
                        }
                    }
                    continue;
                }

                idt = mDbTableOrdinals.get(xmlTableName.toLowerCase()).intValue();

                if (merge)
                {
                    mt = mergedTablesDoc.getTables().addNewTable();
                    mt.addNewColumns();
                }

                compareStringProperty(dbTables[idt].getTableName(), xmlTables[ixt].getTableName(), "TableName", sbOut, bCaseSensitive, true);
                if (merge)
                    mt.setTableName(xmlTables[ixt].getTableName());

                compareStringProperty(dbTables[idt].getTableDbType(), xmlTables[ixt].getTableDbType(), "TableDbType", sbOut, true, true);
                if (merge)
                    mt.setTableDbType(xmlTables[ixt].getTableDbType());

                if (bFull)
                {
                    bCopyTargetNode = compareStringProperty(dbTables[idt].getTableTitle(), xmlTables[ixt].getTableTitle(), "TableTitle", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableTitle(xmlTables[ixt].getTableTitle());

                    bCopyTargetNode = compareStringProperty(dbTables[idt].getTableGroup(), xmlTables[ixt].getTableGroup(), "TableGroup", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableGroup(xmlTables[ixt].getTableGroup());

                    bCopyTargetNode = compareStringProperty(dbTables[idt].getDbTableName(), xmlTables[ixt].getDbTableName(), "DbTableName", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setDbTableName(xmlTables[ixt].getDbTableName());

                    bCopyTargetNode = compareStringProperty(dbTables[idt].getPkColumnName(), xmlTables[ixt].getPkColumnName(), "PkColumnName", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setPkColumnName(xmlTables[ixt].getPkColumnName());

                    bCopyTargetNode = compareStringProperty(dbTables[idt].getVersionColumnName(), xmlTables[ixt].getVersionColumnName(), "VersionColumnName", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setVersionColumnName(xmlTables[ixt].getVersionColumnName());

                    bCopyTargetNode = compareStringProperty(dbTables[idt].getTableUrl(), xmlTables[ixt].getTableUrl(), "TableUrl", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableUrl(xmlTables[ixt].getTableUrl());

                    bCopyTargetNode = compareStringProperty(dbTables[idt].getNextStep(), xmlTables[ixt].getNextStep(), "NextStep", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setNextStep(xmlTables[ixt].getNextStep());

                    bCopyTargetNode = compareStringProperty(dbTables[idt].getTitleColumn(), xmlTables[ixt].getTitleColumn(), "TitleColumn", sbOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTitleColumn(xmlTables[ixt].getTitleColumn());


                    bCopyTargetNode = compareBoolProperty((dbTables[idt].isSetManageTableAllowed() ? dbTables[idt].getManageTableAllowed() : null),
                            (xmlTables[ixt].isSetManageTableAllowed() ? xmlTables[ixt].getManageTableAllowed() : null),
                            "ManageTableAllowed", sbOut);
                    if (merge && bCopyTargetNode)
                        mt.setManageTableAllowed(xmlTables[ixt].getManageTableAllowed());

                }

                dbCols = dbTables[idt].getColumns().getColumnArray();
                xmlCols = xmlTables[ixt].getColumns().getColumnArray();

                mXmlColOrdinals = new TreeMap<String, Integer>();
                mDbColOrdinals = new TreeMap<String, Integer>();

                for (int m = 0; m < xmlCols.length; m++)
                {
                    xmlColName = xmlCols[m].getColumnName();
                    if (mXmlColOrdinals.containsKey(xmlColName.toLowerCase()))
                        sbOut.append("ERROR:  Table " + xmlTables[ixt].getTableName()+ " Column name " + xmlColName + " duplicated in Xml. <BR/>");
                    else
                        mXmlColOrdinals.put(xmlColName.toLowerCase(), new Integer(m));
                }

                for (int m = 0; m < dbCols.length; m++)
                {
                    dbColName = dbCols[m].getColumnName();
                    mDbColOrdinals.put(dbColName.toLowerCase(), new Integer(m));
                }

                for (int ixc = 0; ixc < xmlCols.length; ixc++)
                {
                    xmlColName = xmlCols[ixc].getColumnName();

                    if (!mDbColOrdinals.containsKey(xmlColName.toLowerCase()))
                    {
                        sbOut.append("ERROR:  Table "+ xmlTables[ixt].getTableName()+ " Column name " + xmlColName +
                                " found in Xml but not in database. <BR/>");
                        continue;
                    }
                    idc = mDbColOrdinals.get(xmlColName.toLowerCase()).intValue();

                    bCopyTargetNode = compareStringProperty(dbCols[idc].getColumnName(), xmlCols[ixc].getColumnName(), "ColumnName", sbOut, bCaseSensitive, true);
                    if (merge)
                    {
                        mc = mt.getColumns().addNewColumn();
                        mc.setColumnName(xmlCols[ixc].getColumnName());
                    }


                    if (bFull)
                    {
                        StringBuffer sbTmp=new StringBuffer();

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getDatatype(), xmlCols[ixc].getDatatype(), "Datatype", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDatatype(xmlCols[ixc].getDatatype());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getColumnTitle(), xmlCols[ixc].getColumnTitle(), "ColumnTitle", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setColumnTitle(xmlCols[ixc].getColumnTitle());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getDefaultValue(), xmlCols[ixc].getDefaultValue(), "DefaultValue", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDefaultValue(xmlCols[ixc].getDefaultValue());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getAutoFillValue(), xmlCols[ixc].getAutoFillValue(), "AutoFillValue", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setAutoFillValue(xmlCols[ixc].getAutoFillValue());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getInputType(), xmlCols[ixc].getInputType(), "InputType", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setInputType(xmlCols[ixc].getInputType());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getOnChange(), xmlCols[ixc].getOnChange(), "OnChange", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setOnChange(xmlCols[ixc].getOnChange());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getDescription(), xmlCols[ixc].getDescription(), "ColumnText", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDescription(xmlCols[ixc].getDescription());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getOptionlistQuery(), xmlCols[ixc].getOptionlistQuery(), "OptionlistQuery", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setOptionlistQuery(xmlCols[ixc].getOptionlistQuery());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getUrl(), xmlCols[ixc].getUrl(), "Url", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setUrl(xmlCols[ixc].getUrl());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getFormatString(), xmlCols[ixc].getFormatString(), "FormatString", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setFormatString(xmlCols[ixc].getFormatString());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getTextAlign(), xmlCols[ixc].getTextAlign(), "TextAlign", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setTextAlign(xmlCols[ixc].getTextAlign());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getPropertyURI(), xmlCols[ixc].getPropertyURI(), "PropertyURI", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setPropertyURI(xmlCols[ixc].getPropertyURI());

                        bCopyTargetNode = compareStringProperty(dbCols[idc].getDisplayWidth(), xmlCols[ixc].getDisplayWidth(), "DisplayWidth", sbTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDisplayWidth(xmlCols[ixc].getDisplayWidth());

                        bCopyTargetNode = compareIntegerProperty(
                                (dbCols[idc].isSetScale() ? dbCols[idc].getScale() : null),
                                (xmlCols[ixc].isSetScale() ? xmlCols[ixc].getScale() : null),
                                "Scale", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setScale(xmlCols[ixc].getScale());

                        bCopyTargetNode = compareIntegerProperty(
                                (dbCols[idc].isSetPrecision() ? dbCols[idc].getPrecision() : null),
                                (xmlCols[ixc].isSetPrecision() ? xmlCols[ixc].getPrecision() : null),
                                "Precision", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setPrecision(xmlCols[ixc].getPrecision());

                        bCopyTargetNode = compareIntegerProperty(
                                (dbCols[idc].isSetInputLength() ? dbCols[idc].getInputLength() : null),
                                (xmlCols[ixc].isSetInputLength() ? xmlCols[ixc].getInputLength() : null),
                                "InputLength", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setInputLength(xmlCols[ixc].getInputLength());

                        bCopyTargetNode = compareIntegerProperty(
                                (dbCols[idc].isSetInputRows() ? dbCols[idc].getInputRows() : null),
                                (xmlCols[ixc].isSetInputRows() ? xmlCols[ixc].getInputRows() : null),
                                "InputRows", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setInputRows(xmlCols[ixc].getInputRows());


                        bCopyTargetNode = compareBoolProperty(
                                (dbCols[idc].isSetNullable() ? dbCols[idc].getNullable() : null),
                                (xmlCols[ixc].isSetNullable() ? xmlCols[ixc].getNullable() : null),
                                "Nullable", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setNullable(xmlCols[ixc].getNullable());

                        bCopyTargetNode = compareBoolProperty(
                                (dbCols[idc].isSetIsAutoInc() ? dbCols[idc].getIsAutoInc() : null),
                                (xmlCols[ixc].isSetIsAutoInc() ? xmlCols[ixc].getIsAutoInc() : null),
                                "IsAutoInc", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsAutoInc(xmlCols[ixc].getIsAutoInc());

                        bCopyTargetNode = compareBoolProperty(
                                (dbCols[idc].isSetIsDisplayColumn() ? dbCols[idc].getIsDisplayColumn() : null),
                                (xmlCols[ixc].isSetIsDisplayColumn() ? xmlCols[ixc].getIsDisplayColumn() : null),
                                "IsDisplayColumn", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsDisplayColumn(xmlCols[ixc].getIsDisplayColumn());

                        bCopyTargetNode = compareBoolProperty(
                                (dbCols[idc].isSetIsReadOnly() ? dbCols[idc].getIsReadOnly() : null),
                                (xmlCols[ixc].isSetIsReadOnly() ? xmlCols[ixc].getIsReadOnly() : null),
                                "IsReadOnly", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsReadOnly(xmlCols[ixc].getIsReadOnly());

                        bCopyTargetNode = compareBoolProperty(
                                (dbCols[idc].isSetIsUserEditable() ? dbCols[idc].getIsUserEditable() : null),
                                (xmlCols[ixc].isSetIsUserEditable() ? xmlCols[ixc].getIsUserEditable() : null),
                                "IsUserEditable", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsUserEditable(xmlCols[ixc].getIsUserEditable());

                        bCopyTargetNode = compareBoolProperty(
                                (dbCols[idc].isSetIsKeyField() ? dbCols[idc].getIsKeyField() : null),
                                (xmlCols[ixc].isSetIsKeyField() ? xmlCols[ixc].getIsKeyField() : null),
                                "IsKeyField", sbTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsKeyField(xmlCols[ixc].getIsKeyField());

                        // check and merge FK property
                        ColumnType.Fk fk = null;
                        boolean declFk = false;
                        if ((null != dbCols[idc].getFk()))
                            declFk = true;

                        if (null != xmlCols[ixc].getFk())
                        {
                            compareStringProperty((declFk ? dbCols[idc].getFk().getFkColumnName() : null)
                                    , xmlCols[ixc].getFk().getFkColumnName()
                                    , "FkColumnName", sbTmp, bCaseSensitive);

                            compareStringProperty((declFk ? dbCols[idc].getFk().getFkTable() : null)
                                    , xmlCols[ixc].getFk().getFkTable()
                                    , "FkTable", sbTmp, bCaseSensitive);

                            bCopyTargetNode = compareStringProperty((declFk ? dbCols[idc].getFk().getFkDbSchema() : null)
                                    , xmlCols[ixc].getFk().getFkDbSchema()
                                    , "FkDbSchema", sbTmp, bCaseSensitive);

                            // if FK is declared in xml use it as a whole node, don't mix and match.
                            if (merge)
                            {
                                fk = mc.addNewFk();
                                fk.setFkColumnName(xmlCols[ixc].getFk().getFkColumnName());
                                fk.setFkTable(xmlCols[ixc].getFk().getFkTable());
                                if (bCopyTargetNode)
                                    fk.setFkDbSchema(xmlCols[ixc].getFk().getFkDbSchema());
                            }

                        }

                        // check and merge Ontology (assumed not from metadata)
                        OntologyType o = null;
                        if (null != dbCols[idc].getOntology())
                        {
                            sbTmp.append("ERROR:  Table " + dbTables[idt].getTableName()+ " Unexpected Ontology node in dbTablesDoc xmldoc from metadata, ColName " + dbCols[idc] + "  <BR/>");
                            continue;
                        }

                        if (merge && (null != xmlCols[ixc].getOntology()))
                        {
                            o = mc.addNewOntology();
                            o.setStringValue(xmlCols[ixc].getOntology().getStringValue());
                            o.setRefId(xmlCols[ixc].getOntology().getRefId());
                            if (null != xmlCols[ixc].getOntology().getSource())
                                o.setSource(xmlCols[ixc].getOntology().getSource());

                        }

                        if (sbTmp.length() > 0)
                        {
                            sbOut.append("<br/> Table " + dbTables[idt].getTableName() + " Column " + dbCols[idc].getColumnName() + " errors and warnings <br/>");
                            sbOut.append(sbTmp);
                        }
                    }

                    mDbColOrdinals.remove(xmlColName.toLowerCase());
                }
                // now check any extra columns in the db
                for (String dbCol : mDbColOrdinals.keySet())
                {
                    idc = mDbColOrdinals.get(dbCol).intValue();
                    sbOut.append("ERROR:  Table " + dbTables[idt].getTableName()+ " Column Name " + dbCol + " missing from Xml. <BR/>");
                    if (merge)
                    {
                        mc = mt.getColumns().addNewColumn();
                        mc.setColumnName(dbTables[idt].getColumns().getColumnArray(idc).getColumnName());
                    }

                }

                mDbTableOrdinals.remove(xmlTableName.toLowerCase());
            }
            // now check any extra tabledefs in the db
            for (String dbTab : mDbTableOrdinals.keySet())
            {
                if (dbTab.startsWith("_"))
                    continue;
                idt = mDbTableOrdinals.get(dbTab).intValue();
                sbOut.append("ERROR:  Table " + dbTab + " missing from Xml. <BR/>");
                if (merge)
                {
                    //copy db node to end of table array
                    mt = mergedTablesDoc.getTables().addNewTable();
                    mt.setTableName(dbTables[idt].getTableName());
                    mt.setTableDbType(dbTables[idt].getTableDbType());
                    mt.addNewColumns();
                    for (int i=0;i<dbTables[idt].getColumns().getColumnArray().length; i++)
                    {
                        mc=mt.getColumns().addNewColumn();
                        mc.setColumnName(dbTables[idt].getColumns().getColumnArray(i).getColumnName());
                    }

                }
            }

        }
        catch (Exception e)
        {
            sbOut.append("ERROR:  Exception in compare:  " + e.getMessage());
            e.printStackTrace();
            mergedTablesDoc = null;
        }
        finally
        {
        }
        return sbOut.toString();
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, StringBuffer sbOut, boolean bCaseSensitive)
    {
        return compareStringProperty(refProp, targetProp, propName, sbOut, bCaseSensitive, false);
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, StringBuffer sbOut, boolean bCaseSensitive, boolean reqd)
    {
        boolean bMatch;
        if (null == refProp)
        {
            if (null != targetProp)
                return true;
            else
                return false;
        }
        if (null == targetProp)
        {
            if (reqd)
                sbOut.append("ERROR:  property " + propName + " value " + refProp + "not found in XML:  <BR/>");
            return false;
        }

        bMatch = refProp.equalsIgnoreCase(targetProp);
        if (bMatch)
        {
            if ((bCaseSensitive) && (!(bMatch = refProp.equals(targetProp))))
                sbOut.append("WARNING : (case mismatch) ");
        }
        else
            sbOut.append("WARNING   ");

        if (!bMatch)
        {
            sbOut.append("property " + propName + " value " + refProp + " doesn't match xml:  " + targetProp + " ; Xml value used <BR/>");
            // mismatch who wins?  assume xmlDoc wins
            return true;
        }
        else if (!reqd)
            sbOut.append("WARNING :  property " + propName + " value " + refProp + " unnecessary in  xml:  <BR/>");

        return false;
    }

    private static boolean compareIntegerProperty(Integer refProp, Integer targetProp, String propName, StringBuffer sbOut)
    {
        if (null == refProp)
        {
            if (null != targetProp)
                return true;
            else
                return false;
        }
        if (null == targetProp)
            return false;

        if (refProp.equals(targetProp))
        {
            sbOut.append("WARNING :  property " + propName + " value " + refProp + " unnecessary in  xml:  <BR/>");
            return false;
        }
        sbOut.append("WARNING:  property " + propName + " value " + refProp + " doesn't match xml:  " + targetProp + " ; Xml value used <BR/>");
        return true;
    }

    private static boolean compareBoolProperty(Boolean refProp, Boolean targetProp, String propName, StringBuffer sbOut)
    {
        if (null == refProp)
        {
            if (null != targetProp)
                return true;
            else
                return false;
        }
        if (null == targetProp)
            return false;       

        if (refProp.equals(targetProp))
        {
            sbOut.append("WARNING:  property " + propName + " value " + refProp + " unnecessary in  xml.  <BR/>");
            return false;
        }
        sbOut.append("WARNING:  property " + propName + " value " + refProp + " doesn't match xml:  " + targetProp + "  ; Xml value used  <BR/>");
        return true;
    }

    public static String compareOldToNewNames(String dbs)
    {
        Map mcols = new HashMap();
        Map mtabs = new HashMap();
        StringBuffer sbOut = new StringBuffer();

        Iterator it = mcols.values().iterator();
        while (it.hasNext())
        {
            String key = ((String) it.next());
            String oldName = (String) mcols.get(key);
            String newName = key;
            if (!oldName.equals(newName))
                sbOut.append("<br/>Mismatch on col name: " + key + "  oldName: " + oldName + " newName: " + newName);
        }
        it = mtabs.values().iterator();
        while (it.hasNext())
        {
            String key = ((String) it.next());
            String oldName = (String) mtabs.get(key);
            String newName = key;
            if (!oldName.equals(newName))
                sbOut.append("<br/>Mismatch on tab name: " + key + "  oldName: " + oldName + " newName: " + newName);
        }

        return sbOut.toString();
    }

    private static String getOldName(String newName, Map mnames, StringBuffer msgs)
    {
        String sqlNameFromList = (String) mnames.get(newName);

        if ((null == sqlNameFromList) || newName.equals(sqlNameFromList))
            return newName;

        msgs.append("---------- INFO:   SqlNames:  name " + newName + " changed to " + sqlNameFromList + "<BR/>");
        return sqlNameFromList;
    }

}
