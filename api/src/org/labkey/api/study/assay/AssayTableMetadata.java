/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.util.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures information about where assay implementations store various fields within their schema. For example,
 * different assays might have different column names for the same conceptual value, or store values at the run level
 * instead of the batch level.
 *
 * User: jeckels
 * Date: May 11, 2009
 */
public class AssayTableMetadata
{
    private final AssayProvider _provider;
    private final ExpProtocol _protocol;

    private final FieldKey _runFieldKey;
    private final FieldKey _resultRowIdFieldKey;
    private final FieldKey _specimenDetailParentFieldKey;
    /** The name of the property in the dataset that points back to the RowId-type column in the assay's data table */
    private final String _datasetRowIdPropertyName;

    public AssayTableMetadata(AssayProvider provider, ExpProtocol protocol, FieldKey specimenDetailParentFieldKey, FieldKey runFieldKey, FieldKey resultRowIdFieldKey)
    {
        this(provider, protocol, specimenDetailParentFieldKey, runFieldKey, resultRowIdFieldKey, resultRowIdFieldKey.getName());
    }

    public AssayTableMetadata(AssayProvider provider, ExpProtocol protocol, FieldKey specimenDetailParentFieldKey, FieldKey runFieldKey, FieldKey resultRowIdFieldKey, String datasetRowIdPropertyName)
    {
        _provider = provider;
        _protocol = protocol;
        _runFieldKey = runFieldKey;
        _resultRowIdFieldKey = resultRowIdFieldKey;
        _specimenDetailParentFieldKey = specimenDetailParentFieldKey;
        _datasetRowIdPropertyName = datasetRowIdPropertyName;
    }

    public FieldKey getSpecimenDetailParentFieldKey()
    {
        return _specimenDetailParentFieldKey;
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME);
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.SPECIMENID_PROPERTY_NAME);
    }

    /** @return the name of the property in the dataset that points back to the RowId-type column in the assay's data table */
    public String getDatasetRowIdPropertyName()
    {
        return _datasetRowIdPropertyName;
    }

    /** @return The Date or Visit FieldKey. */
    public FieldKey getVisitIDFieldKey(TimepointType timepointType)
    {
        if (timepointType == TimepointType.DATE)
        {
            return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.DATE_PROPERTY_NAME);
        }
        else if (timepointType == TimepointType.VISIT)
        {
            return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.VISITID_PROPERTY_NAME);
        }
        else
        {
            return null;
        }
    }

    public FieldKey getRunFieldKeyFromResults()
    {
        return _runFieldKey;
    }

    /** @return relative to the assay's results table, the FieldKey that gets to the Run table */
    public FieldKey getRunRowIdFieldKeyFromResults()
    {
        return new FieldKey(_runFieldKey, ExpRunTable.Column.RowId.toString());
    }

    public FieldKey getResultRowIdFieldKey()
    {
        return _resultRowIdFieldKey;
    }

    /**
     * Get the FieldKey to the TargetStudy column relative to the results table.  The assay instance
     * may define the TargetStudy column on the results table itself, the run table, or the batch table.
     *
     * @return relative to the assay's results table, the FieldKey that gets to the TargetStudy
     */
    public FieldKey getTargetStudyFieldKey()
    {
        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> pair = _provider.findTargetStudyProperty(_protocol);
        if (pair == null)
            return null;

        switch (pair.first)
        {
            case Result:
                return getTargetStudyFieldKeyOnResults();

            case Run:
                return getTargetStudyFieldKeyOnRun();

            case Batch:
            default:
                return getTargetStudyFieldKeyOnBatch();
        }
    }

    protected FieldKey getTargetStudyFieldKeyOnResults()
    {
        return new FieldKey(null, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
    }

    protected FieldKey getTargetStudyFieldKeyOnRun()
    {
        FieldKey runFK = getRunFieldKeyFromResults();
        return new FieldKey(runFK, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
    }

    protected FieldKey getBatchFieldKey()
    {
        FieldKey batchFK = new FieldKey(getRunRowIdFieldKeyFromResults().getParent(), AssayService.BATCH_COLUMN_NAME);
        return batchFK;
    }

    protected FieldKey getTargetStudyFieldKeyOnBatch()
    {
        FieldKey batchFK = getBatchFieldKey();
        return new FieldKey(batchFK, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
    }

    /**
     * Get the list of possible TargetStudies associated with the rows of the {@code assayDataTable} including any filters.
     *
     * The general strategy is to write a query of the form (for Batches):
     * <pre>
     *     SELECT DISTINCT TargetStudy
     *     FROM Batches
     *     WHERE RowId IN (
     *         SELECT DISTINCT X.Data.Run.Batch
     *         FORM Data X
     *     )
     * </pre>
     * To keep the container filter on the referenced tables, we push the tables into Query.
     */
    public Collection<String> getTargetStudyContainers(AssaySchema schema, TableInfo assayDataTable, ColumnInfo targetStudyCol)
    {
        final FieldKey targetStudyFieldKey = targetStudyCol.getFieldKey();
        final boolean onBatch = targetStudyFieldKey.equals(getTargetStudyFieldKeyOnBatch());
        final boolean onRun = targetStudyFieldKey.equals(getTargetStudyFieldKeyOnRun());

        if (onBatch || onRun)
        {
            // TargetStudy on batch or run
            FilteredTable targetStudyTable = (FilteredTable)schema.getTable(
                    onBatch ? AssayProtocolSchema.BATCHES_TABLE_NAME : AssayProtocolSchema.RUNS_TABLE_NAME);
            targetStudyTable.setContainerFilter(assayDataTable.getContainerFilter());

            SQLFragment sqlf = new SQLFragment("SELECT DISTINCT ");
            sqlf.append(targetStudyFieldKey.getName());
            sqlf.append(" FROM\n");
            sqlf.append(" __TARGET_STUDY_TABLE\n");
            sqlf.append("WHERE RowId IN (\n");
            sqlf.append("  SELECT DISTINCT X.").append(targetStudyFieldKey.getTable().toSQLString());
            sqlf.append("  FROM __DATA X\n");
            sqlf.append(")\n");

            Map<String, TableInfo> tableMap = new HashMap<>();
            tableMap.put("__TARGET_STUDY_TABLE", targetStudyTable);
            tableMap.put("__DATA", assayDataTable);

            try (ResultSet rs = QueryService.get().select(schema, sqlf.getSQL(), tableMap, false, true))
            {
                ArrayList<String> ids = new ArrayList<>();
                while (rs.next())
                    ids.add(rs.getString(1));
                return ids;
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            // TargetStudy on results
            SQLFragment sqlf = new SQLFragment("SELECT DISTINCT ");
            sqlf.append(targetStudyCol.getValueSql("x"));
            sqlf.append("\nFROM ");
            sqlf.append(assayDataTable.getFromSQL("x"));
            Map<String,SQLFragment> joins = new LinkedHashMap<>();
            targetStudyCol.declareJoins("x",joins);
            for (SQLFragment join : joins.values())
            {
                sqlf.append(join);
            }

            ArrayList<String> ids = new SqlSelector(schema.getDbSchema(), sqlf).getArrayList(String.class);
            return ids;
        }
    }
}
