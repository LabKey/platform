/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.assay.nab.view;

import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionAssayRun;
import org.labkey.api.assay.dilution.DilutionDataHandler;
import org.labkey.api.assay.nab.RenderAssayBean;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.Set;

/**
 * User: klum
 * Date: 5/15/13
 */
public abstract class RunDetailsAction<FormType extends RenderAssayBean> extends SimpleViewAction<FormType>
{
    protected int _runRowId;
    protected ExpProtocol _protocol;

    public ModelAndView getView(FormType form, BindException errors) throws Exception
    {
        _runRowId = form.getRowId();
        ExpRun run = ExperimentService.get().getExpRun(form.getRowId());
        if (run == null)
        {
            throw new NotFoundException("Run " + form.getRowId() + " does not exist.");
        }
        if (!run.getContainer().equals(getContainer()))
        {
            // Need to redirect
            ActionURL newURL = getViewContext().getActionURL().clone();
            newURL.setContainer(run.getContainer());
            throw new RedirectException(newURL);
        }

        // 8128 : NAb should show only print details view if user doesn't have permission to container
        // Using the permissions annotations, we've already checked that the user has permissions
        // at this point.  However, if the user can view the dataset but not the container,
        // lots of links will be broken. The workaround for now is to redirect to a print view.
        if (!isPrint() && !getContainer().hasPermission(getUser(), ReadPermission.class))
        {
            throw new RedirectException(getViewContext().getActionURL().clone().addParameter("_print", true));
        }

        // If the current user doesn't have ReadPermission to the current container, but the
        // RunDatasetContextualRoles has granted us permission to this action, we can elevate the user's
        // permissions as accessed via the NabAssayRun.  This allows access to schemas and queries used by
        // NabAssayRun even though the original user doesn't have permission to the container.
        User elevatedUser = getUser();
        if (!getContainer().hasPermission(getUser(), ReadPermission.class))
        {
            User currentUser = getUser();
            Set<Role> contextualRoles = new HashSet<>(currentUser.getStandardContextualRoles());
            contextualRoles.add(RoleManager.getRole(ReaderRole.class));
            elevatedUser = new LimitedUser(currentUser, currentUser.getGroups(), contextualRoles, false);
        }
        else if (getUser().equals(run.getCreatedBy()) && !getContainer().hasPermission(getUser(), DeletePermission.class))
        {
            User currentUser = getUser();
            Set<Role> contextualRoles = new HashSet<>(currentUser.getStandardContextualRoles());
            contextualRoles.add(RoleManager.getRole(EditorRole.class));
            elevatedUser = new LimitedUser(currentUser, currentUser.getGroups(), contextualRoles, false);
        }

        try
        {
            DilutionAssayRun assay = getNabAssayRun(run, form.getFitTypeEnum(), elevatedUser);
            _protocol = run.getProtocol();
            AbstractPlateBasedAssayProvider provider = (AbstractPlateBasedAssayProvider) AssayService.get().getProvider(_protocol);

            form.setContext(getViewContext());
            form.setAssay(assay);

            RunDetailsHeaderView headerView = new RunDetailsHeaderView(getContainer(), _protocol, provider, run, assay.getSampleResults(), elevatedUser);
            if (headerView.isShowGraphLayoutOptions())
                assay.updateRenderAssayBean(form);
            HttpView view = new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/runDetails.jsp", form);
            if (!isPrint())
                view = new VBox(headerView, view);

            return view;
        }
        catch (ExperimentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            return new SimpleErrorView(errors);
        }
    }

    protected DilutionAssayRun getNabAssayRun(ExpRun run, StatsService.CurveFitType fit, User user) throws ExperimentException
    {
        AssayProvider provider = AssayService.get().getProvider(run.getProtocol());
        if (provider instanceof DilutionAssayProvider)
        {
            try {
                return ((DilutionAssayProvider)provider).getDataHandler().getAssayResults(run, user);
            }
            catch (DilutionDataHandler.MissingDataFileException e)
            {
                throw new NotFoundException(e.getMessage());
            }
        }
        return null;
    }
}
