package org.labkey.study.assay.query;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.data.*;

import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;

/**
 * User: ulberge
 * Date: Aug 14, 2007
 */
public class AssayListPortalView extends AssayListQueryView
{
    public AssayListPortalView(ViewContext context, QuerySettings settings)
    {
        super(context, settings);
    }

    protected DataRegion createDataRegion()
    {
        DataRegion rgn = super.createDataRegion();
        rgn.setShowRecordSelectors(false);
        List<DisplayColumn> displayCols = new ArrayList<DisplayColumn>();
        String[] displayColNames = { "name", "description", "type", "created", "modified"};

        for (DisplayColumn col : rgn.getDisplayColumns())
        {
            String colName = col.getName();
            for (String displayColName : displayColNames)
            {
                if (displayColName.equalsIgnoreCase(colName) || !(col instanceof DataColumn))
                {
                    displayCols.add(col);
                    break;
                }
            }
        }
        rgn.setDisplayColumns(displayCols);
        return rgn;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        super.renderView(model, out);
        out.write(textLink("Manage Assays", new ActionURL("assay", "begin.view", getContainer())));
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
    }
}
