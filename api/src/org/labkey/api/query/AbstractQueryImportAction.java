/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExtFormResponseWriter;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
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
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
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
        public List<Pair<String, String>> urlExcelTemplates = null;
        public String importMessage = null;
        public String successMessageSuffix = null;
        public boolean hideTsvCsvCombo = false;
        // extra EXT config to inject into the form
        public JSONArray extraFields = null;
        public boolean acceptZeroResults;  //0 changes will show the update message/redirect, instead of an error
    }

    protected AbstractQueryImportAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    // Caller can import into table, using TableInfo or into simpler List of Objects, using ColumnDescriptors
    protected TableInfo _target;
    protected QueryUpdateService _updateService;

    protected boolean _noTableInfo = false;         // No table info; expect importData to be overridden; DERIVED MUST OVERRIDE validatePermissions
    protected boolean _hasColumnHeaders = true;
    protected String _importMessage = null;
    protected String _successMessageSuffix = "inserted";
    protected boolean _targetHasBeenSet = false;    // You can only set target TableInfo or NoTableInfo once
    protected boolean _hideTsvCsvCombo = false;
    protected boolean _importIdentity = false;
    protected boolean _importLookupByAlternateKey = false;
    protected boolean _acceptZeroResults = false;     //0 returned results are OK

    protected void setTarget(TableInfo t) throws ServletException
    {
        if (_targetHasBeenSet)
            throw new ServletException("Import/Upload target has already been set.");
        if (null == t)
            throw new ServletException("TableInfo not found.");
        _target = t;
        _updateService = _target.getUpdateService();
        _targetHasBeenSet = true;
    }

    protected void setNoTableInfo() throws ServletException
    {
        if (_targetHasBeenSet)
            throw new ServletException("Import/Upload target has already been set.");
        _noTableInfo = true;
        _targetHasBeenSet = true;
    }

    protected void setHasColumnHeaders(boolean hasColumnHeaders)
    {
        _hasColumnHeaders = hasColumnHeaders;
    }

    protected void setImportMessage(String importMessage)
    {
        _importMessage = importMessage;
    }

    protected void setHideTsvCsvCombo(boolean hideTsvCsvCombo)
    {
        _hideTsvCsvCombo = hideTsvCsvCombo;
    }

    protected String getSuccessMessageSuffix()
    {
        return _successMessageSuffix;
    }

    protected void setSuccessMessageSuffix(String successMessageSuffix)
    {
        this._successMessageSuffix = successMessageSuffix;
    }

    public ModelAndView getDefaultImportView(FORM form, BindException errors) throws Exception
    {
        return getDefaultImportView(form, null, errors);
    }

    public void setAcceptZeroResults(boolean acceptZeroResults)
    {
        _acceptZeroResults = acceptZeroResults;
    }
    
    public ModelAndView getDefaultImportView(FORM form, JSONArray extraFields, BindException errors) throws Exception
    {
        ActionURL url = getViewContext().getActionURL();
        User user = getUser();
        Container c = getContainer();

        validatePermission(user, errors);
        ImportViewBean bean = new ImportViewBean();

        bean.urlReturn = StringUtils.trimToNull(url.getParameter(ActionURL.Param.returnUrl));
        bean.urlCancel = StringUtils.trimToNull(url.getParameter(ActionURL.Param.cancelUrl));
        bean.hideTsvCsvCombo = _hideTsvCsvCombo;
        bean.successMessageSuffix = _successMessageSuffix;
        bean.extraFields = extraFields;
        bean.acceptZeroResults = _acceptZeroResults;

        if (null == bean.urlReturn)
        {
            ActionURL success = getSuccessURL(form);
            if (null != success)
                bean.urlReturn = success.getLocalURIString(false);
            else if (null != _target && null != _target.getGridURL(c))
                bean.urlReturn = _target.getGridURL(c).getLocalURIString(false);
            else
                bean.urlReturn = PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(url).getLocalURIString(false);
        }
        if (null == bean.urlCancel)
            bean.urlCancel = bean.urlReturn;

        bean.urlEndpoint = url.getLocalURIString();

        if (_target != null)
        {
            if(_target.getImportMessage() != null)
                bean.importMessage = _target.getImportMessage();    // Get message from TableInfo if available
            else
                bean.importMessage = _importMessage;                //Otherwise, get the passed in message

            bean.urlExcelTemplates = new ArrayList<>();

            List<Pair<String, String>> it = _target.getImportTemplates(getViewContext());
            if (it != null)
            {
                for (Pair<String, String> pair : it)
                {
                    bean.urlExcelTemplates.add(Pair.of(pair.first, pair.second));
                }
            }
        }
        else if (_noTableInfo)
        {
            bean.importMessage = _importMessage;     // Use passed in message if no TableInfo
        }
        else
        {
            errors.reject(SpringActionController.ERROR_MSG, "No table has been set to receive imported data");
        }

        return new JspView<>(AbstractQueryImportAction.class, "import.jsp", bean, errors);
    }


    @Override
    public final ApiResponse execute(FORM form, BindException errors) throws Exception
    {
        CPUTimer t = new CPUTimer("upload");
        try
        {
            assert t.start();
            return _execute(form, errors);
        }
        finally
        {
            assert t.stop();
//            System.err.println("upload complete: " + t.getDuration());
        }
    }

    public final ApiResponse _execute(FORM form, BindException errors) throws Exception
    {
        initRequest(form);

        User user = getUser();
        validatePermission(user, errors);

        if (errors.hasErrors())
            throw errors;

        File tempFile = null;
        boolean hasPostData = false;
        FileStream file = null;
        String originalName = null;
        DataLoader loader = null;

        String text = getViewContext().getRequest().getParameter("text");
        String path = getViewContext().getRequest().getParameter("path");

        String module = getViewContext().getRequest().getParameter("module");
        String moduleResource = getViewContext().getRequest().getParameter("moduleResource");

        // TODO: once importData() is refactored to accept DataIteratorContext, change importIdentity into local variable
        if (getViewContext().getRequest().getParameter("importIdentity") != null)
            _importIdentity = Boolean.valueOf(getViewContext().getRequest().getParameter("importIdentity"));

        if (getViewContext().getRequest().getParameter("importLookupByAlternateKey") != null)
            _importLookupByAlternateKey = Boolean.valueOf(getViewContext().getRequest().getParameter("importLookupByAlternateKey"));

        try
        {
            if (null != StringUtils.trimToNull(text))
            {
                hasPostData = true;
                originalName = "upload.tsv";
                TabLoader tabLoader = new TabLoader(text, _hasColumnHeaders);
                if ("csv".equalsIgnoreCase(getViewContext().getRequest().getParameter("format")))
                {
                    tabLoader.setDelimiterCharacter(',');
                    originalName = "upload.csv";
                }
                loader = tabLoader;
                file = new FileStream.ByteArrayFileStream(text.getBytes(StringUtilsLabKey.DEFAULT_CHARSET));
                // di = loader.getDataIterator(ve);
            }
            else if (null != StringUtils.trimToNull(path))
            {
                WebdavResource resource = WebdavService.get().getResolver().lookup(Path.parse(path));
                if (null == resource || !resource.isFile())
                {
                    errors.reject(SpringActionController.ERROR_MSG, "File not found: " + path);
                }
                else
                {
                    hasPostData = true;
                    loader = DataLoader.get().createLoader(resource, _hasColumnHeaders, null, null);
                    file = resource.getFileStream(user);
                    originalName = resource.getName();
                }
            }
            else if (null != StringUtils.trimToNull(moduleResource))
            {
                if (module == null && _target != null)
                {
                    module = _target.getSchema().getName();
                }

                Module m = module != null ? ModuleLoader.getInstance().getModuleForSchemaName(module) : null;
                if (m == null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Module required to import module resource");
                }
                else
                {
                    Path p;
                    if (moduleResource.contains("/"))
                        p = Path.parse(moduleResource).normalize();
                    else
                        p = Path.parse("schemas/dbscripts/" + moduleResource).normalize();

                    Resource r = m.getModuleResource(p);
                    if (r == null || !r.isFile())
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "File not found: " + p);
                    }
                    else
                    {
                        hasPostData = true;
                        loader = DataLoader.get().createLoader(r, _hasColumnHeaders, null, TabLoader.TSV_FILE_TYPE);
                        originalName = p.getName();
                        // Set file to null so assay import doesn't copy the file
                        file = null;
                    }
                }
            }
            else if (getViewContext().getRequest() instanceof MultipartHttpServletRequest)
            {
                Map<String, MultipartFile> files = ((MultipartHttpServletRequest)getViewContext().getRequest()).getFileMap();
                MultipartFile multipartfile = null==files ? null : files.get("file");
                if (null != multipartfile && multipartfile.getSize() > 0)
                {
                    hasPostData = true;
                    originalName = multipartfile.getOriginalFilename();
                    // can't read the multipart file twice so create temp file (12800)
                    tempFile = File.createTempFile("~upload", multipartfile.getOriginalFilename());
                    multipartfile.transferTo(tempFile);
                    loader = DataLoader.get().createLoader(tempFile, multipartfile.getContentType(), _hasColumnHeaders, null, null);
                    file = new FileAttachmentFile(tempFile, multipartfile.getOriginalFilename());
                }
            }

            if (!hasPostData)
                errors.reject(SpringActionController.ERROR_MSG, "Form contains no data");
            if (errors.hasErrors())
                throw errors;

            BatchValidationException ve = new BatchValidationException();
            //di = wrap(di, ve);
            //importData(di, ve);

            //apply known columns so loader can do better type conversion
            if (loader != null && _target != null)
                loader.setKnownColumns(_target.getColumns());

            int rowCount = importData(loader, file, originalName, ve);

            if (ve.hasErrors())
                throw ve;

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("rowCount", rowCount);
            return new ApiSimpleResponse(response);
        }
        catch (IOException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            throw errors;
        }
        finally
        {
            if (null != file)
                file.closeInputStream();
            if (null != tempFile)
                tempFile.delete();
        }
    }

    @Override
    public ApiResponseWriter createResponseWriter() throws IOException
    {
        return new ExtFormResponseWriter(getViewContext().getRequest(), getViewContext().getResponse());
    }

    protected void validatePermission(User user, BindException errors)
    {
        if (_noTableInfo)
        {
            // There is no TableInfo; Derived class should check permissions
            errors.reject(SpringActionController.ERROR_MSG, "Table not specified");
        }
        else if (null == _target)
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


    /* TODO change prototype to take DataIteratorBuilder, and DataIteratorContext */
    protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors) throws IOException
    {
        if (_target != null)
        {
            DataIteratorContext context = new DataIteratorContext(errors);
            context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
            context.setAllowImportLookupByAlternateKey(_importLookupByAlternateKey);
            if (_importIdentity)
            {
                context.setInsertOption(QueryUpdateService.InsertOption.IMPORT_IDENTITY);
                context.setSupportAutoIncrementKey(true);
            }

            try (DbScope.Transaction transaction = _target.getSchema().getScope().ensureTransaction())
            {
                int count = _updateService.loadRows(getUser(), getContainer(), dl, context, new HashMap<>());
                if (errors.hasErrors())
                    return 0;
                transaction.commit();
                return count;
            }
            /* catch (BatchValidationException x)
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
            } */
            catch (SQLException x)
            {
                boolean isConstraint = RuntimeSQLException.isConstraintException(x);
                if (isConstraint)
                    errors.addRowError(new ValidationException(x.getMessage()));
                else
                    throw new RuntimeSQLException(x);
            }
        }
        else
        {
            errors.addRowError(new ValidationException("Table not specified"));
        }

        return 0;
    }


    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }
}
