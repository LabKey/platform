/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.UnionContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.exp.ExperimentMembershipDisplayColumnFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExpExperimentTableImpl extends ExpTableImpl<ExpExperimentTable.Column> implements ExpExperimentTable
{
    public ExpExperimentTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoExperiment(), schema, new ExpExperimentImpl(new Experiment()));
        addCondition(new SQLFragment("Hidden = ?", Boolean.FALSE), FieldKey.fromParts("Hidden"));

        ActionURL deleteExpUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteSelectedExperimentsURL(getContainer(), null);
        setDeleteURL(new DetailsURL(deleteExpUrl));
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Comments:
                return wrapColumn(alias, _rootTable.getColumn("Comments"));
            case Folder:
                return setupNonEditableCol(wrapColumn(alias, _rootTable.getColumn("Container")));
            case Created:
                return setupNonEditableCol(wrapColumn(alias, _rootTable.getColumn("Created")));
            case CreatedBy:
                return setupNonEditableCol(createUserColumn(alias, _rootTable.getColumn("CreatedBy")));
            case Modified:
                return setupNonEditableCol(wrapColumn(alias, _rootTable.getColumn("Modified")));
            case ModifiedBy:
                return setupNonEditableCol(createUserColumn(alias, _rootTable.getColumn("ModifiedBy")));
            case Hypothesis:
                return wrapColumn(alias, _rootTable.getColumn("Hypothesis"));
            case Contact:
                ColumnInfo contactCol = wrapColumn(alias, _rootTable.getColumn("ContactId"));
                contactCol.setLabel("Contact");
                return contactCol;
            case ExperimentDescriptionURL:
                ColumnInfo descUrlCol = wrapColumn(alias, _rootTable.getColumn("ExperimentDescriptionURL"));
                descUrlCol.setLabel("Description URL");
                return descUrlCol;
            case LSID:
                return setupNonEditableCol(wrapColumn(alias, _rootTable.getColumn("LSID")));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn("RowId"));
            case RunCount:
                ExprColumn runCountColumnInfo = new ExprColumn(this, "RunCount", new SQLFragment("(SELECT COUNT(*) FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " WHERE ExperimentId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId)"), JdbcType.INTEGER);
                runCountColumnInfo.setDescription("Contains the number of runs associated with this run group");
                setupNonEditableCol(runCountColumnInfo);
                return runCountColumnInfo;
            case BatchProtocolId:
            {
                ColumnInfo batchProtocolCol = wrapColumn(alias, _rootTable.getColumn("BatchProtocolId"));
                batchProtocolCol.setLabel("Batch Protocol");
                batchProtocolCol.setFk(getExpSchema().getProtocolForeignKey("RowId"));
                setupNonEditableCol(batchProtocolCol);
                return batchProtocolCol;
            }
            default:
                throw new UnsupportedOperationException("Unknown column " + column);
        }
    }

    private ColumnInfo setupNonEditableCol (ColumnInfo col)
    {
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setShownInInsertView(false);
        col.setShownInUpdateView(false);
        return col;
    }

    public void addExperimentMembershipColumn(ExpRun run)
    {
        final SQLFragment sql;
        if (run != null)
        {
            SQLFragment existsSql = new SQLFragment("EXISTS (SELECT ExperimentId FROM ");
            existsSql.append(ExperimentServiceImpl.get().getTinfoRunList(), "rl");
            existsSql.append(" WHERE ExperimentRunId = ").append(run.getRowId()).append(" AND ExperimentId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".RowId)");

            sql = getSqlDialect().wrapExistsExpression(existsSql);
        }
        else
        {
            sql = new SQLFragment("?", false);
        }

        ColumnInfo expRowIdCol = getColumn(Column.RowId);
        ExprColumn result = new ExprColumn(this, "RunMembership", sql, JdbcType.BOOLEAN, expRowIdCol);
        result.setLabel("");
        result.setDisplayColumnFactory(new ExperimentMembershipDisplayColumnFactory(expRowIdCol, run == null ? -1 : run.getRowId()));
        addColumn(result);
    }

    public void setBatchProtocol(@Nullable ExpProtocol protocol)
    {
        SimpleFilter filter = new SimpleFilter();
        if (protocol != null)
        {
            filter.addCondition(FieldKey.fromParts(Column.BatchProtocolId.name()), protocol.getRowId());
        }
        else
        {
            filter.addCondition(FieldKey.fromParts(Column.BatchProtocolId.name()), null, CompareType.ISBLANK);
        }
        addCondition(filter);
    }

    public void populate()
    {
        ColumnInfo colRowId = addColumn(Column.RowId);
        colRowId.setHidden(true);
        colRowId.setKeyField(true);
        colRowId.setFk(new RowIdForeignKey(colRowId));
        addColumn(Column.Name);
        addColumn(Column.Hypothesis);
        addColumn(Column.Contact);
        addColumn(Column.ExperimentDescriptionURL);
        addColumn(Column.Comments);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addColumn(Column.RunCount);
        addColumn(Column.BatchProtocolId);
        addContainerColumn(Column.Folder, new ActionURL(ExperimentController.ShowRunGroupsAction.class, getContainer()));
        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(Column.Name.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Hypothesis.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Comments.toString()));
        defaultCols.add(FieldKey.fromParts(Column.RunCount.toString()));
        defaultCols.add(FieldKey.fromParts(Column.Folder.toString()));
        setDefaultVisibleColumns(defaultCols);
        addColumn(Column.LSID).setHidden(true);
        setTitleColumn("Name");

        DetailsURL detailsURL = new DetailsURL(new ActionURL(ExperimentController.DetailsAction.class, _userSchema.getContainer()), Collections.singletonMap("rowId", "RowId"));
        setDetailsURL(detailsURL);
//        getColumn(Column.Name).setURL(detailsURL);
    }

    public ColumnInfo createRunCountColumn(String alias, ExpProtocol parentProtocol, ExpProtocol childProtocol)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(exp.experimentrun.rowid) FROM exp.experimentrun" +
                "\nINNER JOIN exp.runlist ON exp.experimentrun.rowid = exp.runlist.experimentrunid" +
                "\nAND exp.runlist.experimentid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid");
        if (parentProtocol != null)
        {
            sql.append("\nAND exp.experimentrun.protocollsid = ");
            sql.appendStringLiteral(parentProtocol.getLSID());
        }
        if (childProtocol != null)
        {
            sql.append("\nAND exp.experimentrun.rowid IN" +
                    "\n(SELECT exp.protocolapplication.runid" +
                    "\nFROM exp.protocolapplication WHERE exp.protocolapplication.protocollsid = ");
            sql.appendStringLiteral(childProtocol.getLSID());
            sql.append(")");
        }
        sql.append(")");
        return new ExprColumn(this, alias, sql, JdbcType.INTEGER);
    }

    @Override
    public boolean hasDefaultContainerFilter()
    {
        // Lie a little bit - we have two "standard" filters.
        return super.hasDefaultContainerFilter() || getContainerFilter() instanceof ContainerFilter.CurrentPlusProjectAndShared;
    }

    @Override
    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        if (getContainerFilter() instanceof ContainerFilter.CurrentPlusProjectAndShared)
        {
            filter = new UnionContainerFilter(filter, getContainerFilter());
        }
        super.setContainerFilter(filter);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return isAllowedPermission(perm) && _userSchema.getContainer().hasPermission(user, perm);
    }
}
