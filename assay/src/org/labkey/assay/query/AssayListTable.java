/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.UnionContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.assay.AssayController;

import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: Jun 28, 2007
 */
public class AssayListTable extends FilteredTable<AssaySchemaImpl>
{
    public AssayListTable(AssaySchemaImpl schema)
    {
        super(ExperimentService.get().getTinfoProtocol(), schema, new ContainerFilter.AssayLocation(schema.getContainer(), schema.getUser()));
        setDescription("Contains all of the assay definitions visible in this folder");
        addCondition(_rootTable.getColumn("ApplicationType"), ExpProtocol.ApplicationType.ExperimentRun.toString());
        setName(AssaySchema.ASSAY_LIST_TABLE_NAME);
        setTitleColumn("Name");

        ActionURL url = new ActionURL(AssayController.AssayBeginAction.class, _userSchema.getContainer());
        DetailsURL detailsURL = new DetailsURL(url, "rowId", FieldKey.fromParts("RowId"));
        // Don't let our context be stomped over by the one using the Container/Folder column. We want to stay
        // in the current container, even when the protocol is defined in another container.
        detailsURL.setContainerContext(_userSchema.getContainer());

        addWrapColumn(_rootTable.getColumn("RowId")).setHidden(true);
        var nameCol = addWrapColumn(_rootTable.getColumn("Name"));
        nameCol.setURL(detailsURL);

        var desc = wrapColumn("Description", _rootTable.getColumn("ProtocolDescription"));
        addColumn(desc);

        addWrapColumn(_rootTable.getColumn("Created"));
        addWrapColumn(_rootTable.getColumn("CreatedBy"));
        addWrapColumn(_rootTable.getColumn("Modified"));
        addWrapColumn(_rootTable.getColumn("ModifiedBy"));
        addWrapColumn("Folder", _rootTable.getColumn("Container"));

        var lsidColumn = addWrapColumn(_rootTable.getColumn("LSID"));
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
        var typeColumn = new ExprColumn(this, "Type", typeFrag, JdbcType.VARCHAR);
        typeColumn.setFk(QueryForeignKey.from(getUserSchema(),null).to(AssaySchema.ASSAY_PROVIDERS_TABLE_NAME, "Name", "Name"));
        typeColumn.setDisplayColumnFactory(TypeDisplayColumn::new);
        addColumn(typeColumn);

        addWrapColumn(_rootTable.getColumn("Status")).setHidden(true);

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
        return getContainerFilter() instanceof ContainerFilter.AssayLocation;
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
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (perm == ReadPermission.class)
            return super.hasPermission(user, AssayReadPermission.class);

        return super.hasPermission(user, perm);
    }
}
