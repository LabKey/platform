package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.GridView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.*;
import org.labkey.experiment.api.ExperimentServiceImpl;

/**
 * User: jeckels
* Date: Dec 18, 2007
*/
public class ProtocolListView extends GridView
{
    public ProtocolListView(ExpProtocol protocol, Container c)
    {
        super(new DataRegion());
        TableInfo ti = ExperimentServiceImpl.get().getTinfoProtocolActionDetails();
        ColumnInfo[] cols = ti.getColumns("RowId,Name,LSID,ActionSequence,ProtocolDescription");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);
        getDataRegion().getDisplayColumn(1).setURL(ActionURL.toPathString("Experiment", "protocolPredecessors", c.getPath()) + "?ParentLSID=" + protocol.getLSID() + "&Sequence=${ActionSequence}");
        getDataRegion().getDisplayColumn(2).setTextAlign("left");

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("ParentProtocolLSID", protocol.getLSID(), CompareType.EQUAL);
        filter.addCondition("ChildProtocolLSID", protocol.getLSID(), CompareType.NEQ);
        setFilter(filter);

        setSort(new Sort("ActionSequence"));

        getDataRegion().setButtonBar(new ButtonBar());

        setTitle("Protocol Steps");
    }
}
