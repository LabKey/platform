package org.labkey.study.view;

import org.labkey.api.view.*;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.assay.AssayService;

/**
 * User: jeckels
 * Date: Jul 19, 2007
 */
public class AssayListWebPartFactory extends WebPartFactory
{
    public AssayListWebPartFactory()
    {
        super("Assay List");
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        QueryView listView = AssayService.get().createAssayListView(portalCtx, true);
        ActionURL url = portalCtx.cloneActionURL();
        url.deleteParameters();
        url.setPageFlow("assay");
        url.setAction("begin.view");
        listView.setTitle("Assay List");
        listView.setTitleHref(url.getLocalURIString());
        return listView;
    }
}
