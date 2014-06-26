/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.cbcassay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayResultDetailsAction;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayResultTable;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: 10/19/12
 */
public class CBCProtocolSchema extends AssayProtocolSchema
{
    public CBCProtocolSchema(User user, Container container, @NotNull CBCAssayProvider provider, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(user, container, provider, protocol, targetStudy);
    }

    @Override
    public FilteredTable createDataTable(boolean includeCopiedToStudyColumns)
    {
        AssayResultTable table = new AssayResultTable(this, includeCopiedToStudyColumns);

        ActionURL showDetailsUrl = new ActionURL(AssayResultDetailsAction.class, getContainer());
        showDetailsUrl.addParameter("rowId", getProtocol().getRowId());
        Map<String, String> params = new HashMap<>();
        params.put("dataRowId", AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);
        table.setDetailsURL(new DetailsURL(showDetailsUrl, params));

        ActionURL updateUrl = new ActionURL(CBCAssayController.UpdateAction.class, null);
        updateUrl.addParameter("rowId", getProtocol().getRowId());
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("dataRowId", FieldKey.fromString(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME));
        table.setUpdateURL(new DetailsURL(updateUrl, updateParams));

        return table;
    }

    @Nullable
    @Override
    protected ResultsQueryView createDataQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        return new CBCResultsQueryView((CBCAssayProvider)getProvider(), getProtocol(), context, settings);
    }
}
