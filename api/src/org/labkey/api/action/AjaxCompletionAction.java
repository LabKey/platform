package org.labkey.api.action;

import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.AjaxCompletionView;
import org.labkey.api.view.AjaxResponse;
import org.springframework.validation.BindException;

import java.util.List;

/**
 * User: adamr
 * Date: September 19, 2007
 * Time: 9:15:37 AM
 */
public abstract class AjaxCompletionAction<FORM> extends AjaxAction<FORM>
{
    public AjaxCompletionAction()
    {
    }

    public AjaxCompletionAction(Class<? extends FORM> commandClass)
    {
        super(commandClass);
    }

    protected String getCommandClassMethodName()
    {
        return "getCompletions";
    }

    public abstract List<AjaxCompletion> getCompletions(FORM form, BindException errors) throws Exception;

    public AjaxResponse getResponse(FORM form, BindException errors) throws Exception
    {
        return new AjaxCompletionView(getCompletions(form, errors));
    }
}
