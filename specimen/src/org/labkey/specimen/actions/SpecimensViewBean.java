package org.labkey.specimen.actions;

import org.labkey.api.specimen.Vial;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.view.ViewContext;

import java.util.List;

public abstract class SpecimensViewBean
{
    protected SpecimenQueryView _specimenQueryView;
    protected List<Vial> _vials;

    public SpecimensViewBean(ViewContext context, List<Vial> vials, boolean showHistoryLinks,
                             boolean showRecordSelectors, boolean disableLowVialIndicators, boolean restrictRecordSelectors)
    {
        _vials = vials;
        if (vials != null && vials.size() > 0)
        {
            _specimenQueryView = SpecimenQueryView.createView(context, vials, SpecimenQueryView.ViewType.VIALS);
            _specimenQueryView.setShowHistoryLinks(showHistoryLinks);
            _specimenQueryView.setShowRecordSelectors(showRecordSelectors);
            _specimenQueryView.setDisableLowVialIndicators(disableLowVialIndicators);
            _specimenQueryView.setRestrictRecordSelectors(restrictRecordSelectors);
        }
    }

    public SpecimenQueryView getSpecimenQueryView()
    {
        return _specimenQueryView;
    }

    public List<Vial> getVials()
    {
        return _vials;
    }
}
