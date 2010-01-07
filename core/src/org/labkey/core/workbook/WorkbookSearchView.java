package org.labkey.core.workbook;

import org.labkey.api.view.JspView;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 10:44:03 AM
 */
public class WorkbookSearchView extends JspView<WorkbookSearchBean>
{
    public WorkbookSearchView(WorkbookQueryView queryView)
    {
        this(queryView, null);
    }

    public WorkbookSearchView(WorkbookQueryView queryView, String initialSearchString)
    {
        super("/org/labkey/core/workbook/workbookSearch.jsp", new WorkbookSearchBean(queryView, initialSearchString));
    }
}
