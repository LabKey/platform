/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.pipeline.browse;

import org.labkey.api.pipeline.browse.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.jsp.FormPage;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.*;

public class BrowseViewImpl extends BrowseView
{
    static abstract public class Page extends FormPage<BrowseForm>
    {
        protected List<Map.Entry<String, BrowseFile>> parents;
        protected List<BrowseFile> browseFiles;
        private Set<String> selectedFiles;

        @Override
        public void setForm(BrowseForm viewForm)
        {
            super.setForm(viewForm);
            BrowseForm form = getForm();
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
            FileFilter fileFilter = form.getFileFilterObject();
            File browsePath = null;
            if (form.getPath() == null)
            {
                String path = pipeRoot.getStartingPath(getContainer(), getUser());
                if (path != null)
                {
                    browsePath = new File(URIUtil.resolve(pipeRoot.getUri(), path));
                }
            }
            else
            {
                browsePath = pipeRoot.resolvePath(form.getPath());
                if (browsePath != null)
                {
                    pipeRoot.rememberStartingPath(getContainer(), getUser(), URIUtil.relativize(pipeRoot.getUri(), browsePath.toURI()).toString());
                }
            }
            if (browsePath == null)
            {
                browsePath = pipeRoot.getRootPath();
            }
            parents = new ArrayList<Map.Entry<String, BrowseFile>>();
            File currentPath = browsePath;
            while(true)
            {
                BrowseFile bf = new BrowseFile(pipeRoot, currentPath);
                if (bf.getRelativePath().length() == 0)
                {
                    break;
                }
                parents.add(0, new Pair(bf.getName(), bf));
                currentPath = currentPath.getParentFile();
            }
            parents.add(0, new Pair("root", new BrowseFile(pipeRoot, pipeRoot.getRootPath())));
            browseFiles = new ArrayList();
            File[] files = browsePath.listFiles();
            if (files != null)
            {
                for (File file : files)
                {
                    BrowseFile bf = new BrowseFile(pipeRoot, file);
                    if (bf.isDirectory())
                    {
                        browseFiles.add(bf);
                    }
                    else
                    {
                        if (fileFilter.accept(bf))
                        {
                            browseFiles.add(bf);
                        }
                    }
                }
            }
            Collections.sort(browseFiles, form.getBrowseFileComparator());

            selectedFiles = new HashSet(Arrays.asList(form.getFile()));

        }

        public boolean isFileSelected(BrowseFile file)
        {
            return selectedFiles.contains(file.getRelativePath());
        }

        public boolean isMultiSelect()
        {
            return getForm().isMultiSelect();
        }

        public boolean isDirectoriesSelectable()
        {
            return getForm().isDirectoriesSelectable();
        }

        protected BrowseForm getForm()
        {
            return __form;
        }

        public ActionURL getUrlBrowsePath()
        {
            ActionURL ret = getViewContext().cloneActionURL();
            ret.deleteParameter("path");
            return ret;
        }

        public String paramName(BrowseForm.Param param)
        {
            return getForm().paramName(param); 
        }
    }

    Page page;

    public BrowseViewImpl(BrowseForm form)
    {
        page = (Page) FormPage.get(BrowseViewImpl.class, form, "browse.jsp");
    }

    protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        JspView view = new JspView(page);
        view.render(request, response);
    }
}
