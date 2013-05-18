package org.labkey.api.assay.nab.view;

import org.labkey.api.assay.nab.Luc5Assay;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.nab.NabUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DataView;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 5/17/13
*/
public class DuplicateDataFileRunView extends RunListQueryView
{
    private Luc5Assay _assay;
    private ExpRun _run;

    public DuplicateDataFileRunView(AssayProtocolSchema schema, QuerySettings settings, Luc5Assay assay, ExpRun run)
    {
        super(schema, settings);
        setShowExportButtons(false);
        _assay = assay;
        _run = run;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion rgn = view.getDataRegion();
        ButtonBar bar = rgn.getButtonBar(DataRegion.MODE_GRID);
        ActionButton selectButton = ActionButton.BUTTON_SELECT_ALL.clone();
        selectButton.setDisplayPermission(InsertPermission.class);
        bar.add(selectButton);

        ActionButton clearButton = ActionButton.BUTTON_CLEAR_ALL.clone();
        clearButton.setDisplayPermission(InsertPermission.class);
        bar.add(clearButton);

        ActionButton deleteButton = new ActionButton(PageFlowUtil.urlProvider(NabUrls.class).urlDeleteRun(getContainer()),
                "Delete", DataRegion.MODE_GRID, ActionButton.Action.POST);
        deleteButton.setRequiresSelection(true);
        deleteButton.setDisplayPermission(DeletePermission.class);
        bar.add(deleteButton);

        SimpleFilter filter;
        if (view.getRenderContext().getBaseFilter() instanceof SimpleFilter)
        {
            filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
        }
        else
        {
            filter = new SimpleFilter(view.getRenderContext().getBaseFilter());
        }
        filter.addCondition("Name", _assay.getDataFile().getName());
        filter.addCondition("RowId", _run.getRowId(), CompareType.NEQ);
        view.getRenderContext().setBaseFilter(filter);
        return view;
    }

    public void setRun(ExpRun run)
    {
        _run = run;
    }
}
