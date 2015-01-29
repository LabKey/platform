package org.labkey.announcements.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.ToursController;
import org.labkey.announcements.model.TourManager;
import org.labkey.api.announcements.api.TourService;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

import java.util.List;
import java.util.Map;

/**
 * Created by Marty on 1/19/2015.
 */
public class TourServiceImpl implements TourService.Interface
{
    public ActionURL getManageListsURL(Container container)
    {
        return new ActionURL(ToursController.BeginAction.class, container);
    }

    public List<Map<String,String>> getApplicableTours(@Nullable Container container)
    {
        return TourManager.getApplicableTours(container);
    }
}
