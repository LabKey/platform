package org.labkey.api.qc;

import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

@RequiresPermission(AdminPermission.class)
public abstract class AbstractDeleteQCStateAction extends FormHandlerAction<DeleteQCStateForm>
{
    protected static QCStateHandler _qcStateHandler;
    public abstract QCStateHandler getQCStateHandler();
    public abstract ActionURL getSuccessURL(DeleteQCStateForm form);

    @Override
    public void validateCommand(DeleteQCStateForm target, Errors errors)
    {
    }

    @Override
    public boolean handlePost(DeleteQCStateForm form, BindException errors) throws Exception
    {
        if (form.isAll())
        {
            for (QCState state : QCStateManager.getInstance().getQCStates(getContainer()))
            {
                if (!getQCStateHandler().isQCStateInUse(state))
                    QCStateManager.getInstance().deleteQCState(state);
            }
        }
        else
        {
            QCState state = QCStateManager.getInstance().getQCStateForRowId(getContainer(), form.getId());
            if (state != null)
                QCStateManager.getInstance().deleteQCState(state);
        }
        return true;
    }
}
