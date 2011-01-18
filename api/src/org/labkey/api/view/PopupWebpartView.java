package org.labkey.api.view;

import org.labkey.api.util.UniqueID;

import java.io.PrintWriter;

/**
 * User: Nick Arnold
 */
public class PopupWebpartView extends PopupMenuView
{
    private boolean visible;

    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
        visible = true;
        if (visible)
            super.renderInternal(model, out);
        else
            out.write("&nbsp;");
    }

    public PopupWebpartView(final ViewContext context, NavTree menu)
    {
        if (menu != null)
        {
            menu.setId("webpartMenu" + UniqueID.getRequestScopedUID(context.getRequest()));
            setNavTree(menu);
        }
        
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }
}
