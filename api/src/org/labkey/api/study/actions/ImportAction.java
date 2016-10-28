/*
 * Copyright (c) 2011-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.study.actions;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Dec 10, 2010
 * Time: 2:16:07 PM
 */
@RequiresPermission(DesignAssayPermission.class)
public class ImportAction extends BaseAssayAction<ImportAction.ImportForm>
{
    public static class ImportForm extends ProtocolIdForm
    {
        private String _path;
        private String[] _file;

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }

        public String[] getFile()
        {
            return _file;
        }

        public void setFile(String[] file)
        {
            _file = file;
        }
    }

    private ImportForm _form;

    public static final String ASSAY_IMPORT_FILE = "org.labkey.assay.importFile";

    public ModelAndView getView(ImportForm form, BindException errors) throws Exception
    {
        _form = form;
        Map<String, String> properties = new HashMap<>();

        properties.put("providerName", form.getProviderName());
        String path = form.getPath();
        if (path != null)
            properties.put("path", path);

        AssayProvider provider = AssayService.get().getProvider(form.getProviderName());
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider " + form.getProviderName());
        }

        if (form.getReturnUrl() != null)
        {
            properties.put(ActionURL.Param.returnUrl.name(), form.getReturnUrl());
        }

        VBox result = new VBox();
        List<File> files = getFiles(getContainer(), form.getPath(), form.getFile());

        if (!files.isEmpty())
        {
            // for 11.1 we will constrain importing of assay designs to a single file
            assert(files.size() == 1) : "Expected exactly one file, but found " + files.size();

            File srcFile = files.get(0);
//            HttpSession session = getViewContext().getSession();
//            session.setAttribute(ASSAY_IMPORT_FILE, srcFile);
            properties.put("file", srcFile.getName());

            // if the file(s) are already on the server, hide the file upload form on the importer
            properties.put("skipFileUpload", Boolean.toString(true));
        }
        result.addView(createGWTView(properties));
        return result;
    }

    public static List<File> getFiles(Container c, String dirPath, String[] files)
    {
        String path = StringUtils.trimToEmpty(dirPath);
        List<File> retFiles = new ArrayList<>();

        PipeRoot root = PipelineService.get().findPipelineRoot(c);
        if (root == null)
        {
            throw new NotFoundException("No pipeline root is available");
        }
        File f = root.resolvePath(path);
        if (!NetworkDrive.exists(f))
        {
            throw new NotFoundException("Unable to find file: " + path);
        }

        if (files == null)
        {
            throw new NotFoundException("Could not find any matching files");
        }

        for (String fileName : files)
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
            retFiles.add(file);
        }

        if (retFiles.isEmpty())
        {
            throw new NotFoundException("Could not find any matching files");
        }

        Collections.sort(retFiles);
        return retFiles;
    }

    protected ModelAndView createGWTView(Map<String, String> properties)
    {
        return AssayService.get().createAssayImportView(properties);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_form.getProviderName() + " Assay Import", new ActionURL(DesignerAction.class, getContainer()));
        return result;
    }

    public ImportForm getForm()
    {
        return _form;
    }
}
