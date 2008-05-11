package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.GridView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;

import java.util.List;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ApplicationOutputGrid extends GridView
{
    public ApplicationOutputGrid(Container c, Integer rowIdPA, TableInfo ti)
    {
        super(new DataRegion());
        List<ColumnInfo> cols = ti.getColumns("RowId,Name,LSID");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);
        getDataRegion().getDisplayColumn(1).setURL(ActionURL.toPathString("Experiment", "resolveLSID", c.getPath()) + "?lsid=${LSID}");
        getDataRegion().getDisplayColumn(2).setWidth("400");
        getDataRegion().getDisplayColumn(2).setTextAlign("left");
        getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("SourceApplicationId", rowIdPA);
        setFilter(filter);
        setTitle("Output " + ti.getName());
    }
}
