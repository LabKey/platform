/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.writer.AbstractContext;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:48:59 PM
 */
public class ImportContext extends AbstractContext
{
    private final ActionURL _url;

    public ImportContext(User user, Container c, StudyDocument studyDoc, ActionURL url)
    {
        super(user, c, studyDoc);
        _url = url;
    }

    @Deprecated
    // TODO: All importers should new up ActionURLs using getContainer() 
    public ActionURL getUrl()
    {
        return _url;
    }
}
