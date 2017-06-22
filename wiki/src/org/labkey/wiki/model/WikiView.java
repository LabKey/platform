/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.wiki.model;

/**
 * User: adam
 * Date: Aug 11, 2007
 * Time: 3:30:42 PM
 */
public class WikiView extends BaseWikiView
{
    public WikiView(Wiki wiki, WikiVersion wikiversion, boolean hasContent)
    {
        super();
        this.wiki = wiki;
        this.wikiVersion = wikiversion;
        this.hasContent = hasContent;

        init(getViewContext().getContainer(), wiki.getName());

        // For the webpart version, see sibling class WikiWebPart
        setIsWebPart(false);
    }
}
