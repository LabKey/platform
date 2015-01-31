package org.labkey.api.announcements.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

import java.util.List;

/**
 * Created by Marty on 1/19/2015.
 */
public class TourService
{
    static private Interface instance;

    public static final String MODULE_NAME = "Announcement";

    @Nullable
    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface
    {
        public ActionURL getManageListsURL(Container container);
        public List<Tour> getApplicableTours(@Nullable Container container);
    }
}
