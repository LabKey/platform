/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.ParticipantClassification;
import org.labkey.study.model.ParticipantListManager;
import org.labkey.study.model.StudyManager;

import java.util.Map;

public class ParticipantTable extends FilteredTable
{
    StudyQuerySchema _schema;

    public ParticipantTable(StudyQuerySchema schema)
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
        }
        );
        addColumn(datasetColumn);

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

        // join in participant classifications
        for (ParticipantClassification classification : ParticipantListManager.getInstance().getParticipantClassifications(getContainer()))
        {
            ColumnInfo classificationColumn = new ParticipantClassificationColumn(classification, this);
            addColumn(classificationColumn);
        }
    }

    public ActionURL getBaseDetailsURL()
    {
        return new ActionURL(StudyController.ParticipantAction.class, _schema.getContainer());
    }

    public static class ParticipantClassificationColumn extends ExprColumn
    {
        private ParticipantClassification _def;
        private String PARTICIPANT_GROUP_ALIAS;
        private String PARTICIPANT_LIST_JOIN;
        private String PARTICIPANT_GROUP_JOIN;

        public ParticipantClassificationColumn(ParticipantClassification def, FilteredTable parent)
        {
            super(parent, def.getLabel(), new SQLFragment(), JdbcType.VARCHAR);

            _def = def;

            // set up the join aliases
            PARTICIPANT_GROUP_ALIAS = ColumnInfo.legalNameFromName(_def.getLabel()) + "$" + "ParticipantGroup$";
            PARTICIPANT_LIST_JOIN = ColumnInfo.legalNameFromName(_def.getLabel()) + "$" + "ParticipantListJoin$";
            PARTICIPANT_GROUP_JOIN = ColumnInfo.legalNameFromName(_def.getLabel()) + "$" + "ParticipantGroupJoin$";

            SQLFragment sql = new SQLFragment();
            sql.append(ExprColumn.STR_TABLE_ALIAS).append("$").append(PARTICIPANT_GROUP_JOIN).append(".label\n");
            setValueSQL(sql);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + PARTICIPANT_LIST_JOIN;
            String groupAlias = parentAlias + "$" + PARTICIPANT_GROUP_ALIAS;
            String groupJoinAlias = parentAlias + "$" + PARTICIPANT_GROUP_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment sql = new SQLFragment();

            sql.append(" LEFT OUTER JOIN (SELECT * FROM ");
            sql.append(" (SELECT * FROM ").append(ParticipantListManager.getInstance().getTableInfoParticipantGroup(), "");
            sql.append(" WHERE ClassificationId = ? ) ").append(groupAlias).append(" JOIN ").append(ParticipantListManager.getInstance().getTableInfoParticipantGroupMap(), "");
            sql.append(" ON GroupId = ").append(groupAlias).append(".RowId )").append(groupJoinAlias);
            sql.append(" ON ").append(groupJoinAlias).append(".ParticipantId = ").append(parentAlias).append(".ParticipantId");

            sql.add(_def.getRowId());

            map.put(tableAlias, sql);
        }
    }
}
