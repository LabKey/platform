package org.labkey.api.laboratory.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**

 */
public class AssaySummaryNavItem extends QueryCountNavItem
{
    private ExpProtocol _protocol;

    public AssaySummaryNavItem(DataProvider provider, String schema, String query, String category, String label, ExpProtocol p)
    {
        super(provider, schema, query, category, label);
        _protocol = p;
    }

    @Override
    protected ActionURL getActionURL(Container c, User u)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, _protocol);
    }
}
