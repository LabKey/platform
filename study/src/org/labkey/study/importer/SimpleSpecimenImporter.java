/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
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


    public static final HashMap<String,String> DEFAULT_COLUMN_LABELS = new HashMap<String,String>();
    static
    {
        DEFAULT_COLUMN_LABELS.put(VIAL_ID, "Vial Id");
        DEFAULT_COLUMN_LABELS.put(SAMPLE_ID, "Sample Id");
        DEFAULT_COLUMN_LABELS.put(DRAW_TIMESTAMP, "Date");
        DEFAULT_COLUMN_LABELS.put(VOLUME, "Volume");
        DEFAULT_COLUMN_LABELS.put(UNITS, "Units");
        DEFAULT_COLUMN_LABELS.put(PRIMARY_SPECIMEN_TYPE, "Specimen Type");
        DEFAULT_COLUMN_LABELS.put(ADDITIVE_TYPE, "Additive Type");
        DEFAULT_COLUMN_LABELS.put(PARTICIPANT_ID, "Subject Id");
    }

    private Map<String,String> _columnLabels;
    private boolean _dateBasedStudy;

    public SimpleSpecimenImporter()
    {
        this(true, "Subject Id");
    }

    public SimpleSpecimenImporter(boolean dateBasedStudy, String participantIdLabel)
    {
        _dateBasedStudy = dateBasedStudy;
        _columnLabels = new HashMap<String,String>(DEFAULT_COLUMN_LABELS);
        _columnLabels.put(PARTICIPANT_ID, participantIdLabel);
    }

    public String label(String columnName)
    {
        String str = _columnLabels.get(columnName);
        return str == null ? columnName : str;
    }

    public void setColumnLabels(Map<String,String> map)
    {
        _columnLabels = map;
    }

    public Map<String,String> getColumnLabels()
    {
        return _columnLabels;
    }

    public void process(User user, Container container, File tsvFile, Logger logger) throws SQLException, IOException, ConversionException
    {
        TabLoader tl = new TabLoader(tsvFile);
        tl.setThrowOnErrors(true);
        fixupSpecimenColumns(tl);

        process(user, container, tl.load(), logger);
    }

    public void fixupSpecimenColumns(TabLoader tl)
            throws IOException
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

    public ColumnDescriptor[] getSimpleSpecimenColumns()
    {
        List<String> colNames = new ArrayList<String>(Arrays.asList(
                VIAL_ID,
                SAMPLE_ID,
                DRAW_TIMESTAMP,
                PARTICIPANT_ID,
                VOLUME,
                UNITS,
                PRIMARY_SPECIMEN_TYPE,
                DERIVIATIVE_TYPE,
                ADDITIVE_TYPE));
        if (!_dateBasedStudy)
            colNames.add(3, VISIT);

        ColumnDescriptor[] cols = new ColumnDescriptor[colNames.size()];
        for (int i = 0; i < cols.length; i++)
        {
            SpecimenColumn specCol = findSpecimenColumn(colNames.get(i));
            cols[i] = null == specCol ? new ColumnDescriptor(colNames.get(i), String.class) : specCol.getColumnDescriptor();
        }
        
        return cols;
    }

    public void process(User user, Container container, List<Map<String,Object>> rows) throws SQLException, IOException
    {
        process(user, container, rows, Logger.getLogger(getClass()));
    }

    public void process(User user, Container container, List<Map<String,Object>> rows, Logger logger) throws SQLException, IOException
    {
        //Map from column name to
        Study study = StudyManager.getInstance().getStudy(container);
        Map<String,LookupTable> lookupTables = new HashMap<String,LookupTable>();
        lookupTables.put("additive", new LookupTable("additives", "additive_type_id", "additive_id", "additive"));
        lookupTables.put("derivative_type", new LookupTable("derivatives", "derivative_type_id", "derivative_id", "derivative"));
        lookupTables.put("primary_specimen_type", new LookupTable("primary_types", "primary_specimen_type_id", "primary_type_id", "primary_type"));
        LabLookupTable labLookup =  new LabLookupTable();
        lookupTables.put("lab", labLookup);

        List<Map<String,Object>> specimenRows = new ArrayList<Map<String,Object>>();
        int recordId = 1;
        for (Map<String,Object> row : rows)
        {
            Map<String,Object> specimenRow = new HashMap<String,Object>();
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
            
            if (study.isDateBased())
                specimenRow.put(VISIT, StudyManager.sequenceNumFromDate((Date) specimenRow.get(DRAW_TIMESTAMP)));

            if (!row.containsKey(VIAL_ID))
                specimenRow.put(VIAL_ID, specimenRow.get(SAMPLE_ID));
            specimenRow.put("record_id", recordId++);
            specimenRows.add(specimenRow);
        }

        Map<String,List<Map<String, Object>>> inputTSVs = new HashMap<String, List<Map<String, Object>>>();
        inputTSVs.put("specimens", specimenRows);
        for (LookupTable lookupTable : lookupTables.values())
            inputTSVs.put(lookupTable.getName(), lookupTable.toMaps());

        super.process(user, container, inputTSVs, logger);
    }

    private static class LookupTable
    {
        private String name;
        private String foreignKeyCol;
        private String idCol;
        private String labelCol;
        private HashMap<String, Integer> keyMap = new HashMap();

        LookupTable(String name, String foreignKeyCol, String idCol, String labelCol)
        {
            this.name = name;
            this.foreignKeyCol = foreignKeyCol;
            this.idCol = idCol;
            this.labelCol = labelCol;
        }

        Integer getVal(String label)
        {
            Integer i = keyMap.get(label);
            if (null == i)
            {
                i = keyMap.size() + 1;
                keyMap.put(label, i);
            }
            return i;
        }

        List<Map<String, Object>> toMaps()
        {
            List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>(keyMap.size());
            for (int i = 0; i < keyMap.size(); i++)
            {
                // Grow the list so that we can set them in any order
                maps.add(null);
            }
            for (Map.Entry<String,Integer> entry : keyMap.entrySet())
            {
                Map<String,Object> m = new HashMap<String,Object>();
                m.put(labelCol, entry.getKey());
                m.put(idCol, entry.getValue());
                maps.set(entry.getValue() - 1, m);
            }
            return maps;
        }

        public String getName()
        {
            return name;
        }

        public String getIdCol()
        {
            return idCol;
        }

        public String getForeignKeyCol()
        {
            return foreignKeyCol;
        }
    }

    private static class LabLookupTable extends LookupTable
    {
        static final String DEFAULT_LAB = "Not Specified";
        private Integer defaultLabId;
        LabLookupTable()
        {
            super("labs", "lab_id", "lab_id", "lab_name");
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
            specimenColumnMap = new HashMap<String,SpecimenColumn>();
            for (SpecimenColumn col : SPECIMEN_COLUMNS)
                specimenColumnMap.put(col.getTsvColumnName(), col);
        }

        return specimenColumnMap.get(name);
    }
}
