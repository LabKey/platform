package org.labkey.core.admin.miniprofiler;

import org.apache.log4j.Logger;
import org.labkey.api.action.IgnoresAllocationTracking;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: kevink
 * Date: 9/22/14
 */
@Marshal(Marshaller.Jackson)
public class MiniProfilerController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
            MiniProfilerController.class);

    private static final Logger LOG = Logger.getLogger(MiniProfilerController.class);

    public MiniProfilerController()
    {
        setActionResolver(_actionResolver);
    }

    public static class ReportForm
    {
        private long _id;

        public long getId()
        {
            return _id;
        }

        public void setId(long id)
        {
            _id = id;
        }
    }

    @RequiresNoPermission // permissions will be checked in the action
    @IgnoresAllocationTracking
    public class ReportAction extends MutatingApiAction<ReportForm>
    {
        @Override
        public Object execute(ReportForm form, BindException errors) throws Exception
        {
            if (!MiniProfiler.isEnabled(getViewContext()))
                throw new UnauthorizedException();

            RequestInfo req = MemTracker.getInstance().getRequest(form.getId());
            MemTracker.get().setViewed(getUser(), form.getId());

            // Reset the X-MiniProfiler-Ids header to only include remaining unviewed (without the id we are returning)
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            ids.addAll(MemTracker.get().getUnviewed(getUser()));
            getViewContext().getResponse().setHeader("X-MiniProfiler-Ids", ids.toString());

            return req;
        }
    }

    @RequiresNoPermission // permissions will be checked in the action
    @IgnoresAllocationTracking
    public class RecentRequestsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (!MiniProfiler.isEnabled(getViewContext()))
                throw new UnauthorizedException();

            // TODO: filter requests by user/session if not site admin
            List<RequestInfo> requests = MemTracker.getInstance().getNewRequests(0);
            //return new JspView<List<RequestInfo>>("...");
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}
