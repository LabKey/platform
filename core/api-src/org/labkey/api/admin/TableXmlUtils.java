/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.admin.sitevalidation.SiteValidationResult;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * User: phussey
 * Date: Sep 19, 2005
 * Time: 11:09:11 PM
 */
public class TableXmlUtils
{
    private static final Logger _log = LogManager.getLogger(TableXmlUtils.class);

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
            TablesDocument tablesDocFromXml = DbScope.getSchemaXml(schema);

            if (null != tablesDocFromXml)
                compareTableDocuments(tablesDocFromDatabaseMetaData, tablesDocFromXml, bFull, bCaseSensitive, resultList, errorOnXmlMiss, schema);
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
                                              SiteValidationResultList rlOut,
                                              boolean errorOnXmlMiss,
                                              DbSchema schema)
    {
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
                    continue;
                }

                idt = mDbTableOrdinals.get(xmlTableName.toLowerCase());

                TableType tt = dbTables[idt];
                compareStringProperty(tt.getTableName(), xmlTable.getTableName(), "TableName", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName(), true);

                // Special value "UNKNOWN" means the type is different on different databases, e.g., some of the tables/views/synonyms in the test schema
                if (!"UNKNOWN".equals(xmlTable.getTableDbType()))
                    compareStringProperty(tt.getTableDbType(), xmlTable.getTableDbType(), "TableDbType", rlOut, true, schema.getName() + "." + tt.getTableName(), true);

                if (bFull)
                {
                   compareStringProperty(tt.getTableTitle(), xmlTable.getTableTitle(), "TableTitle", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());

                   compareStringProperty(tt.getTableGroup(), xmlTable.getTableGroup(), "TableGroup", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());

                   compareStringProperty(tt.getDbTableName(), xmlTable.getDbTableName(), "DbTableName", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());

                   compareStringProperty(tt.getPkColumnName(), xmlTable.getPkColumnName(), "PkColumnName", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());

                   compareStringProperty(tt.getVersionColumnName(), xmlTable.getVersionColumnName(), "VersionColumnName", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());

                   compareStringProperty(tt.getTableUrl().getStringValue(), xmlTable.getTableUrl().getStringValue(), "TableUrl", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());

                   compareStringProperty(tt.getNextStep(), xmlTable.getNextStep(), "NextStep", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());

                   compareStringProperty(tt.getTitleColumn(), xmlTable.getTitleColumn(), "TitleColumn", rlOut, bCaseSensitive, schema.getName() + "." + tt.getTableName());


                   compareBoolProperty((tt.isSetManageTableAllowed() ? tt.getManageTableAllowed() : null),
                            (xmlTable.isSetManageTableAllowed() ? xmlTable.getManageTableAllowed() : null),
                            "ManageTableAllowed", rlOut, schema.getName() + "." + tt.getTableName());
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

                    String problematicItem = schema.getName() + "." + tt.getTableName() + "." + columnType.getColumnName();

                    compareStringProperty(columnType.getColumnName(), xmlCol.getColumnName(), "ColumnName", rlOut, bCaseSensitive, problematicItem, true);

                    if (bFull)
                    {
                        SiteValidationResultList rlTmp = new SiteValidationResultList();

                        compareStringProperty(columnType.getDatatype(), xmlCol.getDatatype(), "Datatype", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getColumnTitle(), xmlCol.getColumnTitle(), "ColumnTitle", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getDefaultValue(), xmlCol.getDefaultValue(), "DefaultValue", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getAutoFillValue(), xmlCol.getAutoFillValue(), "AutoFillValue", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getInputType(), xmlCol.getInputType(), "InputType", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getOnChange(), xmlCol.getOnChange(), "OnChange", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getDescription(), xmlCol.getDescription(), "ColumnText", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getOptionlistQuery(), xmlCol.getOptionlistQuery(), "OptionlistQuery", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getUrl().getStringValue(), xmlCol.getUrl().getStringValue(), "Url", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getFormatString(), xmlCol.getFormatString(), "FormatString", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getTextAlign(), xmlCol.getTextAlign(), "TextAlign", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getPropertyURI(), xmlCol.getPropertyURI(), "PropertyURI", rlTmp, bCaseSensitive, problematicItem);

                        compareStringProperty(columnType.getDisplayWidth(), xmlCol.getDisplayWidth(), "DisplayWidth", rlTmp, bCaseSensitive, problematicItem);

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetScale() ? columnType.getScale() : null),
                                (xmlCol.isSetScale() ? xmlCol.getScale() : null),
                                "Scale", rlTmp, problematicItem);

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetPrecision() ? columnType.getPrecision() : null),
                                (xmlCol.isSetPrecision() ? xmlCol.getPrecision() : null),
                                "Precision", rlTmp, problematicItem);

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetInputLength() ? columnType.getInputLength() : null),
                                (xmlCol.isSetInputLength() ? xmlCol.getInputLength() : null),
                                "InputLength", rlTmp, problematicItem);

                        bCopyTargetNode = compareIntegerProperty(
                                (columnType.isSetInputRows() ? columnType.getInputRows() : null),
                                (xmlCol.isSetInputRows() ? xmlCol.getInputRows() : null),
                                "InputRows", rlTmp, problematicItem);

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetNullable() ? columnType.getNullable() : null),
                                (xmlCol.isSetNullable() ? xmlCol.getNullable() : null),
                                "Nullable", rlTmp, problematicItem);

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsAutoInc() ? columnType.getIsAutoInc() : null),
                                (xmlCol.isSetIsAutoInc() ? xmlCol.getIsAutoInc() : null),
                                "IsAutoInc", rlTmp, problematicItem);

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsDisplayColumn() ? columnType.getIsDisplayColumn() : null),
                                (xmlCol.isSetIsDisplayColumn() ? xmlCol.getIsDisplayColumn() : null),
                                "IsDisplayColumn", rlTmp, problematicItem);

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsReadOnly() ? columnType.getIsReadOnly() : null),
                                (xmlCol.isSetIsReadOnly() ? xmlCol.getIsReadOnly() : null),
                                "IsReadOnly", rlTmp, problematicItem);

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsUserEditable() ? columnType.getIsUserEditable() : null),
                                (xmlCol.isSetIsUserEditable() ? xmlCol.getIsUserEditable() : null),
                                "IsUserEditable", rlTmp, problematicItem);

                        bCopyTargetNode = compareBoolProperty(
                                (columnType.isSetIsKeyField() ? columnType.getIsKeyField() : null),
                                (xmlCol.isSetIsKeyField() ? xmlCol.getIsKeyField() : null),
                                "IsKeyField", rlTmp, problematicItem);

                        // check and merge FK property
                        boolean declFk = null != columnType.getFk();

                        if (null != xmlCol.getFk())
                        {
                            compareStringProperty((declFk ? columnType.getFk().getFkColumnName() : null)
                                    , xmlCol.getFk().getFkColumnName()
                                    , "FkColumnName", rlTmp, bCaseSensitive, problematicItem);

                            compareStringProperty((declFk ? columnType.getFk().getFkTable() : null)
                                    , xmlCol.getFk().getFkTable()
                                    , "FkTable", rlTmp, bCaseSensitive, problematicItem);

                            bCopyTargetNode = compareStringProperty((declFk ? columnType.getFk().getFkDbSchema() : null)
                                    , xmlCol.getFk().getFkDbSchema()
                                    , "FkDbSchema", rlTmp, bCaseSensitive, problematicItem);
                        }

                        // check and merge Ontology (assumed not from metadata)
                        if (null != columnType.getOntology())
                        {
                            rlTmp.addError("ERROR: Table ").append(tt.getTableName()).append(" Unexpected Ontology node in dbTablesDoc xmldoc from metadata, ColName ").append(columnType);
                            continue;
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
                    mDbColOrdinals.get(dbCol);
                    SiteValidationResult result;
                    if (errorOnXmlMiss)
                        result = rlOut.addError("ERROR: ");
                    else result = rlOut.addWarn("WARNING: ");
                    result.append("Table \"").append(tt.getTableName()).append("\", column \"").append(dbCol).append("\" missing from XML.");
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
            }

        }
        catch (Exception e)
        {
            rlOut.addError("ERROR: Exception in compare: ").append(e.getMessage());
            LogManager.getLogger(TableXmlUtils.class).warn(e);
        }
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, SiteValidationResultList rlOut, boolean bCaseSensitive, String problematicItem)
    {
        return compareStringProperty(refProp, targetProp, propName, rlOut, bCaseSensitive, problematicItem, false);
    }

    private static boolean compareStringProperty(String refProp, String targetProp, String propName, SiteValidationResultList rlOut, boolean bCaseSensitive, String problematicItem, boolean reqd)
    {
        boolean bMatch;
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
        {
            if (reqd)
                rlOut.addError("ERROR: property ").append(propName).append(" value ").append(refProp).append(" for ").append(problematicItem).append(" not found in XML:");
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
            mismatchWarn.append("property ").append(propName).append(" value ").append(refProp).append(" for ").append(problematicItem).append(" doesn't match XML: ").append(targetProp).append(" ; XML value used");
            // mismatch who wins?  assume xmlDoc wins
            return true;
        }
        else if (!reqd)
            mismatchWarn.append("WARNING: property ").append(propName).append(" value ").append(refProp).append(" for ").append(problematicItem).append(" unnecessary in XML:");

        return false;
    }

    private static boolean compareIntegerProperty(Integer refProp, Integer targetProp, String propName, SiteValidationResultList rlOut, String problematicItem)
    {
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
            return false;

        if (refProp.equals(targetProp))
        {
            rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" for ").append(problematicItem).append(" unnecessary in  XML:");
            return false;
        }
        rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" for ").append(problematicItem).append(" doesn't match XML: ").append(targetProp).append(" ; XML value used");
        return true;
    }

    private static boolean compareBoolProperty(Boolean refProp, Boolean targetProp, String propName, SiteValidationResultList rlOut, String problematicItem)
    {
        if (null == refProp)
        {
            return null != targetProp;
        }
        if (null == targetProp)
            return false;       

        if (refProp.equals(targetProp))
        {
            rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" for ").append(problematicItem).append(" unnecessary in XML.");
            return false;
        }
        rlOut.addWarn("WARNING: property ").append(propName).append(" value ").append(refProp).append(" doesn't match XML: ").append(targetProp).append(" for ").append(problematicItem).append("  ; XML value used");
        return true;
    }
}
