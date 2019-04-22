package org.labkey.api.qc;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.HashSet;
import java.util.Set;

@RequiresPermission(AdminPermission.class)
public abstract class ManageQCStatesAction extends FormViewAction<ManageQCStatesForm>
{
    public abstract QcDefaultSettings getCurrentQcDefaultSettings();
    public abstract void persistQcSettings(QcDefaultSettings qcDefaultSettings);

    public void validateCommand(ManageQCStatesForm form, Errors errors)
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

    public boolean handlePost(ManageQCStatesForm form, BindException errors)
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

        updateQcState(form);

        return true;
    }

    public ActionURL getSuccessURL(ManageQCStatesForm manageQCStatesForm, Class<? extends SimpleViewAction> defaultActionClass)
    {
        if (manageQCStatesForm.isReshowPage())
        {
            ActionURL url = new ActionURL(ManageQCStatesAction.class, getContainer());
            if (manageQCStatesForm.getReturnUrl() != null)
                url.addParameter(ActionURL.Param.returnUrl, manageQCStatesForm.getReturnUrl());
            return url;
        }
        else if (manageQCStatesForm.getReturnUrl() != null)
            return new ActionURL(manageQCStatesForm.getReturnUrl());
        else
            return new ActionURL(defaultActionClass, getContainer());
    }

    public static class QcDefaultSettings {
        Integer _defaultAssayQCState;
        Integer _defaultPipelineQCState;
        Integer _defaultDirectEntryQCState;
        boolean _blankQCStatePublic;
        boolean _showPrivateDataByDefault;

        public Integer getDefaultAssayQCState()
        {
            return _defaultAssayQCState;
        }

        public void setDefaultAssayQCState(Integer defaultAssayQCState)
        {
            _defaultAssayQCState = defaultAssayQCState;
        }

        public Integer getDefaultPipelineQCState()
        {
            return _defaultPipelineQCState;
        }

        public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
        {
            _defaultPipelineQCState = defaultPipelineQCState;
        }

        public Integer getDefaultDirectEntryQCState()
        {
            return _defaultDirectEntryQCState;
        }

        public void setDefaultDirectEntryQCState(Integer defaultDirectEntryQCState)
        {
            _defaultDirectEntryQCState = defaultDirectEntryQCState;
        }

        public boolean isBlankQCStatePublic()
        {
            return _blankQCStatePublic;
        }

        public void setBlankQCStatePublic(boolean blankQCStatePublic)
        {
            _blankQCStatePublic = blankQCStatePublic;
        }

        public boolean isShowPrivateDataByDefault()
        {
            return _showPrivateDataByDefault;
        }

        public void setShowPrivateDataByDefault(boolean showPrivateDataByDefault)
        {
            _showPrivateDataByDefault = showPrivateDataByDefault;
        }
    }

    public void updateQcState(ManageQCStatesForm form)
    {
        QcDefaultSettings qcDefaultSettings = getCurrentQcDefaultSettings();

        if (!nullSafeEqual(qcDefaultSettings.getDefaultAssayQCState(), form.getDefaultAssayQCState()) ||
                !nullSafeEqual(qcDefaultSettings.getDefaultPipelineQCState(), form.getDefaultPipelineQCState()) ||
                !nullSafeEqual(qcDefaultSettings.getDefaultDirectEntryQCState(), form.getDefaultDirectEntryQCState()) ||
                !nullSafeEqual(qcDefaultSettings.isBlankQCStatePublic(), form.isBlankQCStatePublic()) ||
                qcDefaultSettings.isShowPrivateDataByDefault() != form.isShowPrivateDataByDefault())
        {
            persistQcSettings(qcDefaultSettings);
        }
    }

    public static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }
}
