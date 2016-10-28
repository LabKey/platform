/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StringExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: May 7, 2009
 *
 * If you touch this file, run AssayTest, FlowSpecimenTest, ElispotAssay, and TargetStudyTest
 */
public class SpecimenForeignKey extends LookupForeignKey
{
    private final AssaySchema _schema;
    private final AssayProvider _provider;
    private final ExpProtocol _protocol;
    private final AssayTableMetadata _tableMetadata;
    private final ContainerFilter _studyContainerFilter;

    private static final String ASSAY_SUBQUERY_SUFFIX = "$AS";
    private static final String SPECIMEN_SUBQUERY_SUFFIX = "$SP";
    private static final String VIAL_SUBQUERY_SUFFIX = "$VI";
    private static final String STUDY_SUBQUERY_SUFFIX = "$ST";
    private static final String DRAW_DT_COLUMN_NAME = "DrawDT";

    private Container _targetStudyOverride;
    private SimpleFilter _specimenFilter;


    // computed in _initAssayColumns
    Container _defaultTargetContainer;
    TableInfo _assayDataTable;
    ColumnInfo _assayParticipantIdCol;
    ColumnInfo _assayVisitIdCol;
    ColumnInfo _assayDateCol;
    ColumnInfo _assayTargetStudyCol;
    ColumnInfo _assaySpecimenIdCol;
    Map<FieldKey,ColumnInfo> _assayColumns;

    List<Container> _containerList;

    // SpecimenForeignKey joins to the vial table AND the specimen table
    // The SpecimenForeignKey that the caller creates appears to join to vial (_joinToSpecimen=false),
    // the one we create here (see SpecimenForiegnKey.createLookupColumn()) joins to Specimen (_joinToSpecimen=true)
    //boolean _joinToSpecimen = false;


    public SpecimenForeignKey(AssaySchema schema, AssayProvider provider, ExpProtocol protocol)
    {
        this(schema, provider, protocol, provider.getTableMetadata(protocol));
    }


    public SpecimenForeignKey(AssaySchema schema, AssayProvider provider, ExpProtocol protocol, AssayTableMetadata tableMetadata)
    {
        super("RowId");
        _schema = schema;
        _provider = provider;
        _protocol = protocol;
        _tableMetadata = tableMetadata;
        _studyContainerFilter = new StudyContainerFilter(schema);
    }


    public SpecimenForeignKey(AssaySchema schema, TableInfo assayDataTable, AssayTableMetadata tableMetadata)
    {
        super("RowId");
        _schema = schema;
        _assayDataTable = assayDataTable;
        _provider = null;
        _protocol = null;
        _tableMetadata = tableMetadata;
        _studyContainerFilter = new StudyContainerFilter(schema);
    }


    boolean initialized = false;

    /*
     * can't call init() in constructor, we're probably construction an assay data now, and we'll end up recursing
     * NOTE: ForeignKeys should be stateless, so notice that this init() only depends on data passed into the constructor
     * so that should be OK.  Shouldn't need to be thread-safe unless/until there's tableinfo caching...)
     */
    void _initAssayColumns()
    {
        if (initialized)
            return;
        initialized = true;

        Container targetStudy;
        if (_targetStudyOverride != null)
            targetStudy = _targetStudyOverride;
        else
            targetStudy = _schema.getTargetStudy();
        _defaultTargetContainer = null!=targetStudy ? targetStudy: _schema.getContainer();

        UserSchema studySchema = QueryService.get().getUserSchema(_schema.getUser(), _defaultTargetContainer, "study");

        if (null == _assayDataTable)
        {
            AssayProtocolSchema assaySchema = _provider.createProtocolSchema(studySchema.getUser(), _schema.getContainer(), _protocol, null);
            _assayDataTable = assaySchema.createDataTable();
        }

        // set container filter BEFORE we call getColumns(), it affects the filters on the join tables
        // TODO could the caller pass in the container filter at construction time, or is that too early?
        ((ContainerFilterable)_assayDataTable).setContainerFilter(ContainerFilter.EVERYTHING);

        FieldKey specimenFK = _tableMetadata.getSpecimenIDFieldKey();
        FieldKey targetStudyFK = _tableMetadata.getTargetStudyFieldKey();
        FieldKey participantFK = _tableMetadata.getParticipantIDFieldKey();
        FieldKey visitFK = _tableMetadata.getVisitIDFieldKey(TimepointType.VISIT);
        FieldKey dateFK = _tableMetadata.getVisitIDFieldKey(TimepointType.DATE);
        FieldKey drawDateFK = new FieldKey(dateFK != null ? dateFK.getParent() : null, DRAW_DT_COLUMN_NAME);
        List<FieldKey> keys = new ArrayList<>(Arrays.asList(specimenFK, targetStudyFK, participantFK, visitFK, dateFK, drawDateFK));
        List<String> pks = _assayDataTable.getPkColumnNames();
        for (String pk : pks)
            keys.add(FieldKey.fromParts(pk));

        _assayColumns = QueryService.get().getColumns(_assayDataTable, keys);

        _assaySpecimenIdCol = _assayColumns.get(specimenFK);
        _assayParticipantIdCol = _assayColumns.get(participantFK);
        _assayVisitIdCol = _assayColumns.get(visitFK);
        _assayDateCol = _assayColumns.get(dateFK);
        // Check for an alternate column name
        if (_assayDateCol == null)
            _assayDateCol = _assayColumns.get(drawDateFK);
        _assayTargetStudyCol = _assayColumns.get(targetStudyFK);

        _containerList = getTargetStudyContainers();
    }


    @Override
    public Set<FieldKey> getSuggestedColumns()
    {
        _initAssayColumns();
        HashSet<FieldKey> ret = new HashSet<>();
        for (String name : _assayDataTable.getPkColumnNames())
            ret.add(new FieldKey(null,name));
        Set<FieldKey> superColumns = super.getSuggestedColumns();
        if (null != superColumns)
            ret.addAll(superColumns);
        return ret;
    }


    public TableInfo getLookupTableInfo()
    {
        _initAssayColumns();
        if (!_defaultTargetContainer.hasPermission(_schema.getUser(), ReadPermission.class))
        {
            return null;
        }

        TableInfo vialTableInfo = getVialTableInfo();
        if (null == vialTableInfo)
            return null;
        FilteredTable ft = new FilteredTable<AssaySchema>(vialTableInfo, _schema)
        {
            @NotNull
            @Override
            public SQLFragment getFromSQL(String alias)
            {
                throw new IllegalStateException("should not be using this table...");
            }
        };
        ft.wrapAllColumns(true);
        ft.addColumn(new ColumnInfo(new FieldKey(null,AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME),ft,JdbcType.BOOLEAN));
        ft.getColumn("Specimen").setFk(new _SpecimenUnionForeignKey());
        ft.setPublic(false);
        ft.setLocked(true);
        return ft;
    }


    @Nullable
    private TableInfo getVialTableInfo()
    {
        _initAssayColumns();
        UserSchema studySchema = QueryService.get().getUserSchema(_schema.getUser(), _schema.getContainer(), "study");
        List<Container> list = _containerList;
        if (null == _containerList || _containerList.isEmpty())
            list = Collections.singletonList(_defaultTargetContainer);
        Set<Container> containerSet = new HashSet<>();
        for (Container container : list)
            containerSet.add(container);
        TableInfo vialTableInfo = StudyService.get().getVialTableUnion(studySchema, containerSet);
        return vialTableInfo;
    }


    private TableInfo getSpecimenTableInfo()
    {
        _initAssayColumns();
        UserSchema studySchema = QueryService.get().getUserSchema(_schema.getUser(), _schema.getContainer(), "study");
        List<Container> list = _containerList;
        if (null == _containerList || _containerList.isEmpty())
            list = Collections.singletonList(_defaultTargetContainer);
        Set<Container> containerSet = new HashSet<>();
        for (Container container : list)
            containerSet.add(container);
        TableInfo specimenTableInfo = StudyService.get().getSpecimenTableUnion(studySchema, containerSet);
        return specimenTableInfo;
    }


    public void setTargetStudyOverride(Container targetStudy)
    {
        _targetStudyOverride = targetStudy;
    }

    public void addSpecimenFilter(SimpleFilter filter)
    {
        if (_specimenFilter == null)
            _specimenFilter = new SimpleFilter();
        _specimenFilter.addAllClauses(filter);
    }


    public StringExpression getURL(ColumnInfo parent)
    {
        FieldKey targetStudyFK = _tableMetadata.getTargetStudyFieldKey();
        Container targetStudy;

        if (_targetStudyOverride != null)
            targetStudy = _targetStudyOverride;
        else
            targetStudy = _schema.getTargetStudy();

        DetailsURL detailsURL;
        if (targetStudy != null)
            detailsURL = DetailsURL.fromString("study-samples/sampleEventsRedirect.view?id=${" + parent.getFieldKey() + "}&targetStudy=" + targetStudy.getId() );
        else
            detailsURL = DetailsURL.fromString("study-samples/sampleEventsRedirect.view?id=${" + parent.getFieldKey() + "}&targetStudy=${" + targetStudyFK + "}");
        return detailsURL;
    }


    @Override
    public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayFieldName)
    {
        Container targetContainer = _targetStudyOverride != null ? _targetStudyOverride : _schema.getContainer();
        if (displayFieldName == null)
        {
            // Don't show the lookup's value for the base column, so we can filter on it and we don't get
            // brackets around specimen ids that don't match up with the target study
            return foreignKey;
        }

        FieldKey displayFieldKey = new FieldKey(null, displayFieldName);
        if (displayFieldName.equalsIgnoreCase(AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME))
        {
            return new SpecimenMatchLookupColumn(foreignKey);
        }
        else if (displayFieldName.equalsIgnoreCase("specimen"))
        {
            TableInfo vialTableInfo = getVialTableInfo();
            if (null != vialTableInfo)
            {
                ColumnInfo lookupColumn = vialTableInfo.getColumn(displayFieldName);
                ColumnInfo specimenCol = new SpecimenLookupColumn(foreignKey, displayFieldKey, lookupColumn, false);
                specimenCol.setFk(new _SpecimenUnionForeignKey());
                return specimenCol;
            }
        }
        else
        {
            TableInfo vialTableInfo = getVialTableInfo();
            if (null != vialTableInfo)
            {
                ColumnInfo lookupColumn = vialTableInfo.getColumn(displayFieldName);
                if (null != lookupColumn)
                    return new SpecimenLookupColumn(foreignKey, displayFieldKey, lookupColumn, false);
                lookupColumn = getSpecimenTableInfo().getColumn(displayFieldName);
                if (null != lookupColumn)
                    return new SpecimenLookupColumn(foreignKey, displayFieldKey, lookupColumn, true);
            }
        }
        return null;
    }


    String getBaseAlias(String parentAlias, String fkAlias)
    {
        return LookupColumn.getTableAlias(parentAlias,fkAlias, _schema.getDbSchema().getSqlDialect());
    }


    public SQLFragment _declareJoinsAssayAndVial(String parentAlias, ColumnInfo foreignKey, Map<String, SQLFragment> map)
    {
        _initAssayColumns();

        SqlDialect dialect = foreignKey.getSqlDialect();
        Container targetStudy;
        if (_targetStudyOverride != null)
            targetStudy = _targetStudyOverride;
        else
            targetStudy = _schema.getTargetStudy();

        // Do a complicated join if we can identify a target study so that we choose the right specimen
        if (_assayTargetStudyCol != null || targetStudy != null)
        {
            TableInfo vialTableInfo = getVialTableInfo();
            if (null == vialTableInfo)
                return null;

            SQLFragment sql = new SQLFragment();
            sql.appendComment("<" + this.getClass().getName() + ".declareJoins(" + parentAlias + ")" + ">", dialect);
            sql.append(" LEFT OUTER JOIN (");

            // Select all the assay-side specimen columns that we'll need to do the comparison
            ((ContainerFilterable)_assayDataTable).setContainerFilter(foreignKey.getParentTable().getContainerFilter());
            SQLFragment targetStudySQL = QueryService.get().getSelectSQL(_assayDataTable, _assayColumns.values(), null, null, Table.ALL_ROWS, Table.NO_OFFSET, false);
            sql.append(targetStudySQL);

            String baseAlias = getBaseAlias(parentAlias, foreignKey.getAlias());
            String assaySubqueryAlias = baseAlias + ASSAY_SUBQUERY_SUFFIX;
            String vialSubqueryAlias = baseAlias + VIAL_SUBQUERY_SUFFIX;


            // TODO the SFK should really be attached to the PK instead of the specimenid
            // As it is, there is no completely correct strategy for finding the join columns
            //
            // Try to work backward from the given specimen lookup column
            // for instance if the specimen columns is lsid/property/specimenid and the foreignkey field key is
            // a/b/c/lsid/property/specimendid we should probably prefix the PK with a/b/c, but even this is not
            // guaranteed to be right
            FieldKey fkBase = foreignKey.getFieldKey();
            FieldKey fkSpecimen = _tableMetadata.getSpecimenIDFieldKey();
            while (null != fkBase && null != fkSpecimen && fkBase.getName().equalsIgnoreCase(fkSpecimen.getName()))
            {
                fkBase = fkBase.getParent();
                fkSpecimen = fkSpecimen.getParent();
            }

            sql.append(") AS " + assaySubqueryAlias + " ON ");
            ArrayList<FieldKey> pkFieldKeys = new ArrayList<>();
            for (ColumnInfo pk : _assayDataTable.getPkColumns())
            {
                pkFieldKeys.add(new FieldKey(fkBase,pk.getName()));
                if (null != fkBase)
                    pkFieldKeys.add(new FieldKey(null,pk.getName()));
            }
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(foreignKey.getParentTable(), pkFieldKeys);
            String sep = "";
            for (ColumnInfo pk : _assayDataTable.getPkColumns())
            {
                ColumnInfo fkCol = cols.get(new FieldKey(fkBase, pk.getFieldKey().getName()));
                if (null == fkCol && fkBase != null)
                    fkCol = cols.get(new FieldKey(null, pk.getFieldKey().getName()));
                ColumnInfo pkCol = _assayColumns.get(pk.getFieldKey());
                if (null == fkCol || null == pkCol)
                    throw new IllegalStateException("Could not find column needed for SpecimenForeignKey: " + pk.getName());
                sql.append(sep);
                sql.append(fkCol.getValueSql(parentAlias));
                sql.append("=");
                sql.append(pkCol.getValueSql(assaySubqueryAlias));
                sep = " AND ";
            }

            sql.append("\n\tLEFT OUTER JOIN ");
            sql.append(vialTableInfo.getFromSQL(vialSubqueryAlias));
            sql.append(" ON " + vialSubqueryAlias + ".GlobalUniqueId = ");

            // Do type conversion if needed
            if (_assaySpecimenIdCol.getJdbcType() == JdbcType.VARCHAR)
            {
                sql.append(assaySubqueryAlias);
                sql.append(".");
                sql.append(_assaySpecimenIdCol.getAlias());
            }
            else
            {
                sql.append("CAST(");
                sql.append(assaySubqueryAlias);
                sql.append(".");
                sql.append(_assaySpecimenIdCol.getAlias());
                sql.append(" AS ");
                sql.append(dialect.sqlTypeNameFromJdbcType(JdbcType.VARCHAR));
                sql.append(")");
            }

            if (targetStudy != null)
            {
                // We're in the middle of a copy to study, so ignore what the user selected as the target when they uploaded
                sql.append(" AND " + vialSubqueryAlias + ".Container = ?");
                sql.add(targetStudy.getId());
            }
            else if (_containerList != null && _containerList.size() == 1)
            {
                // We've determined that there is only one target study container (see .getTargetStudyContainers())
                sql.append(" AND " + vialSubqueryAlias + ".Container = ?");
                sql.add(_containerList.get(0).getId());
            }
            else
            {
                // Match based on the target study associated with the assay data
                sql.append(" AND " + assaySubqueryAlias + "." + _assayTargetStudyCol.getAlias() + " = " + vialSubqueryAlias + ".Container");
            }

            sql.appendComment("</" + this.getClass().getName() + ".declareJoins()" + ">", dialect);
            return sql;
        }
        else
        {
            return null;
        }
    }


    public class SpecimenLookupColumn extends ColumnInfo // extends LookupColumn
    {
        private boolean _returnNull;
        ColumnInfo _foreignKey;
        ColumnInfo _lookupColumn;
        boolean _specimenLookup; // as opposed to vial lookup

        public SpecimenLookupColumn(ColumnInfo foreignKey, FieldKey lookupColumnFieldKey, ColumnInfo lookupColumn, boolean specimenLookup)
        {
            // mashup of LookupColumn() and LookupColumn.create()
            super(FieldKey_concat(foreignKey.getFieldKey(), lookupColumnFieldKey), foreignKey.getParentTable());
            _specimenLookup = specimenLookup;
            _foreignKey = foreignKey;
//            _lookupKey = lookupKey;
//            assert lookupKey.getValueSql("test") != null;
            _lookupColumn = lookupColumn;
            setSqlTypeName(lookupColumn.getSqlTypeName());
            String alias = foreignKey.getAlias() + "$" + lookupColumn.getAlias();
            if (alias.length() > 60)
                alias = AliasManager.truncate(foreignKey.getAlias(), 30) + "$" + AliasManager.truncate(lookupColumn.getAlias(),30);
            setAlias(alias);
            copyAttributesFrom(lookupColumn);
            copyURLFrom(lookupColumn, foreignKey.getFieldKey(), null);
            setLabel("Specimen  " + lookupColumn.getLabel());
            setShortLabel(lookupColumn.getShortLabel());
            if (getFk() instanceof RowIdForeignKey)
                setFk(null);
        }


        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            boolean assertEnabled = false; // needed to generate SQL for logging/debugging
            assert assertEnabled = true;

            String baseAlias = getBaseAlias(parentAlias, _foreignKey.getAlias());
            String vialSubqueryAlias = baseAlias + VIAL_SUBQUERY_SUFFIX;

            if (assertEnabled || !map.containsKey(vialSubqueryAlias))
            {
                _foreignKey.declareJoins(parentAlias, map);
                SQLFragment strJoin = _declareJoinsAssayAndVial(parentAlias, _foreignKey, map);
                if (null != strJoin)
                {
                    assert null == map.get(vialSubqueryAlias) || map.get(vialSubqueryAlias).getSQL().equals(strJoin.getSQL()) : "Join SQL does not match:\n" + strJoin.getSQL() + "\n\nvs\n\n" + map.get(vialSubqueryAlias).getSQL();
                    map.put(vialSubqueryAlias, strJoin);
                }
            }
            if (null == map.get(vialSubqueryAlias))
                _returnNull = true;
        }


        @Override
        public SQLFragment getValueSql(String tableAlias)
        {
            if (_returnNull)
            {
                return new SQLFragment("CAST(NULL AS " + getSqlDialect().sqlTypeNameFromJdbcType(getJdbcType()) + ")");
            }
            if (_specimenLookup)
            {
                String alias = getBaseAlias(tableAlias,_foreignKey.getAlias()) + SPECIMEN_SUBQUERY_SUFFIX;
                return _lookupColumn.getValueSql(alias);
            }
            else
            {
                String alias = getBaseAlias(tableAlias,_foreignKey.getAlias()) + VIAL_SUBQUERY_SUFFIX;
                return _lookupColumn.getValueSql(alias);
            }
        }
    }


    public class SpecimenMatchLookupColumn extends ColumnInfo
    {
        ColumnInfo _foreignKey;

        SpecimenMatchLookupColumn(ColumnInfo foreignKey)
        {
            super(new FieldKey(foreignKey.getFieldKey(),AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME), foreignKey.getParentTable(), JdbcType.BOOLEAN);
            _foreignKey = foreignKey;
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            SqlDialect dialect = getSqlDialect();
            Container targetStudy;
            if (_targetStudyOverride != null)
                targetStudy = _targetStudyOverride;
            else
                targetStudy = _schema.getTargetStudy();

            String baseAlias = getBaseAlias(parentAlias, _foreignKey.getAlias());
            String specimenSubqueryAlias = baseAlias + SPECIMEN_SUBQUERY_SUFFIX;
            String vialSubqueryAlias = baseAlias + VIAL_SUBQUERY_SUFFIX;
            String studySubqueryAlias = baseAlias + STUDY_SUBQUERY_SUFFIX;

            if (!map.containsKey(vialSubqueryAlias))
            {
                _foreignKey.declareJoins(parentAlias, map);
                SQLFragment strJoin = _declareJoinsAssayAndVial(parentAlias, _foreignKey, map);
                if (null != strJoin)
                    map.put(vialSubqueryAlias, strJoin);
            }

            if (!map.containsKey(specimenSubqueryAlias))
            {
                // Select all the study-side specimen columns that we'll need to do the comparison
                SQLFragment sql = new SQLFragment();
                TableInfo specimenTableInfo = getSpecimenTableInfo();
//                TableInfo vialTableInfo = getVialTableInfo();

//                sql.append("\n\tLEFT OUTER JOIN (SELECT PTID AS ParticipantId, drawtimestamp AS Date, VisitValue AS Visit, *");
//                sql.append(" FROM ");
//                sql.append(specimenTableInfo.getFromSQL("_"));
//                //            sql.append("study.specimens");
//                if (_specimenFilter != null)
//                {
//                    sql.append(_specimenFilter.getSQLFragment(dialect));
//                }
//                sql.append("\n\t) AS " + specimenSubqueryAlias + " ON ").append(specimenTableInfo.getColumn("rowid").getValueSql(specimenSubqueryAlias)).append("= " + vialSubqueryAlias + ".SpecimenID");

                sql.append("\n\tLEFT OUTER JOIN ");
                sql.append(specimenTableInfo.getFromSQL(specimenSubqueryAlias));
                sql.append(" ON ").append(specimenTableInfo.getColumn("rowid").getValueSql(specimenSubqueryAlias)).append(" = " + vialSubqueryAlias + ".Specimen");
                sql.append(" AND ").append(specimenTableInfo.getColumn("container").getValueSql(specimenSubqueryAlias)).append(" = " + vialSubqueryAlias + ".Container");
                if (_specimenFilter != null)
                {
                    sql.append(" AND ");
                    sql.append(_specimenFilter.getSQLFragment(dialect));
                }

                sql.append("\n\tLEFT OUTER JOIN study.study AS " + studySubqueryAlias);
                sql.append(" ON " + studySubqueryAlias + ".Container = ").append(specimenTableInfo.getColumn("container").getValueSql(specimenSubqueryAlias));
                map.put(specimenSubqueryAlias, sql);
            }
        }

        @Override
        public SQLFragment getValueSql(String tableAlias)
        {
            _initAssayColumns();

            String baseAlias = getBaseAlias(tableAlias, _foreignKey.getAlias());
            String specimenAlias = baseAlias + SPECIMEN_SUBQUERY_SUFFIX;
            String studyAlias = baseAlias + STUDY_SUBQUERY_SUFFIX;
            String targetStudyAlias = baseAlias + ASSAY_SUBQUERY_SUFFIX;

            SqlDialect dialect = _assayDataTable.getSqlDialect();
            SQLFragment sql = new SQLFragment();

            if (_assayParticipantIdCol != null || _assayVisitIdCol != null || _assayDateCol != null)
            {
                // We want NULL if there's no match based on specimen id
                sql.append("CASE WHEN (" + studyAlias + ".TimepointType IS NULL) THEN NULL ELSE (CASE WHEN (");
                if (_assayParticipantIdCol != null)
                {
                    // Check if the participants match, or if they're both NULL
                    sql.append("(" + specimenAlias + ".ParticipantId = ");

                    // See if we need to cast - Postgres 8.3 is picky about these comparisons
                    if (_assayParticipantIdCol.getJavaClass() != String.class)
                    {
                        sql.append("CAST(" + targetStudyAlias + "." + _assayParticipantIdCol.getAlias() + " AS VARCHAR)");
                    }
                    else
                    {
                        sql.append(targetStudyAlias + "." + _assayParticipantIdCol.getAlias());
                    }
                    sql.append(" OR ");
                    sql.append("(" + specimenAlias + ".ParticipantId IS NULL AND " + targetStudyAlias + "." + _assayParticipantIdCol.getAlias() + " IS NULL))");
                }
                if (_assayVisitIdCol != null || _assayDateCol != null)
                {
                    if (_assayParticipantIdCol != null)
                    {
                        sql.append(" AND ");
                    }
                    sql.append("(");
                    if (_assayVisitIdCol != null)
                    {
                        // If we're in a visit-based study, check that both the visits match or are null. Also,
                        // if the assay has a date column and it has a value, it needs to match as well.
                        sql.append("((" + studyAlias + ".TimepointType IS NULL OR " + studyAlias + ".TimepointType = '" + TimepointType.VISIT + "')");
                        sql.append(" AND (" + specimenAlias + ".SequenceNum = " + targetStudyAlias + "." + _assayVisitIdCol.getAlias() + " OR (" + specimenAlias + ".Visit IS NULL AND " + targetStudyAlias + "." + _assayVisitIdCol.getAlias() + " IS NULL))");
                        if (_assayDateCol != null)
                        {
                            sql.append(" AND (" + targetStudyAlias + "." + _assayDateCol.getAlias() + " IS NULL OR " + dialect.getDateTimeToDateCast(specimenAlias + ".drawtimestamp") + " = " + dialect.getDateTimeToDateCast(targetStudyAlias + "." + _assayDateCol.getAlias()) + " OR (" + specimenAlias + ".drawtimestamp IS NULL AND " + targetStudyAlias + "." + _assayDateCol.getAlias() + " IS NULL))");
                            sql.append(")");
                            sql.append(" OR ");
                        }
                        else
                        {
                            sql.append(")");
                        }
                    }
                    if (_assayDateCol != null)
                    {
                        // If we're in a relative date or continuous date study, check that the dates match or are both NULL
                        sql.append("((" + studyAlias + ".TimepointType = '" + TimepointType.DATE + "' OR " + studyAlias + ".TimepointType = '" + TimepointType.CONTINUOUS + "' OR " + studyAlias + ".TimepointType IS NULL) AND (" +
                                dialect.getDateTimeToDateCast(specimenAlias + ".drawtimestamp") + " = " +
                                dialect.getDateTimeToDateCast(targetStudyAlias + "." + _assayDateCol.getAlias()) + " OR (" + specimenAlias + ".drawtimestamp IS NULL AND " + targetStudyAlias + "." + _assayDateCol.getAlias() + " IS NULL)))");
                    }
                    sql.append(")");
                }
                sql.append(") THEN ? ELSE ? END) END");
                sql.add(Boolean.TRUE);
                sql.add(Boolean.FALSE);
            }
            else
            {
                sql.append("CAST(NULL AS " + dialect.getBooleanDataType() + ")");
            }
            return sql;
        }
    }


    /* Vial has a regular foreign key to Specimen defined, but
     * a) we need to join to the union version
     * b) we want to hide/deprecate since we've already exposed the specimen columns in this virtual table (SpecimenForeignKey.getLookupTableInfo()
     */
    private class _SpecimenUnionForeignKey extends LookupForeignKey
    {
        _SpecimenUnionForeignKey()
        {
            super("rowid");
            addJoin(new FieldKey(null,"Container"),"Container", false);
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            return getSpecimenTableInfo();
        }

        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            return super.createLookupColumn(parent, displayField);
        }
    }


    public static FieldKey FieldKey_concat(FieldKey parent, FieldKey append)
    {
        FieldKey ret = parent;
        for (String part : append.getParts())
            ret = new FieldKey(ret,part);
        return ret;
    }

    List<Container> getTargetStudyContainers()
    {
        if (_targetStudyOverride != null)
            return Collections.singletonList(_targetStudyOverride);

        HashSet<Container> containers = new HashSet<>();

        if (null != _schema.getTargetStudy())
        {
            Study study = StudyService.get().getStudy(_schema.getTargetStudy());
            if (null != study)
                containers.add(_schema.getTargetStudy());
        }

        if (null != _assayTargetStudyCol)
        {
            Collection<String> ids = _tableMetadata.getTargetStudyContainers(_schema, _assayDataTable, _assayTargetStudyCol);
            if (!ids.isEmpty())
            {
                Set<GUID> filterIds = null;
                if (null != _studyContainerFilter)
                {
                    Collection<GUID> studyContainerFilterIds = _studyContainerFilter.getIds(_schema.getContainer());
                    if (null != studyContainerFilterIds)
                        filterIds = new HashSet<>(studyContainerFilterIds);
                }

                for (String id : ids)
                {
                    Container c = ContainerManager.getForId(id);
                    if (null == c)
                        continue;
                    if (!c.hasPermission(_schema.getUser(), ReadPermission.class))
                        continue;
                    Study study = StudyService.get().getStudy(c);
                    if (null == study)
                        continue;
                    if (null != filterIds && !filterIds.contains(c.getEntityId()))
                        continue;
                    containers.add(c);
                }
            }
        }

        return new ArrayList<>(containers);
    }

    @Override
    public boolean allowImportByAlternateKey()
    {
        return false;
    }
}
