package org.labkey.study.view;

import org.labkey.api.view.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Nov 17, 2008
 * Time: 9:28:08 PM
 */
public class StudyListWebPartFactory extends AlwaysAvailableWebPartFactory
{
   public StudyListWebPartFactory()
   {
       super("Study List", "menubar", false, false);
   }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        WebPartView view = new JspView(this.getClass(), "studyList.jsp", null);
        view.setTitle("Studies");
        return view;
    }
}
