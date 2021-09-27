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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.WikiRendererDisplayColumn;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jgarms
 * Date: Aug 7, 2008
 * Time: 4:23:44 PM
 */
public class StudyPropertiesTable extends BaseStudyTable
{
    private Domain _domain;
    private List<FieldKey> _visibleColumns = new ArrayList<>();

    public StudyPropertiesTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudy(), cf);

        Container c = schema.getContainer();

        var labelColumn = addRootColumn("label", true, true);
        DetailsURL detailsURL = new DetailsURL(PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c));
        labelColumn.setURL(detailsURL);
        addRootColumn("startDate", true, true);
        addRootColumn("endDate", true, true);

        var containerColumn = addRootColumn("container", false, false);
        containerColumn.setFk(new ContainerForeignKey(schema));
        containerColumn.setKeyField(true);

        var timepointTypeColumn = addRootColumn("timepointType", false, false);
        addRootColumn("subjectNounSingular", false, true);
        addRootColumn("subjectNounPlural", false, true);
        addRootColumn("subjectColumnName", false, true);
        addRootColumn("grant", true, true);
        addRootColumn("investigator", true, true);
        addRootColumn("species", true, true);
        addRootColumn("participantAliasDatasetId", true, true);
        addRootColumn("participantAliasProperty", true, true);
        addRootColumn("participantAliasSourceProperty", true, true);
        addRootColumn("assayPlan", true, true);
        var descriptionColumn = addRootColumn("description", true, true);
        final var descriptionRendererTypeColumn = addRootColumn("descriptionRendererType", false, true);

        WikiService ws = WikiService.get();

        if (null != ws)
        {
            descriptionRendererTypeColumn.setFk(new LookupForeignKey("Value")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return ws.getRendererTypeTable(_userSchema.getUser(), _userSchema.getContainer());
                }
            });
        }

        descriptionColumn.setDisplayColumnFactory(colInfo -> new WikiRendererDisplayColumn(colInfo, descriptionRendererTypeColumn.getName(), WikiRendererType.TEXT_WITH_LINKS));

        String bTRUE = getSchema().getSqlDialect().getBooleanTRUE();
        String bFALSE = getSchema().getSqlDialect().getBooleanFALSE();

        var dateBasedColumn = new ExprColumn(this, "DateBased", new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".timepointType != 'VISIT' THEN " + bTRUE + " ELSE " + bFALSE + " END)"), JdbcType.BOOLEAN, timepointTypeColumn);
        dateBasedColumn.setUserEditable(false);
        dateBasedColumn.setHidden(true);
        dateBasedColumn.setDescription("Deprecated. Use 'timepointType' column instead.");
        addColumn(dateBasedColumn);

        var lsidColumn = addRootColumn("LSID", false, false);
        lsidColumn.setHidden(true);

        String domainURI = StudyImpl.DOMAIN_INFO.getDomainURI(c);
        _domain = PropertyService.get().getDomain(c, domainURI);

        if (null == _domain)
        {
            _domain = PropertyService.get().createDomain(c, domainURI, StudyImpl.DOMAIN_INFO.getDomainName());

            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                // Don't save the domain if we're in the root. We want to allow cross-folder queries of this table from the
                // root, but we won't show any custom properties in this case, since they're defined in each project. #20090
                if (!c.isRoot())
                    _domain.save(schema.getUser());
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            for (ColumnInfo extraColumn : _domain.getColumns(this, lsidColumn, c, schema.getUser()))
            {
                safeAddColumn( (BaseColumnInfo) extraColumn);
                _visibleColumns.add(FieldKey.fromParts(extraColumn.getName()));
            }
        }

        setDefaultVisibleColumns(_visibleColumns);

        // disable import data link for this table
        setImportURL(AbstractTableInfo.LINK_DISABLER);
    }

    @Override
    public String getPublicName()
    {
        // Issue 43988 - need to use the preferred query schema name for the table to make linked schemas and other
        // metadata-driven scenarios consistent
        return StudyQuerySchema.PROPERTIES_TABLE_NAME;
    }

    private MutableColumnInfo addRootColumn(String columnName, boolean visible, boolean userEditable)
    {
        var columnInfo = addWrapColumn(_rootTable.getColumn(columnName));
        columnInfo.setUserEditable(userEditable);
        if (visible)
            _visibleColumns.add(columnInfo.getFieldKey());
        return columnInfo;
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return hasPermissionOverridable(user, perm);
    }

    @Override
    public boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        if (UpdatePermission.class == perm || InsertPermission.class == perm || ReadPermission.class.equals(perm))
            return canReadOrIsAdminPermission(user, perm);
        return false;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _userSchema.getUser();
        if (!getContainer().hasPermission(user, AdminPermission.class))
            return null;
        return new StudyPropertiesUpdateService(this);
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return FieldKey.fromParts("Container");
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return true;
    }
}
