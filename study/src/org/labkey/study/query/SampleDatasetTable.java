package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.compliance.TableRules;
import org.labkey.api.compliance.TableRulesManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.study.model.DatasetDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SampleDatasetTable extends LinkedDatasetTable
{
    private ExpSampleType _sampleType;
    private TableInfo _sampleTable;
    private List<FieldKey> _defaultVisibleColumns = null;

    SampleDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);

        TableInfo sampleTable = getSamplesTable();
        ExpObject publishSource = _dsd.resolvePublishSource();
        if (sampleTable != null && publishSource instanceof ExpSampleType)
        {
            for (final ColumnInfo columnInfo : sampleTable.getColumns())
            {
                String name = columnInfo.getName();
                if (name.equals(ExpMaterialTable.Column.LSID.name()))
                {
                    // add the sample row's lsid column as "SourceRowLsid" so it won't collide with the dataset's LSID column
                    name = SOURCE_ROW_LSID;
                }

                if (!getColumnNameSet().contains(name))
                {
                    ExprColumn wrappedColumn = wrapPublishSourceColumn(columnInfo, name, this::getSampleTableAlias);
                    addColumn(wrappedColumn);
                }
            }
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns != null)
            return _defaultVisibleColumns;

        ExpObject publishSource = _dsd.resolvePublishSource();
        if (publishSource instanceof ExpSampleType)
        {
            // compute default visible columns for sample dataset
            List<FieldKey> defaultVisibleCols = new ArrayList<>(super.getDefaultVisibleColumns());
            TableInfo sampleTable = getSamplesTable();
            if (null != sampleTable)
            {
                for (FieldKey fieldKey : sampleTable.getDefaultVisibleColumns())
                {
                    if (!defaultVisibleCols.contains(fieldKey) && !defaultVisibleCols.contains(FieldKey.fromParts(fieldKey.getName())))
                    {
                        defaultVisibleCols.add(fieldKey);
                    }
                }
            }
            _defaultVisibleColumns = Collections.unmodifiableList(defaultVisibleCols);
            return _defaultVisibleColumns;
        }
        else
            return super.getDefaultVisibleColumns();
    }

    @Override
    protected @NotNull SQLFragment _getFromSQL(String alias, boolean includeParticipantVisit)
    {
        SQLFragment sqlf = super._getFromSQL(alias, includeParticipantVisit);

        String sampleTableAlias = getSampleTableAlias(alias);
        TableInfo sampleTable = getSamplesTable();
        if (sampleTable != null)
        {
            sqlf.append(" LEFT OUTER JOIN ").append(sampleTable.getFromSQL(sampleTableAlias)).append("\n");
            sqlf.append(" ON ").append(sampleTableAlias).append(".").append(sampleTable.getPkColumnNames().get(0)).append(" = ");
            sqlf.append(alias).append(".").append(getSqlDialect().getColumnSelectName(_dsd.getKeyPropertyName()));
        }

        return getTransformedFromSQL(sqlf);
    }

    @Nullable
    private TableInfo getSamplesTable()
    {
        if (_sampleTable == null)
        {
            ExpObject source = _dsd.resolvePublishSource();
            if (!(source instanceof ExpSampleType sampleType))
                return null;
            _sampleType = sampleType;

            UserSchema userSchema = QueryService.get().getUserSchema(_userSchema.getUser(), sampleType.getContainer(), SamplesSchema.SCHEMA_NAME);

            // Hide 'linked' column for Sample Type Datasets
            if (userSchema instanceof SamplesSchema samplesSchema)
            {
                // We want to create a samples table here with a few differences from the "regular" sample type table
                // - No container filter.  We rely on the study and dataset permissions to control access.
                // - No linkToStudy columns
                // - No PHI restrictions, again we want PHI permission from the study folder
                //
                // It is easier to handle these changes on the construction-side, so we use a helper schema
                // CONSIDER: do we need a version of getTable() that allows passing custom options?
                var noLinks = Objects.requireNonNull(samplesSchema.getSchema(SamplesSchema.STUDY_LINKED_SCHEMA_NAME));
                _sampleTable = Objects.requireNonNull(noLinks.getTable(sampleType.getName(), ContainerFilter.EVERYTHING));
            }
            else
            {
                throw new IllegalStateException(String.format("%s must be a SamplesSchema", userSchema.getName()));
            }
        }
        return _sampleTable;
    }

    // NOTE we are going to wrap a

    private String getSampleTableAlias(String mainAlias)
    {
        return mainAlias + "_ST";
    }


    @Override
    public boolean supportTableRules() // intentional override
    {
        return true;
    }

    @Override
    protected @NotNull TableRules findTableRules()
    {
        // init _sampleType
        getSamplesTable();
        if (null != _sampleType)
            return TableRulesManager.get().getTableRules(_sampleType.getContainer(), getUserSchema().getUser(), _dsd.getContainer());
        return super.findTableRules();
    }
}
