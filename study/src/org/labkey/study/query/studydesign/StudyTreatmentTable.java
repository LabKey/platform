/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.study.query.studydesign;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.wiki.WikiRendererDisplayColumn;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * User: cnathe
 * Date: 12/17/13
 */
public class StudyTreatmentTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Label"));
        defaultVisibleColumns.add(FieldKey.fromParts("Description"));
        defaultVisibleColumns.add(FieldKey.fromParts("DescriptionRendererType"));
    }

    public static StudyTreatmentTable create(Domain domain, UserSchema schema, @Nullable ContainerFilter filter)
    {
        TableInfo storageTableInfo = StorageProvisioner.createTableInfo(domain);
        if (null == storageTableInfo)
        {
            throw new IllegalStateException("Could not create provisioned table for domain: " + domain.getTypeURI());
        }
        return new StudyTreatmentTable(domain, storageTableInfo, schema, filter);
    }


    private StudyTreatmentTable(Domain domain, TableInfo storageTableInfo, UserSchema schema, @Nullable ContainerFilter filter)
    {
        super(domain, storageTableInfo, schema, filter);

        setName(StudyQuerySchema.TREATMENT_TABLE_NAME);
        setDescription("Contains one row per study treatment");
    }

    @Override
    protected void initColumn(ColumnInfo col)
    {
        if ("Description".equalsIgnoreCase(col.getName()))
        {
            col.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new WikiRendererDisplayColumn(colInfo, "DescriptionRendererType", WikiRendererType.TEXT_WITH_LINKS);
                }
            });
        }
        else if ("DescriptionRendererType".equalsIgnoreCase(col.getName()))
        {
            col.setFk(new LookupForeignKey("Value")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), WikiService.SCHEMA_NAME).getTable(WikiService.RENDERER_TYPE_TABLE_NAME);
                }
            });
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
