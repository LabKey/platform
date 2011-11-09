package org.labkey.api.view;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-11-09
 * Time: 1:28 PM
 */
public class HttpRedirectView extends HttpView
{
    final String _url;

    public HttpRedirectView(String url)
    {
        _url = url;
    }

    @Override
    public View getView()
    {
        return new RedirectView(_url, false);
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        throw new RedirectException(_url);
    }
}
