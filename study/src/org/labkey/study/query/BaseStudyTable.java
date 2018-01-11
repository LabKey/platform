/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DelegatingContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class BaseStudyTable extends FilteredTable<StudyQuerySchema>
{
    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable)
    {
        this(schema, realTable, false);
    }

    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable, boolean includeSourceStudyData)
    {
        this(schema, realTable, includeSourceStudyData, false);
    }


    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable, boolean includeSourceStudyData, boolean skipPermissionChecks)
    {
        super(realTable, schema);

        if (includeSourceStudyData && null != schema._study && !schema._study.isDataspaceStudy())
            _setContainerFilter(new ContainerFilter.StudyAndSourceStudy(schema.getUser(), skipPermissionChecks));
        else
            _setContainerFilter(schema.getDefaultContainerFilter());

        if (!includeSourceStudyData && skipPermissionChecks)
            throw new IllegalArgumentException("Skipping permission checks only applies when including source study data");
        if (includeSourceStudyData && getParticipantColumnName() != null)
        {
            // If we're in an ancillary study, show the parent folder's specimens, but filter to include only those
            // that relate to subjects in the ancillary study.  This filter will have no effect on samples uploaded
            // directly in this this study folder (since all local specimens should have an associated subject ID
            // already in the participant table.
            StudyImpl currentStudy = StudyManager.getInstance().getStudy(schema.getContainer());
            if (currentStudy != null && currentStudy.isAncillaryStudy())
            {
                List<String> ptids = ParticipantGroupManager.getInstance().getAllGroupedParticipants(schema.getContainer());
                if (!ptids.isEmpty())
                {
                    StudyImpl sourceStudy = currentStudy.getSourceStudy();
                    if ("specimentables".equalsIgnoreCase(getRealTable().getSchema().getName()) ||
                            StudyQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME.equalsIgnoreCase(getName()) ||
                            StudyQuerySchema.SPECIMEN_SUMMARY_TABLE_NAME.equalsIgnoreCase(getName()) ||
                            StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME.equalsIgnoreCase(getName()) ||
                            StudyQuerySchema.SPECIMEN_DETAIL_TABLE_NAME.equalsIgnoreCase(getName()))
                    {
                        // Do nothing
                        int i = 1;
                    }
                    else
                    {   // TODO: are there cases here?
                        SQLFragment condition = new SQLFragment("(Container = ? AND " + getParticipantColumnName() + " ");
                        condition.add(sourceStudy.getContainer());
                        getSqlDialect().appendInClauseSql(condition, ptids);
                        condition.append(") OR Container = ?");
                        condition.add(currentStudy.getContainer());
                        addCondition(condition, FieldKey.fromParts("Container"), FieldKey.fromParts(getParticipantColumnName()));
                    }

                }
            }
        }
    }


    protected String getParticipantColumnName()
    {
        return null;
    }


    protected ColumnInfo addWrapParticipantColumn(String rootTableColumnName)
    {
        final String subjectColName = StudyService.get().getSubjectColumnName(getContainer());
        final String subjectTableName = StudyService.get().getSubjectTableName(getContainer());

        ColumnInfo participantColumn =
                new AliasedColumn(this, subjectColName, _rootTable.getColumn(rootTableColumnName));
        LookupForeignKey lfk = new LookupForeignKey(subjectTableName, subjectColName, null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.getTable(subjectTableName);
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                TableInfo table = getLookupTableInfo();
                if (table == null)
                    return null;
                return LookupForeignKey.getDetailsURL(parent, table, _columnName);
            }
        };
        lfk.addJoin(FieldKey.fromParts("Container"), "Container", false);
        participantColumn.setFk(lfk);

        // Don't setKeyField. Use addQueryFieldKeys where needed

        if (DemoMode.isDemoMode(_userSchema.getContainer(), _userSchema.getUser()))
        {
            participantColumn.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo column)
                {
                    return new PtidObfuscatingDisplayColumn(column);
                }
            });
        }

        return addColumn(participantColumn);
    }


    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        // The name of the subject ID column can now be customized by study.  Since there are some joins
        // from the assay side which join to cross-study data, we need a single column name that works
        // in all containers.  This is 'ParticipantId', since this was the column name before
        // customization was possible:
        if ("ParticipantId".equalsIgnoreCase(name))
        {
            String alt = StudyService.get().getSubjectColumnName(getContainer());
            if (!"ParticipantId".equalsIgnoreCase(alt))
                return getColumn(alt);
        }
        return super.resolveColumn(name);
    }


    protected ColumnInfo addWrapLocationColumn(String wrappedName, String rootTableColumnName)
    {
        ColumnInfo locationColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        locationColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                LocationTable result = new LocationTable(_userSchema);
                if (_userSchema.allowSetContainerFilter())
                    result.setContainerFilter(new DelegatingContainerFilter(BaseStudyTable.this));
                return result;
            }
        });
        return addColumn(locationColumn);
    }

    protected ColumnInfo addContainerColumn()
    {
        return  addContainerColumn(false);
    }

    protected ColumnInfo addContainerColumn(boolean isProvisioned)
    {
        ColumnInfo containerCol;
        if (isProvisioned)
        {
            SQLFragment sql = new SQLFragment("CAST (");
            sql.append(getSqlDialect().getStringHandler().quoteStringLiteral(getContainer().getId()));
            sql.append(" AS ").append(getSchema().getSqlDialect().getGuidType()).append(")");
            containerCol = new ExprColumn(this, "Container", sql, JdbcType.GUID);
        }
        else
        {
            containerCol = new AliasedColumn(this, "Container", _rootTable.getColumn("Container"));
        }
        containerCol = ContainerForeignKey.initColumn(containerCol, _userSchema);
        containerCol.setHidden(true);
        return addColumn(containerCol);
    }

    protected ColumnInfo addWrapTypeColumn(String wrappedName, final String rootTableColumnName)
    {
        ColumnInfo typeColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        LookupForeignKey fk = new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                BaseStudyTable result;
                if (rootTableColumnName.equals("PrimaryTypeId"))
                    result = new PrimaryTypeTable(_userSchema);
                else if (rootTableColumnName.equals("DerivativeTypeId") || rootTableColumnName.equals("DerivativeTypeId2"))
                    result = new DerivativeTypeTable(_userSchema);
                else if (rootTableColumnName.equals("AdditiveTypeId"))
                    result = new AdditiveTypeTable(_userSchema);
                else
                    throw new IllegalStateException(rootTableColumnName + " is not recognized as a valid specimen type column.");
                if (_userSchema.allowSetContainerFilter())
                    result.setContainerFilter(new DelegatingContainerFilter(BaseStudyTable.this));
                return result;
            }
        };
        typeColumn.setFk(fk);

        return addColumn(typeColumn);
    }


    protected ColumnInfo addSpecimenVisitColumn(TimepointType timepointType, boolean isProvisioned)
    {
        ColumnInfo aliasVisitColumn = new AliasedColumn(this, "SequenceNum", _rootTable.getColumn("VisitValue"));
        return addSpecimenVisitColumn(timepointType, aliasVisitColumn, isProvisioned);
    }

    protected ColumnInfo addSpecimenVisitColumn(TimepointType timepointType, ColumnInfo aliasVisitColumn, boolean isProvisioned)
    {
        ColumnInfo visitColumn = null;
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));

        // add the sequenceNum column so we have it for later queries
        // Make it visible by default since it's useful in scenarios like specimen lookups from assay data
        addColumn(aliasVisitColumn);

        if (timepointType == TimepointType.DATE || timepointType == TimepointType.CONTINUOUS)
        {
            //consider:  use SequenceNumMin for visit-based studies too (in visit-based studies VisitValue == SequenceNumMin)
            // could change to visitrowid but that changes datatype and displays rowid
            // instead of sequencenum when label is null
            visitColumn = addColumn(new ParticipantVisitColumn(this, isProvisioned ? getContainer() : null));
            visitColumn.setLabel("Timepoint");

            visitDescriptionColumn.setHidden(true);
        }
        else    // if (timepointType == TimepointType.VISIT)     // only other choice
        {
            visitColumn = addColumn(new ParticipantVisitColumn(this, isProvisioned ? getContainer() : null));
        }

        LookupForeignKey visitFK = new LookupForeignKey(null, (String) null, "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                VisitTable visitTable = new VisitTable(_userSchema);
                if (_userSchema.allowSetContainerFilter())
                    visitTable.setContainerFilter(new DelegatingContainerFilter(BaseStudyTable.this));
                return visitTable;
            }
        };
        visitFK.addJoin(FieldKey.fromParts("Container"), "Folder", false);
        visitColumn.setFk(visitFK);
        // Don't setKeyField. Use addQueryFieldKeys where needed

        visitColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo col)
            {
                return new VisitDisplayColumn(col, FieldKey.fromParts("SequenceNum"));
            }
        });

        return visitColumn;
    }

    public static class VisitDisplayColumn extends DataColumn
    {
        private FieldKey _seqNumMinFieldKey;

        public VisitDisplayColumn(ColumnInfo col, FieldKey seqNumMinFieldKey)
        {
            super(col);
            _seqNumMinFieldKey = seqNumMinFieldKey;
        }

        @Override @NotNull
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = ctx.get(getDisplayColumn().getFieldKey());
            if (value == null)
            {
                value = ctx.get(_seqNumMinFieldKey);

                if (value == null)
                    return super.getFormattedValue(ctx);
            }
            return PageFlowUtil.filter(value);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = super.getDisplayValue(ctx);
            if (value == null)
                value = getFormattedValue(ctx);
            return value;
        }

        @Override
        public String getTsvFormattedValue(RenderContext ctx)
        {
            return getFormattedValue(ctx);
        }

        @Override
        public Object getExcelCompatibleValue(RenderContext ctx)
        {
            return getFormattedValue(ctx);
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_seqNumMinFieldKey);
        }

        @Override
        protected String getHoverContent(RenderContext ctx)
        {
            Study study = StudyManager.getInstance().getStudy(ctx.getContainer());
            if (study != null && ctx.get(getColumnInfo().getAlias()) != null)
            {
                VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, Integer.parseInt(ctx.get(getColumnInfo().getAlias()).toString()));
                if (visit != null && (visit.getDescription() != null || visit.getLabel() != null))
                    return PageFlowUtil.filter(visit.getDescription() != null ? visit.getDescription() : visit.getLabel());
            }

            return null;
        }

        @Override
        protected String getHoverTitle(RenderContext ctx)
        {
            return "Visit Description";
        }
    }

    private static class ParticipantVisitColumn extends ExprColumn
    {
        Container container;

        public ParticipantVisitColumn(TableInfo parent, @Nullable Container container)
        {
            super(parent, "Visit", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$PV" + ".VisitRowId"), JdbcType.INTEGER);
            this.container = container;
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            String pvAlias = parentAlias + "$PV";
            SQLFragment join = new SQLFragment();

            join.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoParticipantVisit(), pvAlias).append(" ON\n");
            join.append(parentAlias).append(".ParticipantSequenceNum = ").append(pvAlias).append(".ParticipantSequenceNum AND\n");
            if (null == container)
                join.append(parentAlias).append(".Container = ").append(pvAlias).append(".Container");
            else
                join.append(getSqlDialect().getStringHandler().quoteStringLiteral(container.getId()))
                        .append(" = ").append(pvAlias).append(".Container");

            map.put(pvAlias, join);
        }
    }


/*
    private static class DateVisitColumn extends ExprColumn
    {
        private static final String DATE_VISIT_JOIN_ALIAS = "DateVisitJoin";
        public DateVisitColumn(TableInfo parent)
        {
            super(parent, "Visit", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$" + DATE_VISIT_JOIN_ALIAS + ".SequenceNumMin"), JdbcType.VARCHAR);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            String pvAlias = parentAlias + "$PV";
            String dateVisitJoinAlias = parentAlias + "$" + DATE_VISIT_JOIN_ALIAS;
            SQLFragment join = new SQLFragment();
            join.append(" LEFT OUTER JOIN " + StudySchema.getInstance().getTableInfoParticipantVisit() + " " + pvAlias + " ON\n" +
                    parentAlias + ".ParticipantSequenceNum = " + pvAlias + ".ParticipantSequenceNum AND\n" +
                    parentAlias + ".Container = " + pvAlias + ".Container\n");
            join.append("LEFT OUTER JOIN " + StudySchema.getInstance().getTableInfoVisit() + " " + dateVisitJoinAlias +
                    " ON " + dateVisitJoinAlias + ".RowId = " + pvAlias + ".VisitRowId");
            map.put(DATE_VISIT_JOIN_ALIAS, join);
        }
    }
*/

    protected void addVialCommentsColumn(final boolean joinBackToSpecimens)
    {
        ColumnInfo commentsColumn = new AliasedColumn(this, "VialComments", _rootTable.getColumn("GlobalUniqueId"));
        LookupForeignKey commentsFK = new LookupForeignKey("GlobalUniqueId")
        {
            public TableInfo getLookupTableInfo()
            {
                SpecimenCommentTable result = new SpecimenCommentTable(_userSchema, joinBackToSpecimens);
                result.setContainerFilter(new DelegatingContainerFilter(BaseStudyTable.this));
                return result;
            }
        };
        commentsFK.addJoin(FieldKey.fromParts("Container"), "Folder", false);
        commentsColumn.setFk(commentsFK);
        commentsColumn.setDescription("");
        commentsColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });
        commentsColumn.setUserEditable(false);
        addColumn(commentsColumn);
    }

    protected void addSpecimenCommentColumns(StudyQuerySchema schema, boolean isSpecimenDetail)
    {
        DatasetDefinition defPtid = null;
        DatasetDefinition defPtidVisit = null;
        final String alias;
        final boolean includeVialComments;
        final boolean hidden;
        if (isSpecimenDetail)
        {
            alias = "Comments";
            includeVialComments = true;
            hidden = false;
        }
        else
        {
            alias = SpecimenCommentColumn.COLUMN_NAME;
            includeVialComments = false;
            hidden = true;
        }

        StudyImpl study = StudyManager.getInstance().getStudy(_userSchema.getContainer());
        if (study.getParticipantCommentDatasetId() != null)
            defPtid = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantCommentDatasetId());
        if (study.getParticipantVisitCommentDatasetId() != null)
            defPtidVisit = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantVisitCommentDatasetId());

        boolean validParticipantCommentTable = defPtid != null && defPtid.canRead(schema.getUser()) && defPtid.isDemographicData();
        TableInfo participantCommentTable = validParticipantCommentTable ? defPtid.getTableInfo(schema.getUser()) : null;
        boolean validParticipantVisitCommentTable = defPtidVisit != null && defPtidVisit.canRead(schema.getUser()) && !defPtidVisit.isDemographicData();
        TableInfo participantVisitCommentTable = validParticipantVisitCommentTable ? defPtidVisit.getTableInfo(schema.getUser()) : null;

        addColumn(new SpecimenCommentColumn(this, participantCommentTable, study.getParticipantCommentProperty(),
            participantVisitCommentTable, study.getParticipantVisitCommentProperty(), alias, includeVialComments, hidden));
    }

    public static class SpecimenCommentColumn extends ExprColumn
    {
        public static final String COLUMN_NAME = "SpecimenComment";
        protected static final String VIAL_COMMENT_ALIAS = "VialComment";
        protected static final String PARTICIPANT_COMMENT_JOIN = "ParticipantCommentJoin$";
        protected static final String PARTICIPANT_COMMENT_ALIAS = "ParticipantComment";
        protected static final String PARTICIPANTVISIT_COMMENT_JOIN = "ParticipantVisitCommentJoin$";
        protected static final String PARTICIPANTVISIT_COMMENT_ALIAS = "ParticipantVisitComment";
        protected static final String SPECIMEN_COMMENT_JOIN = "SpecimenCommentJoin$";

        private final TableInfo _ptidCommentTable;
        private final TableInfo _ptidVisitCommentTable;
        private final boolean _includeVialComments;
        private final Container _container;

        public SpecimenCommentColumn(FilteredTable parent, TableInfo ptidCommentTable, String ptidCommentProperty,
                                     TableInfo ptidVisitCommentTable, String ptidVisitCommentProperty, String name,
                                     boolean includeVialComments, boolean hidden)
        {
            super(parent, name, new SQLFragment(), JdbcType.VARCHAR);
            setHidden(hidden);
            _container = parent.getContainer();
            _ptidCommentTable = ptidCommentTable;
            _ptidVisitCommentTable = ptidVisitCommentTable;
            _includeVialComments = includeVialComments;
            SQLFragment sql = new SQLFragment();
            String ptidCommentAlias = null;
            String ptidVisitCommentAlias = null;

            String subjectNoun = StudyService.get().getSubjectNounSingular(_container);

            if (ptidCommentProperty != null && _ptidCommentTable != null)
            {
                if (_ptidCommentTable.getColumn(ptidCommentProperty) != null)
                    ptidCommentAlias = ColumnInfo.legalNameFromName(ptidCommentProperty);
            }

            if (ptidVisitCommentProperty != null && _ptidVisitCommentTable != null)
            {
                if (_ptidVisitCommentTable.getColumn(ptidVisitCommentProperty) != null)
                    ptidVisitCommentAlias = ColumnInfo.legalNameFromName(ptidVisitCommentProperty);
            }

            List<Pair<String, String>> commentFields = new ArrayList<>();

            if (_includeVialComments)
            {
                String field = ExprColumn.STR_TABLE_ALIAS + "$" + SPECIMEN_COMMENT_JOIN + ".Comment";
                parent.addColumn(new ExprColumn(parent, VIAL_COMMENT_ALIAS, new SQLFragment(field), JdbcType.VARCHAR)
                {
                    @Override
                    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                    {
                        super.declareJoins(parentAlias, map);
                        specimenCommentJoin(parentAlias, map);
                    }
                }).setHidden(true);
                if (getSqlDialect().isSqlServer())
                    field = "CAST((" + field + ") AS VARCHAR(500))";
                commentFields.add(new Pair<>(field, "'Vial: '"));
            }
            if (ptidCommentTable != null && ptidCommentAlias != null)
            {
                String field = ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANT_COMMENT_JOIN + "." + ptidCommentAlias;
                parent.addColumn(new ExprColumn(parent, PARTICIPANT_COMMENT_ALIAS, new SQLFragment(field), JdbcType.VARCHAR)
                {
                    @Override
                    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                    {
                        super.declareJoins(parentAlias, map);
                        participantCommentJoin(parentAlias, map);
                    }
                }).setHidden(true);
                commentFields.add(new Pair<>(field, "'" + subjectNoun + ": '"));
            }
            if (ptidVisitCommentTable != null && ptidVisitCommentAlias != null)
            {
                String field = ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANTVISIT_COMMENT_JOIN + "." + ptidVisitCommentAlias;
                parent.addColumn(new ExprColumn(parent, PARTICIPANTVISIT_COMMENT_ALIAS, new SQLFragment(field), JdbcType.VARCHAR)
                {
                    @Override
                    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                    {
                        super.declareJoins(parentAlias, map);
                        participantVisitCommentJoin(parentAlias, map);
                    }
                }).setHidden(true);
                commentFields.add(new Pair<>(field, "'" + subjectNoun + "/Visit: '"));
            }

            StringBuilder sb = new StringBuilder();
            if (!commentFields.isEmpty())
            {
                switch (commentFields.size())
                {
                    case 1:
                        sb.append("CASE");
                        appendCommentCaseSQL(sb, commentFields);
                        sb.append(" ELSE NULL END");
                        break;
                    case 2:
                        sb.append("CASE");
                        appendCommentCaseSQL(sb, commentFields);
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(0)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(1)));
                        sb.append(" ELSE NULL END");
                        break;
                    case 3:
                        sb.append("CASE");
                        appendCommentCaseSQL(sb, commentFields);
                        appendCommentCaseSQL(sb, Arrays.asList(commentFields.get(0), commentFields.get(1)));
                        appendCommentCaseSQL(sb, Arrays.asList(commentFields.get(0), commentFields.get(2)));
                        appendCommentCaseSQL(sb, Arrays.asList(commentFields.get(1), commentFields.get(2)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(0)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(1)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(2)));
                        sb.append(" ELSE NULL END");
                        break;
                }
            }
            sql.append("(");
            if (sb.length() > 0)
                sql.append(sb.toString());
            else
                sql.append("' '");
            sql.append(")");
            setValueSQL(sql);

            // Address issue #20328. Seems like this should be set automatically, since we're essentially wrapping a column that
            // we know shouldn't be faceted (based on inputType, e.g.), but that information isn't getting propagated.
            setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
        }

        private void appendCommentCaseSQL(StringBuilder sb, List<Pair<String, String>> fields)
        {
            String concat = "";

            sb.append(" WHEN ");
            for (Pair<String, String> field : fields)
            {
                sb.append(concat).append(field.first).append(" IS NOT NULL ");
                concat = "AND ";
            }

            sb.append("THEN ");

            String[] castFields = new String[fields.size()];
            String sep = "''";

            for (int i = 0; i < fields.size(); i++)
            {
                castFields[i] = "CAST((" + getSqlDialect().concatenate(sep, fields.get(i).second, fields.get(i).first) + ") AS VARCHAR" +
                                 (getSqlDialect().isSqlServer() ? "(500)" : "") + ")";
                sep = "', '";
            }

            sb.append(getSqlDialect().concatenate(castFields));
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            if (_includeVialComments)
                specimenCommentJoin(parentAlias, map);

            if (_ptidCommentTable != null)
                participantCommentJoin(parentAlias, map);

            if (_ptidVisitCommentTable != null)
                participantVisitCommentJoin(parentAlias, map);
        }

        private void specimenCommentJoin(String parentAlias, Map<String, SQLFragment> map)
        {
            String tableAlias = parentAlias + "$" + SPECIMEN_COMMENT_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoSpecimenComment().getFromSQL(tableAlias));
            joinSql.append(" ON ");
            joinSql.append(parentAlias).append(".GlobalUniqueId = ").append(tableAlias).append(".GlobalUniqueId AND ");
            joinSql.append(parentAlias).append(".Container = ").append(tableAlias).append(".Container\n");
            map.put(tableAlias, joinSql);
        }

        private void participantCommentJoin(String parentAlias, Map<String, SQLFragment> map)
        {
            String ptidTableAlias = parentAlias + "$" + PARTICIPANT_COMMENT_JOIN;
            if (map.containsKey(ptidTableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(_ptidCommentTable.getFromSQL(ptidTableAlias));
            joinSql.append(" ON ");
            joinSql.append(parentAlias).append(".Ptid = ")
                    .append(_ptidCommentTable.getColumn(StudyService.get().getSubjectColumnName(_container)).getValueSql(ptidTableAlias))
                    .append("\n");
            map.put(ptidTableAlias, joinSql);
        }

        private void participantVisitCommentJoin(String parentAlias, Map<String, SQLFragment> map)
        {
            String ptidTableAlias = parentAlias + "$" + PARTICIPANTVISIT_COMMENT_JOIN;
            if (map.containsKey(ptidTableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.append(" LEFT OUTER JOIN ").append(_ptidVisitCommentTable.getFromSQL(ptidTableAlias));
            joinSql.append(" ON ");
            joinSql.append(parentAlias).append(".ParticipantSequenceNum = ").append(ptidTableAlias).append(".ParticipantSequenceNum");
            map.put(ptidTableAlias, joinSql);
        }
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value == null)
                return "";
            else
                return value;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value != null  && value instanceof String)
                out.write((String) value);
        }
    }

    protected static final String COMMENT_FORMAT_HTML = "<i>%s:&nbsp;</i>";
    protected static final String COMMENT_FORMAT = "%s: ";
    protected static final String LINE_SEPARATOR_HTML = "<br>";
    protected static final String LINE_SEPARATOR = ", ";

    protected static String formatParticipantComments(RenderContext ctx, boolean renderHtml)
    {
        Map<String, Object> row = ctx.getRow();

        Object vialComment = row.get(SpecimenCommentColumn.VIAL_COMMENT_ALIAS);
        Object participantComment = row.get(SpecimenCommentColumn.PARTICIPANT_COMMENT_ALIAS);
        Object participantVisitComment = row.get(SpecimenCommentColumn.PARTICIPANTVISIT_COMMENT_ALIAS);

        String lineSeparator = renderHtml ? LINE_SEPARATOR_HTML : LINE_SEPARATOR;
        String commentFormat = renderHtml ? COMMENT_FORMAT_HTML : COMMENT_FORMAT;

        StringBuilder sb = new StringBuilder();

        if (vialComment instanceof String)
        {
            sb.append(String.format(commentFormat, "Vial"));
            if (renderHtml)
                sb.append(PageFlowUtil.filter(vialComment));
            else
                sb.append(vialComment);
            sb.append(lineSeparator);
        }
        String subjectNoun = StudyService.get().getSubjectNounSingular(ctx.getContainer());
        if (participantComment instanceof String)
        {
            if (sb.length() > 0)
                sb.append(lineSeparator);
            if (renderHtml)
            {
                sb.append(String.format(commentFormat, PageFlowUtil.filter(subjectNoun)));
                sb.append(PageFlowUtil.filter(participantComment));
            }
            else
            {
                sb.append(String.format(commentFormat, subjectNoun));
                sb.append(participantComment);
            }
            sb.append(lineSeparator);
        }
        if (participantVisitComment instanceof String)
        {
            if (sb.length() > 0)
                sb.append(lineSeparator);

            if (renderHtml)
            {
                sb.append(String.format(commentFormat, PageFlowUtil.filter(subjectNoun) +  "/Visit"));
                sb.append(PageFlowUtil.filter(participantVisitComment));
            }
            else
            {
                sb.append(String.format(commentFormat, subjectNoun +  "/Visit"));
                sb.append(participantVisitComment);
            }
            sb.append(lineSeparator);
        }
        return sb.toString();
    }

    /* NOTE getUpdateService() and hasPermission() should usually be overridden together */

    @Override
    public QueryUpdateService getUpdateService()
    {
        return null;
    }

    @Override
    // ONLY OVERRIDE THIS IF TABLE SHOULD BE VISIBLE IN DATASPACE PROJECT-LEVEL CONTAINER
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // Most tables should not be editable in Dataspace
        if (getContainer().isDataspace())
            return false;
        return hasPermissionOverridable(user, perm);
    }

    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return false;
    }

    protected boolean canReadOrIsAdminPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && _userSchema.getContainer().hasPermission(user, perm) ||
                _userSchema.getContainer().hasPermission(user, AdminPermission.class);
    }

    protected void addOptionalColumns(List<DomainProperty> optionalProperties, boolean editable, @Nullable List<String> readOnlyColumnNames)
    {
        SqlDialect dialect = getSqlDialect();
        for (DomainProperty domainProperty : optionalProperties)
        {
            PropertyDescriptor property = domainProperty.getPropertyDescriptor();
            SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS);
            String legalName = property.getLegalSelectName(dialect);
            sql.append(".").append(legalName);
            ColumnInfo column = new ExprColumn(this, legalName, sql, property.getJdbcType());
            PropertyColumn.copyAttributes(getUserSchema().getUser(), column, domainProperty, getContainer(), null);
            if (editable)
            {
                // Make editable, but some should be read only
                column.setUserEditable(true);
                if (null != readOnlyColumnNames && readOnlyColumnNames.contains(column.getName().toLowerCase()))
                    column.setReadOnly(true);
            }
            addColumn(column);
        }
    }


    // UNDONE: a lot of study tables already use addContainerColumns()

    protected ColumnInfo addFolderColumn()
    {
        // Workaround to prevent IllegalArgumentException for assay tables
        if (getColumn("Folder") == null)
        {
            ColumnInfo folder = new AliasedColumn(this, "Folder", _rootTable.getColumn("Container"));
            ContainerForeignKey.initColumn(folder,getUserSchema());
            folder.setHidden(true);
            addColumn(folder);
        }
        return getColumn("Folder");
    }


    protected ColumnInfo addStudyColumn()
    {
        ColumnInfo study = new AliasedColumn(this, "Study", _rootTable.getColumn("Container"));

//      NOTE: QFK doesn't seem to support container joins
//      study.setFk(new QueryForeignKey("study", getUserSchema().getContainer(), null, getUserSchema().getUser(), "studyproperties", "Container", "Label", false));
        study.setFk(new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getUserSchema().getTable("studyproperties");
            }
        });
        study.setHidden(true);
        addColumn(study);
        return study;
    }


    @Override
    public boolean supportsContainerFilter()
    {
        if (!_userSchema.allowSetContainerFilter())
            return false;
        return super.supportsContainerFilter();
    }


    @NotNull
    public ContainerFilter getDefaultContainerFilter()
    {
        return getUserSchema().getDefaultContainerFilter();
    }



    // for testing/breakpoints

    @Override
    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        super.setContainerFilter(filter);
    }

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        return super.getContainerFilter();
    }
}
