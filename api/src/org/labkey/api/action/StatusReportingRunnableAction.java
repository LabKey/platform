package org.labkey.api.action;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.util.*;

/**
* User: adam
* Date: Jun 26, 2010
* Time: 10:37:05 PM
*/
public abstract class StatusReportingRunnableAction<K extends StatusReportingRunnable> extends FormViewAction<StatusReportingRunnableAction.StatusReportingRunnableForm>
{
    private static final Map<Class<? extends StatusReportingRunnableAction>, StatusReportingRunnable> EXISTING_RUNNABLES = new HashMap<Class<? extends StatusReportingRunnableAction>, StatusReportingRunnable>();

    @Override
    public void validateCommand(StatusReportingRunnableForm form, Errors errors)
    {
    }

    @Override
    public ModelAndView getView(StatusReportingRunnableForm form, boolean reshow, BindException errors) throws Exception
    {
        getPageConfig().setTemplate(PageConfig.Template.Dialog);

        K runnable = getRunnable();

        if (null == runnable)
            return new HtmlView("Error: Task has not been run");

        final Collection<String> status = runnable.getStatus(form.getOffset());

        return new WebPartView() {
            @Override
            protected void renderView(Object model, PrintWriter out) throws Exception
            {
                for (String line : status)
                {
                    out.print(line);
                    out.print("<br>\n");
                }
            }
        };
    }

    @Override
    public boolean handlePost(StatusReportingRunnableForm form, BindException errors) throws Exception
    {
        synchronized (this.getClass())
        {
            ensureRunning();
        }

        return true;
    }

    private @Nullable K getRunnable()
    {
        synchronized (EXISTING_RUNNABLES)
        {
            //noinspection unchecked
            return (K) EXISTING_RUNNABLES.get(this.getClass());
        }
    }

    private @NotNull K ensureRunning()
    {
        synchronized (EXISTING_RUNNABLES)
        {
            StatusReportingRunnable runnable = EXISTING_RUNNABLES.get(this.getClass());

            if (null == runnable || !runnable.isRunning())
            {
                runnable = createRunnable();
                runnable.run();
                EXISTING_RUNNABLES.put(this.getClass(), runnable);
            }

            //noinspection unchecked
            return (K) runnable;
        }
    }

    protected abstract K createRunnable();

    @Override
    public URLHelper getSuccessURL(StatusReportingRunnableForm form)
    {
        return getViewContext().getActionURL();
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        throw new IllegalStateException("Shouldn't get here");
    }

    public static class StatusReportingRunnableForm
    {
        private Integer _offset = null;

        public Integer getOffset()
        {
            return _offset;
        }

        public void setOffset(Integer offset)
        {
            _offset = offset;
        }
    }
}
