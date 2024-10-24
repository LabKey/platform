/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractTempDirDataCollector;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.vfs.FileLike;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class TextAreaDataCollector<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AbstractTempDirDataCollector<ContextType>
{
    private static final String FORM_ELEMENT_NAME = "TextAreaDataCollector.textArea";

    @Override
    public HttpView getView(ContextType context)
    {
        return HtmlView.unsafe("<textarea id=\"" + FORM_ELEMENT_NAME + "\" name=\"" + FORM_ELEMENT_NAME + "\" rows=\"10\" cols=\"80\"></textarea>\n" +
                "<script type=\"text/javascript\" nonce=\"" + HttpView.currentPageConfig().getScriptNonce()+ "\">\n" +
                "    LABKEY.requiresExt3ClientAPI(function() {\n" +
                "       Ext.EventManager.on('" + FORM_ELEMENT_NAME + "', 'keydown', LABKEY.ext.Utils.handleTabsInTextArea);\n" +
                "    });\n" +
                "</script>");
    }

    @Override
    public String getShortName()
    {
        return "textAreaDataProvider";
    }

    @Override
    public String getDescription(ContextType context)
    {
        return "Paste in a tab-separated set of values (including column headers)";
    }

    @Override
    @NotNull
    public Map<String, FileLike> createData(ContextType context) throws IOException, ExperimentException
    {
        if (_uploadComplete)
            return Collections.emptyMap();
        ExpProtocol protocol = context.getProtocol();
        String data = context.getRequest().getParameter(FORM_ELEMENT_NAME);
        if (data == null)
        {
            throw new IllegalArgumentException("Data not found in request");
        }
        if (data.equals(""))
        {
            throw new ExperimentException("Data file contained zero data rows");
        }

        // NOTE: We use a 'tmp' file extension so that DataLoaderService will sniff the file type by parsing the file's header.
        FileLike dir = getFileTargetDir(context);
        FileLike file = createFile(protocol, dir, "tmp");
        ByteArrayInputStream bIn = new ByteArrayInputStream(data.getBytes(context.getRequest().getCharacterEncoding()));

        writeFile(bIn, file.toNioPathForRead().toFile());
        return Collections.singletonMap(PRIMARY_FILE, file);
    }

    @Override
    protected String getPreferredAssayId(File primaryFile)
    {
        // Strip off the .tmp extension if present
        String result = primaryFile.getName();
        if (result.toLowerCase().endsWith(".tmp"))
        {
            result = result.substring(0, result.length() - ".tmp".length());
        }
        return result;
    }
}
