/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.study.controllers;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UpdateView;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyPropertiesTable;
import org.labkey.study.query.StudyQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * User: jgarms
 * Date: Aug 8, 2008
 * Time: 8:59:21 AM
 */
public class StudyPropertiesController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(StudyPropertiesController.class);

    public StudyPropertiesController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    // need this to be able to get the error object on a reshow
    static class StudyProperties
    {
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateAction extends FormViewAction<StudyProperties>
    {
        public ModelAndView getView(StudyProperties studyPropertiesForm, boolean reshow, BindException errors) throws Exception
        {
            StudyPropertiesTable table = getTableInfo();

            QueryUpdateForm updateForm = new QueryUpdateForm(table, getViewContext(), errors);

            // In order to pull the data out for an edit, we need to explicitly add the container id to the parameters
            // that the query update form will use
            updateForm.set("container", getContainer().getId());

            UpdateView view = new UpdateView(updateForm, errors);
            DataRegion dataRegion = view.getDataRegion();

            ActionURL cancelURL = new ActionURL(StudyController.ManageStudyAction.class, getContainer());
            
            ButtonBar buttonBar = dataRegion.getButtonBar(DataRegion.MODE_UPDATE);
            buttonBar = new ButtonBar(buttonBar); // need to copy since the original is read-only
            ActionButton cancelButton = new ActionButton(cancelURL, "Cancel", DataRegion.MODE_UPDATE, ActionButton.Action.GET);
            cancelButton.setURL(cancelURL);
            buttonBar.add(1, cancelButton);
            dataRegion.setButtonBar(buttonBar);

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Edit Study Properties");
            return root;
        }

        public void validateCommand(StudyProperties target, Errors errors) {}

        public boolean handlePost(StudyProperties studyPropertiesForm, BindException errors) throws Exception
        {
            QueryUpdateForm updateForm = new QueryUpdateForm(getTableInfo(), getViewContext(), errors);

            if (errors.hasErrors())
                return false;

            Map<String, Object> dataMap = updateForm.getTypedColumns();

            StudyImpl study = getStudyThrowIfNull();

            String newLabel = (String)dataMap.remove("label"); // remove and handle label, as it isn't an ontology object

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                if (newLabel != null && !study.getLabel().equals(newLabel))
                {
                    study = study.createMutable();
                    study.setLabel(newLabel);
                    StudyManager.getInstance().updateStudy(getUser(), study);
                }

                study.savePropertyBag(dataMap);

                transaction.commit();
                return true;
            }
            catch (ValidationException e)
            {
                for (ValidationError error : e.getErrors())
                    errors.reject(SpringActionController.ERROR_MSG, PageFlowUtil.filter(error.getMessage()));
                return false;
            }
        }

        public ActionURL getSuccessURL(StudyProperties studyPropertiesForm)
        {
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        protected StudyPropertiesTable getTableInfo()
        {
            StudyImpl study = getStudyThrowIfNull();
            StudyQuerySchema schema = StudyQuerySchema.createSchema(study, getUser(), true);
            return new StudyPropertiesTable(schema);
        }
    }
}
