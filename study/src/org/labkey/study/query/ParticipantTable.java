/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.TitleForeignKey;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.study.CohortForeignKey;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyManager;

public class ParticipantTable extends FilteredTable<StudyQuerySchema>
{
    public ParticipantTable(StudyQuerySchema schema, boolean hideDataSets)
    {
        super(StudySchema.getInstance().getTableInfoParticipant(), schema);
        setName(StudyService.get().getSubjectTableName(schema.getContainer()));

        Study study = StudyManager.getInstance().getStudy(schema.getContainer());
        ColumnInfo rowIdColumn = new AliasedColumn(this, StudyService.get().getSubjectColumnName(getContainer()), _rootTable.getColumn("ParticipantId"));
        rowIdColumn.setFk(new TitleForeignKey(getBaseDetailsURL(), null, null, "participantId", getContainerContext()));
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
                return new ParticipantDataSetTable(_userSchema, parent).getColumn(displayField);
            }

            public TableInfo getLookupTableInfo()
            {
                return new ParticipantDataSetTable(_userSchema, null);
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        });
        addColumn(datasetColumn);
        datasetColumn.setHidden(hideDataSets);

        ColumnInfo containerCol = new AliasedColumn(this, "Container", _rootTable.getColumn("Container"));
        containerCol = ContainerForeignKey.initColumn(containerCol, _userSchema);
        containerCol.setHidden(true);
        addColumn(containerCol);


        ColumnInfo currentCohortColumn;
        boolean showCohorts = StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser());
        if (!showCohorts)
        {
            currentCohortColumn = new NullColumnInfo(this, "Cohort", JdbcType.INTEGER);
            currentCohortColumn.setHidden(true);
        }
        else
        {
            currentCohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CurrentCohortId"));
        }
        currentCohortColumn.setFk(new CohortForeignKey(_userSchema, showCohorts, currentCohortColumn.getLabel()));
        addColumn(currentCohortColumn);


        ColumnInfo initialCohortColumn;
        if (!showCohorts)
        {
            initialCohortColumn = new NullColumnInfo(this, "InitialCohort", JdbcType.INTEGER);
            initialCohortColumn.setHidden(true);
        }
        else if (null != study && study.isAdvancedCohorts())
        {
            initialCohortColumn = new AliasedColumn(this, "InitialCohort", _rootTable.getColumn("InitialCohortId"));
        }
        else
        {
            initialCohortColumn = new AliasedColumn(this, "InitialCohort", _rootTable.getColumn("CurrentCohortId"));
            initialCohortColumn.setHidden(true);
        }
        initialCohortColumn.setFk(new CohortForeignKey(_userSchema, showCohorts, initialCohortColumn.getLabel()));
        addColumn(initialCohortColumn);

        ForeignKey fkSite = LocationTable.fkFor(_userSchema);
        addWrapColumn(_rootTable.getColumn("EnrollmentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("CurrentSiteId")).setFk(fkSite);
        addWrapColumn(_rootTable.getColumn("StartDate"));
        setTitleColumn(StudyService.get().getSubjectColumnName(getContainer()));

        setDetailsURL(new DetailsURL(getBaseDetailsURL(), "participantId",
                FieldKey.fromParts(StudyService.get().getSubjectColumnName(_userSchema.getContainer()))));

        setDefaultVisibleColumns(getDefaultVisibleColumns());

        // join in participant categories
        for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), _userSchema.getUser()))
        {
            ColumnInfo categoryColumn = new ParticipantCategoryColumn(category, this);
            addColumn(categoryColumn);
        }
    }

    public ActionURL getBaseDetailsURL()
    {
        return new ActionURL(StudyController.ParticipantAction.class, _userSchema.getContainer());
    }


    public static class ParticipantCategoryColumn extends ExprColumn
    {
        private final ParticipantCategoryImpl _def;

        public ParticipantCategoryColumn(ParticipantCategoryImpl def, FilteredTable parent)
        {
            super(parent, def.getLabel(), new SQLFragment(), JdbcType.VARCHAR);
            _def = def;
        }

        @Override
        public SQLFragment getValueSql(String parentAlias)
        {
            SQLFragment sql = new SQLFragment();
            sql.appendComment("<ParticipantTable: " + _def.getLabel() + ">", getSqlDialect());
            sql.append("(SELECT Label FROM ");
            sql.append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup(), "_g" );
            sql.append(" JOIN ");
            sql.append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "_m");
            sql.append(" ON _g.CategoryId = ? AND _g.RowId = _m.GroupId AND _g.Container=? AND _m.Container=?\n");
            sql.append("WHERE _m.ParticipantId = ").append(parentAlias).append(".ParticipantId)");
            sql.add(_def.getRowId());
            sql.add(_def.getContainerId());
            sql.add(_def.getContainerId());
            sql.appendComment("</ParticipantTable: " + _def.getLabel() + ">", getSqlDialect());
            return sql;
        }
    }


    @Override
    public ContainerContext getContainerContext()
    {
        return _userSchema.getContainer();
    }
}
