/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Apr 13, 2009
 */
@RequiresPermission(InsertPermission.class)
public class PipelineDataCollectorRedirectAction extends SimpleViewAction<PipelineDataCollectorRedirectAction.UploadRedirectForm>
{
    public ModelAndView getView(UploadRedirectForm form, BindException errors) throws Exception
    {
        Container container = getContainer();
        // Can't trust the form's getPath() because it translates the empty string into null, and we
        // need to know if the parameter was present
        String path = getViewContext().getRequest().getParameter("path");
        List<File> files = new ArrayList<>();
        if (path != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(container);
            if (root == null)
            {
                throw new NotFoundException("No pipeline root is available");
            }
            File f = root.resolvePath(path);
            if (!NetworkDrive.exists(f))
            {
                throw new NotFoundException("Unable to find file: " + path);
            }

            for (String fileName : getViewContext().getRequest().getParameterValues("file"))
            {
                if (fileName.indexOf("/") != -1 || fileName.indexOf("\\") != -1)
                {
                    throw new NotFoundException(fileName);
                }
                File file = new File(f, fileName);
                if (!NetworkDrive.exists(file))
                {
                    throw new NotFoundException(fileName);
                }
                files.add(file);
            }
        }
        else
        {
            for (int dataId : DataRegionSelection.getSelectedIntegers(getViewContext(), true))
            {
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data == null || !data.getContainer().equals(container))
                {
                    throw new NotFoundException("Could not find all selected datas");
                }

                File f = data.getFile();
                if (f != null && f.isFile())
                {
                    files.add(f);
                }
            }
        }

        if (files.isEmpty())
        {
            throw new NotFoundException("Could not find any matching files");
        }
        files = validateFiles(errors, files);

        if (errors.getErrorCount() > 0)
        {
            return new SimpleErrorView(errors);
        }

        Collections.sort(files);
        List<Map<String, File>> maps = new ArrayList<>();
        for (File file : files)
        {
            maps.add(Collections.singletonMap(AssayDataCollector.PRIMARY_FILE, file));
        }
        PipelineDataCollector.setFileCollection(getViewContext().getRequest().getSession(true), container, form.getProtocol(), maps);
        throw new RedirectException(AssayService.get().getProvider(form.getProtocol()).getImportURL(container, form.getProtocol()));
    }

    /**
     *
     * @param errors any fatal errors with this set of files
     * @param files the selected files
     * @return the subset of the files that should actually be loaded
     */
    protected List<File> validateFiles(BindException errors, List<File> files)
    {
        for (File file : files)
        {
            ExpData data = ExperimentService.get().getExpDataByURL(file, getContainer());
            if (data != null && data.getRun() != null)
            {
                errors.addError(new LabKeyError("The file " + file.getAbsolutePath() + " has already been imported"));
            }
        }
        return files;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild("Assay Upload Attempt");
    }

    public static class UploadRedirectForm
    {
        private int _protocolId;
        private String _path;

        public int getProtocolId()
        {
            return _protocolId;
        }

        public ExpProtocol getProtocol()
        {
            ExpProtocol result = ExperimentService.get().getExpProtocol(_protocolId);
            if (result == null)
            {
                throw new NotFoundException("Could not find protocol");
            }
            return result;
        }

        public void setProtocolId(int protocolId)
        {
            _protocolId = protocolId;
        }

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }
    }

}
