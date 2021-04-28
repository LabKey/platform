package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.study.model.DatasetDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SampleDatasetTable extends DatasetTableImpl
{
    public static final FieldKey SAMPLE_RESULT_LSID = FieldKey.fromParts("SampleResultLsid");

    private TableInfo _sampleResultTable;
    private List<FieldKey> _defaultVisibleColumns = null;

    public SampleDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);

        TableInfo sampleResultTable = getSamplesTable();
        ExpObject publishSource = _dsd.resolvePublishSource();
        if (sampleResultTable != null && publishSource instanceof ExpSampleType)
        {
            for (final ColumnInfo columnInfo : sampleResultTable.getColumns())
            {
                String name = columnInfo.getName();
                if (columnInfo.getFieldKey().equals(SAMPLE_RESULT_LSID))
                {
                    // add the sample result lsid column as "SampleResultLsid" so it won't collide with the dataset's LSID column
                    name = SAMPLE_RESULT_LSID.toString();
                }

                if (!getColumnNameSet().contains(name))
                {
                    ExprColumn wrappedColumn = wrapPublishSourceColumn(columnInfo, name, this::getSampleResultAlias);
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
            TableInfo sampleResultTable = getSamplesTable();
            if (null != sampleResultTable)
            {
                for (FieldKey fieldKey : sampleResultTable.getDefaultVisibleColumns())
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

        String sampleResultAlias = getSampleResultAlias(alias);
        TableInfo sampleResultTable = getSamplesTable();
        if (sampleResultTable != null)
        {
            sqlf.append(" LEFT OUTER JOIN ").append(sampleResultTable.getFromSQL(sampleResultAlias)).append("\n");
            sqlf.append(" ON ").append(sampleResultAlias).append(".").append(sampleResultTable.getPkColumnNames().get(0)).append(" = ");
            sqlf.append(alias).append(".").append(getSqlDialect().getColumnSelectName(_dsd.getKeyPropertyName()));
        }

        return getTransformedFromSQL(sqlf);
    }

    @Nullable
    private TableInfo getSamplesTable()
    {
        if (_sampleResultTable == null)
        {
            ExpObject source = _dsd.resolvePublishSource();
            if (!(source instanceof ExpSampleType))
                return null;

            ExpSampleType sampleType = (ExpSampleType)source;
            UserSchema userSchema = QueryService.get().getUserSchema(_userSchema.getUser(), sampleType.getContainer(), SamplesSchema.SCHEMA_NAME);
            if (userSchema != null)
                _sampleResultTable = userSchema.getTable(sampleType.getName());
        }
        return _sampleResultTable;
    }

    private String getSampleResultAlias(String mainAlias)
    {
        return mainAlias + "_SR";
    }
}
