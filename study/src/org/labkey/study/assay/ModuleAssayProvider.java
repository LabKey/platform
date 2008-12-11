/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.assay;

import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayPublishKey;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.Map;
import java.util.List;
import java.io.File;

/**
 * User: kevink
 * Date: Dec 10, 2008 2:20:38 PM
 */
public class ModuleAssayProvider extends AbstractAssayProvider
{
    private String name;

    public ModuleAssayProvider(String name)
    {
        super(name + "Protocol", name + "Run", TsvDataHandler.DATA_TYPE);
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public ExpData getDataForDataRow(Object dataRowId)
    {
        return null;
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return null;
    }

    public TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol)
    {
        return null;
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return null;
    }

    public FieldKey getVisitIDFieldKey(Container targetStudy)
    {
        return null;
    }

    public FieldKey getRunIdFieldKeyFromDataRow()
    {
        return null;
    }

    public FieldKey getDataRowIdFieldKey()
    {
        return null;
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return null;
    }

    public ActionURL publish(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        return null;
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return null;
    }
}
