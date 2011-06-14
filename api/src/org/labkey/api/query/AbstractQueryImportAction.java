package org.labkey.api.query;

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-06-10
 * Time: 2:39 PM
 */
public class AbstractQueryImportAction extends FormApiAction<AbstractQueryImportAction.ImportForm>
{
    public static class ImportForm extends ReturnUrlForm
    {
    }


    TableInfo _target;
    QueryUpdateService _updateService;


    protected void setTarget(TableInfo t)
    {
        _target = t;
        _updateService = _target.getUpdateService();
    }


    @Override
    public ModelAndView getView(ImportForm form, BindException errors) throws Exception
    {
        User user = getViewContext().getUser();
        validatePermission(user, errors);
        return new JspView<ImportForm>(AbstractQueryImportAction.class, "import.jsp", form, errors);
    }


    @Override
    public final ApiResponse execute(ImportForm ImportForm, BindException errors) throws Exception
    {
        User user = getViewContext().getUser();
        validatePermission(user, errors);
        if (!errors.hasErrors())
        {
            /* handle various file formats and then importData() */
            BatchValidationException ve = new BatchValidationException();
            DataIterator di = null;
            importData(di, ve);
        }
        return new ApiSimpleResponse(new JSONObject());
    }


    protected void validatePermission(User user, BindException errors)
    {
        if (null == _target)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Table not specified");
        }
        else if (null == _updateService)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Table does not support update service: " + _target.getName());
        }
        else if (!_target.hasPermission(user, InsertPermission.class))
        {
            errors.reject(SpringActionController.ERROR_MSG, "User does not have permission to insert rows");
        }
    }


    protected boolean importData(DataIterator di, BatchValidationException errors)
    {
        return !errors.hasErrors();
    }


    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }
}
