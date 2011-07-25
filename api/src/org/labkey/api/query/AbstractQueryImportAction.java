/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.query;

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExtFormResponseWriter;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
        public String urlExcelTemplate = null;
    }

    protected AbstractQueryImportAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }


    protected TableInfo _target;
    protected QueryUpdateService _updateService;


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

        bean.urlReturn = StringUtils.trimToNull(url.getParameter(ActionURL.Param.returnUrl));
        bean.urlCancel = StringUtils.trimToNull(url.getParameter(ActionURL.Param.cancelUrl));

        if (null == bean.urlReturn)
        {
            ActionURL success = getSuccessURL(form);
            if (null != success)
                bean.urlReturn = success.getLocalURIString(false);
            else if (null != _target && null != _target.getGridURL(c))
                bean.urlReturn = _target.getGridURL(c).getLocalURIString(false);
            else
                bean.urlReturn =  url.clone().setAction("executeQuery").getLocalURIString(false);
        }
        if (null == bean.urlCancel)
            bean.urlCancel = bean.urlReturn;

        bean.urlEndpoint = url.getLocalURIString();
        ActionURL xl = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(c, _target.getPublicSchemaName(), _target.getName());
        if (null != xl)
        {
            xl.addParameter("captionType", ExcelWriter.CaptionType.Name.name());
            bean.urlExcelTemplate = xl.getLocalURIString();
        }
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
        FileStream file = null;
        DataLoader loader = null;

        String text = getViewContext().getRequest().getParameter("text");
        String path = getViewContext().getRequest().getParameter("path");

        if (null != StringUtils.trimToNull(text))
        {
            hasPostData = true;
            TabLoader tabLoader = new TabLoader(text, true);
            if ("csv".equals(getViewContext().getRequest().getParameter("format")))
            {
                tabLoader.setDelimiterCharacter(',');
            }
            loader = tabLoader;
            file = new FileStream.ByteArrayFileStream(text.getBytes("UTF-8"));
            // di = loader.getDataIterator(ve);
        }
        else if (null != StringUtils.trimToNull(path))
        {
            WebdavResource resource = WebdavService.get().getResolver().lookup(Path.parse(path));
            if (null == resource || !resource.isFile())
                errors.reject(SpringActionController.ERROR_MSG, "File not found: " + path);
            else
            {
                hasPostData = true;
                loader = DataLoader.getDataLoaderForFile(resource);
                file = resource.getFileStream(user);
            }
        }
        else if (getViewContext().getRequest() instanceof MultipartHttpServletRequest)
        {
            Map<String, MultipartFile> files = ((MultipartHttpServletRequest)getViewContext().getRequest()).getFileMap();
            MultipartFile multipartfile = null==files ? null : files.get("file");
            if (null != file && multipartfile.getSize() > 0)
            {
                hasPostData = true;
                loader = DataLoader.getDataLoaderForFile(multipartfile);
                file = new SpringAttachmentFile(multipartfile);
            }
        }

        if (!hasPostData)
            errors.reject(SpringActionController.ERROR_MSG, "Form contains no data");
        if (errors.hasErrors())
            throw errors;

        BatchValidationException ve = new BatchValidationException();
        //di = wrap(di, ve);
        //importData(di, ve);
        int rowCount = importData(loader, file, ve);

        if (ve.hasErrors())
            throw ve;

        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("rowCount", rowCount);
        return new ApiSimpleResponse(response);
    }

    @Override
    public ApiResponseWriter createResponseWriter() throws IOException
    {
        return new ExtFormResponseWriter(getViewContext().getRequest(), getViewContext().getResponse());
    }

    protected void validatePermission(User user, BindException errors)
    {
        if (null == _target)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Table not specified");
        }
        else if (!_target.hasPermission(user, InsertPermission.class))
        {
            if (user.isGuest())
                throw new UnauthorizedException();
            errors.reject(SpringActionController.ERROR_MSG, "User does not have permission to insert rows");
        }
        else if (null == _updateService)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Table does not support update service: " + _target.getName());
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

    protected ActionURL getSuccessURL(FORM form)
    {
        return null;
    }


    /* TODO change prototype if/when QueryUpdateServie supports DataIterator */
    protected int importData(DataLoader dl, FileStream file, BatchValidationException errors) throws IOException
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
