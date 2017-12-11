package org.labkey.api.wiki;

import org.labkey.api.view.WebPartView;

public class WikiTemplateView
{
    public enum userView {
        ALL_USERS,
        ONLY_GUESTS,
        ONLY_REGISTERED_USERS
    }

    private String activeModuleName;
    private userView visibility;
    private WebPartView view;

    public WikiTemplateView(WebPartView view)
    {
         this(view, userView.ALL_USERS, null);
    }

    public WikiTemplateView(WebPartView view, userView visibility)
    {
        this(view, visibility, null);
    }

    public WikiTemplateView(WebPartView view, userView visibility, String moduleName)
    {
        this.view = view;
        this.visibility = visibility;
        this.activeModuleName = moduleName;
    }

    public String getRequiredActiveModuleName()
    {
        return activeModuleName;
    }

    public userView getVisibility()
    {
        return visibility;
    }

    public WebPartView getView()
    {
        return view;
    }
}
