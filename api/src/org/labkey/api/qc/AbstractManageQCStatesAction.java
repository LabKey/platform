/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.qc;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.HashSet;
import java.util.Set;

@RequiresPermission(AdminPermission.class)
public abstract class AbstractManageQCStatesAction<FORM extends AbstractManageDataStatesForm> extends FormViewAction<FORM>
{
    public abstract String getQcStateDefaultsPanel(Container container, DataStateHandler qcStateHandler);
    public abstract String getDataVisibilityPanel(Container container, DataStateHandler qcStateHandler);
    public abstract boolean hasQcStateDefaultsPanel();
    public abstract boolean hasDataVisibilityPanel();

    protected DataStateHandler _dataStateHandler;

    public AbstractManageQCStatesAction(DataStateHandler dataStateHandler, Class<FORM> commandClass)
    {
        super(commandClass);
        _dataStateHandler = dataStateHandler;
    }

    public void updateQcState(Container container, FORM form, User user)
    {
        _dataStateHandler.updateState(container, form, user);
    }

    @Override
    public void validateCommand(FORM form, Errors errors)
    {
        Set<String> labels = new HashSet<>();
        Set<String> newLabels = new HashSet<>();
        if (form.getLabels() != null)
        {
            for (String label : form.getLabels())
            {
                if (labels.contains(label))
                {
                    errors.reject(null, "QC state \"" + label + "\" is defined more than once.");
                    return;
                }
                else
                    labels.add(label);
            }
        }
        if (form.getNewLabels() != null)
        {
            for (String newLabel : form.getNewLabels())
            {
                if (labels.contains(newLabel) || newLabels.contains(newLabel))
                    errors.reject(null, "QC state \"" + newLabel + "\" is defined more than once.");
                else
                    newLabels.add(newLabel);
            }
        }
    }

    @Override
    public boolean handlePost(FORM form, BindException errors)
    {
        if (form.getIds() != null)
        {
            // use a map to store the IDs of the public QC states; since checkboxes are
            // omitted from the request entirely if they aren't checked, we use a different
            // method for keeping track of the checked values (by posting the rowid of the item as the
            // checkbox value).
            Set<Integer> set = new HashSet<>();
            if (form.getPublicData() != null)
            {
                for (int i = 0; i < form.getPublicData().length; i++)
                    set.add(form.getPublicData()[i]);
            }

            for (int i = 0; i < form.getIds().length; i++)
            {
                int rowId = form.getIds()[i];
                DataState state = new DataState();
                state.setRowId(rowId);
                state.setLabel(form.getLabels()[i]);
                if (form.getDescriptions() != null)
                    state.setDescription(form.getDescriptions()[i]);
                state.setPublicData(set.contains(state.getRowId()));
                state.setContainer(getContainer());
                DataStateManager.getInstance().updateState(getUser(), state);
            }
        }

        if (form.getNewIds() != null)
        {
            // use a map to store the IDs of the new QC states; since checkboxes are
            // omitted from the request entirely if they aren't checked, we use a different
            // method for keeping track of the checked values (by posting the rowid of the item as the
            // checkbox value).
            Set<Integer> newSet = new HashSet<>();
            if (form.getNewPublicData() != null)
            {
                for (int i = 0; i < form.getNewPublicData().length; i++)
                    newSet.add(form.getNewPublicData()[i]);
            }

            for (int i = 0; i < form.getNewIds().length; i++)
            {
                int newRowId = form.getNewIds()[i];
                DataState newState = new DataState();
                newState.setLabel(form.getNewLabels()[i]);
                if (form.getNewDescriptions() != null)
                    newState.setDescription(form.getNewDescriptions()[i]);
                newState.setPublicData(newSet.contains(newRowId));
                newState.setContainer(getContainer());
                DataStateManager.getInstance().insertState(getUser(), newState);
            }
        }

        updateQcState(getContainer(), form, getUser());

        return true;
    }

    public ActionURL getSuccessURL(FORM manageQCStatesForm, Class<? extends AbstractManageQCStatesAction> manageActionClass, Class<? extends SimpleViewAction> defaultActionClass)
    {
        if (manageQCStatesForm.isReshowPage())
        {
            ActionURL url = new ActionURL(manageActionClass, getContainer());
            if (manageQCStatesForm.getReturnUrl() != null)
                url.addParameter(ActionURL.Param.returnUrl, manageQCStatesForm.getReturnUrl());
            return url;
        }
        else if (manageQCStatesForm.getReturnUrl() != null)
            return new ActionURL(manageQCStatesForm.getReturnUrl());
        else
            return new ActionURL(defaultActionClass, getContainer());
    }

    protected String getQcStateHtml(Container container, DataStateHandler qcStateHandler, String selectName, Integer qcStateId)
    {
        StringBuilder qcStateHtml = new StringBuilder();
        qcStateHtml.append("          <td>");
        qcStateHtml.append("              <select name=\"").append(selectName).append("\">");
        qcStateHtml.append("                  <option value=\"\">[none]</option>");
        for (Object stateObj : qcStateHandler.getStates(container))
        {
            DataState state = (DataState) stateObj;
            boolean selected = (qcStateId != null) && (qcStateId == state.getRowId());
            String selectedText = (selected) ? " selected" : "";
            qcStateHtml.append("              <option value=\"").append(state.getRowId()).append("\"").append(selectedText).append(">").append(state.getLabel()).append("</option>");
        }
        qcStateHtml.append("              </select>");
        qcStateHtml.append("          </td>");

        return qcStateHtml.toString();
    }

}
