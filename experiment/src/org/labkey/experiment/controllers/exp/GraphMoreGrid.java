package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.GridView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.experiment.api.ExperimentServiceImpl;

/**
 * User: jeckels
* Date: Jan 24, 2008
*/
public class GraphMoreGrid extends GridView
{
    public GraphMoreGrid(Container c, ActionURL url)
    {
        super(new DataRegion());
        String objectType = ExperimentServiceImpl.TYPECODE_PROT_APP;
        String title = "Selected ";
        TableInfo ti;
        String runIdParam;

        if (null != url.getParameter("objtype"))
            objectType = url.getParameter("objtype");

        if (objectType.equals(ExperimentServiceImpl.TYPECODE_MATERIAL))
        {
            ti = ExperimentServiceImpl.get().getTinfoMaterial();
            title += "Materials";
        }
        else if (objectType.equals(ExperimentServiceImpl.TYPECODE_DATA))
        {
            ti = ExperimentServiceImpl.get().getTinfoData();
            title += "Data Objects";
        }
        else
        {
            ti = ExperimentServiceImpl.get().getTinfoProtocolApplication();
            title += "Protocol Applications";
        }

        // for starting inputs, the runId is passed, not looked up
        if (null != url.getParameter("runId"))
            runIdParam="?rowId=" + url.getParameter("runId");
        else
            runIdParam="?rowId=${RunId}";

        ColumnInfo[] cols = ti.getColumns("RowId,Name,LSID,RunId");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);
        getDataRegion().getDisplayColumn(1).setURL(ActionURL.toPathString("Experiment", "resolveLSID", c.getPath()) + "?lsid=${LSID}");
        getDataRegion().getDisplayColumn(2).setVisible(false);
        getDataRegion().getDisplayColumn(3).setVisible(false);

        getDataRegion().addColumn(new SimpleDisplayColumn("[Lineage Graph]"));
        getDataRegion().getDisplayColumn(4).setWidth("200px");
        getDataRegion().getDisplayColumn(4).setURL(ActionURL.toPathString("Experiment", "showRunGraphDetail", c.getPath())
                + runIdParam + "&detail=true&focus=" + objectType + "${rowId}");

        getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        String inClause = " RowId IN (" + url.getParameter("rowId~in") + ") ";
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(inClause, new Object[]{});
        setFilter(filter);
        setTitle(title);
    }
}
