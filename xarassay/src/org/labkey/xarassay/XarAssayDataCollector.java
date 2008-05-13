/*
 * Copyright (c) 2007 LabKey Corporation
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

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AbstractAssayDataCollector;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AbstractAssayDataCollector;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewURLHelper;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: phussey
 * Date: Sep 23, 2007
 * Time: 1:09:11 PM
 */
public class XarAssayDataCollector extends AbstractAssayDataCollector
{
    public static String NAME="mzXMLFiles";
    public static String PATH_FORM_ELEMENT_NAME = "PreviouslyUploadedFilePaths";
    public static String NAME_FORM_ELEMENT_NAME = "PreviouslyUploadedFileNames";
    public static String CURRENT_FILE_FORM_ELEMENT_NAME = "currentFileName";
    public static String NUMBER_REMAINING_FORM_ELEMENT_NAME ="numFilesRemaining" ;
    private final Map<String, File> _uploadedFiles;

    public XarAssayDataCollector(Map<String, File> uploadedFiles)
    {
        _uploadedFiles = uploadedFiles;
    }


    public XarAssayDataCollector()
    {
        this(Collections.<String, File>emptyMap());
    }


    public boolean isVisible()
    {
        return true;
    }

    public Map<String, File> createData(AssayRunUploadContext ctx) throws IOException
    {
        XarAssayForm form = (XarAssayForm)ctx;
        Map<String, File> result = new HashMap<String, File>();
        PipeRoot pipelineRoot = getPipelineRoot(form);

        String[] paths = form.getRequest().getParameterValues(PATH_FORM_ELEMENT_NAME);
        String[] names = form.getRequest().getParameterValues(NAME_FORM_ELEMENT_NAME);
        if (paths == null)
        {
            paths = new String[0];
        }
        if (names == null)
        {
            names = new String[0];
        }
        if (paths.length != names.length)
        {
            throw new IOException("The number of paths did not match the number of names");
        }
        if (paths.length==0)
        {
            File dirData = pipelineRoot.getRootPath();
            if (null != form.getPath())
                dirData = pipelineRoot.resolvePath(form.getPath());
            File[] mzXMLFiles = dirData.listFiles(new XarAssayProvider.AnalyzeFileFilter());
            for (int i = 0; i < mzXMLFiles.length; i++)
            {
                result.put(mzXMLFiles[i].getName(), mzXMLFiles[i]);
            }
        }
        else
        {
            for (int i = 0; i < paths.length; i++)
                result.put(names[i], pipelineRoot.resolvePath(paths[i]));
        }

        return result;
    }


    public String getHTML(AssayRunUploadContext context)
    {
        ExpProtocol p = context.getProtocol();
        XarAssayForm ctx = (XarAssayForm)context;

        StringBuilder sb = new StringBuilder();
        String separator = "";
        PipeRoot pipeRoot = getPipelineRoot(context);
        Map<String, String> links = new LinkedHashMap<String, String>();

        try
        {
            ViewURLHelper showSamplesURL = new ViewURLHelper("Experiment", "showMaterialSource", context.getContainer());
            showSamplesURL.addParameter("rowId", (ExperimentService.get().ensureActiveSampleSet(context.getContainer())).getRowId());
            links.put("Edit Samples", showSamplesURL.toString());
        }
        catch (SQLException e)
        {
            System.out.println("failed to get sample set url");
        }

        if (ctx.getContainer().hasPermission(ctx.getUser(), ACL.PERM_DELETE))
        {
            if (ctx.getNumFilesRemaining().intValue() == _uploadedFiles.size())
                links.put("Delete Assay Runs", "javascript: window.alert('No Assay Runs associated with these files to delete') ");
            else
            {
                ViewURLHelper deleteURL = new ViewURLHelper("XarAssay", "xarAssayUpload", context.getContainer());
                deleteURL.addParameter("path", ctx.getPath());
                deleteURL.addParameter("rowId", ctx.getRowId());
                deleteURL.addParameter("uploadStep", XarAssayUploadAction.DeleteAssaysStepHandler.NAME);
                deleteURL.addParameter("providerName", ctx.getProviderName());
                links.put("Delete Assay Runs", "javascript: if (window.confirm('Are you sure you want to delete the existing assay runs associated with these files?')) { window.location = '" + deleteURL + "' }");
            }
        }


        if (ctx.getNumFilesRemaining()>0)
        {

            sb.append("<br/>" + ctx.getNumFilesRemaining() + " files remaining of " +  _uploadedFiles.size() + " in selected folder. ");
            sb.append("<br/>");
            sb.append("Current file:  ");
            sb.append("<strong>");
            sb.append(ctx.getCurrentFileName());
            sb.append("</strong>");

        }
        else
        {
            sb.append("<strong>");
            sb.append("<br/>All files in the selected folder have been described by Assay runs");
            sb.append("</strong>");
        }
        sb.append("<br/><br/>");
        for (Map.Entry<String, String> entry : links.entrySet())
        {
            sb.append(PageFlowUtil.textLink(entry.getKey(), entry.getValue()));
            sb.append("&nbsp;");
        }

        return sb.toString();

    }

    public String getShortName()
    {
        return NAME;
    }

    public String getDescription()
    {
        return "mzXML files in selected folder";
    }

    protected String getStrippedFileName(File f)
    {
        String result = f.getName();
        int index = result.lastIndexOf(".");
        if (index != -1)
        {
            return result.substring(0, index);
        }
        return result;
    }

}
