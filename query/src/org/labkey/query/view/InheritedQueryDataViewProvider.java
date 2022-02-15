/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.query.view;

import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.ProviderType;
import org.labkey.api.query.CustomView;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.view.ViewContext;

/**
 * User: klum
 * Date: 3/17/13
 */
public class InheritedQueryDataViewProvider extends AbstractQueryDataViewProvider
{
    public static final DataViewProvider.Type TYPE = new ProviderType("grid views (inherited)", "Inherited grid views can be shown if the " +
            "custom view in the parent folder has been saved with the inherit check box selected. Only certain data types can be inherited.", false);

    @Override
    public DataViewProvider.Type getType()
    {
        return TYPE;
    }

    @Override
    protected boolean includeInheritable()
    {
        return true;
    }

    @Override
    protected boolean includeView(ViewContext context, CustomView view)
    {
        return ReportUtil.isInherited(view, context.getContainer());
    }
}
