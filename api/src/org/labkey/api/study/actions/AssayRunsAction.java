package org.labkey.api.study.actions;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.*;
import org.labkey.api.study.assay.AssayRunsView;
import org.labkey.api.data.DataRegionSelection;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:30:05 PM
*/
@RequiresPermission(ACL.PERM_READ)
public class AssayRunsAction extends BaseAssayAction<AssayRunsAction.ClearSelectionForm>
{
    public static class ClearSelectionForm extends ProtocolIdForm
    {
        private String _clearCataRegionSelectionKey;

        public String getClearCataRegionSelectionKey()
        {
            return _clearCataRegionSelectionKey;
        }

        public void setClearCataRegionSelectionKey(String clearCataRegionSelectionKey)
        {
            _clearCataRegionSelectionKey = clearCataRegionSelectionKey;
        }
    }

    private ExpProtocol _protocol;

    public ModelAndView getView(ClearSelectionForm summaryForm, BindException errors) throws Exception
    {
        if (summaryForm.getClearCataRegionSelectionKey() != null)
            DataRegionSelection.clearAll(getViewContext(), summaryForm.getClearCataRegionSelectionKey());
        _protocol = getProtocol(summaryForm);
        return new AssayRunsView(_protocol, false);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild(_protocol.getName() + " Runs");
    }
}
