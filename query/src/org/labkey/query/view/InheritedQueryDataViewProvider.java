package org.labkey.query.view;

import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.ProviderType;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.view.ViewContext;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 3/17/13
 */
public class InheritedQueryDataViewProvider extends AbstractQueryDataViewProvider
{
    public static final DataViewProvider.Type TYPE = new ProviderType("queries (inherited)", "Inherited Custom Views", false);

    public DataViewProvider.Type getType()
    {
        return TYPE;
    }

    @Override
    protected boolean includeView(ViewContext context, CustomViewInfo view)
    {
        return ReportUtil.isInherited(view, context.getContainer());
    }
}
