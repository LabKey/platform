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
package org.labkey.query.view;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DefaultViewInfo;
import org.labkey.api.data.views.ProviderType;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: Apr 4, 2012
 */
public class QueryDataViewProvider extends AbstractQueryDataViewProvider
{
    public static final DataViewProvider.Type TYPE = new ProviderType("queries", "Custom Views", true);

    public DataViewProvider.Type getType()
    {
        return TYPE;
    }

    @Override
    protected boolean includeView(ViewContext context, CustomViewInfo view)
    {
        return !ReportUtil.isInherited(view, context.getContainer());
    }
}
