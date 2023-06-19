package org.labkey.assay.actions;

import org.labkey.api.exp.AbstractMoveEntitiesAction;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.MoveEntitiesForm;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.springframework.validation.Errors;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.action.SpringActionController.ERROR_GENERIC;

@RequiresPermission(UpdatePermission.class)
public class MoveAssayRunsAction extends AbstractMoveEntitiesAction
{
    private List<? extends ExpRun> _expRuns;

    @Override
    public void validateForm(MoveEntitiesForm form, Errors errors)
    {
        _entityType = "assayRuns";
        super.validateForm(form, errors);
        validateRuns(form, errors);
    }

    @Override
    public Map<String, Integer> doMove(MoveEntitiesForm form) throws ExperimentException, BatchValidationException
    {
        return ExperimentService.get().moveAssayRuns(_expRuns, getContainer(), _targetContainer, getUser(), form.getUserComment(), form.getAuditBehavior());
    }

    private void validateRuns(MoveEntitiesForm form, Errors errors)
    {
        Set<Integer> runIds = form.getIds(false); // handle clear of selectionKey after move complete
        if (runIds == null || runIds.isEmpty())
        {
            errors.reject(ERROR_GENERIC, "Run IDs must be specified for the move operation.");
            return;
        }

        _expRuns = ExperimentService.get().getExpRuns(runIds);
        if (_expRuns.size() != runIds.size())
        {
            errors.reject(ERROR_GENERIC, "Unable to find all runs for the move operation.");
            return;
        }

        // verify all runs are from the current container
        if (_expRuns.stream().anyMatch(run -> !run.getContainer().equals(getContainer())))
        {
            errors.reject(ERROR_GENERIC, "All assay runs must be from the current container for the move operation.");
            return;
        }

        // verify allowed moves based on assay QC statuses ?

    }

    @Override
    protected void updateSelections(MoveEntitiesForm form)
    {
        updateSelections(form, _expRuns.stream().map(run -> Integer.toString(run.getRowId())).collect(Collectors.toSet()));
    }

}
