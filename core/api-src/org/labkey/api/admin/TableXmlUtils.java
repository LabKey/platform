/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
package org.labkey.api.admin;

import org.apache.log4j.Logger;
import org.labkey.api.admin.sitevalidation.SiteValidationResult;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.resource.Resource;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.OntologyType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;

import java.io.InputStream;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * User: phussey
 * Date: Sep 19, 2005
 * Time: 11:09:11 PM
 */
public class TableXmlUtils
{
    private static final Logger _log = Logger.getLogger(TableXmlUtils.class);

    // Careful: don't use DbSchema.get(), schema.getTable(), or schema.getTables() -- we don't want schema.xml applied
    // and we don't want to cache these TableInfos (because schema.xml hasn't been applied).
    public static TablesDocument createXmlDocumentFromDatabaseMetaData(DbScope scope, String schemaName, boolean bFull) throws Exception
    {
        DbSchema dbSchema = DbSchema.createFromMetaData(scope, schemaName, DbSchemaType.Bare);
        TablesDocument xmlTablesDoc = TablesDocument.Factory.newInstance();
        TablesType xmlTables = xmlTablesDoc.addNewTables();

        for (String tableName : dbSchema.getTableNames())
        {
            SchemaTableInfo tableInfo = dbSchema.createTableFromDatabaseMetaData(tableName);

            if (null == tableInfo)
                throw new IllegalStateException("getTableNames() returned a name that doesn't exist in the database: " + tableName);

            TableType xmlTable = xmlTables.addNewTable();
            tableInfo.copyToXml(xmlTable, bFull);
        }

        return xmlTablesDoc;
    }

    public static SiteValidationResultList compareXmlToMetaData(DbSchema schema, boolean bFull, boolean bCaseSensitive, boolean errorOnXmlMiss)
    {
        SiteValidationResultList resultList = new SiteValidationResultList();

        try
        {
            TablesDocument tablesDocFromDatabaseMetaData = createXmlDocumentFromDatabaseMetaData(schema.getScope(), schema.getName(), false);

            if (null != tablesDocFromDatabaseMetaData)
            {
                Resource r = schema.getSchemaResource();

                if (null != r)
                {
                    try (InputStream xmlStream = r.getInputStream())
                    {
                        if (null != xmlStream)
                        {
                            TablesDocument tablesDocFromXml = TablesDocument.Factory.parse(xmlStream);

                            if (null != tablesDocFromXml)
                                compareTableDocuments(tablesDocFromDatabaseMetaData, tablesDocFromXml, bFull, bCaseSensitive, null, resultList, errorOnXmlMiss);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Exception loading schema " + schema.getDisplayName(), e);
            resultList.addError("+++ ERROR: Exception " + e.getMessage());
        }

        return resultList;
    }

    private static void compareTableDocuments(TablesDocument dbTablesDoc,
                                              TablesDocument xmlTablesDoc,
                                              boolean bFull,
                                              boolean bCaseSensitive,
                                              TablesDocument mergedTablesDoc,
                                              SiteValidationResultList rlOut, boolean errorOnXmlMiss)
    {
        boolean merge = (null != mergedTablesDoc);
        boolean bCopyTargetNode;
        TableType[] dbTables;
        TableType[] xmlTables;
        TableType mt = null;
        ColumnType mc = null;
        String xmlTableName;
        String xmlTableType;
        String dbTableName;
        String dbColName;
        String xmlColName;
        int idt;
        int idc;
        ColumnType[] dbCols;
        ColumnType[] xmlCols;
        SortedMap<String, Integer> mXmlTableOrdinals;
        SortedMap<String, Integer> mXmlColOrdinals;
        SortedMap<String, Integer> mDbTableOrdinals;
        SortedMap<String, Integer> mDbColOrdinals;

        try
        {
            dbTables = dbTablesDoc.getTables().getTableArray();
            xmlTables = xmlTablesDoc.getTables().getTableArray();

            mXmlTableOrdinals = new TreeMap<>();
            for (int i = 0; i < xmlTables.length; i++)
            {
                xmlTableName = xmlTables[i].getTableName();
                if (mXmlTableOrdinals.containsKey(xmlTableName.toLowerCase()))
                    rlOut.addError("ERROR: TableName \"").append(xmlTableName).append("\" duplicated in XML.");
                else
                    mXmlTableOrdinals.put(xmlTableName.toLowerCase(), i);
            }

            mDbTableOrdinals = new TreeMap<>();
            for (int i = 0; i < dbTables.length; i++)
            {
                dbTableName = dbTables[i].getTableName();
                mDbTableOrdinals.put(dbTableName.toLowerCase(), i);
            }

            for (TableType xmlTable : xmlTables)
            {
                xmlTableName = xmlTable.getTableName();
                xmlTableType = xmlTable.getTableDbType();

                if (!(mDbTableOrdinals.containsKey(xmlTableName.toLowerCase())))
                {
                    if (xmlTableType.equals("UNKNOWN"))
                    {
                        continue;
                    }

                    if (!xmlTableType.equals("NOT_IN_DB"))
                    {
                        rlOut.addBlank();
                        rlOut.addError("ERROR: TableName \"").append(xmlTableName).append("\" type \"").append(xmlTableType).append("\" found in XML but not in database.");
                    }
                    else
                    {
                        if (merge)
                        {
                            //copy XML-only node to end of table array
                            int size = mergedTablesDoc.getTables().getTableArray().length;
                            mergedTablesDoc.getTables().addNewTable();
                            mt = (TableType) xmlTable.copy();
                            mergedTablesDoc.getTables().setTableArray(size, mt);
                        }
                    }
                    continue;
                }

                idt = mDbTableOrdinals.get(xmlTableName.toLowerCase());

                if (merge)
                {
                    mt = mergedTablesDoc.getTables().addNewTable();
                    mt.addNewColumns();
                }

                TableType tt = dbTables[idt];
                compareStringProperty(tt.getTableName(), xmlTable.getTableName(), "TableName", rlOut, bCaseSensitive, true);
                if (merge)
                    mt.setTableName(xmlTable.getTableName());

                // Special value "UNKNOWN" means the type is different on different databases, e.g., some of the tables/views/synonyms in the test schema
                if (!"UNKNOWN".equals(xmlTable.getTableDbType()))
                    compareStringProperty(tt.getTableDbType(), xmlTable.getTableDbType(), "TableDbType", rlOut, true, true);

                if (merge)
                    mt.setTableDbType(xmlTable.getTableDbType());

                if (bFull)
                {
                    bCopyTargetNode = compareStringProperty(tt.getTableTitle(), xmlTable.getTableTitle(), "TableTitle", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableTitle(xmlTable.getTableTitle());

                    bCopyTargetNode = compareStringProperty(tt.getTableGroup(), xmlTable.getTableGroup(), "TableGroup", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableGroup(xmlTable.getTableGroup());

                    bCopyTargetNode = compareStringProperty(tt.getDbTableName(), xmlTable.getDbTableName(), "DbTableName", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setDbTableName(xmlTable.getDbTableName());

                    bCopyTargetNode = compareStringProperty(tt.getPkColumnName(), xmlTable.getPkColumnName(), "PkColumnName", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setPkColumnName(xmlTable.getPkColumnName());

                    bCopyTargetNode = compareStringProperty(tt.getVersionColumnName(), xmlTable.getVersionColumnName(), "VersionColumnName", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setVersionColumnName(xmlTable.getVersionColumnName());

                    bCopyTargetNode = compareStringProperty(tt.getTableUrl().getStringValue(), xmlTable.getTableUrl().getStringValue(), "TableUrl", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTableUrl(xmlTable.getTableUrl());

                    bCopyTargetNode = compareStringProperty(tt.getNextStep(), xmlTable.getNextStep(), "NextStep", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setNextStep(xmlTable.getNextStep());

                    bCopyTargetNode = compareStringProperty(tt.getTitleColumn(), xmlTable.getTitleColumn(), "TitleColumn", rlOut, bCaseSensitive);
                    if (merge && bCopyTargetNode)
                        mt.setTitleColumn(xmlTable.getTitleColumn());


                    bCopyTargetNode = compareBoolProperty((tt.isSetManageTableAllowed() ? tt.getManageTableAllowed() : null),
                            (xmlTable.isSetManageTableAllowed() ? xmlTable.getManageTableAllowed() : null),
                            "ManageTableAllowed", rlOut);
                    if (merge && bCopyTargetNode)
                        mt.setManageTableAllowed(xmlTable.getManageTableAllowed());
                }

                dbCols = tt.getColumns().getColumnArray();
                if (null != xmlTable.getColumns())
                    xmlCols = xmlTable.getColumns().getColumnArray();
                else
                    xmlCols = new ColumnType[0];

                mXmlColOrdinals = new TreeMap<>();
                mDbColOrdinals = new TreeMap<>();

                for (int m = 0; m < xmlCols.length; m++)
                {
                    xmlColName = xmlCols[m].getColumnName();
                    if (mXmlColOrdinals.containsKey(xmlColName.toLowerCase()))
                        rlOut.addError("ERROR: Table \"").append(xmlTable.getTableName()).append("\" column \"").append(xmlColName).append("\" duplicated in XML.");
                    else
                        mXmlColOrdinals.put(xmlColName.toLowerCase(), m);
                }

                for (int m = 0; m < dbCols.length; m++)
                {
                    dbColName = dbCols[m].getColumnName();
                    mDbColOrdinals.put(dbColName.toLowerCase(), m);
                }

                for (ColumnType xmlCol : xmlCols)
                {
                    xmlColName = xmlCol.getColumnName();
                    boolean isColumnInDatabase = mDbColOrdinals.containsKey(xmlColName.toLowerCase());

                    if (null == xmlCol.getWrappedColumnName())
                    {
                        if (!isColumnInDatabase)
                        {
                            rlOut.addError("ERROR: Table \"").append(xmlTable.getTableName()).append("\", column \"").append(xmlColName).append("\" found in XML but not in database.");
                            continue;
                        }
                    }
                    else
                    {
                        if (isColumnInDatabase)
                        {
                            rlOut.addError("ERROR: Table \"").append(xmlTable.getTableName()).append("\", column \"").append(xmlColName).append("\" found in database but shouldn't be, since it's a wrapped column");
                        }

                        continue;   // Skip further checks for wrapped columns... they aren't in the database
                    }

                    idc = mDbColOrdinals.get(xmlColName.toLowerCase());
                    ColumnType columnType = dbCols[idc];

                    compareStringProperty(columnType.getColumnName(), xmlCol.getColumnName(), "ColumnName", rlOut, bCaseSensitive, true);

                    if (merge)
                    {
                        mc = mt.getColumns().addNewColumn();
                        mc.setColumnName(xmlCol.getColumnName());
                    }

                    if (bFull)
                    {
                        SiteValidationResultList rlTmp = new SiteValidationResultList();

                        bCopyTargetNode = compareStringProperty(columnType.getDatatype(), xmlCol.getDatatype(), "Datatype", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDatatype(xmlCol.getDatatype());

                        bCopyTargetNode = compareStringProperty(columnType.getColumnTitle(), xmlCol.getColumnTitle(), "ColumnTitle", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setColumnTitle(xmlCol.getColumnTitle());

                        bCopyTargetNode = compareStringProperty(columnType.getDefaultValue(), xmlCol.getDefaultValue(), "DefaultValue", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDefaultValue(xmlCol.getDefaultValue());

                        bCopyTargetNode = compareStringProperty(columnType.getAutoFillValue(), xmlCol.getAutoFillValue(), "AutoFillValue", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setAutoFillValue(xmlCol.getAutoFillValue());

                        bCopyTargetNode = compareStringProperty(columnType.getInputType(), xmlCol.getInputType(), "InputType", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setInputType(xmlCol.getInputType());

                        bCopyTargetNode = compareStringProperty(columnType.getOnChange(), xmlCol.getOnChange(), "OnChange", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setOnChange(xmlCol.getOnChange());

                        bCopyTargetNode = compareStringProperty(columnType.getDescription(), xmlCol.getDescription(), "ColumnText", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDescription(xmlCol.getDescription());

                        bCopyTargetNode = compareStringProperty(columnType.getOptionlistQuery(), xmlCol.getOptionlistQuery(), "OptionlistQuery", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setOptionlistQuery(xmlCol.getOptionlistQuery());

                        bCopyTargetNode = compareStringProperty(columnType.getUrl().getStringValue(), xmlCol.getUrl().getStringValue(), "Url", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setUrl(xmlCol.getUrl());

                        bCopyTargetNode = compareStringProperty(columnType.getFormatString(), xmlCol.getFormatString(), "FormatString", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setFormatString(xmlCol.getFormatString());

                        bCopyTargetNode = compareStringProperty(columnType.getTextAlign(), xmlCol.getTextAlign(), "TextAlign", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setTextAlign(xmlCol.getTextAlign());

                        bCopyTargetNode = compareStringProperty(columnType.getPropertyURI(), xmlCol.getPropertyURI(), "PropertyURI", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setPropertyURI(xmlCol.getPropertyURI());

                        bCopyTargetNode = compareStringProperty(columnType.getDisplayWidth(), xmlCol.getDisplayWidth(), "DisplayWidth", rlTmp, bCaseSensitive);
                        if (merge && bCopyTargetNode)
                            mc.setDisplayWidth(xmlCol.getDisplayWidth());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetScale() ? columnType.getScale() : null),
                                (xmlCol.isSetScale() ? xmlCol.getScale() : null),
                                "Scale", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setScale(xmlCol.getScale());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetPrecision() ? columnType.getPrecision() : null),
                                (xmlCol.isSetPrecision() ? xmlCol.getPrecision() : null),
                                "Precision", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setPrecision(xmlCol.getPrecision());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetInputLength() ? columnType.getInputLength() : null),
                                (xmlCol.isSetInputLength() ? xmlCol.getInputLength() : null),
                                "InputLength", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setInputLength(xmlCol.getInputLength());

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetInputRows() ? columnType.getInputRows() : null),
                                (xmlCol.isSetInputRows() ? xmlCol.getInputRows() : null),
                                "InputRows", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setInputRows(xmlCol.getInputRows());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetNullable() ? columnType.getNullable() : null),
                                (xmlCol.isSetNullable() ? xmlCol.getNullable() : null),
                                "Nullable", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setNullable(xmlCol.getNullable());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsAutoInc() ? columnType.getIsAutoInc() : null),
                                (xmlCol.isSetIsAutoInc() ? xmlCol.getIsAutoInc() : null),
                                "IsAutoInc", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsAutoInc(xmlCol.getIsAutoInc());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsDisplayColumn() ? columnType.getIsDisplayColumn() : null),
                                (xmlCol.isSetIsDisplayColumn() ? xmlCol.getIsDisplayColumn() : null),
                                "IsDisplayColumn", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsDisplayColumn(xmlCol.getIsDisplayColumn());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsReadOnly() ? columnType.getIsReadOnly() : null),
                                (xmlCol.isSetIsReadOnly() ? xmlCol.getIsReadOnly() : null),
                                "IsReadOnly", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsReadOnly(xmlCol.getIsReadOnly());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsUserEditable() ? columnType.getIsUserEditable() : null),
                                (xmlCol.isSetIsUserEditable() ? xmlCol.getIsUserEditable() : null),
                                "IsUserEditable", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsUserEditable(xmlCol.getIsUserEditable());

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsKeyField() ? columnType.getIsKeyField() : null),
                                (xmlCol.isSetIsKeyField() ? xmlCol.getIsKeyField() : null),
                                "IsKeyField", rlTmp);
                        if (merge && bCopyTargetNode)
                            mc.setIsKeyField(xmlCol.getIsKeyField());

                        // check and merge FK property
                        ColumnType.Fk fk;
                        boolean declFk = false;
                        if ((null != columnType.getFk()))
                            declFk = true;

                        if (null != xmlCol.getFk())
                        {
                            compareStringProperty((declFk ? columnType.getFk().getFkColumnName() : null)
                                    , xmlCol.getFk().getFkColumnName()
                                    , "FkColumnName", rlTmp, bCaseSensitive);

                            compareStringProperty((declFk ? columnType.getFk().getFkTable() : null)
                                    , xmlCol.getFk().getFkTable()
                                    , "FkTable", rlTmp, bCaseSensitive);

                            bCopyTargetNode = compareStringProperty((declFk ? columnType.getFk().getFkDbSchema() : null)
                                    , xmlCol.getFk().getFkDbSchema()
                                    , "FkDbSchema", rlTmp, bCaseSensitive);

                            // if FK is declared in xml use it as a whole node, don't mix and match.
                            if (merge)
                            {
                                fk = mc.addNewFk();
                                fk.setFkColumnName(xmlCol.getFk().getFkColumnName());
                                fk.setFkTable(xmlCol.getFk().getFkTable());
                                if (bCopyTargetNode)
                                    fk.setFkDbSchema(xmlCol.getFk().getFkDbSchema());
                            }
                        }

                        // check and merge Ontology (assumed not from metadata)
                        if (null != columnType.getOntology())
                        {
                            rlTmp.addError("ERROR: Table ").append(tt.getTableName()).append(" Unexpected Ontology node in dbTablesDoc xmldoc from metadata, ColName ").append(columnType);
                            continue;
                        }

                        if (merge && (null != xmlCol.getOntology()))
                        {
                            OntologyType o = mc.addNewOntology();
                            o.setStringValue(xmlCol.getOntology().getStringValue());
                            o.setRefId(xmlCol.getOntology().getRefId());
                            if (null != xmlCol.getOntology().getSource())
                                o.setSource(xmlCol.getOntology().getSource());
                        }

                        if (!rlTmp.getResults().isEmpty())
                        {
                            rlOut.addBlank();
                            rlOut.addInfo("Table ").append(tt.getTableName()).append(" column ").append(columnType.getColumnName()).append(" errors and warnings");
                            rlOut.addAll(rlTmp);
                        }
                    }

                    mDbColOrdinals.remove(xmlColName.toLowerCase());
                }
                // now check any extra columns in the db
                for (String dbCol : mDbColOrdinals.keySet())
                {
                    idc = mDbColOrdinals.get(dbCol);
                    SiteValidationResult result;
                    if (errorOnXmlMiss)
                        result = rlOut.addError("ERROR: ");
                    else result = rlOut.addWarn("WARNING: ");
                    result.append("Table \"").append(tt.getTableName()).append("\", column \"").append(dbCol).append("\" missing from XML.");

                    if (merge)
                    {
                        mc = mt.getColumns().addNewColumn();
                        mc.setColumnName(tt.getColumns().getColumnArray(idc).getColumnName());
                    }
                }

                mDbTableOrdinals.remove(xmlTableName.toLowerCase());
            }
            // now check any extra tabledefs in the db
            for (String dbTab : mDbTableOrdinals.keySet())
            {
                if (dbTab.startsWith("_"))
                    continue;
                idt = mDbTableOrdinals.get(dbTab);
                TableType tt = dbTables[idt];
                SiteValidationResult result;
                if (errorOnXmlMiss)
                    result = rlOut.addError("ERROR: ");
                else result = rlOut.addWarn("WARNING: ");
                result.append("Table \"").append(dbTab).append("\" missing from XML.");
                if (merge)
                {
                    //copy db node to end of table array
                    mt = mergedTablesDoc.getTables().addNewTable();
                    mt.setTableName(tt.getTableName());
                    mt.setTableDbType(tt.getTableDbType());
                    mt.addNewColumns();
                    for (int i=0;i<tt.getColumns().getColumnArray().length; i++)
                    {
                        mc=mt.getColumns().addNewColumn();
                        mc.setColumnName(tt.getColumns().getColumnArray(i).getColumnName());
                    }
                }
            }

        }
        catch (Exception e)
        {
            rlOut.addError("ERROR: Exception in compare: ").append(e.getMessage());
            Logger.getLogger(TableXmlUtils.class).warn(e);
        }
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, SiteValidationResultList rlOut, boolean bCaseSensitive)
    {
        return compareStringProperty(refProp, targetProp, propName, rlOut, bCaseSensitive, false);
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, SiteValidationResultList rlOut, boolean bCaseSensitive, boolean reqd)
    {
        boolean bMatch;
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
        {
            if (reqd)
                rlOut.addError("ERROR: property ").append(propName).append(" value ").append(refProp).append("not found in XML:");
            return false;
        }

        bMatch = refProp.equalsIgnoreCase(targetProp);
        SiteValidationResult mismatchWarn = SiteValidationResult.Level.WARN.create();
        if (bMatch)
        {
            if ((bCaseSensitive) && (!(bMatch = refProp.equals(targetProp))))
                mismatchWarn = rlOut.addWarn("WARNING: (case mismatch) ");
        }
        else
            mismatchWarn = rlOut.addWarn("WARNING:   ");

        if (!bMatch)
        {
            mismatchWarn.append("property ").append(propName).append(" value ").append(refProp).append(" doesn't match XML: ").append(targetProp).append(" ; XML value used");
            // mismatch who wins?  assume xmlDoc wins
            return true;
        }
        else if (!reqd)
            mismatchWarn.append("WARNING: property ").append(propName).append(" value ").append(refProp).append(" unnecessary in XML:");

        return false;
    }

    private static boolean compareIntegerProperty(Integer refProp, Integer targetProp, String propName, SiteValidationResultList rlOut)
    {
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
            return false;

        if (refProp.equals(targetProp))
        {
            rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" unnecessary in  XML:");
            return false;
        }
        rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" doesn't match XML: ").append(targetProp).append(" ; XML value used");
        return true;
    }

    private static boolean compareBoolProperty(Boolean refProp, Boolean targetProp, String propName, SiteValidationResultList rlOut)
    {
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
            return false;       

        if (refProp.equals(targetProp))
        {
            rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" unnecessary in XML.");
            return false;
        }
        rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" doesn't match XML: ").append(targetProp).append("  ; XML value used");
        return true;
    }
}
