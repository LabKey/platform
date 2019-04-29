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
public abstract class AbstractManageQCStatesAction<FORM extends AbstractManageQCStatesForm> extends FormViewAction<FORM>
{
    public abstract String getQcStateDefaultsPanel(Container container, QCStateHandler qcStateHandler);
    public abstract String getDataVisibilityPanel(Container container, QCStateHandler qcStateHandler);
    public abstract boolean hasQcStateDefaultsPanel();
    public abstract boolean hasDataVisibilityPanel();

    protected QCStateHandler _qcStateHandler;

    public AbstractManageQCStatesAction(QCStateHandler qcStateHandler, Class<FORM> commandClass)
    {
        super(commandClass);
        _qcStateHandler = qcStateHandler;
    }

    public void updateQcState(Container container, FORM form, User user)
    {
        _qcStateHandler.updateQcState(container, form, user);
    }

    public void validateCommand(FORM form, Errors errors)
    {
        Set<String> labels = new HashSet<>();
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
        if (labels.contains(form.getNewLabel()))
            errors.reject(null, "QC state \"" + form.getNewLabel() + "\" is defined more than once.");
    }

    public boolean handlePost(FORM form, BindException errors)
    {
        if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
        {
            QCState newState = new QCState();
            newState.setContainer(getContainer());
            newState.setLabel(form.getNewLabel());
            newState.setDescription(form.getNewDescription());
            newState.setPublicData(form.isNewPublicData());
            QCStateManager.getInstance().insertQCState(getUser(), newState);
        }
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
                QCState state = new QCState();
                state.setRowId(rowId);
                state.setLabel(form.getLabels()[i]);
                if (form.getDescriptions() != null)
                    state.setDescription(form.getDescriptions()[i]);
                state.setPublicData(set.contains(state.getRowId()));
                state.setContainer(getContainer());
                QCStateManager.getInstance().updateQCState(getUser(), state);
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
}
