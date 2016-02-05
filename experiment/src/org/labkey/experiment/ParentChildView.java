/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HBox;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: kevink
 * Date: 10/20/15
 */
public class ParentChildView extends VBox
{
    public ParentChildView(ExpProtocolOutput output, ViewContext context)
    {
        setViewContext(context);
        setFrame(FrameType.PORTAL);
        setTitle("Lineage");

        HBox parentsHBox = new HBox();
        HBox childrenHBox = new HBox();
        addView(parentsHBox);
        addView(childrenHBox);

        Pair<Set<ExpData>, Set<ExpMaterial>> parents = ExperimentService.get().getParents(output);
        Set<ExpData> parentDatas = parents.first;

        QueryView parentDatasView = createDataView(parentDatas, "parentData", "Parent Data");
        parentsHBox.addView(parentDatasView);

        Set<ExpMaterial> parentMaterials = parents.second;
        QueryView parentSamplesView = createMaterialsView(parentMaterials, "parentMaterials", "Parent Samples");
        parentsHBox.addView(parentSamplesView);

        Pair<Set<ExpData>, Set<ExpMaterial>> children = ExperimentService.get().getChildren(output);
        Set<ExpData> childData = children.first;
        QueryView childDataView = createDataView(childData, "childData", "Child Data");
        childrenHBox.addView(childDataView);

        Set<ExpMaterial> childMaterials = children.second;
        QueryView childSamplesView = createMaterialsView(childMaterials, "childMaterials", "Child Samples");
        childrenHBox.addView(childSamplesView);
    }

    private Container getContainer() { return getViewContext().getContainer(); }
    private User getUser() { return getViewContext().getUser(); }

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

    private QueryView createDataView(Set<ExpData> data, String dataRegionName, String title)
    {
        Integer classId = null;
        data.removeIf(d -> !d.getContainer().hasPermission(getUser(), ReadPermission.class));
        for (ExpData d : data)
        {
            Integer id = ((ExpDataImpl)d).getDataObject().getClassId();
            if (classId == null)
            {
                classId = id;
            }
            else if (!classId.equals(id))
            {
                classId = null;
                break;
            }
        }

        final List<Integer> rowIds = data.stream().map(ExpData::getRowId).collect(Collectors.toList());

        final ExpDataClass dataClass = classId == null ? null : ExperimentService.get().getDataClass(classId);

        UserSchema schema = new ExpSchema(getUser(), getContainer());
        QuerySettings settings;
        if (dataClass == null)
        {
            settings = schema.getSettings(getViewContext(), dataRegionName, ExpSchema.TableType.Data.toString());
            settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("rowId"), rowIds, CompareType.IN));
        }
        else
        {
            schema = schema.getUserSchema(ExpSchema.NestedSchemas.data.toString());
            settings = schema.getSettings(getViewContext(), dataRegionName, dataClass.getName());
            settings.getBaseFilter().addClause(new SimpleFilter.InClause(FieldKey.fromParts("rowId"), rowIds));
        }


        QueryView queryView = new QueryView(schema, settings, null)
        {
            protected TableInfo createTable()
            {
                //ExpDataTable table = ExperimentServiceImpl.get().createDataTable(ExpSchema.TableType.Data.toString(), getSchema());
                TableInfo table = super.createTable();
                //table.populate();
                // We've already set an IN clause that restricts us to showing just data that we have permission
                // to view
                //table.setContainerFilter(ContainerFilter.EVERYTHING);

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
                table.setDefaultVisibleColumns(defaultVisibleColumns);
                return table;
            }
        };

        return configureView(queryView, title);
    }

    private QueryView createMaterialsView(final Set<ExpMaterial> materials, String dataRegionName, String title)
    {
        // Strip out materials in folders that the user can't see - this lets us avoid a container filter that
        // enforces the permissions when we do the query
        String typeName = null;
        boolean sameType = true;
        for (Iterator<ExpMaterial> iter = materials.iterator(); iter.hasNext(); )
        {
            ExpMaterial material = iter.next();
            if (!material.getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                iter.remove();
            }

            String type = material.getCpasType();
            if (sameType)
            {
                if (typeName == null)
                    typeName = type;
                else if (!typeName.equals(type))
                {
                    typeName = null;
                    sameType = false;
                }
            }
        }

        final ExpSampleSet ss;
        if (sameType && typeName != null && !"Material".equals(typeName) && !"Sample".equals(typeName))
            ss = ExperimentService.get().getSampleSet(typeName);
        else
            ss = null;

        QuerySettings settings;
        UserSchema schema;
        if (ss == null)
        {
            schema = new ExpSchema(getUser(), getContainer());
            settings = schema.getSettings(getViewContext(), dataRegionName, ExpSchema.TableType.Materials.toString());
        }
        else
        {
            schema = new SamplesSchema(getUser(), getContainer());
            settings = schema.getSettings(getViewContext(), dataRegionName, ss.getName());
        }

        QueryView queryView = new QueryView(schema, settings, null)
        {
            protected TableInfo createTable()
            {
                ExpMaterialTable table = ExperimentServiceImpl.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), getSchema());
                table.setMaterials(materials);
                table.populate(ss, false);
                // We've already set an IN clause that restricts us to showing just data that we have permission
                // to view
                table.setContainerFilter(ContainerFilter.EVERYTHING);

                List<FieldKey> defaultVisibleColumns = new ArrayList<>();
                if (ss == null)
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
