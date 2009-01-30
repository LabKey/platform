package org.labkey.study.view;

import org.labkey.api.view.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Nov 17, 2008
 * Time: 9:28:08 PM
 */
public class AssayList2WebPartFactory extends AlwaysAvailableWebPartFactory
{
   public AssayList2WebPartFactory()
   {
       super("AssayList2", "menubar", false, false);
   }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        WebPartView view = new JspView(this.getClass(), "assayList2.jsp", null);
        view.setTitle("Assays");
        return view;
    }
}
