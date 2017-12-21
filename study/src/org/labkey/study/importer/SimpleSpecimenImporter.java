/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.study.importer;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenImportStrategy;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;
import org.springframework.jdbc.BadSqlGrammarException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Mark Igra
 * Date: Apr 26, 2007
 * Time: 1:17:36 PM
 */
public class SimpleSpecimenImporter extends SpecimenImporter
{
    public static final String RECORD_ID = "record_id";
    public static final String VIAL_ID = "global_unique_specimen_id";
    public static final String SAMPLE_ID = "specimen_number";
    public static final String DRAW_TIMESTAMP = "draw_timestamp";
    public static final String VISIT = "visit_value";
    public static final String VOLUME = "volume";
    public static final String UNITS = "volume_units";
    public static final String PRIMARY_SPECIMEN_TYPE = "primary_specimen_type";
    public static final String DERIVIATIVE_TYPE = "derivative_type";
    public static final String ADDITIVE_TYPE = "additive_type";
    public static final String PARTICIPANT_ID = "ptid";

    private static final Map<String, String> DEFAULT_COLUMN_LABELS = new CaseInsensitiveHashMap<>();

    static
    {
        DEFAULT_COLUMN_LABELS.put(VIAL_ID, "Global Unique Id");
        DEFAULT_COLUMN_LABELS.put(SAMPLE_ID, "Sample Id");
        DEFAULT_COLUMN_LABELS.put(DRAW_TIMESTAMP, "Draw Timestamp");
        DEFAULT_COLUMN_LABELS.put(VISIT, "Visit");
        DEFAULT_COLUMN_LABELS.put(VOLUME, "Volume");
        DEFAULT_COLUMN_LABELS.put(UNITS, "Volume Units");
        DEFAULT_COLUMN_LABELS.put(PRIMARY_SPECIMEN_TYPE, "Primary Type");
        DEFAULT_COLUMN_LABELS.put(ADDITIVE_TYPE, "Additive Type");
        DEFAULT_COLUMN_LABELS.put(DERIVIATIVE_TYPE, "Derivative Type");
        DEFAULT_COLUMN_LABELS.put(PARTICIPANT_ID, "Subject Id");
    }

    private Map<String, String> _columnLabels;
    private final TimepointType _timepointType;

    public SimpleSpecimenImporter(Container container, User user)
    {
        this(container, user, TimepointType.DATE, "Subject");
    }

    public SimpleSpecimenImporter(Container container, User user, TimepointType timepointType, String participantIdLabel)
    {
        super(container, user);
        _timepointType = timepointType;
        _columnLabels = new HashMap<>(DEFAULT_COLUMN_LABELS);
        _columnLabels.put(PARTICIPANT_ID, participantIdLabel + " Id");
    }

    public String label(String columnName)
    {
        String str = _columnLabels.get(columnName);
        return str == null ? columnName : str;
    }

    public void setColumnLabels(Map<String, String> map)
    {
        _columnLabels = map;
    }

    public Map<String,String> getColumnLabels()
    {
        return _columnLabels;
    }

    // TODO: Remove this? Never called...
    public void process(File tsvFile, boolean merge, Logger logger) throws IOException, ConversionException, ValidationException
    {
        TabLoader tl = new TabLoader(tsvFile);
        tl.setThrowOnErrors(true);
        fixupSpecimenColumns(tl);

        _process(tl.load(), merge, logger);
    }

    public void fixupSpecimenColumns(TabLoader tl) throws IOException
    {
        ColumnDescriptor[] cols = tl.getColumns();
        ColumnDescriptor[] mappedCols = new ColumnDescriptor[cols.length];

        for (int i = 0; i < cols.length; i++)
        {
            ColumnDescriptor col = cols[i];
            SpecimenColumn specCol = findSpecimenColumn(col.name);
            if (null == specCol)
            {
                //Default cols should be type string so lookups can be created
                col.clazz = String.class;
                mappedCols[i] = col;
            }
            else
                mappedCols[i] = specCol.getColumnDescriptor();

        }
        tl.setColumns(mappedCols);
    }

    // UNDONE: Converting values belongs in _process
    public List<Map<String, Object>> fixupSpecimenRows(List<Map<String, Object>> rows)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows)
            result.add(fixupSpecimenRow(row));
        return result;
    }

    private Map<String, Object> fixupSpecimenRow(Map<String, Object> row)
    {
        Map<String, Object> result = new HashMap<>();
        for (String key : row.keySet())
        {
            Object value = row.get(key);
            if (value instanceof String)
            {
                SpecimenColumn specCol = findSpecimenColumn(key);
                Class type = String.class;
                if (null != specCol)
                    type = specCol.getColumnDescriptor().clazz;
                value = ConvertUtils.convert((String)value, type);
            }
            result.put(key, value);
        }

        return result;
    }

    public ColumnDescriptor[] getSimpleSpecimenColumns()
    {
        List<String> colNames = new ArrayList<>(Arrays.asList(
                VIAL_ID,
                SAMPLE_ID,
                DRAW_TIMESTAMP,
                PARTICIPANT_ID,
                VOLUME,
                UNITS,
                PRIMARY_SPECIMEN_TYPE,
                DERIVIATIVE_TYPE,
                ADDITIVE_TYPE));
        if (_timepointType == TimepointType.VISIT)
            colNames.add(3, VISIT);

        ColumnDescriptor[] cols = new ColumnDescriptor[colNames.size()];

        for (int i = 0; i < cols.length; i++)
        {
            SpecimenColumn specCol = findSpecimenColumn(colNames.get(i));
            cols[i] = null == specCol ? new ColumnDescriptor(colNames.get(i), String.class) : specCol.getColumnDescriptor();
        }
        
        return cols;
    }

    public void process(List<Map<String, Object>> rows, boolean merge) throws IOException, ValidationException
    {
        _process(rows, merge, Logger.getLogger(getClass()));
    }

    // Avoid conflict with SpecimenImporter.process() (has similar signature)
    private void _process(List<Map<String, Object>> rows, boolean merge, Logger logger) throws IOException, ValidationException
    {
        //Map from column name to
        Container container = getContainer();
        Study study = StudyManager.getInstance().getStudy(container);
        Map<String, LookupTable> lookupTables = new HashMap<>();
        lookupTables.put("additive_type", new LookupTable(StudySchema.getInstance().getTableInfoSpecimenAdditive(container), container, _additivesTableType, "additive_type_id", "additive_id", "additive", "Additive"));
        lookupTables.put("derivative_type", new LookupTable(StudySchema.getInstance().getTableInfoSpecimenDerivative(container), container, _derivativesTableType, "derivative_type_id", "derivative_id", "derivative", "Derivative"));
        lookupTables.put("primary_specimen_type", new LookupTable(StudySchema.getInstance().getTableInfoSpecimenPrimaryType(container), container, _primaryTypesTableType, "primary_specimen_type_id", "primary_type_id", "primary_type", "PrimaryType"));
        LabLookupTable labLookup =  new LabLookupTable(StudySchema.getInstance().getTableInfoSite(container), container, _labsTableType);
        lookupTables.put("lab", labLookup);

        List<Map<String, Object>> specimenRows = new ArrayList<>();
        int recordId = 1;

        for (Map<String, Object> row : rows)
        {
            Map<String, Object> specimenRow = new HashMap<>();
            for (String colName : row.keySet())
            {
                //If this column should be mapped to an integer lookup, do so
                LookupTable lookupTable = lookupTables.get(colName);
                if (null == lookupTable)
                    specimenRow.put(colName, row.get(colName));
                else
                    specimenRow.put(lookupTable.getForeignKeyCol(), lookupTable.getVal((String) row.get(colName)));
            }
            //Make specimen requestable
            if (null == row.get("lab"))
                specimenRow.put(labLookup.getForeignKeyCol(), labLookup.getDefaultLabId());

            if (study.getTimepointType() != TimepointType.VISIT)
                specimenRow.put(VISIT, StudyManager.sequenceNumFromDate((Date) specimenRow.get(DRAW_TIMESTAMP)));

            if (!row.containsKey(VIAL_ID))
                specimenRow.put(VIAL_ID, specimenRow.get(SAMPLE_ID));
            specimenRow.put("record_id", recordId++);
            specimenRows.add(specimenRow);
        }

        SpecimenImportStrategy strategy = new StandardSpecimenImportStrategy(container);
        Map<SpecimenTableType, SpecimenImportFile> sifMap = new HashMap<>();
        addSpecimenImportFile(sifMap, _specimensTableType, specimenRows, strategy);
        for (LookupTable lookupTable : lookupTables.values())
            addSpecimenImportFile(sifMap, lookupTable.getTableType(), lookupTable.toMaps(), strategy);

        try
        {
            super.process(sifMap, merge, logger, null, true, false);
        }
        catch (ValidationException ex)
        {
            if (logger != null)
                logger.error("Error during import", ex);
            throw ex;
        }
        catch (BadSqlGrammarException ex)
        {
            if (areDuplicateLabIds(labLookup))
                throw new RuntimeException("Error possibly caused by duplicate ExternalIds in Location tables: " + ex.getSQLException().getMessage());
            throw new RuntimeSQLException(ex.getSQLException());
        }
    }


    private void addSpecimenImportFile(Map<SpecimenTableType, SpecimenImportFile> fileNameMap, SpecimenTableType type, List<Map<String, Object>> specimenRows, SpecimenImportStrategy strategy)
    {
        assert null != type : "Unknown type!";
        fileNameMap.put(type, new IteratorSpecimenImportFile(specimenRows, strategy, type));
    }


    private static class LookupTable
    {
        private final SpecimenTableType tableType;
        private final String foreignKeyCol;
        private final String tsvIdCol;
        private final String tsvLabelCol;
        // additive, derivative, primarytype, and site all use ExternalId as the id column.
        private final String dbIdCol = "ExternalId";
        private final String dbRowIdCol = "RowId";
        private final String dbLabelCol;

        // Maps of label -> id
        private final HashMap<String, Integer> existingKeyMap = new HashMap<>();
        private final HashMap<String, Integer> keyMap = new HashMap<>();
        private int minId = 0;
        private int lastId = 0;

        LookupTable(TableInfo table, Container c, SpecimenTableType tableType, String foreignKeyCol, String tsvIdCol, String tsvLabelCol, String dbLabelCol)
        {
            this.tableType = tableType;
            this.foreignKeyCol = foreignKeyCol;
            this.tsvIdCol = tsvIdCol;
            this.tsvLabelCol = tsvLabelCol;
            this.dbLabelCol = dbLabelCol;

            getKeyMap(table, c);
        }

        private void getKeyMap(TableInfo table, Container c)
        {
            final List<Map<String, Object>> missingExternalId = new ArrayList<>();
            Filter filter = SimpleFilter.createContainerFilter(c);
            new TableSelector(table, PageFlowUtil.set(dbRowIdCol, dbIdCol, dbLabelCol), filter, new Sort("RowId")).forEachMap(new ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    Integer id = (Integer)map.get(dbIdCol);
                    if (id == null)
                    {
                        missingExternalId.add(map);
                    }
                    else
                    {
                        existingKeyMap.put((String)map.get(dbLabelCol), id);
                        minId = Math.min(minId, id);
                        lastId = Math.max(lastId, id);
                    }
                }
            });

            // UNDONE: Temporary fix for 11.1: Create fake ExternalId for rows with null ExternalId
            for (Map<String, Object> map : missingExternalId)
            {
                Integer rowId = (Integer)map.get(dbRowIdCol);
                int id = --minId;
                existingKeyMap.put((String)map.get(dbLabelCol), id);

                SQLFragment sql = new SQLFragment();
                sql.append("UPDATE ").append(table);
                sql.append(" SET ").append(dbIdCol).append(" = ?").add(id);
                sql.append(" WHERE ").append(dbRowIdCol).append(" = ?").add(rowId);
                new SqlExecutor(table.getSchema()).execute(sql);
            }
        }

        Integer getVal(String label)
        {
            Integer i = keyMap.get(label);
            if (null == i)
            {
                i = existingKeyMap.get(label);
                if (null == i)
                {
                    i = lastId = lastId+1;
                    keyMap.put(label, i);
                }
                else
                {
                    keyMap.put(label, i);
                }
            }
            return i;
        }

        List<Map<String, Object>> toMaps()
        {
            List<Map<String, Object>> maps = new ArrayList<>(keyMap.size());

            for (Map.Entry<String, Integer> entry : keyMap.entrySet())
            {
                Map<String, Object> m = new HashMap<>();
                m.put(tsvLabelCol, entry.getKey());
                m.put(tsvIdCol, entry.getValue());
                maps.add(m);
            }

            maps.sort((a, b) ->
            {
                Integer aId = (Integer) a.get(tsvIdCol);
                Integer bId = (Integer) b.get(tsvIdCol);
                return aId.compareTo(bId);
            });

            return maps;
        }

        public SpecimenTableType getTableType()
        {
            return tableType;
        }

        public String getTsvIdCol()
        {
            return tsvIdCol;
        }

        public String getForeignKeyCol()
        {
            return foreignKeyCol;
        }

        public Map<String, Integer> getExistingKeyMap()
        {
            return existingKeyMap;
        }
    }

    private static class LabLookupTable extends LookupTable
    {
        static final String DEFAULT_LAB = "Not Specified";
        private final Integer defaultLabId;

        LabLookupTable(TableInfo tableInfo, Container c, SpecimenTableType tableType)
        {
            super(tableInfo, c, tableType, "lab_id", "lab_id", "lab_name", "Label");
            defaultLabId = getVal(DEFAULT_LAB);
        }

        @Override
        List<Map<String, Object>> toMaps()
        {
            List<Map<String, Object>> maps = super.toMaps();
            //Treat each like a repository so that specimens might be requestable.
            for (Map<String, Object> m : maps)
            {
                m.put("is_repository", Boolean.TRUE);
            }
            return maps;
        }

        @Override
        Integer getVal(String label)
        {
            return null == label ? defaultLabId : super.getVal(label); 
        }

        Integer getDefaultLabId()
        {
            return defaultLabId;
        }
    }

    private Map<String,SpecimenColumn> specimenColumnMap;

    private SpecimenColumn findSpecimenColumn(String name)
    {
        if (null == specimenColumnMap)
        {
            specimenColumnMap = new HashMap<>();
            for (SpecimenColumn col : getSpecimenColumns())
                specimenColumnMap.put(col.getTsvColumnName(), col);
        }

        return specimenColumnMap.get(name);
    }

    private static boolean areDuplicateLabIds(LabLookupTable labLookupTable)
    {
        Set<Integer> labids = new HashSet<>();
        for (Integer id : labLookupTable.getExistingKeyMap().values())
        {
            if (!labids.add(id))
                return true;        // already there
        }
        return false;
    }
}
