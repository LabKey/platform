/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data collector that supplies files the user previously selected through the pipeline/file browser.
 *
 * User: jeckels
 * Date: Jan 2, 2008
 */
public class PipelineDataCollector<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AbstractTempDirDataCollector<ContextType>
{
    public PipelineDataCollector()
    {
    }

    private File _originalFileLocation = null;

    public HttpView getView(ContextType context) throws ExperimentException
    {
        return new HtmlView(getHTML(context));
    }

    public String getHTML(ContextType context) throws ExperimentException
    {
        Map<String, File> files = getCurrentFilesForDisplay(context);
        if (files.isEmpty())
        {
            return "<div class=\"labkey-error\">No files have been selected.</div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<ul>");
        for (File file : files.values())
        {
            sb.append("<li>");
            sb.append(PageFlowUtil.filter(file.getName()));
            sb.append("</li>");
        }
        sb.append("</ul>");
        int additionalSets = getAdditionalFileSetCount(context);
        if (additionalSets > 0)
        {
            sb.append(" (");
            sb.append(additionalSets);
            sb.append(" more run");
            sb.append(additionalSets > 1 ? "s" : "");
            sb.append(" available)");
        }

        return sb.toString();
    }

    /**
     * @return the number of additional files available for uploading
     */
    protected int getAdditionalFileSetCount(ContextType context)
    {
        return getFileQueue(context).size() - 1;
    }

    /** @return the files to be processed for the current upload attempt */
    protected Map<String, File> getCurrentFilesForDisplay(ContextType context)
    {
        List<Map<String, File>> files = getFileQueue(context);
        if (files.isEmpty())
        {
            return Collections.emptyMap();
        }
        return files.get(0);
    }

    public String getShortName()
    {
        return "Pipeline";
    }

    public String getDescription(ContextType context)
    {
        List<Map<String, File>> allFiles = getFileQueue(context);
        if (allFiles.isEmpty())
        {
            return "";
        }
        Map<String, File> files = allFiles.get(0);
        return (files.size() > 1 ? files.size() + " files" : "One file ") + " from the Data Pipeline in " + files.values().iterator().next().getParent();
    }

    public static synchronized void setFileCollection(HttpSession session, Container c, ExpProtocol protocol, List<Map<String, File>> files)
    {
        List<Map<String, File>> existingFiles = getFileQueue(session, c, protocol);
        existingFiles.clear();
        existingFiles.addAll(files);
    }

    public static List<Map<String, File>> getFileQueue(AssayRunUploadContext context)
    {
        return getFileQueue(context.getRequest().getSession(true), context.getContainer(), context.getProtocol());
    }

    private static List<Map<String, File>> getFileQueue(HttpSession session, Container c, ExpProtocol protocol)
    {
        // Use the protocol's RowId instead the ExpProtocol itself because it will be serialized as part of the session
        // state when Tomcat is shut down cleanly
        Map<Pair<Container, Integer>, List<Map<String, File>>> collections = (Map<Pair<Container, Integer>, List<Map<String, File>>>) session.getAttribute(PipelineDataCollector.class.getName());
        if (collections == null)
        {
            collections = new HashMap<>();
            session.setAttribute(PipelineDataCollector.class.getName(), collections);
        }
        Pair<Container, Integer> key = new Pair<>(c, protocol.getRowId());
        List<Map<String, File>> result = collections.get(key);
        if (result == null)
        {
            result = new ArrayList<>();
            collections.put(key, result);
        }
        return result;
    }

    @NotNull
    public Map<String, File> createData(ContextType context) throws IOException, ExperimentException
    {
        List<Map<String, File>> files = getFileQueue(context);
        if (files.isEmpty())
        {
            throw new FileNotFoundException("No files from the pipeline directory have been selected");
        }

        Map<String, File> currentFiles = files.get(0);
        if (!currentFiles.isEmpty())
        {
            _originalFileLocation = currentFiles.values().iterator().next().getParentFile();
        }
        return savePipelineFiles(context, currentFiles);
    }

    @Nullable
    @Override
    public File getOriginalFileLocation()
    {
        return _originalFileLocation;
    }

    public boolean isVisible()
    {
        return true;
    }

    // When importing via pipeline, the file is already on the server so return the path of that file
    @Nullable
    @Override
    protected File getFilePath(ContextType context, @Nullable ExpRun run, File tempDirFile) throws ExperimentException
    {
        Map<String, File> files = getFileQueue(context).get(0);
        for (File file : files.values())
        {
            if(file.getName().equals(tempDirFile.getName()))
                return file;
        }

        return null;
    }

    // Default case in AbstractTempDirDataCollector is to create a copy of the input file to the assayData directory.
    // We already have the input file on the server so don't need to make a copy.
    @Override
    protected void handleTempFile(File tempDirFile, File assayDirFile) throws IOException
    {
        // Do not move the file
    }

    public Map<String, File> uploadComplete(ContextType context, @Nullable ExpRun run) throws ExperimentException
    {
        Map<String, File> result = super.uploadComplete(context, run);
        List<Map<String, File>> files = getFileQueue(context);
        if (!files.isEmpty())
        {
            files.remove(0);
        }
        return result;
    }

    public AdditionalUploadType getAdditionalUploadType(ContextType context)
    {
        if (getFileQueue(context).size() > 1)
        {
            return AdditionalUploadType.AlreadyUploaded;
        }
        return AdditionalUploadType.Disallowed;
    }
}
