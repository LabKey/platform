/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.CohortForeignKey;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.Map;

public class ParticipantVisitTable extends BaseStudyTable
{
    Map<String, ColumnInfo> _demographicsColumns;

    public ParticipantVisitTable(StudyQuerySchema schema, ContainerFilter cf, boolean hideDatasets)
    {
        super(schema, StudySchema.getInstance().getTableInfoParticipantVisit(), cf);
        _setContainerFilter(schema.getDefaultContainerFilter());
        setName(StudyService.get().getSubjectVisitTableName(schema.getContainer()));
        _demographicsColumns = new CaseInsensitiveHashMap<>();
        Study study = StudyService.get().getStudy(schema.getContainer());

        MutableColumnInfo participantSequenceNumColumn = null;
        for (ColumnInfo col : _rootTable.getColumns())
        {
            if ("Container".equalsIgnoreCase(col.getName()))
            {
                // 20546: need to expose Container for use in DatasetTableImpl.ParticipantVisitForeignKey
                var containerColumn = new AliasedColumn(this, "Container", col);
                addColumn(containerColumn);
            }
            else if ("VisitRowId".equalsIgnoreCase(col.getName()))
            {
                var visitColumn = new AliasedColumn(this, "Visit", col);
                LookupForeignKey visitFK = new LookupForeignKey(cf, "RowId", null)
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        var ret = new VisitTable(_userSchema, getLookupContainerFilter());
                        ret.afterConstruct();
                        return ret;
                    }
                };
                visitColumn.setFk(visitFK);
                visitColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo col)
                    {
                        return new BaseStudyTable.VisitDisplayColumn(col, FieldKey.fromParts("SequenceNum"));
                    }
                });
                addColumn(visitColumn);
            }
            else if ("CohortID".equalsIgnoreCase(col.getName()))
            {
                BaseColumnInfo cohortColumn;
                boolean showCohorts = StudyManager.getInstance().showCohorts(getContainer(), schema.getUser());
                if (!showCohorts)
                {
                    cohortColumn = new NullColumnInfo(this, "Cohort", JdbcType.INTEGER);
                    cohortColumn.setHidden(true);
                }
                else
                {
                    cohortColumn = new AliasedColumn(this, "Cohort", col);
                }
                cohortColumn.setLabel(col.getLabel());
                cohortColumn.setFk(new CohortForeignKey(_userSchema, cf, showCohorts, cohortColumn.getLabel()));
                addColumn(cohortColumn);
            }
            else if ("ParticipantSequenceNum".equalsIgnoreCase(col.getName()))
            {
                participantSequenceNumColumn = addWrapColumn(col);
                participantSequenceNumColumn.setHidden(true);
            }
            else if ("ParticipantId".equalsIgnoreCase(col.getName()))
            {
                addWrapParticipantColumn(col.getName());
            }
            else if (study != null && study.getTimepointType() != TimepointType.VISIT && "SequenceNum".equalsIgnoreCase(col.getName()))
            {
                var sequenceNumCol = addWrapColumn(col);
                sequenceNumCol.setHidden(true);
            }
            else
                addWrapColumn(col);
        }

        for (DatasetDefinition dataset : _userSchema.getStudy().getDatasets())
        {
            // verify that the current user has permission to read this dataset (they may not if
            // advanced study security is enabled).
            if (!dataset.canReadInternal(schema.getUser()))
                continue;

            String name = _userSchema.decideTableName(dataset);
            if (name == null)
                continue;

            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;

            // if not keyed by Participant/SequenceNum it is not a lookup
            if (dataset.getKeyPropertyName() != null)
                continue;

            var datasetColumn = createDatasetColumn(name, cf, dataset, participantSequenceNumColumn);
            datasetColumn.setHidden(hideDatasets);

            // Don't add demographics datasets, but stash it for backwards compatibility with <11.3 queries if needed.
            if (dataset.isDemographicData())
                _demographicsColumns.put(name, datasetColumn);
            else
                addColumn(datasetColumn);
        }
    }


    protected BaseColumnInfo createDatasetColumn(String name, ContainerFilter cf, final DatasetDefinition dsd, ColumnInfo participantSequenceNumColumn)
    {
        var ret = new AliasedColumn(name, participantSequenceNumColumn);
        ret.setFk(new PVForeignKey(dsd, cf));
        ret.setLabel(dsd.getLabel());
        ret.setIsUnselectable(true);
        return ret;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        var col = super.resolveColumn(name);
        if (col != null)
            return col;

        col = _demographicsColumns.get(name);
        if (col != null)
            return col;

        // Resolve 'ParticipantSequenceKey' to 'ParticipantSequenceNum' for compatibility with versions <12.2.
        if ("ParticipantSequenceKey".equalsIgnoreCase(name))
            return getColumn("ParticipantSequenceNum");

        return null;
    }

    private class PVForeignKey extends LookupForeignKey
    {
        private final DatasetDefinition dsd;

        public PVForeignKey(DatasetDefinition dsd, ContainerFilter cf)
        {
            super(cf, StudyService.get().getSubjectVisitColumnName(dsd.getContainer()), null);
            this.dsd = dsd;
        }
        
        @Override
        public DatasetTableImpl getLookupTableInfo()
        {
            try
            {
                DatasetTableImpl ret = _userSchema.createDatasetTableInternal(dsd, getLookupContainerFilter());
                ret.hideParticipantLookups();
                return ret;
            }
            catch (UnauthorizedException e)
            {
                return null;
            }
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    /* You would usually want to turn off session participantgroup for the whole schema,
     * however, you might want to also turn off just for ParticpantTable when this table
     * is being used as a lookup (especially for a table that is already filtered)
     */
    boolean _ignoreSessionParticipantGroup = false;

    public void setIgnoreSessionParticipantGroup()
    {
        _ignoreSessionParticipantGroup = true;
    }

    @Override
    protected SimpleFilter getFilter()
    {
        SimpleFilter sf;
        sf = super.getFilter();

        ParticipantGroup group = _ignoreSessionParticipantGroup ? null : getUserSchema().getSessionParticipantGroup();
        if (null == group)
            return sf;

        SimpleFilter ret = new SimpleFilter();
        ret.addAllClauses(sf);

        FieldKey participantFieldKey = FieldKey.fromParts("ParticipantId");
        ret.addClause(new ParticipantGroupFilterClause(participantFieldKey, group));
        return ret;
    }

}

