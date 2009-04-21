package org.labkey.filecontent;

import org.labkey.api.data.Container;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 15, 2009
 * Time: 12:47:02 PM
 */
public class ManageWebPart extends FilesWebPart
{
    private static final String JSP = "/org/labkey/filecontent/view/davfiles.jsp";


    public ManageWebPart(Container c)
    {
        super(JSP, c);
    }

    public ManageWebPart(Container c, String fileSet)
    {
        super(JSP, c, fileSet);
    }

}
