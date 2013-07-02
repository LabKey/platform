/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 9:23 AM
 */
public interface LaboratoryUrls extends UrlProvider
{
    public ActionURL getSearchUrl(Container c, String schemaName, String queryName);

    public ActionURL getImportUrl(Container c, User u, String schemaName, String queryName);

    public ActionURL getAssayRunTemplateUrl(Container c, ExpProtocol protocol);

    public ActionURL getViewAssayRunTemplateUrl(Container c, User u, ExpProtocol protocol);
}
