/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.cbcassay;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

public class CBCAssayManager
{
    private static final CBCAssayManager _instance = new CBCAssayManager();

    private CBCAssayManager()
    {
        // prevent external construction with a private default constructor
    }

    public static CBCAssayManager get()
    {
        return _instance;
    }

    public CBCAssayProvider getProvider()
    {
        return (CBCAssayProvider) AssayService.get().getProvider(CBCAssayProvider.NAME);
    }

    public TableInfo getDataTable(User user, Container container, ExpProtocol protocol)
    {
        AssaySchema schema = AssayService.get().createSchema(user, container);
        return getProvider().createDataTable(schema, protocol);
    }
}