package org.labkey.core;

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: jeckels
 * Date: Jan 4, 2007
 */
@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class CoreController  extends ViewController
{
    private static final long SECS_IN_DAY = 60 * 60 * 24;
    private static final long MILLIS_IN_DAY = 1000 * SECS_IN_DAY;

    @Jpf.Action
    protected Forward begin() throws Exception
    {
        return null;
    }


    static AtomicReference<PageFlowUtil.Content> _cssContent = new AtomicReference<PageFlowUtil.Content>();


    @Jpf.Action
    protected Forward stylesheet() throws Exception
    {
        // This action gets called a LOT, so cache the generated .css 
        PageFlowUtil.Content c = _cssContent.get();
        Integer dependsOn = AppProps.getInstance().getLookAndFeelRevision();
        if (null == c || !dependsOn.equals(c.dependencies) || null != getRequest().getParameter("nocache") || AppProps.getInstance().isDevMode())
        {
            JspView view = new JspView("/org/labkey/core/stylesheet.jsp");
            view.setFrame(WebPartView.FrameType.NONE);
            c = PageFlowUtil.getViewContent(view, getRequest(), getResponse());
            c.dependencies = dependsOn;
            c.encoded = compressCSS(c.content);
            _cssContent.set(c);
        }

        HttpServletResponse response = getResponse();
        response.setContentType("text/css");
        response.setDateHeader("Expires", System.currentTimeMillis() + MILLIS_IN_DAY * 10);
        response.setDateHeader("Last-Modified", c.modified);
        if (StringUtils.trimToEmpty(getRequest().getHeader("Accept-Encoding")).contains("gzip"))
        {
            response.setHeader("Content-Encoding", "gzip");
            response.getOutputStream().write(c.encoded);
        }
        else
        {
            response.getWriter().write(c.content);
        }
        return null;
    }


    byte[] compressCSS(String s)
    {
        String c = s.trim();
        // this works but probably unnecesary with gzip
        //c = c.replaceAll("\\s+", " ");
        //c = c.replaceAll("\\s*}\\s*", "}\r\n");
        return PageFlowUtil.gzip(c);
    }


    @Jpf.Action
    protected Forward containerRedirect(RedirectForm form) throws Exception
    {
        Container targetContainer = ContainerManager.getForId(form.getContainerId());
        if (targetContainer == null)
        {
            return HttpView.throwNotFound();
        }
        ActionURL url = getActionURL().clone();
        url.deleteParameter("action");
        url.deleteParameter("pageflow");
        url.deleteParameter("containerId");
        url.setPageFlow(form.getPageflow());
        url.setAction(form.getAction());
        url.setExtraPath(targetContainer.getPath());
        return HttpView.throwRedirect(url);
    }


    public static class RedirectForm extends FormData
    {
        private String _containerId;
        private String _action;
        private String _pageflow;

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public String getContainerId()
        {
            return _containerId;
        }

        public void setContainerId(String containerId)
        {
            _containerId = containerId;
        }

        public String getPageflow()
        {
            return _pageflow;
        }

        public void setPageflow(String pageflow)
        {
            _pageflow = pageflow;
        }
    }

}
