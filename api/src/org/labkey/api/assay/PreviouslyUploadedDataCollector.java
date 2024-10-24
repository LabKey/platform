/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.InsertView;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * This data collector doesn't write any files to the assay upload temp directory, but it may reference them
 * after an error causes the page to reshow. Therefore, it needs to subclass AbstractTempDirDataCollector so that
 * it migrates the files to the main assay file directory after a successful import.
 */
public class PreviouslyUploadedDataCollector<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AbstractTempDirDataCollector<ContextType>
{
    private final Map<String, FileLike> _uploadedFiles;
    private final Type _type;

    /**
     * Distinguishes between the form element names for the two different ways to point to files already on the
     * server's file system. They shouldn't be treated as a single set of files, so it's important to keep them
     * straight. See bug 18865.
     */
    public enum Type
    {
        /** The user wants to re-run an existing runs, so we offer up the original run's file for potential reuse */
        ReRun("ReRunReuseFilePaths", "ReRunReuseFileNames"),
        /** The user uploaded a file but there's some validation error, possibly unrelated to the file itself */
        ErrorReshow("PreviouslyUploadedFilePaths", "PreviouslyUploadedFileNames"),
        /** We just want to propagate whatever files the user selected through an additional wizard step, whose UI does not give the user the choice of changing files */
        PassThrough("PassThroughUploadedFilePaths", "PassThroughUploadedFileNames");

        private final String _pathFormElementName;
        private final String _nameFormElementName;

        Type(String pathFormElementName, String nameFormElementName)
        {
            _pathFormElementName = pathFormElementName;
            _nameFormElementName = nameFormElementName;
        }

        public String getPathFormElementName()
        {
            return _pathFormElementName;
        }

        public String getNameFormElementName()
        {
            return _nameFormElementName;
        }
    }

    public PreviouslyUploadedDataCollector(Map<String, FileLike> uploadedFiles, Type type)
    {
        _uploadedFiles = uploadedFiles;
        _type = type;
    }

    /** Default type is ErrorReshow */
    public PreviouslyUploadedDataCollector(Map<String, FileLike> uploadedFiles)
    {
        this(uploadedFiles, Type.ErrorReshow);
    }

    @Override
    public HtmlView getView(ContextType context)
    {
        HtmlStringBuilder sb = HtmlStringBuilder.of();
        String separator = "";
        for (Map.Entry<String, FileLike> entry : _uploadedFiles.entrySet())
        {
            sb.append(separator);
            separator = ", ";
            sb.append(entry.getValue().getName());
            sb.append(getHiddenFormElementHTML(context.getContainer(), entry.getKey(), entry.getValue().toNioPathForRead().toFile()));
        }
        return new HtmlView(sb);
    }

    public HtmlString getHiddenFormElementHTML(Container container, String formElementName, File file)
    {
        PipeRoot pipeRoot = getPipelineRoot(container);
        HtmlStringBuilder sb = HtmlStringBuilder.of();
        sb.unsafeAppend("<input name=\"");
        sb.append(_type.getPathFormElementName());
        sb.unsafeAppend("\" type=\"hidden\" value=\"");
        sb.append(pipeRoot.relativePath(file).replace('\\', '/'));
        sb.unsafeAppend("\"/><input type=\"hidden\" name=\"");
        sb.append(_type.getNameFormElementName());
        sb.unsafeAppend("\" value=\"");
        sb.append(formElementName);
        sb.unsafeAppend("\"/>");
        return sb.getHtmlString();
    }

    @Override
    public String getShortName()
    {
        return "Previously uploaded files";
    }

    @Override
    public String getDescription(ContextType context)
    {
        return "Use the data file(s) already uploaded to the server";
    }

    @Override
    @NotNull
    public Map<String, FileLike> createData(ContextType context) throws IOException
    {
        if (_uploadComplete)
            return Collections.emptyMap();

        Map<String, FileLike> result = CollectionUtils.enforceValueClass(new LinkedHashMap<>(), FileLike.class);
        // Add the files for the specific flavor we're expecting
        result.putAll(getFilesFromRequest(context, _type));
        if (_type != Type.PassThrough)
        {
            // Also include all of the files that are just being piped through a page that doesn't let the user
            // choose which files to use
            result.putAll(getFilesFromRequest(context, Type.PassThrough));
        }
        return result;
    }

    private Map<String, FileLike> getFilesFromRequest(ContextType context, Type type) throws IOException
    {
        String[] paths = context.getRequest().getParameterValues(type.getPathFormElementName());
        String[] names = context.getRequest().getParameterValues(type.getNameFormElementName());
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
            throw new IOException("The number of paths did not match the number of names for form elements " + _type.getPathFormElementName() + " and " + _type.getNameFormElementName());
        }

        PipeRoot pipelineRoot = getPipelineRoot(context.getContainer());

        Map<String, FileLike> result = CollectionUtils.enforceValueClass(new LinkedHashMap<>(), FileLike.class);
        for (int i = 0; i < paths.length; i++)
        {
            result.put(names[i], pipelineRoot.resolvePathToFileLike(paths[i]));
        }
        return result;
    }

    @Override
    public boolean isVisible()
    {
        return !_uploadedFiles.isEmpty();
    }

    public void addHiddenFormFields(InsertView view, ContextType context)
    {
        PipeRoot pipeRoot = getPipelineRoot(context.getContainer());

        view.getDataRegion().addHiddenFormField("dataCollectorName", getShortName());
        for (Map.Entry<String, FileLike> entry : _uploadedFiles.entrySet())
        {
            view.getDataRegion().addHiddenFormField(_type.getNameFormElementName(), entry.getKey());
            view.getDataRegion().addHiddenFormField(_type.getPathFormElementName(), pipeRoot.relativePath(entry.getValue().toNioPathForWrite().toFile()));
        }
    }
}
