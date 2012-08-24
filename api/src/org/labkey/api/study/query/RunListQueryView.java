/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.ReplacedRunFilter;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class RunListQueryView extends ExperimentRunListView
{
    public static final FieldKey REPLACED_FIELD_KEY = FieldKey.fromParts(ExpRunTable.Column.Replaced);


    protected final ExpProtocol _protocol;
    private final ReplacedRunFilter _replacedRunFilter;

    public RunListQueryView(ExpProtocol protocol, UserSchema schema, QuerySettings settings, AssayRunType assayRunFilter)
    {
        super(schema, settings, assayRunFilter);
        _protocol = protocol;
        setShowDeleteButton(true);
        setShowExportButtons(true);
        setShowAddToRunGroupButton(false);

        _replacedRunFilter = ReplacedRunFilter.getFromURL(this, REPLACED_FIELD_KEY);
    }

    // Here so that we can use the same schema object for both the QueryView and QuerySettings. This is important
    // so that TableQueryDefinition.getTable() doesn't think that it's being asked for a table from a different schema
    private RunListQueryView(ExpProtocol protocol, UserSchema schema, ViewContext context)
    {
        this(protocol, schema, getDefaultQuerySettings(protocol, schema, context), getDefaultAssayRunFilter(protocol, context));
    }

    public RunListQueryView(ExpProtocol protocol, ViewContext context)
    {
        this(protocol, getDefaultUserSchema(context), context);
    }

    public static AssayRunType getDefaultAssayRunFilter(ExpProtocol protocol, ViewContext context)
    {
        return new AssayRunType(protocol, context.getContainer());
    }

    public static QuerySettings getDefaultQuerySettings(ExpProtocol protocol, UserSchema schema, ViewContext context)
    {
        return ExperimentRunListView.getRunListQuerySettings(schema, context, AssayService.get().getRunsTableName(protocol), true);
    }

    public static UserSchema getDefaultUserSchema(ViewContext context)
    {
        return QueryService.get().getUserSchema(context.getUser(), context.getContainer(), AssayRunType.SCHEMA_NAME);
    }

    @Override
    public DataView createDataView()
    {
        DataView result = super.createDataView();
        SimpleFilter filter = (SimpleFilter) result.getRenderContext().getBaseFilter();
        _replacedRunFilter.addFilterCondition(filter, REPLACED_FIELD_KEY);
        return result;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        if (provider != null && provider.supportsReRun())
        {
            MenuButton button = new MenuButton("Replaced Filter");
            for (ReplacedRunFilter.Type type : ReplacedRunFilter.Type.values())
            {
                ActionURL url = view.getViewContext().cloneActionURL();
                type.addToURL(url, getDataRegionName(), REPLACED_FIELD_KEY);
                button.addMenuItem(type.getTitle(), url).setSelected(type == _replacedRunFilter.getType());

            }
            bar.add(button);
        }
    }
}
