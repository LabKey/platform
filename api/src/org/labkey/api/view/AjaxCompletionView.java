package org.labkey.api.view;

import org.labkey.api.util.PageFlowUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * User: adam
 * Date: Sep 23, 2007
 * Time: 5:59:44 PM
 */
public class AjaxCompletionView extends AjaxResponse
{
    private List<AjaxCompletion> _completions;

    public AjaxCompletionView(List<AjaxCompletion> completions)
    {
        _completions = completions;
    }

    protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PageFlowUtil.sendAjaxCompletions(response, _completions);
    }
}
