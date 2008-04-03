package org.labkey.study.view;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.JspView;

import javax.servlet.ServletException;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 8, 2006
 * Time: 3:54:21 PM
 */
public class DatasetsWebPartView extends JspView<Object>
{
    public DatasetsWebPartView()
    {
        super("/org/labkey/study/view/datasets.jsp");
        setTitle("Study Datasets");
    }

    @Override
    protected void prepareWebPart(Object model) throws ServletException
    {
        super.prepareWebPart(model);
    }
}
