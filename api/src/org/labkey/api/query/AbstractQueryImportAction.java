/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExtFormResponseWriter;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.vfs.FileLike;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_GENERIC;
import static org.labkey.api.query.AbstractQueryUpdateService.addTransactionAuditEvent;
import static org.labkey.api.query.AbstractQueryUpdateService.createTransactionAuditEvent;


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
        public String successMessageSuffix = "inserted";
        public boolean showMergeOption = false;
        public boolean showUpdateOption = false;
        public boolean hideTsvCsvCombo = false;
        // extra EXT config to inject into the form
        public JSONArray extraFields = null;
        public String importHelpTopic = "dataPrep";
        public String importHelpDisplayText = "help";
        public String typeName = "rows";    // e.g. rows, samples, mice
        public boolean acceptZeroResults;   // 0 changes will show the update message/redirect, instead of an error
    }

    protected ImportViewBean _importViewBean = new ImportViewBean();

    // Caller can import into table, using TableInfo or into simpler List of Objects, using ColumnDescriptors
    protected TableInfo _target;
    protected QueryUpdateService _updateService;

    protected boolean _noTableInfo = false;         // No table info; expect importData to be overridden; DERIVED MUST OVERRIDE validatePermissions
    protected boolean _hasColumnHeaders = true;
    protected String _importMessage = null;
    protected boolean _targetHasBeenSet = false;    // You can only set target TableInfo or NoTableInfo once
    protected QueryUpdateService.InsertOption _insertOption= QueryUpdateService.InsertOption.INSERT;
    protected AuditBehaviorType _auditBehaviorType = null;
    protected String _auditUserComment = null;
    public boolean _useAsync = false; // if true, do import using a background thread

    Map<Params, Boolean> _optionParamsMap; // map of the boolean option parameters (importIdentity, importLookupByAlternateKey, crossTypeImport, allowCreateStorage)

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
        _importViewBean.hideTsvCsvCombo = hideTsvCsvCombo;
    }

    protected void setSuccessMessageSuffix(String successMessageSuffix)
    {
        _importViewBean.successMessageSuffix = successMessageSuffix;
    }

    protected void setImportHelpTopic(String helpTopic)
    {
        _importViewBean.importHelpTopic = helpTopic;
    }

    protected void setImportHelpTopic(String helpTopic, String displayText)
    {
        _importViewBean.importHelpTopic = helpTopic;
        _importViewBean.importHelpDisplayText = displayText;
    }

    protected void setShowMergeOption(boolean b)
    {
        if (b && canUpdate(getUser()))
            _importViewBean.showMergeOption = true;
    }

    protected void setShowUpdateOption(boolean b)
    {
        if (b && canUpdate(getUser()))
            _importViewBean.showUpdateOption = true;
    }

    protected void setTypeName(String name)
    {
        _importViewBean.typeName = name;
    }

    public void setAcceptZeroResults(boolean acceptZeroResults)
    {
        _importViewBean.acceptZeroResults = acceptZeroResults;
    }

    protected boolean canInsert(User user)
    {
        return _target != null
                ? _target.hasPermission(user, InsertPermission.class)
                : getContainer().hasPermission(user, InsertPermission.class);
    }

    protected boolean canUpdate(User user)
    {
        return _target != null
                ? _target.hasPermission(user, UpdatePermission.class)
                : getContainer().hasPermission(user, UpdatePermission.class);
    }

    public ModelAndView getDefaultImportView(FORM form, BindException errors)
    {
        ActionURL url = getViewContext().getActionURL();
        Container c = getContainer();

        if (StringUtils.isBlank(_importViewBean.urlReturn))
        {
            _importViewBean.urlReturn = StringUtils.trimToNull(url.getParameter(ActionURL.Param.returnUrl));
            if (StringUtils.isBlank(_importViewBean.urlReturn))
            {
                ActionURL success = getSuccessURL(form);
                if (null != success)
                    _importViewBean.urlReturn = success.getLocalURIString(false);
                else if (null != _target && null != _target.getGridURL(c))
                    _importViewBean.urlReturn = _target.getGridURL(c).getLocalURIString(false);
                else
                    _importViewBean.urlReturn = PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(url).getLocalURIString(false);
            }
        }

        if (StringUtils.isBlank(_importViewBean.urlCancel))
        {
            _importViewBean.urlCancel = StringUtils.trimToNull(url.getParameter(ActionURL.Param.cancelUrl));
            if (StringUtils.isBlank(_importViewBean.urlCancel))
                _importViewBean.urlCancel = _importViewBean.urlReturn;
        }

        if (StringUtils.isBlank(_importViewBean.urlEndpoint))
            _importViewBean.urlEndpoint = url.getLocalURIString();

        if (_target != null)
        {
            if(_target.getImportMessage() != null)
                _importViewBean.importMessage = _target.getImportMessage();    // Get message from TableInfo if available
            else
                _importViewBean.importMessage = _importMessage;                //Otherwise, get the passed in message

            _importViewBean.urlExcelTemplates = new ArrayList<>();

            List<Pair<String, String>> it = _target.getImportTemplates(getViewContext());
            if (it != null)
            {
                for (Pair<String, String> pair : it)
                {
                    _importViewBean.urlExcelTemplates.add(Pair.of(pair.first, pair.second));
                }
            }
        }
        else if (_noTableInfo)
        {
            _importViewBean.importMessage = _importMessage;     // Use passed in message if no TableInfo
        }
        else
        {
            errors.reject(SpringActionController.ERROR_MSG, "No table has been set to receive imported data");
        }

        return new JspView<>("/org/labkey/api/query/import.jsp", _importViewBean, errors);
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

    public enum Params
    {
        text,
        path,
        module,
        moduleResource,
        saveToPipeline,
        useAsync,
        importIdentity,
        importLookupByAlternateKey,
        format,
        insertOption,
        crossTypeImport,
        allowCreateStorage,
        crossFolderImport,
        useTransactionAuditCache
    }

    @Nullable
    protected String getParam(Params p)
    {
        return getViewContext().getRequest().getParameter(p.name());
    }

    protected Map<Params, Boolean> getOptionParamsMap()
    {
        if (_optionParamsMap == null)
        {
            _optionParamsMap = new HashMap<>();
            _optionParamsMap.put(Params.importIdentity, Boolean.valueOf(getParam(Params.importIdentity)));
            _optionParamsMap.put(Params.importLookupByAlternateKey, Boolean.valueOf(getParam(Params.importLookupByAlternateKey)));
            _optionParamsMap.put(Params.crossTypeImport, Boolean.valueOf(getParam(Params.crossTypeImport)));
            _optionParamsMap.put(Params.allowCreateStorage, Boolean.valueOf(getParam(Params.allowCreateStorage)));
            _optionParamsMap.put(Params.crossFolderImport, Boolean.valueOf(getParam(Params.crossFolderImport)));
            _optionParamsMap.put(Params.useTransactionAuditCache, Boolean.valueOf(getParam(Params.useTransactionAuditCache)));
        }
        return _optionParamsMap;
    }

    protected boolean getOptionParamValue(Params p)
    {
        return getOptionParamsMap().getOrDefault(p, false);
    }

    protected boolean skipInsertOptionValidation()
    {
        return false;
    }

    protected UserSchema getTargetSchema()
    {
        return _target.getUserSchema();
    }

    protected String getPipelineTargetQueryName()
    {
        return _target.getName();
    }

    public final ApiResponse _execute(FORM form, BindException errors) throws Exception
    {
        initRequest(form);

        User user = getUser();
        validatePermission(user, errors);
        if (!errors.hasErrors())
        {
            // validate insert or update permissions based on the insert option
            QueryUpdateService.InsertOption insertOption = QueryUpdateService.InsertOption.INSERT;
            String insertOptionParam = getParam(Params.insertOption);
            if (insertOptionParam != null)
                insertOption = QueryUpdateService.InsertOption.valueOf(insertOptionParam);

            // if subclass already set _insertOption, use their _insertOption
            if (_insertOption == QueryUpdateService.InsertOption.INSERT && insertOption != QueryUpdateService.InsertOption.INSERT)
                _insertOption = insertOption;

            // Issue 42788: Updating dataset data when LK-managed key turned on only inserts new rows
            if (!skipInsertOptionValidation() && _target != null && !_target.supportsInsertOption(_insertOption))
                throw new IllegalArgumentException(_insertOption + " action is not supported for " + _target.getName() + ".");

            switch (_insertOption)
            {
                case UPDATE -> {
                    if (!canUpdate(user))
                        errors.reject(ERROR_GENERIC, "User does not have permission to update rows");
                }
                case MERGE, REPLACE, UPSERT -> {
                    if (!canUpdate(user))
                        errors.reject(ERROR_GENERIC, "User does not have permission to update rows");
                    if (!canInsert(user))
                        errors.reject(ERROR_GENERIC, "User does not have permission to insert rows");
                }
                case IMPORT, IMPORT_IDENTITY, INSERT -> {
                    if (!canInsert(user))
                        errors.reject(ERROR_GENERIC, "User does not have permission to insert rows");
                }
                default -> { throw new IllegalStateException("unhandled InsertOption"); }
            }
        }

        if (errors.hasErrors())
            throw errors;

        FileLike dataFile = null;
        boolean hasPostData = false;
        FileStream file = null;
        String originalName = null;
        DataLoader loader = null;

        String text = getParam(Params.text);
        String path = getParam(Params.path);

        String moduleName = getParam(Params.module);
        String moduleResource = getParam(Params.moduleResource);

        String saveToPipeline = getParam(Params.saveToPipeline); // saveToPipeline saves import file to pipeline root, but doesn't necessarily do import in a background job

        _useAsync = Boolean.valueOf(getParam(Params.useAsync)); // useAsync will save import file to pipeline root as well as run import in a background job

        // Check first if the audit behavior has been defined for the table either in code or through XML.
        // If not defined there, check for the audit behavior defined in the action form (getAuditBehaviorType()).
        AuditBehaviorType behaviorType = (_target != null) ? _target.getAuditBehavior(getAuditBehaviorType()) : getAuditBehaviorType();

        try
        {
            if (null != StringUtils.trimToNull(text))
            {
                hasPostData = true;
                originalName = "upload.tsv";
                TabLoader tabLoader = new TabLoader(text, _hasColumnHeaders);
                if ("csv".equalsIgnoreCase(getParam(Params.format)))
                {
                    tabLoader.setDelimiterCharacter(',');
                    originalName = "upload.csv";
                }
                loader = tabLoader;
                file = new FileStream.ByteArrayFileStream(text.getBytes(StringUtilsLabKey.DEFAULT_CHARSET));
                // di = loader.getDataIterator(ve);
            }
            else if (StringUtils.isNotBlank(path))
            {
                var resolver = WebdavService.get().getResolver();
                // Resolve path under webdav root
                Path parsed = Path.parse(StringUtils.trim(path));
                WebdavResource resource = resolver.lookup(parsed);
                if ((null == resource || !resource.exists()) && !parsed.startsWith(new Path("_webdav")))
                    resource = resolver.lookup(new Path("_webdav").append(parsed));
                if (resource != null && resource.isFile() && resource.canRead(getUser(), true))
                {
                    hasPostData = true;
                    loader = DataLoader.get().createLoader(resource, _hasColumnHeaders, null, null);
                    file = resource.getFileStream(user);
                    originalName = resource.getName();
                }
                else
                {
                    // Resolve file under pipeline root
                    PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                    if (root != null)
                    {
                        // Attempt absolute path first, then relative path from pipeline root
                        File f = new File(path);
                        if (!root.isUnderRoot(f))
                            f = root.resolvePath(path);

                        if (NetworkDrive.exists(f) && root.isUnderRoot(f) && root.hasPermission(getContainer(), getUser(), ReadPermission.class))
                        {
                            hasPostData = true;
                            loader = DataLoader.get().createLoader(f, null, _hasColumnHeaders, null, null);
                            file = new FileAttachmentFile(dataFile, f.getName());
                            originalName = f.getName();
                        }
                    }
                }

                if (!hasPostData)
                {
                    errors.reject(ERROR_GENERIC, "File not found: " + path);
                }
            }
            else if (null != StringUtils.trimToNull(moduleResource))
            {
                Module m = null;

                if (moduleName != null)
                {
                    m = ModuleLoader.getInstance().getModule(moduleName);
                }
                else if (_target != null)
                {
                    DbSchema schema = _target.getSchema();
                    m =  ModuleLoader.getInstance().getModule(schema.getScope(), schema.getName());
                }

                if (m == null)
                {
                    errors.reject(SpringActionController.ERROR_REQUIRED, "Module required to import module resource");
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
                        errors.reject(ERROR_GENERIC, "File not found: " + p);
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
                    dataFile = FileUtil.createTempFileLike("~upload", multipartfile.getOriginalFilename());
                    PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                    if (null != root && (Boolean.parseBoolean(saveToPipeline) || _useAsync))
                    {
                        FileLike dataFileDir = root.getRootFileLike().resolveChild("QueryImportFiles");
                        if (dataFileDir.isFile())
                        {
                            dataFileDir = AssayFileWriter.findUniqueFileName("QueryImportFiles", root.getRootFileLike());
                            if (!FileUtil.mkdir(dataFileDir))
                                throw new RuntimeException("Error attempting to create directory " + dataFileDir.toNioPathForRead()
                                        + " for uploaded query import file " + multipartfile.getOriginalFilename());
                        }
                        else if (!dataFileDir.exists())
                        {
                            if (!FileUtil.mkdir(dataFileDir))
                                throw new RuntimeException("Error attempting to create directory " + dataFileDir
                                        + " for uploaded query import file " + multipartfile.getOriginalFilename());
                        }

                        dataFile = AssayFileWriter.findUniqueFileName(multipartfile.getOriginalFilename(), dataFileDir);

                    }
                    multipartfile.transferTo(dataFile.toNioPathForWrite());
                    if (_useAsync)
                    {
                        if (!isBackgroundImportSupported())
                            throw new RuntimeException("Importing in background currently is not supported for this table");

                        ViewBackgroundInfo info = new ViewBackgroundInfo(getContainer(), getUser(), new ActionURL());

                        UserSchema schema = getTargetSchema();
                        if (schema != null)
                        {
                            String schemaName = schema.getSchemaName();
                            String queryName = getPipelineTargetQueryName();

                            QueryImportPipelineJob.QueryImportAsyncContextBuilder importContextBuilder = new QueryImportPipelineJob.QueryImportAsyncContextBuilder();
                            importContextBuilder
                                .setPrimaryFile(dataFile.toNioPathForRead().toFile())
                                .setHasColumnHeaders(_hasColumnHeaders)
                                .setFileContentType(multipartfile.getContentType())
                                .setSchemaName(schemaName)
                                .setQueryName(queryName)
                                .setRenamedColumns(getRenamedColumns())
                                .setInsertOption(_insertOption)
                                .setAuditBehaviorType(behaviorType)
                                .setAuditUserComment(_auditUserComment)
                                .setOptionParamsMap(getOptionParamsMap())
                                .setAllowLineageColumns(allowLineageColumns())
                                .setJobDescription(getQueryImportDescription())
                                .setJobNotificationProvider(getQueryImportJobNotificationProviderName());

                            QueryImportPipelineJob job = new QueryImportPipelineJob(getQueryImportProviderName(), info, root, importContextBuilder);
                            PipelineService.get().queueJob(job, getQueryImportJobNotificationProviderName());

                            JSONObject response = new JSONObject();
                            response.put("success", true);
                            response.put("jobId", PipelineService.get().getJobId(user, getContainer(), job.getJobGUID()));
                            return new ApiSimpleResponse(response);
                        }

                    }

                    loader = DataLoader.get().createLoader(dataFile.toNioPathForRead().toFile(), multipartfile.getContentType(), _hasColumnHeaders, null, null);
                    file = new FileAttachmentFile(dataFile, multipartfile.getOriginalFilename());
                }
            }

            if (!hasPostData && !errors.hasErrors())
                errors.reject(ERROR_GENERIC, "Form contains no data");
            if (errors.hasErrors())
                throw errors;

            BatchValidationException ve = new BatchValidationException();
            //di = wrap(di, ve);
            //importData(di, ve);

            configureLoader(loader);

            TransactionAuditProvider.TransactionAuditEvent auditEvent = null;
            if (behaviorType != null && behaviorType != AuditBehaviorType.NONE)
                auditEvent = createTransactionAuditEvent(getContainer(), QueryService.AuditAction.INSERT);

            int rowCount = importData(loader, file, originalName, ve, behaviorType, auditEvent, _auditUserComment);

            if (ve.hasErrors())
                throw ve;

            JSONObject response = createSuccessResponse(rowCount);
            if (auditEvent != null)
            {
                response.put("transactionAuditId", auditEvent.getRowId());
                response.put("reselectRowCount", auditEvent.hasMultiActions());
            }

            return new ApiSimpleResponse(response);

        }
        catch (IOException e)
        {
            errors.reject(ERROR_GENERIC, e.getMessage());
            throw errors;
        }
        finally
        {
            if (loader != null)
                loader.close();
            if (null != file)
                file.closeInputStream();
            if (null != dataFile && !Boolean.parseBoolean(saveToPipeline) && !_useAsync)
                dataFile.delete();
        }

    }

    protected void configureLoader(DataLoader loader) throws IOException
    {
        configureLoader(loader, _target, getRenamedColumns(), allowLineageColumns());
    }

    public static void configureLoader(DataLoader loader, @Nullable TableInfo target, @Nullable Map<String, String> renamedColumns, boolean allowLineageColumns) throws IOException
    {
        //apply known columns so loader can do better type conversion
        if (loader != null && target != null)
            loader.setKnownColumns(target.getColumns());

        if (loader != null && renamedColumns != null && !renamedColumns.isEmpty())
        {
            ColumnDescriptor[]  columnDescriptors = loader.getColumns(renamedColumns);
            for (ColumnDescriptor columnDescriptor : columnDescriptors)
            {
                if (renamedColumns.containsKey(columnDescriptor.getColumnName()))
                {
                    columnDescriptor.name = renamedColumns.get(columnDescriptor.getColumnName());
                }
            }
        }

        // Issue 40302: Unable to use samples or data class with integer like names as material or data input
        // treat lineage columns as string values
        if (loader != null && allowLineageColumns)
        {
            ColumnDescriptor[] cols = loader.getColumns();
            for (ColumnDescriptor col : cols)
            {
                String name = col.name.toLowerCase();
                if (name.startsWith(ExpMaterial.MATERIAL_INPUT_PARENT.toLowerCase() + "/") ||
                    name.startsWith(ExpMaterial.MATERIAL_OUTPUT_CHILD.toLowerCase() + "/") ||
                    name.startsWith(ExpData.DATA_INPUT_PARENT.toLowerCase() + "/") ||
                    name.startsWith(ExpData.DATA_OUTPUT_CHILD.toLowerCase() + "/") ||
                    name.equalsIgnoreCase("Name") /* Issue 50710: Treat "Name" column as a string value for sample or data class import */)
                {
                    col.clazz = String.class;
                    col.converter = TabLoader.noopConverter;
                }
            }
        }

    }

    protected boolean allowLineageColumns()
    {
        return false;
    }

    protected Map<String, String> getRenamedColumns()
    {
        return null;
    }

    protected String getQueryImportProviderName()
    {
        return null;
    }

    protected String getQueryImportDescription()
    {
        return null;
    }

    protected String getQueryImportJobNotificationProviderName()
    {
        return null;
    }

    protected boolean isBackgroundImportSupported()
    {
        return false;
    }

    protected JSONObject createSuccessResponse(int rowCount)
    {
        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("rowCount", rowCount);
        return response;
    }

    @Override
    protected ApiResponseWriter createResponseWriter() throws IOException
    {
        return new ExtFormResponseWriter(getViewContext().getRequest(), getViewContext().getResponse());
    }

    protected void validatePermission(User user, BindException errors)
    {
        if (_noTableInfo)
        {
            // There is no TableInfo; Derived class should check permissions
            errors.reject(ERROR_GENERIC, "Table not specified");
        }
        else if (null == _target)
        {
            if (!getOptionParamValue(Params.crossTypeImport))
                errors.reject(ERROR_GENERIC, "Table not specified");
        }
        else if (!_target.hasPermission(user, InsertPermission.class))
        {
            if (user.isGuest())
                throw new UnauthorizedException();
            errors.reject(ERROR_GENERIC, "User does not have permission to insert rows");
        }
        else if (null == _updateService)
        {
            errors.reject(ERROR_GENERIC, "Table does not support update service: " + _target.getName());
        }
    }


    @Override
    protected String getCommandClassMethodName()
    {
        return "getView"; // getView() is abstract, so needs to be implemented in subclasses (unlike initRequest())
    }

    protected void initRequest(FORM form) throws ServletException
    {
    }

    protected AuditBehaviorType getAuditBehaviorType()
    {
        return _auditBehaviorType;
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
    protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors, @Nullable AuditBehaviorType auditBehaviorType, TransactionAuditProvider.@Nullable TransactionAuditEvent auditEvent, @Nullable String auditUserComment) throws IOException
    {
        return importData(dl, _target, _updateService, _insertOption, getOptionParamsMap(), errors, auditBehaviorType, auditEvent, auditUserComment, getUser(), getContainer(), null);
    }

    public static DataIteratorContext createDataIteratorContext(QueryUpdateService.InsertOption insertOption, Map<Params, Boolean> optionParamsMap, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String auditUserComment, BatchValidationException errors, @Nullable Logger logger, @Nullable Container container)
    {
        return createDataIteratorContext(insertOption, optionParamsMap, auditBehaviorType, auditUserComment, errors, logger, container, null);
    }

    public static DataIteratorContext createDataIteratorContext(QueryUpdateService.InsertOption insertOption, Map<Params, Boolean> optionParamsMap, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String auditUserComment, BatchValidationException errors, @Nullable Logger logger, @Nullable Container container, QueryImportPipelineJob importJob)
    {
        boolean importLookupByAlternateKey = optionParamsMap.getOrDefault(AbstractQueryImportAction.Params.importLookupByAlternateKey, false);
        boolean importIdentity = optionParamsMap.getOrDefault(AbstractQueryImportAction.Params.importIdentity, false);
        boolean crossTypeImport = optionParamsMap.getOrDefault(AbstractQueryImportAction.Params.crossTypeImport, false);
        boolean allowCreateStorage = optionParamsMap.getOrDefault(AbstractQueryImportAction.Params.allowCreateStorage, false);
        boolean crossFolderImport = optionParamsMap.getOrDefault(AbstractQueryImportAction.Params.crossFolderImport, false);
        boolean useTransactionAuditCache = optionParamsMap.getOrDefault(Params.useTransactionAuditCache, false);

        DataIteratorContext context = new DataIteratorContext(errors);
        context.setBackgroundJob(importJob);
        context.setInsertOption(insertOption);
        context.setAllowImportLookupByAlternateKey(importLookupByAlternateKey);
        if (auditBehaviorType != null)
        {
            context.putConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, auditBehaviorType);
        }
        if (auditUserComment != null)
        {
            context.putConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditUserComment, auditUserComment);
        }
        if (importIdentity)
        {
            context.setInsertOption(QueryUpdateService.InsertOption.IMPORT_IDENTITY);
            context.setSupportAutoIncrementKey(true);
        }
        context.setCrossTypeImport(crossTypeImport);
        context.setCrossFolderImport(crossFolderImport && container != null && container.hasProductFolders());
        context.setAllowCreateStorage(allowCreateStorage);
        context.setUseTransactionAuditCache(useTransactionAuditCache);
        context.setLogger(logger);
        return context;
    }

    public static int importData(DataLoader dl, TableInfo target, QueryUpdateService updateService, QueryUpdateService.InsertOption insertOption, Map<Params, Boolean> optionParamsMap, BatchValidationException errors, @Nullable AuditBehaviorType auditBehaviorType, TransactionAuditProvider.@Nullable TransactionAuditEvent auditEvent, @Nullable String auditUserComment, User user, Container container, @Nullable Logger logger) throws IOException
    {
        return importData(dl, target, updateService, createDataIteratorContext(insertOption, optionParamsMap, auditBehaviorType, auditUserComment, errors, logger, container), auditEvent, user, container);
    }

    public static int importData(DataLoader dl, TableInfo target, QueryUpdateService updateService, @NotNull DataIteratorContext context, TransactionAuditProvider.@Nullable TransactionAuditEvent auditEvent, User user, Container container) throws IOException
    {
        if (target != null)
        {
            try (DbScope.Transaction transaction = target.getSchema().getScope().ensureTransaction())
            {
                QueryService.AuditAction auditAction = null;
                if (auditEvent != null)
                {
                    auditAction = auditEvent.getAuditAction();
                    if (transaction.getAuditEvent() != null)
                        auditEvent = transaction.getAuditEvent();
                    else
                        addTransactionAuditEvent(transaction, user, auditEvent);
                }
                int count = updateService.loadRows(user, container, dl, context, new HashMap<>());
                if (context.getErrors().hasErrors())
                    return 0;
                if (auditEvent != null)
                    auditEvent.addComment(auditAction, count);

                transaction.commit();
                return count;
            }
            catch (SQLException x)
            {
                boolean isConstraint = RuntimeSQLException.isConstraintException(x);
                if (isConstraint)
                    context.getErrors().addRowError(new ValidationException(x.getMessage()));
                else
                    throw new RuntimeSQLException(x);
            }
        }
        else if (!context.isCrossTypeImport())
        {
            context.getErrors().addRowError(new ValidationException("Table not specified"));
        }

        return 0;
    }


    @Override
    public void addNavTrail(NavTree root)
    {
    }
}
