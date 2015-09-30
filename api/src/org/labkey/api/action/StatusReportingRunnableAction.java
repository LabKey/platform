/*
 * Copyright (c) 2010-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.action;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.StatusReportingRunnableAction.StatusReportingRunnableForm;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
* User: adam
* Date: Jun 26, 2010
* Time: 10:37:05 PM
*/

// Base action class that starts a background task and dynamically reports status of that task back to the initiating
// user and any other user who attempts to invoke the task.  Sort of like a very simple, single-task "pipeline".
//
// To use it, create a class that implements the StatusReportingRunnable interface (just Runnable with a couple status
// reporting methods).  Subclass this base action in your controller, add the appropriate permissions annotation, and
// implement newStatusReportingRunnable() to return an instance of your StatusReportingRunnable.  Then, via a link or
// button, POST to your new action with no parameters.  The action will:
//
// - Check to see if your StatusReportingRunnable is already running; if not, it will create one and start it.
// - Return a status update page that uses AJAX to report the status of the task, updating it once per second.  The
//   action holds onto the StatusReportingRunnable after it finishes, so status can be viewed for completed tasks.
//
// Note that the base action ensures that only one instance of your task executes at any time.  If a request is made
// to start a task that's already running, the status of the currently running task will be reported.  Once a task is
// complete, a new request will start a new instance of that task.
//
// Your StatusReportingRunnable can track its status any way it wants to, but StatusAppender provides an easy way to do
// this via standard log4j mechanisms.  See the StatusAppender comments for more details.
public abstract class StatusReportingRunnableAction<K extends StatusReportingRunnable> extends FormApiAction<StatusReportingRunnableForm>
{
    private static final Map<Class<? extends StatusReportingRunnableAction>, StatusReportingRunnable> EXISTING_RUNNABLES = new HashMap<>();

    protected abstract K newStatusReportingRunnable();

    @Override
    public ModelAndView getView(StatusReportingRunnableForm form, BindException errors) throws Exception
    {
        getPageConfig().setTemplate(PageConfig.Template.Dialog);

        return new JspView("/org/labkey/api/action/statusReport.jsp");
    }

    @Override
    public ModelAndView handlePost() throws Exception
    {
        ActionURL url = getViewContext().getActionURL();

        // Offset parameter means browser is requesting status; if this parameter is not specified, attempt to start the task.
        if (null != url.getParameter("offset"))
        {
            return super.handlePost();
        }
        else
        {
            ensureRunning();

            // Redirect to current URL to render the view.  We could call handleGet() to render it directly, but then
            // the browser would be left with a POST that can't be refreshed as easily.
            return HttpView.redirect(url);
        }
    }

    @Override
    public ApiResponse execute(StatusReportingRunnableForm form, BindException errors) throws Exception
    {
        K runnable = getRunnable();

        Collection<String> status;
        boolean complete;

        if (null == runnable)
        {
            status = Collections.singleton("Error: Task has not been run");
            complete = true;
        }
        else
        {
            status = runnable.getStatus(form.getOffset());
            complete = !runnable.isRunning();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("status", status);
        map.put("complete", complete);

        return new ApiSimpleResponse(map);
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
                runnable = newStatusReportingRunnable();
                ExecutorService exec = Executors.newFixedThreadPool(1);
                exec.submit(runnable);
                exec.shutdown();
                EXISTING_RUNNABLES.put(this.getClass(), runnable);
            }

            //noinspection unchecked
            return (K) runnable;
        }
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
