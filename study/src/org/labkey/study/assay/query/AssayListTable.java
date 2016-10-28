/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.study.assay.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.controllers.assay.AssayController;

import java.util.*;

/**
 * User: brittp
 * Date: Jun 28, 2007
 */
public class AssayListTable extends FilteredTable<AssaySchemaImpl>
{
    public AssayListTable(AssaySchemaImpl schema)
    {
        super(ExperimentService.get().getTinfoProtocol(), schema, new ContainerFilter.WorkbookAssay(schema.getUser()));
        setDescription("Contains all of the assay definitions visible in this folder");
        addCondition(_rootTable.getColumn("ApplicationType"), "ExperimentRun");
        setName(AssaySchema.ASSAY_LIST_TABLE_NAME);
        setTitleColumn("Name");

        ActionURL url = new ActionURL(AssayController.AssayBeginAction.class, _userSchema.getContainer());
        DetailsURL detailsURL = new DetailsURL(url, "rowId", FieldKey.fromParts("RowId"));
        // Don't let our context be stomped over by the one using the Container/Folder column. We want to stay
        // in the current container, even when the protocol is defined in another container.
        detailsURL.setContainerContext(_userSchema.getContainer());

        addWrapColumn(_rootTable.getColumn("RowId")).setHidden(true);
        ColumnInfo nameCol = addWrapColumn(_rootTable.getColumn("Name"));
        nameCol.setURL(detailsURL);

        ColumnInfo desc = wrapColumn("Description", _rootTable.getColumn("ProtocolDescription"));
        addColumn(desc);

        addWrapColumn(_rootTable.getColumn("Created"));
        addWrapColumn(_rootTable.getColumn("CreatedBy"));
        addWrapColumn(_rootTable.getColumn("Modified"));
        addWrapColumn(_rootTable.getColumn("ModifiedBy"));
        ColumnInfo folderCol = wrapColumn("Folder", _rootTable.getColumn("Container"));
        addColumn(ContainerForeignKey.initColumn(folderCol, schema));
        
        ColumnInfo lsidColumn = addWrapColumn(_rootTable.getColumn("LSID"));
        lsidColumn.setHidden(true);

        // Generate a CASE statement that matches an LSID to an AssayProvider so we can create a lookup to the AssayProviderTable.
        // The column value is null if no AssayProvider is matched.
        SQLFragment typeFrag = new SQLFragment();
        typeFrag.append("(CASE");
        for (AssayProvider provider : AssayService.get().getAssayProviders())
        {
            String protocolPattern = provider.getProtocolPattern();
            if (protocolPattern != null)
            {
                typeFrag.append("\n");
                typeFrag.append("WHEN ").append(ExprColumn.STR_TABLE_ALIAS).append(".LSID LIKE ? THEN ?");
                typeFrag.add(protocolPattern);
                typeFrag.add(provider.getName());
            }
        }
        typeFrag.append(" END)");
        ColumnInfo typeColumn = new ExprColumn(this, "Type", typeFrag, JdbcType.VARCHAR);
        typeColumn.setFk(new QueryForeignKey(getUserSchema(), null, AssaySchema.ASSAY_PROVIDERS_TABLE_NAME, "Name", "Name"));
        typeColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new TypeDisplayColumn(colInfo);
            }
        });
        addColumn(typeColumn);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts("Name"));
        defaultCols.add(FieldKey.fromParts("Description"));
        defaultCols.add(FieldKey.fromParts("Type"));
        setDefaultVisibleColumns(defaultCols);

        // TODO - this is a horrible way to filter out non-assay protocols
        addCondition(new SQLFragment("(SELECT MAX(pd.PropertyId) from exp.object o, exp.objectproperty op, exp.propertydescriptor pd where pd.propertyid = op.propertyid and op.objectid = o.objectid and o.objecturi = lsid AND pd.PropertyURI LIKE '%AssayDomain-Run%') IS NOT NULL"));

        setDetailsURL(detailsURL);
    }

    @Override
    public boolean hasDefaultContainerFilter()
    {
        return getContainerFilter() instanceof ContainerFilter.WorkbookAssay;
    }

    @Override
    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        if (hasDefaultContainerFilter())
        {
            filter = new UnionContainerFilter(filter, getContainerFilter());
        }
        super.setContainerFilter(filter);
    }

    @Override
    public String getPublicSchemaName()
    {
        return AssaySchema.NAME;
    }
}
