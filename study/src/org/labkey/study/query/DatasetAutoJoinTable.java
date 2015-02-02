/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.study.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Magic table that joins a source Dataset to other Datasets based on primary key types.
 *
 * Datasets may have three types of primary keys:
 * (A) ParticipantID only.
 * (B) ParticipantID, SequenceNum (either Visit or Date)
 * (C) ParticipantID, SequenceNum, and an additional key.
 *
 * This virtual table has a column for each Dataset that the source Dataset can join to (without row duplication):
 *   A -> A
 *   B -> A or B
 *   C -> A, B, or C (if C key name and type matches)
 *
 * Other joins may make sense (A -> B or A -> C), but would produce row duplication.
 * Assay backed datasets use the extra key column to store the original assay result rowid
 * and so are treated as a (B) type dataset since the assay rowid would never match any other dataset's assay rowid.
 */
public class DatasetAutoJoinTable extends VirtualTable
{
    private StudyQuerySchema _schema;
    private DatasetDefinition _source;
    private String _keyPropertyName;

    // The resolved "ParticipantId" column handed through the ForeignKey.createLookupColumn().
    private ColumnInfo _participantIdColumn;

    // The "SequenceNum" FieldKey that has possibly been remapped.
    private FieldKey _sequenceNumFieldKey;

    // The "_Key" FieldKey that has possibly been remapped.
    private FieldKey _keyFieldKey;

    public DatasetAutoJoinTable(StudyQuerySchema schema, DatasetDefinition source,
                                @Nullable ColumnInfo participantIdColumn,
                                @Nullable FieldKey sequenceNumFieldKey,
                                @Nullable FieldKey keyFieldKey)
    {
        super(StudySchema.getInstance().getSchema(), "DataSets");
        _schema = schema;
        _source = source;
        _keyPropertyName = _source.getKeyPropertyName();

        _participantIdColumn = participantIdColumn;
        _sequenceNumFieldKey = sequenceNumFieldKey;
        _keyFieldKey = keyFieldKey;

        // We only need to the SequenceNum and Key columns when traversing the dataset FKs.
        // The participantIdColumn should always be present in that case.
        if (_participantIdColumn != null)
        {
            assert _sequenceNumFieldKey != null;
            assert _keyFieldKey != null;
            TableInfo parent = _participantIdColumn.getParentTable();

            // Container is always available, but we only need it for Dataspace shared datasets
            if (source.isShared())
            {
                ColumnInfo colContainer = new AliasedColumn(parent, "Container", parent.getColumn("Container"));
                colContainer.setHidden(true);
                addColumn(colContainer);
            }

            // SequenceNum is always available
            ColumnInfo colSequenceNum = new AliasedColumn(parent, "SequenceNum", parent.getColumn(sequenceNumFieldKey.getName()));
            colSequenceNum.setHidden(true);
            addColumn(colSequenceNum);

            // The extra key property is not always available.
            if (_keyPropertyName != null)
            {
                ColumnInfo colExtraKey = new AliasedColumn(parent, "_Key", parent.getColumn(keyFieldKey.getName()));
                colExtraKey.setHidden(true);
                addColumn(colExtraKey);
            }
        }

        Set<FieldKey> defaultVisible = new LinkedHashSet<>();
        for (DatasetDefinition dataset : _schema.getStudy().getDatasets())
        {
            // verify that the current user has permission to read this dataset (they may not if
            // advanced study security is enabled).
            if (!dataset.canRead(schema.getUser()))
                continue;

            String name = _schema.decideTableName(dataset);
            if (name == null)
                continue;

            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;

            ColumnInfo datasetColumn = createDatasetColumn(name, dataset);
            if (datasetColumn != null)
            {
                addColumn(datasetColumn);

                // Make the self-join hidden
                if (source.equals(dataset))
                    datasetColumn.setHidden(true);
                else
                    defaultVisible.add(FieldKey.fromParts(name));
            }
        }

        setDefaultVisibleColumns(defaultVisible);
    }


    protected ColumnInfo createDatasetColumn(String name, final DatasetDefinition dsd)
    {
        ColumnInfo ret;
        if (_participantIdColumn == null)
        {
            ret = new ColumnInfo(name, this);
            ret.setSqlTypeName("VARCHAR");
        }
        else
        {
            ret = new AliasedColumn(name, _participantIdColumn);
        }

        DatasetForeignKey fk = null;
        if (_source.isDemographicData())
        {
            if (dsd.isDemographicData())
                // A -> A
                fk = createParticipantFK(dsd);
        }
        else if (_keyPropertyName == null || _source.isAssayData())
        {
            if (dsd.isDemographicData())
                // B -> A
                fk = createParticipantFK(dsd);
            else if (dsd.getKeyPropertyName() == null)
                // B -> B
                fk = createParticipantSequenceNumFK(dsd);
        }
        else
        {
            if (dsd.isDemographicData())
                // C -> A
                fk = createParticipantFK(dsd);
            else if (dsd.getKeyPropertyName() == null || dsd.isAssayData())
                // C -> B
                fk = createParticipantSequenceNumFK(dsd);
            else
                // C -> C
                fk = createParticipantSequenceNumKeyFK(dsd);
        }

        // The join type was not supported.
        if (fk == null)
            return null;

        // 20546: row duplication for dataspace project w/ same ptid in multiple containers
        if (_participantIdColumn != null && dsd.isShared())
            fk.addJoin(new FieldKey(null,"Container"), "Container", false);

        ret.setFk(fk);
        ret.setLabel(dsd.getLabel());
        ret.setDescription("Lookup to the " + dsd.getLabel() + " DataSet, joined by '" + fk.getJoinDescription() + "'.");
        ret.setIsUnselectable(true);
        ret.setUserEditable(false);
        return ret;
    }

    private DatasetForeignKey createParticipantFK(DatasetDefinition dsd)
    {
        assert dsd.isDemographicData();
        DatasetForeignKey fk = new DatasetForeignKey(dsd);
        fk.setJoinDescription(StudyService.get().getSubjectColumnName(dsd.getContainer()));
        return fk;
    }

    private DatasetForeignKey createParticipantSequenceNumFK(DatasetDefinition dsd)
    {
        assert !dsd.isDemographicData() && (dsd.getKeyPropertyName() == null || dsd.isAssayData());
        assert !_source.isDemographicData();

        DatasetForeignKey fk = new DatasetForeignKey(dsd);
        if (_sequenceNumFieldKey != null)
        {
            fk.addJoin(_sequenceNumFieldKey, "SequenceNum", false);
        }

        fk.setJoinDescription(StudyService.get().getSubjectNounSingular(dsd.getContainer()) +
                              (dsd.getStudy().getTimepointType() == TimepointType.VISIT ? "/Visit" : "/Date"));
        return fk;
    }

    private DatasetForeignKey createParticipantSequenceNumKeyFK(DatasetDefinition dsd)
    {
        assert !dsd.isDemographicData() && dsd.getKeyPropertyName() != null;
        assert !_source.isDemographicData() && _keyPropertyName != null;

        if (!_source.hasMatchingExtraKey(dsd))
            return null;

        DatasetForeignKey fk = new DatasetForeignKey(dsd);
        if (_sequenceNumFieldKey != null && _keyFieldKey != null)
        {
            fk.addJoin(_sequenceNumFieldKey, "SequenceNum", false);
            fk.addJoin(_keyFieldKey, "_key", true);
        }

        fk.setJoinDescription(StudyService.get().getSubjectNounSingular(dsd.getContainer()) +
                (dsd.getStudy().getTimepointType() == TimepointType.VISIT ? "/Visit" : "/Date") +
                "/" + _keyPropertyName);
        return fk;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo col = super.resolveColumn(name);
        if (col != null)
            return col;

        return null;
    }

    private class DatasetForeignKey extends LookupForeignKey
    {
        private final DatasetDefinition dsd;
        private String _joinDescription;

        public DatasetForeignKey(DatasetDefinition dsd)
        {
            super(StudyService.get().getSubjectColumnName(dsd.getContainer()));
            this.dsd = dsd;
        }

        public DatasetTableImpl getLookupTableInfo()
        {
            try
            {
                DatasetTableImpl ret = _schema.createDatasetTableInternal(dsd);
                ret.hideParticipantLookups();
                return ret;
            }
            catch (UnauthorizedException e)
            {
                return null;
            }
        }

        public void setJoinDescription(String description)
        {
            _joinDescription = description;
        }

        public String getJoinDescription()
        {
            return _joinDescription;
        }
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }
}

