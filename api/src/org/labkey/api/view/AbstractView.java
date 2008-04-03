package org.labkey.api.view;

import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Dec 7, 2006
 * Time: 4:06:15 PM
 */

public abstract class AbstractView<ModelBean> implements View
{
    public String getContentType()
    {
        return "text/html";
    }

    /** would like to make this render(bean, out), one step at a time */
    public abstract void render(ModelBean bean, HttpServletRequest request, HttpServletResponse response) throws java.lang.Exception;
}
