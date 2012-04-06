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
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        List<DataViewInfo> dataViews = new ArrayList<DataViewInfo>();

        for (CustomViewInfo view : QueryService.get().getCustomViewInfos(context.getUser(), context.getContainer(), null, null))
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

            // run url and details url are the same for now
            ActionURL runUrl = QueryService.get().urlFor(context.getUser(), context.getContainer(),
                    QueryAction.executeQuery, view.getSchemaName(), view.getQueryName()).
                    addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName.name(), view.getName());

            info.setRunUrl(runUrl);
            info.setDetailsUrl(runUrl);

            if (!StringUtils.isEmpty(view.getCustomIconUrl()))
                info.setIcon(view.getCustomIconUrl());

            info.setThumbnailUrl(PageFlowUtil.urlProvider(QueryUrls.class).urlThumbnail(context.getContainer()));

            dataViews.add(info);
        }
        return dataViews;
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
