/*
 * Copyright (c) 2015-2026 LabKey Corporation
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
package org.labkey.experiment;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.HBox;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.lineage.ExpLineageServiceImpl;
import org.labkey.experiment.lineage.ExpLineageServiceImpl.LineageResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParentChildView extends VBox
{
    public ParentChildView(ExpRunItem output, ViewContext context)
    {
        setViewContext(context);
        setFrame(FrameType.PORTAL);
        setTitle("Lineage");

        HBox parentsHBox = new HBox();
        HBox childrenHBox = new HBox();
        addView(parentsHBox);
        addView(childrenHBox);

        ExpLineageOptions parentOptions = new ExpLineageOptions();
        parentOptions.setChildren(false);
        LineageResult parents = ExpLineageServiceImpl.get().getLineageResult(getContainer(), getUser(), Set.of(output), parentOptions);
        parentsHBox.addView(createDataView(parents.dataIds(), "parentData", "Parent Data"));
        parentsHBox.addView(createMaterialsView(parents.materialIds(), "parentMaterials", "Precursor Samples"));

        ExpLineageOptions childOptions = new ExpLineageOptions();
        childOptions.setParents(false);
        LineageResult children = ExpLineageServiceImpl.get().getLineageResult(getContainer(), getUser(), Set.of(output), childOptions);
        childrenHBox.addView(createDataView(children.dataIds(), "childData", "Child Data"));
        childrenHBox.addView(createMaterialsView(children.materialIds(), "childMaterials", "Child Samples"));
    }

    private Container getContainer() { return getViewContext().getContainer(); }
    private User getUser() { return getViewContext().getUser(); }

    /**
     * @param singleType the type shared by every readable row, or null if they differ
     * @param containerFilter readable folders holding the rows; lineage crosses folders (Issue 38018)
     */
    private record LineageRows(@Nullable Object singleType, ContainerFilter containerFilter) {}

    private LineageRows getLineageRows(TableInfo table, String typeColumn, Set<Long> rowIds)
    {
        Set<Container> containers = new HashSet<>();
        Set<Object> types = new HashSet<>();
        if (!rowIds.isEmpty())
        {
            SQLFragment sql = new SQLFragment("SELECT DISTINCT Container, " + typeColumn + " FROM ").append(table, "t").append(" WHERE RowId ");
            table.getSqlDialect().appendInClauseSql(sql, rowIds);
            new SqlSelector(table.getSchema(), sql).forEach(rs -> {
                Container c = ContainerManager.getForId(rs.getString(1));
                if (c != null && c.hasPermission(getUser(), ReadPermission.class))
                {
                    containers.add(c);
                    types.add(rs.getObject(2));
                }
            });
        }

        Object singleType = types.size() == 1 ? types.iterator().next() : null;
        return new LineageRows(singleType, new ContainerFilter.SimpleContainerFilterWithUser(getUser(), containers));
    }

    private QueryView configureView(QueryView view, String title)
    {
        view.disableContainerFilterSelection();
        view.setShowBorders(true);
        view.setShowInsertNewButton(false);
        view.setShowImportDataButton(false);
        view.setShowDetailsColumn(false);
        view.setShowUpdateColumn(false);
        view.setShowExportButtons(false);
        view.setShowPagination(false);
        view.setShadeAlternatingRows(true);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
        view.setTitle(title);
        view.setFrame(FrameType.TITLE);
        return view;
    }

    private QueryView createDataView(Set<Long> rowIds, String dataRegionName, String title)
    {
        LineageRows rows = getLineageRows(ExperimentServiceImpl.get().getTinfoData(), "ClassId", rowIds);
        final ExpDataClass dataClass = rows.singleType() instanceof Number classId ? ExperimentServiceImpl.get().getDataClass(classId.longValue()) : null;

        UserSchema schema = new ExpSchema(getUser(), getContainer());
        QuerySettings settings;
        if (dataClass == null)
        {
            settings = schema.getSettings(getViewContext(), dataRegionName, ExpSchema.TableType.Data.toString());
            settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts(ExpDataTable.Column.RowId), rowIds, CompareType.IN));
        }
        else
        {
            schema = schema.getUserSchema(ExpSchema.NestedSchemas.data.toString());
            settings = schema.getSettings(getViewContext(), dataRegionName, dataClass.getName());
            settings.getBaseFilter().addInClause(FieldKey.fromParts(ExpDataTable.Column.RowId), rowIds);
        }

        QueryView queryView = new QueryView(schema, settings, null);
        queryView.setContainerFilter(rows.containerFilter());
        TableInfo table = queryView.getTable();

        CustomView v = queryView.getCustomView();
        if (null == v)
        {
            List<FieldKey> defaultVisibleColumns = new ArrayList<>();
            if (dataClass == null)
            {
                // The table columns without any of the DataClass property columns
                defaultVisibleColumns.add(FieldKey.fromParts(ExpDataTable.Column.Name));
                defaultVisibleColumns.add(FieldKey.fromParts(ExpDataTable.Column.DataClass));
                defaultVisibleColumns.add(FieldKey.fromParts(ExpDataTable.Column.Flag));
            }
            else
            {
                defaultVisibleColumns.addAll(table.getDefaultVisibleColumns());
            }
            defaultVisibleColumns.add(FieldKey.fromParts(ExpDataTable.Column.Created));
            defaultVisibleColumns.add(FieldKey.fromParts(ExpDataTable.Column.CreatedBy));
            defaultVisibleColumns.add(FieldKey.fromParts(ExpDataTable.Column.Run));

            queryView.getSettings().setFieldKeys(defaultVisibleColumns);
        }

        return configureView(queryView, title);
    }

    private QueryView createMaterialsView(Set<Long> rowIds, String dataRegionName, String title)
    {
        LineageRows rows = getLineageRows(ExperimentServiceImpl.get().getTinfoMaterial(), "CpasType", rowIds);
        final ExpSampleType st;
        if (rows.singleType() instanceof String typeName && !ExpMaterial.DEFAULT_CPAS_TYPE.equals(typeName) && !"Sample".equals(typeName))
            st = SampleTypeService.get().getSampleType(typeName);
        else
            st = null;

        QuerySettings settings;
        UserSchema schema;
        if (st == null)
        {
            schema = new ExpSchema(getUser(), getContainer());
            settings = schema.getSettings(getViewContext(), dataRegionName, ExpSchema.TableType.Materials.toString());
        }
        else
        {
            schema = new SamplesSchema(getUser(), getContainer());
            settings = schema.getSettings(getViewContext(), dataRegionName, st.getName());
        }

        settings.getBaseFilter().addInClause(FieldKey.fromParts(ExpMaterialTable.Column.RowId), rowIds);

        QueryView queryView = new QueryView(schema, settings, null)
        {
            @Override
            protected TableInfo createTable()
            {
                ExpMaterialTable table = ExperimentServiceImpl.get().createMaterialTable(getSchema(), rows.containerFilter(), st);
                table.populate();

                List<FieldKey> defaultVisibleColumns = new ArrayList<>();
                if (st == null)
                {
                    // The table columns without any of the active SampleSet property columns
                    defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Name));
                    defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.SampleSet));
                    defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Flag));
                }
                else
                {
                    defaultVisibleColumns.addAll(table.getDefaultVisibleColumns());
                }
                defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Created));
                defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.CreatedBy));
                defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Run));
                table.setDefaultVisibleColumns(defaultVisibleColumns);
                return table;
            }
        };

        return configureView(queryView, title);
    }
}
