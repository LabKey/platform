/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.xarassay;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.ViewURLHelper;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.File;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * User: phussey
 * Date: Sep 17, 2007
 * Time: 12:30:55 AM
 */
@RequiresPermission(ACL.PERM_INSERT)
public class XarAssayUploadAction extends UploadWizardAction<XarAssayForm>
{
    public XarAssayUploadAction()
    {
        super(XarAssayForm.class);
        addStepHandler(new DeleteAssaysStepHandler());

    }

    public ModelAndView getView(XarAssayForm assayRunUploadForm, BindException errors) throws Exception
    {
        String referer = assayRunUploadForm.getRequest().getParameter("referer");
        if ((null == referer && null==assayRunUploadForm.getUploadStep()))
        {
            // want to redirect the Upload Runs button from the Assay Details list to the pipeline first
            ViewURLHelper pipelineView = assayRunUploadForm.getViewURLHelper().clone();
            pipelineView.setPageFlow("Pipeline");
            pipelineView.setAction("browse.view");
            HttpView.throwRedirect(pipelineView);
        }
        return super.getView(assayRunUploadForm, errors);

    }


    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteAssaysStepHandler extends StepHandler<XarAssayForm>
    {
        public static final String NAME = "DELETEASSAYS";

        @Override
        public ModelAndView handleStep(XarAssayForm form, BindException errors) throws ServletException, SQLException
        {

            try
            {
                Container c = form.getContainer();

                PipelineService service = PipelineService.get();
                PipeRoot pr = service.findPipelineRoot(c);
                if (pr == null || !URIUtil.exists(pr.getUri()))
                    HttpView.throwNotFound();

                URI uriData = URIUtil.resolve(pr.getUri(c), form.getPath());
                if (uriData == null)
                    HttpView.throwNotFound();

                File[] mzXMLFiles = new File(uriData).listFiles(new XarAssayProvider.AnalyzeFileFilter());
                Lsid lsid;
                for (File mzXMLFile : mzXMLFiles)
                {
                    ExpRun run = ExperimentService.get().getCreatingRun(mzXMLFile, c);
                    if (run != null)
                    {
                        ExperimentService.get().deleteExperimentRunsByRowIds(c, form.getUser(), run.getRowId());
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }

            ViewURLHelper helper = form.getProvider().getUploadWizardURL(getContainer(), _protocol);
            helper.replaceParameter("path", form.getPath());
            helper.replaceParameter("providerName", form.getProviderName());
            HttpView.throwRedirect(helper);

            return null;
        }

        public String getName()
        {
            return NAME;

        }
    }

    // overide to work around a problem I was having when new defaults are saved
    protected Map<String, String> getDefaultValues(String suffix, XarAssayForm form)
    {
        Map<String, String> unmodifiable = super.getDefaultValues(suffix, form);
        Map<String, String> modifiable = new HashMap<String, String>(unmodifiable);
        return modifiable;
    }

    protected InsertView createInsertView(TableInfo baseTable, String lsidCol, Map<PropertyDescriptor, String> propertyDescriptors, boolean reshow, boolean resetDefaultValues, String uploadStepName, XarAssayForm form)
    {
        InsertView view = super.createInsertView(baseTable, lsidCol, propertyDescriptors, reshow, resetDefaultValues, uploadStepName, form);
        try
        {
            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(form.getContainer());

            for (Map.Entry<String, File> entry : form.getUploadedData().entrySet())
            {
                view.getDataRegion().addHiddenFormField(XarAssayDataCollector.PATH_FORM_ELEMENT_NAME, pipelineRoot.relativePath(entry.getValue()).replace('\\', '/'));
                view.getDataRegion().addHiddenFormField(XarAssayDataCollector.NAME_FORM_ELEMENT_NAME, entry.getKey());
            }

            view.getDataRegion().addHiddenFormField("path", form.getPath());

            if (uploadStepName.equals(UploadWizardAction.RunStepHandler.NAME))
            {
                ArrayList<String> udf = form.getUndescribedFiles();
                //todo check if these really need to be done
                if (udf.size()>0)
                {
                    view.getDataRegion().addHiddenFormField(XarAssayDataCollector.CURRENT_FILE_FORM_ELEMENT_NAME, form.getCurrentFileName());
                }
                view.getDataRegion().addHiddenFormField(XarAssayDataCollector.NUMBER_REMAINING_FORM_ELEMENT_NAME, Integer.toString(form.getNumFilesRemaining()));


            }

        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return view;
    }
}
