package org.labkey.api.view.template;

import org.labkey.api.view.JspView;
import org.labkey.api.view.ActionURL;
import org.springframework.web.servlet.ModelAndView;

public class PrintTemplate extends JspView<PageConfig>
{
    protected PrintTemplate(String template, PageConfig page)
    {
        super(template, page);
    }

    public PrintTemplate(ModelAndView body, PageConfig page)
    {
        super("/org/labkey/api/view/template/CommonTemplate.jsp", page);
        setFrame(FrameType.NONE);
        setBody(body);
    }

    public PrintTemplate(ModelAndView body, String title)
    {
        this(body, new PageConfig());
        if (title != null)
            getModelBean().setTitle(title);
    }

    public PrintTemplate(ModelAndView body)
    {
        this(body, (String)null);
    }

    public static String getDefaultTitle(ActionURL helper)
    {
        String title = helper.getHost();
        int dotIndex = title.indexOf('.');
        if (-1 != dotIndex)
            title = title.substring(0, dotIndex);

        String extraPath = helper.getExtraPath();
        if (null != extraPath && !"".equals(extraPath))
        {
            int slashIndex = extraPath.lastIndexOf('/');
            if (-1 != slashIndex)
                extraPath = extraPath.substring(slashIndex + 1);

            title = title + ": " + extraPath;
        }
        return title;
    }

    @Override
    public void prepareWebPart(PageConfig page)
    {
        String title = page.getTitle();
        if (null ==  title || 0 == title.length())
        {
            title = PrintTemplate.getDefaultTitle(getRootContext().getActionURL());
            page.setTitle(title);
        }
    }
}
