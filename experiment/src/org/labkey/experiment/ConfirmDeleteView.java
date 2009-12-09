/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.ACL;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.ReturnURLString;
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
        private ReturnURLString _returnURL;
        private String _dataRegionSelectionKey;
        private Integer _singleObjectRowId;

        public ConfirmDeleteBean(Map<ExpRun, Container> runsWithPermission, Map<ExpRun, Container> runsWithoutPermission, ViewContext viewContext, List<? extends ExpObject> objects, String objectType, String detailAction, Integer singleObjectRowId)
        {
            _runsWithPermission = runsWithPermission;
            _runsWithoutPermission = runsWithoutPermission;
            _viewContext = viewContext;
            _objects = objects;
            _objectType = objectType;
            _detailAction = detailAction;
            _singleObjectRowId = singleObjectRowId;
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

        public void setReturnURL(ReturnURLString url)
        {
            _returnURL =  url;
        }

        public ReturnURLString getReturnURL()
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

        public Integer getSingleObjectRowId()
        {
            return _singleObjectRowId;
        }
    }

    public ConfirmDeleteView(String objectType, String detailAction, List<? extends ExpObject> objects, ExperimentController.DeleteForm form) throws ServletException
    {
        this(objectType, detailAction, objects, form, Collections.<ExpRun>emptyList());
    }

    public ConfirmDeleteView(String objectType, String detailAction, List<? extends ExpObject> objects, ExperimentController.DeleteForm form, List<? extends ExpRun> runs) throws ServletException
    {
        super("/org/labkey/experiment/ConfirmDelete.jsp");

        Map<ExpRun, Container> runsWithPermission = new LinkedHashMap<ExpRun, Container>();
        Map<ExpRun, Container> runsWithoutPermission = new LinkedHashMap<ExpRun, Container>();
        for (ExpRun run : runs)
        {
            Container c = run.getContainer();
            if (c.hasPermission(getViewContext().getUser(), DeletePermission.class))
            {
                runsWithPermission.put(run, c);
            }
            else
            {
                runsWithoutPermission.put(run, c);
            }
        }

        ConfirmDeleteBean bean = new ConfirmDeleteBean(runsWithPermission, runsWithoutPermission, getViewContext(), objects, objectType, detailAction, form.getSingleObjectRowId());
        bean.setReturnURL(form.getReturnURL());
        bean.setDataRegionSelectionKey(form.getDataRegionSelectionKey());
        setModelBean(bean);
    }
}
