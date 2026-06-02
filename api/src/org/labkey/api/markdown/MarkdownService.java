/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
package org.labkey.api.markdown;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

/**
 * Utility service to convert Markdown-formatted text to HTML
 */
public interface MarkdownService
{
    static @Nullable MarkdownService get()
    {
        return ServiceRegistry.get().getService(MarkdownService.class);
    }

    static void setInstance(MarkdownService impl)
    {
        ServiceRegistry.get().registerService(MarkdownService.class, impl);
    }

    /**
     * @return the html string that will render the content described by the Markdown text of the input string
     */
    String toHtml(String mdText);
}
