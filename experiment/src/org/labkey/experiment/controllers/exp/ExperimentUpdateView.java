package org.labkey.experiment.controllers.exp;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.view.UpdateView;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.springframework.validation.BindException;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ExperimentUpdateView extends UpdateView
{
    public ExperimentUpdateView(DataRegion drg, ExperimentForm form, BindException errors)
    {
        super(drg, form, errors);
        drg.addColumns(ExperimentServiceImpl.get().getTinfoExperiment(), "RowId,Name,LSID,ContactId,ExperimentDescriptionURL,Hypothesis,Comments,Created");

        drg.setFixedWidthColumns(false);
        DisplayColumn col = drg.getDisplayColumn("RowId");
        col.setVisible(false);
        drg.getDisplayColumn("LSID").setVisible(false);
        drg.getDisplayColumn("Created").setVisible(false);

        ButtonBar bb = new ButtonBar();
        bb.add(ActionButton.BUTTON_DO_UPDATE);

        drg.setButtonBar(bb);
    }
}
