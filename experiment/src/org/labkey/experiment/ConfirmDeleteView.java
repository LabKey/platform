/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.form.DeleteForm;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        private final List<? extends ExpObject> _objects;
        private final String _objectType;
        private final Class<? extends Controller> _detailAction;
        private ActionURL _cancelUrl;
        private ActionURL _successUrl;
        private String _dataRegionSelectionKey;
        private final Integer _singleObjectRowId;
        private final String _extraNoun;
        private final List<Pair<SecurableResource, ActionURL>> _deleteableExtras;
        private final List<Pair<SecurableResource, ActionURL>> _noPermissionExtras;
        private final List<? extends ExpObject> _referencedItems;
        private final String _referencesDescription;

        public ConfirmDeleteBean(Map<ExpRun, Container> runsWithPermission, Map<ExpRun, Container> runsWithoutPermission, List<? extends ExpObject> objects, String objectType, Class<? extends Controller> detailAction, Integer singleObjectRowId, String extraNoun, List<Pair<SecurableResource, ActionURL>> deleteableExtras, List<Pair<SecurableResource, ActionURL>> noPermissionExtras, @NotNull List<? extends ExpObject> referencedItems, @Nullable String referencesDescription)
        {
            _runsWithPermission = runsWithPermission;
            _runsWithoutPermission = runsWithoutPermission;
            _objects = objects;
            _objectType = objectType;
            _detailAction = detailAction;
            _singleObjectRowId = singleObjectRowId;
            _extraNoun = extraNoun;
            _deleteableExtras = deleteableExtras;
            _noPermissionExtras = noPermissionExtras;
            _referencedItems = referencedItems;
            _referencesDescription = referencesDescription;
        }

        public Map<ExpRun, Container> getRunsWithPermission()
        {
            return _runsWithPermission;
        }

        public Map<ExpRun, Container> getRunsWithoutPermission()
        {
            return _runsWithoutPermission;
        }

        public List<? extends ExpObject> getObjects()
        {
            return _objects;
        }

        public String getObjectType()
        {
            return _objectType;
        }

        public Class<? extends Controller> getDetailAction()
        {
            return _detailAction;
        }

        public void setCancelUrl(ActionURL url)
        {
            _cancelUrl = url;
        }

        public void setSuccessUrl(ActionURL url)
        {
            _successUrl =  url;
        }

        public String getExtraNoun()
        {
            return _extraNoun;
        }

        public List<Pair<SecurableResource, ActionURL>> getDeleteableExtras()
        {
            return _deleteableExtras;
        }

        public List<Pair<SecurableResource, ActionURL>> getNoPermissionExtras()
        {
            return _noPermissionExtras;
        }

        public ActionURL getCancelUrl()
        {
            return _cancelUrl;
        }

        public ActionURL getSuccessUrl()
        {
            return _successUrl;
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

        public List<? extends ExpObject> getReferencedItems()
        {
            return _referencedItems;
        }

        public String getReferencesDescription()
        {
            return _referencesDescription;
        }
    }

    public ConfirmDeleteView(String objectType, Class<? extends Controller> detailAction, List<? extends ExpObject> objects, DeleteForm form, List<? extends ExpRun> runs)
    {
        this(objectType, detailAction, objects, form, runs, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
    }

    public ConfirmDeleteView(String objectType, Class<? extends Controller> detailAction, List<? extends ExpObject> objects, DeleteForm form, List<? extends ExpRun> runs, String extraNoun, List<Pair<SecurableResource, ActionURL>> deleteableExtras, List<Pair<SecurableResource, ActionURL>> noPermissionExtras, @NotNull List<? extends ExpObject> referencedItems, @Nullable String referenceDescription)
    {
        super("/org/labkey/experiment/ConfirmDelete.jsp");

        Map<ExpRun, Container> runsWithPermission = new LinkedHashMap<>();
        Map<ExpRun, Container> runsWithoutPermission = new LinkedHashMap<>();
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

        ConfirmDeleteBean bean = new ConfirmDeleteBean(runsWithPermission, runsWithoutPermission, objects, objectType, detailAction, form.getSingleObjectRowId(), extraNoun, deleteableExtras, noPermissionExtras, referencedItems, referenceDescription);
        bean.setSuccessUrl(form.getSuccessActionURL());
        bean.setCancelUrl(form.getCancelActionURL());
        bean.setDataRegionSelectionKey(form.getDataRegionSelectionKey());
        setModelBean(bean);
    }
}
