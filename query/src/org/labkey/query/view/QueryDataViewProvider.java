/*
 * Copyright (c) 2012 LabKey Corporation
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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Apr 4, 2012
 */
public class QueryDataViewProvider implements DataViewProvider
{
    private static final DataViewProvider.Type _type = new ProviderType("queries", "Custom Views", true);

    public static DataViewProvider.Type getType()
    {
        return _type;
    }

    @Override
    public void initialize(ViewContext context)
    {
    }
    
    @Override
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        List<DataViewInfo> dataViews = new ArrayList<DataViewInfo>();

        for (CustomViewInfo view : QueryService.get().getCustomViews(context.getUser(), context.getContainer(), null, null))
        {
            if (view.isHidden())
                continue;

            if (view.getName() == null)
                continue;

            DefaultViewInfo info = new DefaultViewInfo(_type, view.getEntityId(), view.getName(), view.getContainer());

            info.setType("Query");

            ViewCategory vc = ReportUtil.getDefaultCategory(context.getContainer(), view.getSchemaName(), view.getQueryName());
            info.setCategory(vc);

            info.setCreatedBy(view.getCreatedBy());
            info.setModified(view.getModified());
            info.setShared(view.getOwner() == null);
            info.setAccess(view.isShared() ? "public" : "private");

            info.setSchemaName(view.getSchemaName());
            info.setQueryName(view.getQueryName());

            // run url and details url are the same for now
            ActionURL runUrl = getViewRunURL(context.getUser(), context.getContainer(), view);

            info.setRunUrl(runUrl);
            info.setDetailsUrl(runUrl);

            if (!StringUtils.isEmpty(view.getCustomIconUrl()))
            {
                URLHelper url = new URLHelper(view.getCustomIconUrl());
                url.setContextPath(AppProps.getInstance().getParsedContextPath());
                info.setIcon(url.toString());
            }

            info.setThumbnailUrl(PageFlowUtil.urlProvider(QueryUrls.class).urlThumbnail(context.getContainer()));

            dataViews.add(info);
        }
        return dataViews;
    }

    private ActionURL getViewRunURL(User user, Container c, CustomViewInfo view)
    {
        String dataregionName = QueryView.DATAREGIONNAME_DEFAULT;

        if (StudyService.get().getStudy(c) != null)
        {
            dataregionName = "Dataset";
        }
        return QueryService.get().urlFor(user, c,
                QueryAction.executeQuery, view.getSchemaName(), view.getQueryName()).
                addParameter(dataregionName + "." + QueryParam.viewName.name(), view.getName());
    }

    @Override
    public EditInfo getEditInfo()
    {
        return null;
    }

    @Override
    public boolean isVisible(Container container, User user)
    {
        return true;
    }
}
