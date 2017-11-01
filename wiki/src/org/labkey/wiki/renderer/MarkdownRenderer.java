/*
 * Copyright (c) 2017 LabKey Corporation
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

package org.labkey.wiki.renderer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.wiki.FormattedHtml;

import javax.script.ScriptException;
import java.util.*;

public class MarkdownRenderer extends HtmlRenderer
{

    public MarkdownRenderer(String hrefPrefix, String attachPrefix, Map<String, String> nameTitleMap, @Nullable Collection<? extends Attachment> attachments)
    {
        super(hrefPrefix, attachPrefix, nameTitleMap, attachments);
    }

    @Override
    public FormattedHtml format(String text)
    {
        // translate the markdown to html and reuse the html renderer
        MarkdownService markdownService = ServiceRegistry.get().getService(MarkdownService.class);
        if (null != markdownService)
        {
            try
            {
                return super.format(markdownService.toHtml(text));
            }
            catch( NoSuchMethodException | ScriptException e)
            {
                // if the translation from markdown to html doesnt work then show an error message in the view of the html
                StringBuilder errorMsg = new StringBuilder("<div class=\"labkey-error\"><b>An exception occurred while converting markdown to HTML</b></div><br>The error message was: ");
                errorMsg.append(e.getMessage());
                return super.format(errorMsg.toString());
            }
        }
        else
        {
            // if no markdownService available then show an error message in the view of the html
            String errorMsg = "<div class=\"labkey-error\"><b>No markdown service was available to convert the markdown to HTML</b></div><br>";
            return super.format(errorMsg);
        }

    }

}
