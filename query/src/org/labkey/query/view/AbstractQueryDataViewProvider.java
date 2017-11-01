/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.query.ModuleCustomView;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 3/17/13
 */
public abstract class AbstractQueryDataViewProvider implements DataViewProvider
{
    @Override
    public void initialize(ContainerUser context)
    {
    }

    @Override
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        List<DataViewInfo> dataViews = new ArrayList<>();
        Container ctxContainer = context.getContainer();

        for (CustomView view : getCustomViews(context))
        {
            Container viewContainer = view.getContainer();
            DefaultViewInfo info = new DefaultViewInfo(getType(), view.getEntityId(), view.getLabel(), null != viewContainer ? viewContainer : ctxContainer);

            info.setType("Query");

            // issue : 21895: Use category provided in custom view XML in data views
            ViewCategory vc = null;
            if (view instanceof ModuleCustomView)
            {
                String category = ((ModuleCustomView)view).getCategory();
                if (category != null)
                {
                    String[] parts = ViewCategoryManager.getInstance().decode(category);
                    vc = ViewCategoryManager.getInstance().getCategory(context.getContainer(), parts);

                    // BUG 24355: For shared datasets, project level dataset categories don't match subfolder dataset categories
                    // if this query is inherited then we might want to pick up the view category from a parent directory,
                    // how do we know where the query/table is defined?
                    // TODO general way to find the correct parent directory
                    if (null == vc && !context.getContainer().isProject())
                    {
                        Container project = context.getContainer().getProject();
                        if (null != project && project.isDataspace())
                            vc = ViewCategoryManager.getInstance().getCategory(project, parts);
                    }
                }
            }
            // no explicit category could be found, use the default category
            if (vc == null)
                vc = ReportUtil.getDefaultCategory(ctxContainer, view.getSchemaName(), view.getQueryName());

            info.setCategory(vc);

            info.setCreatedBy(view.getCreatedBy());
            info.setModified(view.getModified());
            info.setShared(view.getOwner() == null);
            info.setAccess(view.isShared() ? "public" : "private");

            info.setSchemaName(view.getSchemaName());
            info.setQueryName(view.getQueryName());

            // run url and details url are the same for now
            ActionURL runUrl = getViewRunURL(context.getUser(), ctxContainer, view);

            info.setRunUrl(runUrl);
            info.setDetailsUrl(runUrl);

            if (!StringUtils.isEmpty(view.getCustomIconUrl()))
            {
                info.setIconUrl(new ResourceURL(view.getCustomIconUrl()));
                info.setIconCls(view.getCustomIconCls());
            }

            // Always return link to a static image for now. See ReportViewProvider for an example of a dynamic thumbnail provider.
            info.setThumbnailUrl(new ResourceURL("/query/query.png"));

            dataViews.add(info);
        }
        return dataViews;
    }

    protected boolean includeInheritable() {
        return false;
    }

    protected List<CustomView> getCustomViews(ViewContext context)
    {
        List<CustomView> views = new ArrayList<>();

        for (CustomView view : QueryService.get().getCustomViews(context.getUser(), context.getContainer(), context.getUser(), null, null, includeInheritable()))
        {
            // issue : 21711 add the ability to hide module custom views from data views
            if (view instanceof ModuleCustomView)
            {
                if (!((ModuleCustomView)view).isShowInDataViews())
                    continue;
            }

            if (view.isHidden())
                continue;

            if (view.getName() == null)
                continue;

            if (includeView(context, view))
                views.add(view);
        }
        return views;
    }

    protected abstract boolean includeView(ViewContext context, CustomView view);

    private ActionURL getViewRunURL(User user, Container c, CustomViewInfo view)
    {
        String dataregionName = QueryView.DATAREGIONNAME_DEFAULT;

        return QueryService.get().urlDefault(c,
                QueryAction.executeQuery, view.getSchemaName(), view.getQueryName()).
                addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName.name(), view.getName());
    }

    @Override
    public DataViewProvider.EditInfo getEditInfo()
    {
        return null;
    }

    @Override
    public boolean isVisible(Container container, User user)
    {
        return true;
    }
}
