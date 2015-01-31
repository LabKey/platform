package org.labkey.announcements.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.ToursController;
import org.labkey.announcements.model.TourManager;
import org.labkey.announcements.model.TourModel;
import org.labkey.api.announcements.api.Tour;
import org.labkey.api.announcements.api.TourService;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Marty on 1/19/2015.
 */
public class TourServiceImpl implements TourService.Interface
{
    public ActionURL getManageListsURL(Container container)
    {
        return new ActionURL(ToursController.BeginAction.class, container);
    }

    public List<Tour> getApplicableTours(@Nullable Container container)
    {
        List<Tour> tours = new ArrayList<>();
        for(TourModel model : TourManager.getApplicableTours(container))
        {
            tours.add(new TourImpl(model));
        }

        return tours;
    }
}
