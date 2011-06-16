package org.labkey.api.query;

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-06-10
 * Time: 2:39 PM
 */
public abstract class AbstractQueryImportAction<FORM> extends FormApiAction<FORM>
{
    public static class ImportViewBean
    {
        public String urlCancel = null;
        public String urlReturn = null;
        public String urlEndpoint = null;
    }

    protected AbstractQueryImportAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }


    TableInfo _target;
    QueryUpdateService _updateService;


    protected void setTarget(TableInfo t)
    {
        _target = t;
        _updateService = _target.getUpdateService();
    }


    public ModelAndView getDefaultImportView(FORM form, BindException errors) throws Exception
    {
        ActionURL url = getViewContext().getActionURL();
        User user = getViewContext().getUser();
        Container c = getViewContext().getContainer();

        validatePermission(user, errors);
        ImportViewBean bean = new ImportViewBean();
        if (null != _target && null != _target.getGridURL(c))
            bean.urlReturn = _target.getGridURL(c).getLocalURIString(false);
        else
            bean.urlReturn =  url.clone().setAction("executeQuery").getLocalURIString(false);
        bean.urlCancel = bean.urlReturn;
        bean.urlEndpoint = url.getLocalURIString();
        return new JspView<ImportViewBean>(AbstractQueryImportAction.class, "import.jsp", bean, errors);
    }


    @Override
    public final ApiResponse execute(FORM form, BindException errors) throws Exception
    {
        initRequest(form);

        User user = getViewContext().getUser();
        validatePermission(user, errors);

        if (errors.hasErrors())
            throw errors;

        boolean hasPostData = false;
        DataLoader loader = null;

        String text = getViewContext().getRequest().getParameter("text");
        if (!StringUtils.isEmpty(text))
        {
            hasPostData = true;
            loader = new TabLoader(text, true);
            // di = loader.getDataIterator(ve);
        }

        if (!hasPostData)
            errors.reject(SpringActionController.ERROR_MSG, "No data provided");
        if (errors.hasErrors())
            throw errors;

        BatchValidationException ve = new BatchValidationException();
        //di = wrap(di, ve);
        //importData(di, ve);
        int rowCount = importData(loader, ve);

        if (ve.hasErrors())
            throw ve;

        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("rowCount", rowCount);
        return new ApiSimpleResponse(response);
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


    protected void initRequest(FORM form) throws ServletException
    {
    }


    /* NYI see comment on importData() */
    protected DataIterator wrap(DataIterator di, BatchValidationException errors)
    {
        return di;
    }


    /* TODO change prototype if/when QueryUpdateServie supports DataIterator */
    protected int importData(DataLoader dl, BatchValidationException errors) throws IOException
    {
        DbScope scope = _target.getSchema().getScope();
        try
        {
            scope.beginTransaction();
            List res = _updateService.insertRows(getViewContext().getUser(), getViewContext().getContainer(), dl.load(), new HashMap<String, Object>());
            scope.commitTransaction();
            return res.size();
        }
        catch (BatchValidationException x)
        {
            assert x.hasErrors();
            if (x != errors)
            {
                for (ValidationException e : x.getRowErrors())
                    errors.addRowError(e);
            }
        }
        catch (DuplicateKeyException x)
        {
            errors.addRowError(new ValidationException(x.getMessage()));
        }
        catch (QueryUpdateServiceException x)
        {
            errors.addRowError(new ValidationException(x.getMessage()));
        }
        catch (SQLException x)
        {
            boolean isConstraint = scope.getSqlDialect().isConstraintException(x);
            if (isConstraint)
                errors.addRowError(new ValidationException(x.getMessage()));
            else
                throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
        return 0;
    }


    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }
}
