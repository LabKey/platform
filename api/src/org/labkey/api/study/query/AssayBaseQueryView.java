package org.labkey.api.study.query;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayService;

import java.io.PrintWriter;

/**
 * User: brittp
 * Date: Jul 16, 2007
 * Time: 2:29:31 PM
 */
public abstract class AssayBaseQueryView extends QueryView
{
    protected ExpProtocol _protocol;

    public AssayBaseQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        super(AssayService.get().createSchema(context.getUser(), context.getContainer()), settings);
        _protocol = protocol;
        setShowCustomizeViewLinkInButtonBar(true);
    }

    protected void renderQueryPicker(PrintWriter out)
    {
        // do nothing: we don't want a query picker for assay views
    }

    protected DataRegion createDataRegion()
    {
        DataRegion dr = super.createDataRegion();
        dr.setShowRecordSelectors(showControls());
        dr.setShadeAlternatingRows(true);
        dr.setShowColumnSeparators(true);
        return dr;
    }

    protected boolean showControls()
    {
        return true;
    }
}
