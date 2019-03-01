package org.labkey.api.products;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

public class UserInfoMenuSection extends MenuSection
{
    public static final String NAME = "Your Items";

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
    @NotNull
    public List<MenuItem> getAllItems()
    {
        List<MenuItem> items = new ArrayList<>();
        UserUrls urlProvider =  PageFlowUtil.urlProvider(UserUrls.class);
        if (urlProvider != null)
            items.add(new MenuItem("Profile", urlProvider.getUserDetailsURL(getContainer(), getUser().getUserId(), getContext().getActionURL()), getUser().getUserId(), 0));
        String docUrl = _provider.getDocumentationUrl();
        if (docUrl != null)
            items.add(new MenuItem("Documentation", docUrl, null, 1));
        items.addAll(_provider.getUserMenuItems());

//        ProjectUrls projectUrls = PageFlowUtil.urlProvider(ProjectUrls.class);
//        if (projectUrls != null)
//            items.add(new MenuItem("Switch to LabKey", projectUrls.getBeginURL(getContainer())));
//        _totalCount = items.size();
        return items;
    }

}
