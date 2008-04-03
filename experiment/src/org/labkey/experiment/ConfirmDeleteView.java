package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.ACL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.controllers.exp.ExperimentController;

import javax.servlet.ServletException;
import java.util.*;

/**
 * User: jeckels
* Date: Nov 21, 2007
*/
public class ConfirmDeleteView extends JspView<ConfirmDeleteView.ConfirmDeleteBean>
{
    public static class ConfirmDeleteBean
    {
        private final Map<ExpRun, Container> _runsWithPermission;
        private final Map<ExpRun, Container> _runsWithoutPermission;
        private final ViewContext _viewContext;
        private final List<? extends ExpObject> _objects;
        private final String _objectType;
        private final String _detailAction;
        private String _returnURL;
        private String _dataRegionSelectionKey;

        public ConfirmDeleteBean(Map<ExpRun, Container> runsWithPermission, Map<ExpRun, Container> runsWithoutPermission, ViewContext viewContext, List<? extends ExpObject> objects, String objectType, String detailAction)
        {
            _runsWithPermission = runsWithPermission;
            _runsWithoutPermission = runsWithoutPermission;
            _viewContext = viewContext;
            _objects = objects;
            _objectType = objectType;
            _detailAction = detailAction;
        }

        public Map<ExpRun, Container> getRunsWithPermission()
        {
            return _runsWithPermission;
        }

        public Map<ExpRun, Container> getRunsWithoutPermission()
        {
            return _runsWithoutPermission;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public List<? extends ExpObject> getObjects()
        {
            return _objects;
        }

        public String getObjectType()
        {
            return _objectType;
        }

        public String getDetailAction()
        {
            return _detailAction;
        }

        public void setReturnURL(String url)
        {
            _returnURL =  url;
        }

        public String getReturnURL()
        {
            return _returnURL;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }
    }

    public ConfirmDeleteView(String objectType, String detailAction, List<? extends ExpObject> objects, ExperimentController.DeleteForm form) throws ServletException
    {
        this(objectType, detailAction, objects, form, Collections.<ExpRun>emptyList());
    }

    public ConfirmDeleteView(String objectType, String detailAction, List<? extends ExpObject> objects, ExperimentController.DeleteForm form, List<ExpRun> runs) throws ServletException
    {
        super("/org/labkey/experiment/ConfirmDelete.jsp");

        Map<ExpRun, Container> runsWithPermission = new LinkedHashMap<ExpRun, Container>();
        Map<ExpRun, Container> runsWithoutPermission = new LinkedHashMap<ExpRun, Container>();
        for (ExpRun run : runs)
        {
            Container c = run.getContainer();
            if (c.hasPermission(getViewContext().getUser(), ACL.PERM_DELETE))
            {
                runsWithPermission.put(run, c);
            }
            else
            {
                runsWithoutPermission.put(run, c);
            }
        }

        ConfirmDeleteBean bean = new ConfirmDeleteBean(runsWithPermission, runsWithoutPermission, getViewContext(), objects, objectType, detailAction);
        bean.setReturnURL(form.getReturnURL());
        bean.setDataRegionSelectionKey(form.getDataRegionSelectionKey());
        setModelBean(bean);
    }
}
