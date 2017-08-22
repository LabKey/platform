package org.labkey.study.view.studydesign;

import org.labkey.api.data.Container;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

public class StudyDesignConfigureMenuItem extends NavTree
{
    public StudyDesignConfigureMenuItem(String text, String schemaName, String queryName, Container container)
    {
        super(text);

        ActionURL url = new ActionURL();
        url.setContainer(container);
        url.addParameter("schemaName", schemaName);
        url.addParameter("query.queryName", queryName);
        setHref(PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(url).toString());
        setTarget("_blank");  // issue 19493
    }
}
