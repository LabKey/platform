package org.labkey.experiment.controllers.list;

import org.apache.struts.action.ActionMapping;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

public class ListDefinitionForm extends ViewForm
{
    protected ListDefinition _listDef;
    private String _returnUrl;
    private String[] _deletedAttachments;
    private boolean _showHistory = false;

    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);

        // TODO: Use proper validate.  Also, share with ListQueryForm validate
        String listIdParam = request.getParameter("listId");
        if (null == listIdParam)
            throw new NotFoundException("Missing listId parameter");

        try
        {
            _listDef = ListService.get().getList(Integer.parseInt(listIdParam));
        }
        catch (NumberFormatException e)
        {
            throw new NotFoundException("Couldn't convert listId '" + listIdParam + "' to an integer");
        }

        if (null == _listDef)
            throw new NotFoundException("List does not exist");
    }

    public ListDefinition getList()
    {
        return _listDef;
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public ActionURL getReturnActionURL()
    {
        return new ActionURL(_returnUrl);
    }

    public boolean isShowHistory()
    {
        return _showHistory;
    }

    public void setShowHistory(boolean showHistory)
    {
        _showHistory = showHistory;
    }

    public String[] getDeletedAttachments()
    {
        return _deletedAttachments;
    }

    public void setDeletedAttachments(String[] deletedAttachments)
    {
        _deletedAttachments = deletedAttachments;
    }
}
