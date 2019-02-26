package org.labkey.api.products;

import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

public class UserInfoMenuSection extends MenuSection
{
    public static final String NAME = "User Info";

    private ProductMenuProvider _provider;

    public UserInfoMenuSection(ViewContext context, ProductMenuProvider provider)
    {
        super(context, NAME, "user", null);
        _provider = provider;
    }

    @Override
    public ActionURL getUrl()
    {
        return null;
    }

    @Override
    public List<MenuItem> getAllItems()
    {
        List<MenuItem> items = new ArrayList<>();
        UserUrls urlProvider =  PageFlowUtil.urlProvider(UserUrls.class);
        if (urlProvider != null)
            // TODO would be nice to provide a return url here, I think
            items.add(new MenuItem("Profile", getUser().getUserId(), urlProvider.getUserDetailsURL(getContainer(), getUser().getUserId(), null)));
        ActionURL docUrl = _provider.getDocumentationUrl();
        if (docUrl != null)
            items.add(new MenuItem("Documentation", docUrl));
        items.addAll(_provider.getUserMenuItems());

        ProjectUrls projectUrls = PageFlowUtil.urlProvider(ProjectUrls.class);
        if (projectUrls != null)
            items.add(new MenuItem("Switch to LabKey", projectUrls.getBeginURL(getContainer())));
        _totalCount = items.size();

        return items;
    }

}
