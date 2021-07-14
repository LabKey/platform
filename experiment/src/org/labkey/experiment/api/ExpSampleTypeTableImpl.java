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

package org.labkey.experiment.api;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.SampleTypeService.MATERIAL_INPUTS_PREFIX;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public class ExpSampleTypeTableImpl extends ExpTableImpl<ExpSampleTypeTable.Column> implements ExpSampleTypeTable
{
    public ExpSampleTypeTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoSampleType(), schema, cf);
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(UpdatePermission.class);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("Container"));
                columnInfo.setURL(new DetailsURL(new ActionURL(ExperimentController.ShowSampleTypeAction.class, getContainer())));
                return columnInfo;
            case Description:
            case LSID:
            case MaterialLSIDPrefix:
            case Name:
            case NameExpression:
            case LabelColor:
            case MetricUnit:
            case AutoLinkTargetContainer:
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn(column.toString()));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case SampleCount:
            {
                SQLFragment sql = new SQLFragment("(SELECT COUNT(*) FROM " +
                    ExperimentServiceImpl.get().getTinfoMaterial() +
                    " m WHERE m.CpasType = " + ExprColumn.STR_TABLE_ALIAS + ".LSID" +
                    " AND m.container = ?)")
                    .add(_userSchema.getContainer().getEntityId());
                ExprColumn sampleCountColumnInfo = new ExprColumn(this, "SampleCount", sql, JdbcType.INTEGER);
                sampleCountColumnInfo.setDescription("Contains the number of samples currently stored in this sample type");
                return sampleCountColumnInfo;
            }
            case MaterialInputImportAliases:
                AliasedColumn materialInputCol = new AliasedColumn(this, "MaterialInputImportAliases", _rootTable.getColumn("RowId"));
                materialInputCol.setDisplayColumnFactory(new ImportAliasesDisplayColumnFactory(MATERIAL_INPUTS_PREFIX));
                return materialInputCol;
            case DataInputImportAliases:
                AliasedColumn dataInputCol = new AliasedColumn(this, "DataInputImportAliases", _rootTable.getColumn("RowId"));
                dataInputCol.setDisplayColumnFactory(new ImportAliasesDisplayColumnFactory("dataInputs/"));
                return dataInputCol;
            case Properties:
                return createPropertiesColumn(alias);
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    protected void populateColumns()
    {
        addColumn(ExpSampleTypeTable.Column.RowId).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.Name);
        addColumn(ExpSampleTypeTable.Column.Description);
        addColumn(ExpSampleTypeTable.Column.NameExpression).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.LabelColor).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.MetricUnit).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.AutoLinkTargetContainer).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.LSID).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.MaterialLSIDPrefix).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.Created);
        addColumn(ExpSampleTypeTable.Column.CreatedBy);
        addColumn(ExpSampleTypeTable.Column.Modified);
        addColumn(ExpSampleTypeTable.Column.ModifiedBy);
        addContainerColumn(ExpSampleTypeTable.Column.Folder, new ActionURL(ExperimentController.ListSampleTypesAction.class, getContainer()));
        addColumn(ExpSampleTypeTable.Column.SampleCount);
        addColumn(ExpSampleTypeTable.Column.MaterialInputImportAliases).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.DataInputImportAliases).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.Properties);

        DetailsURL detailsURL = new DetailsURL(new ActionURL(ExperimentController.ShowSampleTypeAction.class, _userSchema.getContainer()),
                Collections.singletonMap("rowId", "RowId"));
        detailsURL.setContainerContext(_userSchema.getContainer());
        setDetailsURL(detailsURL);
        setImportURL(AbstractTableInfo.LINK_DISABLER);
    }

    static class ImportAliasesDisplayColumnFactory implements DisplayColumnFactory
    {
        private final String _prefix;

        public ImportAliasesDisplayColumnFactory(String prefix)
        {
            _prefix = prefix;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            DataColumn dataColumn = new DataColumn(colInfo)
            {
                private JSONObject getValueFromCtx(RenderContext ctx) throws IOException
                {
                    JSONObject json = null;
                    Integer rowId = (Integer)getValue(ctx);
                    ExpSampleType sampleType = SampleTypeService.get().getSampleType(rowId);
                    if (sampleType != null)
                    {
                        Map<String, String> aliasMap = sampleType.getImportAliasMap();
                        List<String> importKeys = aliasMap.keySet().stream()
                                .filter(key -> aliasMap.get(key).startsWith(_prefix))
                                .sorted()
                                .collect(Collectors.toList());

                        if (importKeys.size() > 0)
                        {
                            json = new JSONObject();
                            for (String importKey : importKeys)
                                json.put(importKey, aliasMap.get(importKey).substring(_prefix.length()));
                        }
                    }

                    return json;
                }

                @Override
                public Object getJsonValue(RenderContext ctx)
                {
                    Object value = getDisplayValue(ctx);
                    return value != null ? value.toString() : null;
                }

                @Override
                public Object getDisplayValue(RenderContext ctx)
                {
                    try
                    {
                        return getValueFromCtx(ctx);
                    }
                    catch (IOException e)
                    {
                        return HtmlString.of("Bad import alias object");
                    }
                }

                @NotNull
                @Override
                public HtmlString getFormattedHtml(RenderContext ctx)
                {
                    try
                    {
                        JSONObject value = getValueFromCtx(ctx);
                        if (null == value)
                            return HtmlString.EMPTY_STRING;

                        ObjectMapper mapper = new ObjectMapper();
                        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
                        pp.indentArraysWith(new DefaultIndenter());

                        Object json = mapper.readValue(value.toString(), Object.class);
                        String strValue = mapper.writer(pp).writeValueAsString(json);
                        String filteredValue = PageFlowUtil.filter(strValue, true);
                        return HtmlString.unsafe("<div>" + filteredValue + "</div>");
                    }
                    catch (IOException e)
                    {
                        return HtmlString.of("Bad import alias object");
                    }
                }
            };

            dataColumn.setTextAlign("left");
            return dataColumn;
        }
    }
}
