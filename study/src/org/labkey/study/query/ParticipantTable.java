/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.TitleForeignKey;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.ParticipantCategory;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyManager;

import java.util.Map;

public class ParticipantTable extends FilteredTable
{
    StudyQuerySchema _schema;

    public ParticipantTable(StudyQuerySchema schema, boolean hideDataSets)
    {
        super(StudySchema.getInstance().getTableInfoParticipant(), schema.getContainer());
        setName(StudyService.get().getSubjectTableName(schema.getContainer()));
        _schema = schema;
        ColumnInfo rowIdColumn = new AliasedColumn(this, StudyService.get().getSubjectColumnName(getContainer()), _rootTable.getColumn("ParticipantId"));
        rowIdColumn.setFk(new TitleForeignKey(getBaseDetailsURL(), null, null, "participantId"));
        addColumn(rowIdColumn);

        ColumnInfo datasetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("ParticipantId"));
        datasetColumn.setKeyField(false);
        datasetColumn.setIsUnselectable(true);
        datasetColumn.setLabel("DataSet");
        datasetColumn.setFk(new AbstractForeignKey()
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;
                return new ParticipantDataSetTable(_schema, parent).getColumn(displayField);
            }

            public TableInfo getLookupTableInfo()
            {
                return new ParticipantDataSetTable(_schema, null);
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        });
        addColumn(datasetColumn);
        datasetColumn.setHidden(hideDataSets);

        if (StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()))
        {
            ColumnInfo currentCohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CurrentCohortId"));
            currentCohortColumn.setFk(new LookupForeignKey("RowId")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new CohortTable(_schema);
                }
            });
            addColumn(currentCohortColumn);

            Study study = StudyManager.getInstance().getStudy(schema.getContainer());

            if (study.isAdvancedCohorts())
            {
                ColumnInfo initialCohortColumn = new AliasedColumn(this, "InitialCohort", _rootTable.getColumn("InitialCohortId"));
                initialCohortColumn.setFk(new LookupForeignKey("RowId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new CohortTable(_schema);
                    }
                });
                addColumn(initialCohortColumn);
            }
        }
        
        ForeignKey fkSite = SiteTable.fkFor(_schema);
        addWrapColumn(_rootTable.getColumn("EnrollmentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("CurrentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("StartDate"));
        setTitleColumn(StudyService.get().getSubjectColumnName(getContainer()));

        setDetailsURL(new DetailsURL(getBaseDetailsURL(), "participantId",
                FieldKey.fromParts(StudyService.get().getSubjectColumnName(_schema.getContainer()))));

        setDefaultVisibleColumns(getDefaultVisibleColumns());

        // join in participant categories
        for (ParticipantCategory category : ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), _schema.getUser()))
        {
            ColumnInfo categoryColumn = new ParticipantCategoryColumn(category, this);
            addColumn(categoryColumn);
        }
    }

    public ActionURL getBaseDetailsURL()
    {
        return new ActionURL(StudyController.ParticipantAction.class, _schema.getContainer());
    }

    public static class ParticipantCategoryColumn extends ExprColumn
    {
        private ParticipantCategory _def;
        private String PARTICIPANT_GROUP_ALIAS;
        private String PARTICIPANT_GROUP_GROUPMAP_JOIN;
        private String PARTICIPANT_GROUPMAP_JOIN_ALIAS;

        public ParticipantCategoryColumn(ParticipantCategory def, FilteredTable parent)
        {
            super(parent, def.getLabel(), new SQLFragment(), JdbcType.VARCHAR);

            _def = def;

            // set up the join aliases
            PARTICIPANT_GROUP_ALIAS = ColumnInfo.legalNameFromName(_def.getLabel()) + "$" + "ParticipantGroup$";
            PARTICIPANT_GROUP_GROUPMAP_JOIN = ColumnInfo.legalNameFromName(_def.getLabel()) + "$" + "ParticipantListJoin$";
            PARTICIPANT_GROUPMAP_JOIN_ALIAS = ColumnInfo.legalNameFromName(_def.getLabel()) + "$" + "ParticipantGroupJoin$";

            SQLFragment sql = new SQLFragment();
            sql.append(ExprColumn.STR_TABLE_ALIAS).append("$").append(PARTICIPANT_GROUPMAP_JOIN_ALIAS).append(".label\n");
            setValueSQL(sql);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + PARTICIPANT_GROUP_GROUPMAP_JOIN;
            String groupAlias = parentAlias + "$" + PARTICIPANT_GROUP_ALIAS;
            String groupJoinAlias = parentAlias + "$" + PARTICIPANT_GROUPMAP_JOIN_ALIAS;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment sql = new SQLFragment();

            sql.append(" LEFT OUTER JOIN (SELECT ParticipantId, ").append(groupAlias).append(".Container, Label FROM ");
            sql.append(" (SELECT * FROM ").append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup(), "");
            sql.append(" WHERE CategoryId = ? ) ").append(groupAlias).append(" JOIN ").append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "");
            sql.append(" ON GroupId = ").append(groupAlias).append(".RowId )").append(groupJoinAlias);
            sql.append(" ON ").append(groupJoinAlias).append(".ParticipantId = ").append(parentAlias).append(".ParticipantId");
            sql.append(" AND ").append(groupJoinAlias).append(".Container = ").append(parentAlias).append(".Container");

            sql.add(_def.getRowId());

            map.put(tableAlias, sql);
        }
    }

    @Override
    public boolean hasContainerContext()
    {
        return true;
    }

    @Override
    public ContainerContext getContainerContext()
    {
        return _schema.getContainer();
    }
}
