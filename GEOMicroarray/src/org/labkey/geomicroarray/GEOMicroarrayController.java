/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.geomicroarray;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.geomicroarray.query.GEOMicroarrayProviderSchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;
import java.util.Map;

public class GEOMicroarrayController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(GEOMicroarrayController.class);

    public GEOMicroarrayController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends RedirectAction
    {
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return new ActionURL(GEOMicroarrayController.ManageFeatureAnnotationSetsAction.class, getContainer());
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageFeatureAnnotationSetsAction extends SimpleViewAction<Object>
    {

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Object>("/org/labkey/geomicroarray/view/manageFeatureAnnotations.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Manage Feature Annotation Sets");
            return root;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteFeatureAnnotationSetAction extends ApiAction<DeleteFeatureAnnotationSetForm>
    {
        @Override
        public ApiResponse execute(DeleteFeatureAnnotationSetForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if(form.getRowId() == null)
            {
                throw new NullPointerException("No feature annotation set specified");
            }

            DbSchema schema = GEOMicroarrayProviderSchema.getSchema();
            DbScope scope = schema.getScope();

            scope.ensureTransaction();

            Integer rowsDeleted = GEOMicroarrayManager.get().deleteFeatureAnnotationSet(form.getRowId());

            scope.commitTransaction();

            response.put("rowsDeleted", rowsDeleted);
            response.put("success", true);

            return response;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class UploadFeatureAnnotationSetAction extends FormViewAction<FeatureAnnotationSetForm>
    {
        @Override
        public void validateCommand(FeatureAnnotationSetForm form, Errors errors)
        {
            Map<String, MultipartFile> fileMap = getFileMap();
            MultipartFile annotationFile = fileMap.get("annotationFile");

            if(form.getName() == null || StringUtils.trimToNull(form.getName()) == null)
            {
                errors.reject(ERROR_MSG, "Name is required.");
            }

            if(form.getVendor() == null || StringUtils.trimToNull(form.getVendor()) == null)
            {
                errors.reject(ERROR_MSG, "Vendor is required.");
            }

            if (null == annotationFile)
            {
                errors.reject(ERROR_MSG, "An annotation file is required.");
            }

            if (null != annotationFile && annotationFile.getSize() == 0)
            {
               errors.reject(ERROR_MSG, "The annotation file cannot be blank");
            }

            // TODO check if feature set with name already exists.
        }

        @Override
        public ModelAndView getView(FeatureAnnotationSetForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<FeatureAnnotationSetForm>("/org/labkey/geomicroarray/view/uploadFeatureAnnotation.jsp", form, errors);
        }

        @Override
        public boolean handlePost(FeatureAnnotationSetForm form, BindException errors) throws Exception
        {
            Map<String, MultipartFile> fileMap = getFileMap();
            MultipartFile annotationFile = fileMap.get("annotationFile");
            DataLoader loader = DataLoader.get().createLoader(annotationFile, true, null, null);

            DbScope scope = GEOMicroarrayProviderSchema.getSchema().getScope();

            try
            {
                scope.ensureTransaction();
                BatchValidationException batchErrors = new BatchValidationException();
                Integer rowsInserted = GEOMicroarrayManager.get().createFeatureAnnotationSet(getUser(), getContainer(), form, loader, batchErrors);

                if (batchErrors.hasErrors())
                {
                    addErrors(batchErrors, errors);
                    return false;
                }

                if (rowsInserted <= 0)
                {
                   errors.reject(ERROR_MSG, "Error: No rows inserted into FeatureAnnotation table.");
                }

                if(!errors.hasErrors() && !batchErrors.hasErrors())
                {
                    scope.commitTransaction();
                }
            }
            catch (SQLException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            catch (DuplicateKeyException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            catch (BatchValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            catch (QueryUpdateServiceException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            finally
            {
                scope.closeConnection();
            }

            return !errors.hasErrors();
        }

        private void addErrors(BatchValidationException batchErrors, BindException errors)
        {
            for(ValidationException batchError : batchErrors.getRowErrors())
            {
                errors.reject(ERROR_MSG, batchError.getMessage());
            }
        }

        @Override
        public URLHelper getSuccessURL(FeatureAnnotationSetForm form)
        {
            return new ActionURL(GEOMicroarrayController.ManageFeatureAnnotationSetsAction.class, getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Upload Feature Annotations");
            return root;
        }
    }

    public static class DeleteFeatureAnnotationSetForm
    {
        Integer _rowId;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }
    }

    public static class FeatureAnnotationSetForm
    {
        private String _name;
        private String _vendor;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getVendor()
        {
            return _vendor;
        }

        public void setVendor(String vendor)
        {
            _vendor = vendor;
        }
    }
}
