/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import java.util.*;

public class MarkdownRenderer extends HtmlRenderer
{

    public MarkdownRenderer(String hrefPrefix, String attachPrefix, Map<String, String> nameTitleMap, @Nullable Collection<? extends Attachment> attachments)
    {
        super(hrefPrefix, attachPrefix, nameTitleMap, attachments);
    }

    public FormattedHtml format(String text)
    {
        // translate the markdown to html and reuse the html renderer
        MarkdownService markdownService = ServiceRegistry.get().getService(MarkdownService.class);
        text = markdownService.mdToHtml(text);
        return super.format(text);
    }

}
