/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.StudyService;
import org.labkey.study.CohortForeignKey;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;
import org.labkey.api.security.permissions.EditSpecimenDataPermission;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class SpecimenDetailTable extends AbstractSpecimenTable
{
    public static final String GLOBAL_UNIQUE_ID_COLUMN_NAME = "GlobalUniqueId";

    public SpecimenDetailTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDetail());

        ColumnInfo guid = addWrapColumn(_rootTable.getColumn(GLOBAL_UNIQUE_ID_COLUMN_NAME));
        guid.setDisplayColumnFactory(ColumnInfo.NOWRAP_FACTORY);

        ColumnInfo pvColumn = new AliasedColumn(this, StudyService.get().getSubjectVisitColumnName(schema.getContainer()),
                _rootTable.getColumn("ParticipantSequenceNum"));//addWrapColumn(baseColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantSequenceNum")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_userSchema, false);
            }
        });
        pvColumn.setIsUnselectable(true);
        pvColumn.setUserEditable(false);
        addColumn(pvColumn);

        addSpecimenVisitColumn(_userSchema.getStudy().getTimepointType());
        addWrapColumn(_rootTable.getColumn("Volume"));
        addSpecimenTypeColumns();
        addWrapColumn(_rootTable.getColumn("PrimaryVolume"));
        addWrapColumn(_rootTable.getColumn("PrimaryVolumeUnits"));
        addWrapColumn(_rootTable.getColumn("TotalCellCount"));
        addWrapColumn(_rootTable.getColumn("TubeType"));

        ColumnInfo specimenComment = createSpecimenCommentColumn(_userSchema, true);
        specimenComment.setName("Comments");
        specimenComment.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SpecimenCommentDisplayColumn(colInfo);
            }
        });
        addColumn(specimenComment);

        boolean enableSpecimenRequest = SampleManager.getInstance().getRepositorySettings(getContainer()).isEnableRequests();
        addWrapColumn(_rootTable.getColumn("LockedInRequest")).setHidden(!enableSpecimenRequest);
        addWrapColumn(_rootTable.getColumn("Requestable")).setHidden(!enableSpecimenRequest);

        ColumnInfo siteNameColumn = wrapColumn("SiteName", getRealTable().getColumn("CurrentLocation"));
        siteNameColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new LocationTable(_userSchema);
            }
        });
        siteNameColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SiteNameDisplayColumn(colInfo);
            }
        });
        addColumn(siteNameColumn);

        ColumnInfo siteLdmsCodeColumn = wrapColumn("SiteLdmsCode", getRealTable().getColumn("CurrentLocation"));
        siteLdmsCodeColumn.setFk(new LookupForeignKey("RowId", "LdmsLabCode")
        {
            public TableInfo getLookupTableInfo()
            {
                return new LocationTable(_userSchema);
            }
        });
        siteLdmsCodeColumn.setUserEditable(false);
        addColumn(siteLdmsCodeColumn);
        addWrapColumn(_rootTable.getColumn("AtRepository"));

        ColumnInfo availableColumn = wrapColumn("Available", getRealTable().getColumn("Available"));
        // Don't setKeyField. Use addQueryFieldKeys where needed
        addColumn(availableColumn).setHidden(!enableSpecimenRequest);
        addWrapColumn(_rootTable.getColumn("AvailabilityReason")).setHidden(!enableSpecimenRequest);

        addColumn(new QualityControlFlagColumn(this));
        addColumn(new QualityControlCommentsColumn(this));

        addColumn(createCollectionCohortColumn(_userSchema, this));

        addWrapColumn(_rootTable.getColumn("VialCount"));
        addWrapColumn(_rootTable.getColumn("LockedInRequestCount")).setHidden(!enableSpecimenRequest);
        addWrapColumn(_rootTable.getColumn("AtRepositoryCount"));
        addWrapColumn(_rootTable.getColumn("AvailableCount")).setHidden(!enableSpecimenRequest);
        addWrapColumn(_rootTable.getColumn("ExpectedAvailableCount")).setHidden(!enableSpecimenRequest);

        setDefaultVisibleColumns(QueryService.get().getDefaultVisibleColumns(getColumns()));

        // the old vial comments column
        boolean joinCommentsToSpecimens = true;
        addVialCommentsColumn(joinCommentsToSpecimens);

        addWrapColumn(_rootTable.getColumn("LatestComments"));
        addWrapColumn(_rootTable.getColumn("LatestQualityComments"));
        addWrapColumn(_rootTable.getColumn("LatestDeviationCode1"));
        addWrapColumn(_rootTable.getColumn("LatestDeviationCode2"));
        addWrapColumn(_rootTable.getColumn("LatestDeviationCode3"));
        addWrapColumn(_rootTable.getColumn("LatestConcentration"));
        addWrapColumn(_rootTable.getColumn("LatestIntegrity"));
        addWrapColumn(_rootTable.getColumn("LatestRatio"));
        addWrapColumn(_rootTable.getColumn("LatestYield"));

        addWrapColumn(_rootTable.getColumn("Freezer"));
        addWrapColumn(_rootTable.getColumn("Fr_container"));
        addWrapColumn(_rootTable.getColumn("Fr_position"));
        addWrapColumn(_rootTable.getColumn("Fr_level1"));
        addWrapColumn(_rootTable.getColumn("Fr_level2"));
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null)
        {
            // Resolve 'ParticipantSequenceKey' to 'ParticipantSequenceNum' for compatibility with versions <12.2.
            if ("ParticipantSequenceKey".equalsIgnoreCase(name))
                return getColumn("ParticipantSequenceNum");
        }

        return result;
    }

    public static class QualityControlColumn extends ExprColumn
    {
        protected static final String QUALITY_CONTROL_JOIN = "QualityControlJoin$";

        public QualityControlColumn(TableInfo parent, String name, SQLFragment sql, JdbcType sqltype)
        {
            super(parent, name, sql, sqltype);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + QUALITY_CONTROL_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoSpecimenComment()).append(" AS ");
            joinSql.append(tableAlias).append(" ON ");
            joinSql.append(parentAlias).append(".GlobalUniqueId = ").append(tableAlias).append(".GlobalUniqueId AND ");
            joinSql.append(tableAlias).append(".Container = ").append(parentAlias).append(".Container");

            map.put(tableAlias, joinSql);
        }
    }


    ColumnInfo createCollectionCohortColumn(StudyQuerySchema schema, TableInfo parent)
    {
        if (!StudyManager.getInstance().showCohorts(getContainer(), schema.getUser()))
        {
            ColumnInfo c = new NullColumnInfo(parent, "CollectionCohort", JdbcType.INTEGER);
            c.setFk(new CohortForeignKey(schema, false, c.getLabel()));
            return c;
        }

        return new CollectionCohortColumn(_userSchema, this);
    }


    public static class CollectionCohortColumn extends ExprColumn
    {
        protected static final String COLLECTION_COHORT_JOIN = "CollectionCohortJoin$";

        public CollectionCohortColumn(final StudyQuerySchema schema, TableInfo parent)
        {
            super(parent, "CollectionCohort",
                    new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$" + COLLECTION_COHORT_JOIN + ".CohortId"),
                    JdbcType.INTEGER);

            setFk(new CohortForeignKey(schema, true, this.getLabel()));
            setDescription("The cohort of the " + StudyService.get().getSubjectNounSingular(schema.getContainer()) + " at the time of specimen collection.");
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + COLLECTION_COHORT_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoParticipantVisit()).append(" AS ");
            joinSql.append(tableAlias).append(" ON ");
            joinSql.append(parentAlias).append(".ParticipantSequenceNum = ").append(tableAlias).append(".ParticipantSequenceNum");
            joinSql.append(" AND ").append(parentAlias).append(".Container = ").append(tableAlias).append(".Container");

            map.put(tableAlias, joinSql);
        }
    }

    public static class QualityControlFlagColumn extends QualityControlColumn
    {
        public QualityControlFlagColumn(BaseStudyTable parent)
        {
            super(parent,
                    "QualityControlFlag",
                    new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + "$" + QUALITY_CONTROL_JOIN + ".QualityControlFlag = ? THEN ? ELSE ? END)", Boolean.TRUE, Boolean.TRUE, Boolean.FALSE),
                    JdbcType.BOOLEAN);
            // our column wrapping is too complex for the description to propagate through- set it here:
            setDescription("Whether this comment is associated with a quality control alert.");
        }
    }

    public static class QualityControlCommentsColumn extends QualityControlColumn
    {
        public QualityControlCommentsColumn(BaseStudyTable parent)
        {
            super(parent,
                    "QualityControlComments",
                    new SQLFragment("(" + ExprColumn.STR_TABLE_ALIAS + "$" + QUALITY_CONTROL_JOIN + ".QualityControlComments)"),
                    JdbcType.VARCHAR);
            // our column wrapping is too complex for the description to propagate through- set it here:
            setDescription("Quality control-associated comments.  Set by the system to indicate which fields are causing quality control alerts.");
        }
    }

    public static class SiteNameDisplayColumn extends DataColumn
    {
        private static final String NO_SITE_DISPLAY_VALUE = "In Transit";
        public SiteNameDisplayColumn(ColumnInfo siteColumn)
        {
            super(siteColumn);
        }

        private ColumnInfo getInRequestColumn()
        {
            FieldKey me = getBoundColumn().getFieldKey();
            FieldKey inRequestKey = new FieldKey(me.getParent(), "LockedInRequest");
            Map<FieldKey, ColumnInfo> requiredColumns = QueryService.get().getColumns(getBoundColumn().getParentTable(), Collections.singleton(inRequestKey));
            return requiredColumns.get(inRequestKey);
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            ColumnInfo inRequestCol = getInRequestColumn();
            if (inRequestCol != null)
                columns.add(inRequestCol);
        }

        private String getNoSiteText(RenderContext ctx)
        {
            ColumnInfo inRequestColumn = getInRequestColumn();
            boolean requested = false;
            if (inRequestColumn != null)
            {
                Object inRequest = inRequestColumn.getValue(ctx);
                requested = (inRequest instanceof Boolean && ((Boolean) inRequest).booleanValue()) ||
                    (inRequest instanceof Integer && ((Integer) inRequest).intValue() == 1);
                return NO_SITE_DISPLAY_VALUE + (requested ? ": Requested" : "");
            }
            else
                return NO_SITE_DISPLAY_VALUE + ": Request status unknown";
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getBoundColumn().getValue(ctx);
            if (value == null)
                return getNoSiteText(ctx);
            else
                return super.getDisplayValue(ctx);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getBoundColumn().getValue(ctx);
            if (value == null)
                out.write(getNoSiteText(ctx));
            else
                super.renderGridCellContents(ctx, out);
        }
    }

    public void changeRequestableColumn()
    {   // TODO: finish fixing bug    (couple of options here)
//        ColumnInfo requestableColumn = getColumn("requestable");
//        SQLFragment sql = new SQLFragment("(CASE WHEN requestable IS NULL THEN 'Null' WHEN requestable = TRUE THEN 'True' ELSE 'False' END) AS RequestableSetting");
//        ColumnInfo newRequestableColumn = new ExprColumn(this, "RequestableSetting", sql, JdbcType.VARCHAR, requestableColumn);
//        requestableColumn.setUserEditable(false);
//        requestableColumn.setHidden(true);

    //    SQLFragment sql = new SQLFragment("CASE WHEN requestable IS NULL THEN ? ELSE ? END");
//        sql.add(getSchema().getSqlDialect().getBooleanTRUE());
//        sql.add(getSchema().getSqlDialect().getBooleanFALSE());
//        ColumnInfo newRequestableColumn = new ExprColumn(this, "RequestableNull", sql, JdbcType.BOOLEAN, requestableColumn);
    //    addColumn(newRequestableColumn);
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm) &&
               getContainer().hasPermission(user, EditSpecimenDataPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        if (_userSchema.getStudy().getRepositorySettings().isSpecimenDataEditable())
            return new SpecimenUpdateService(this);
        return null;
    }
}
