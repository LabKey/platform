/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.wiki;

/**
 * Something that knows how to render "raw" wiki content into HTML that could be passed directly to a web browser.
 * User: Tamra Myers
 * Date: Aug 16, 2006
 */
public interface WikiRenderer
{
    /**
     * @param text the original text that should be rendered into HTML
     * @param sourceDescription info on where the text came from for debugging purposes. For example: Announcement 6654 in /MyContainer
     */
    FormattedHtml format(String text, String sourceDescription);
}
