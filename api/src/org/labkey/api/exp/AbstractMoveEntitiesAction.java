package org.labkey.api.exp;

import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.MoveEntitiesForm;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Collection;
import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_GENERIC;

@RequiresPermission(UpdatePermission.class)
public abstract class AbstractMoveEntitiesAction extends MutatingApiAction<MoveEntitiesForm>
{
    protected Container _targetContainer;
    protected String _entityType;

    @Override
    public void validateForm(MoveEntitiesForm form, Errors errors)
    {
        validateTargetContainer(form, errors);
    }

    protected abstract Map<String, Integer> doMove(MoveEntitiesForm form) throws ExperimentException, BatchValidationException;

    protected abstract void updateSelections(MoveEntitiesForm form);

    protected void updateSelections(MoveEntitiesForm form, Collection<String> selection)
    {
        String selectionKey = form.getDataRegionSelectionKey();
        if (selectionKey != null)
        {
            DataRegionSelection.setSelected(getViewContext(), selectionKey, selection, false);

            // if moving run items from a type, the selections from other selectionKeys in that container will
            // possibly be holding onto invalid keys after the move, so clear them based on the containerPath and selectionKey suffix
            String[] keyParts = selectionKey.split("|");
            if (keyParts.length > 1)
                DataRegionSelection.clearRelatedByContainerPath(getViewContext(), keyParts[keyParts.length - 1]);
        }
    }

    @Override
    public Object execute(MoveEntitiesForm form, BindException errors)
    {
        ApiSimpleResponse resp = new ApiSimpleResponse();
        try
        {
            Map<String, Integer> updateCounts = doMove(form);

            SimpleMetricsService.get().increment(ExperimentService.MODULE_NAME, "moveEntities", _entityType);
            updateSelections(form);

            resp.put("success", true);
            resp.put("updateCounts", updateCounts);
            resp.put("containerPath", _targetContainer.getPath());

        }
        catch (Exception e)
        {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    private void validateTargetContainer(MoveEntitiesForm form, Errors errors)
    {
        if (form.getTargetContainer() == null)
        {
            errors.reject(ERROR_GENERIC, "A target container must be specified for the move operation.");
            return;
        }

        _targetContainer = getTargetContainer(form);
        if (_targetContainer == null)
        {
            errors.reject(ERROR_GENERIC, "The target container was not found: " + form.getTargetContainer() + ".");
            return;
        }

        if (!_targetContainer.hasPermission(getUser(), InsertPermission.class))
        {
            errors.reject(ERROR_GENERIC, "You do not have permission to move " + _entityType + " to the target container: " + form.getTargetContainer() + ".");
            return;
        }

        if (!isValidTargetContainer(getContainer(), _targetContainer))
            errors.reject(ERROR_GENERIC, "Invalid target container for the move operation: " + form.getTargetContainer() + ".");
    }

    private Container getTargetContainer(MoveEntitiesForm form)
    {
        Container c = ContainerManager.getForId(form.getTargetContainer());
        if (c == null)
            c = ContainerManager.getForPath(form.getTargetContainer());

        return c;
    }

    // targetContainer must be in the same app project at this time
    // i.e. child of current project, project of current child, sibling within project
    private boolean isValidTargetContainer(Container current, Container target)
    {
        if (current.isRoot() || target.isRoot())
            return false;

        if (current.equals(target))
            return false;

        boolean moveFromProjectToChild = current.isProject() && target.getParent().equals(current);
        boolean moveFromChildToProject = !current.isProject() && current.getParent().isProject() && current.getParent().equals(target);
        boolean moveFromChildToSibling = !current.isProject() && current.getParent().isProject() && current.getParent().equals(target.getParent());

        return moveFromProjectToChild || moveFromChildToProject || moveFromChildToSibling;
    }

}
