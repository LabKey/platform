package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.data.UpdateableTableInfo.ObjectUriType.schemaColumn;

public class WellTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "Well";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    private static final Set<String> ignoredColumns = new CaseInsensitiveHashSet();
    private Map<FieldKey, DomainProperty> _vocabularyFieldMap = new HashMap<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("PlateId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Row"));
        defaultVisibleColumns.add(FieldKey.fromParts("Col"));

        // for now don't surface value and dilution, we may choose to drop these fields from the
        // db schema at some point
        ignoredColumns.add("value");
        ignoredColumns.add("dilution");
    }

    public WellTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoWell(), cf);
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        addVocabularyDomains();
    }

    private void addVocabularyDomains()
    {
        // for now there is just a single domain supported
        Domain domain = PlateManager.get().getPlateMetadataDomain(getContainer(), getUserSchema().getUser());
        if (domain != null)
        {
            String colName = "Properties";
            var col = addVocabularyDomainColumns(domain, colName);
            if (col != null)
            {
                col.setLabel("Plate Metadata");
                col.setDescription("Custom properties associated with the plate well");
            }

            for (DomainProperty field : domain.getProperties())
            {
                // resolve vocabulary fields by property URI and field key
                _vocabularyFieldMap.put(FieldKey.fromParts("properties", field.getName()), field);
                _vocabularyFieldMap.put(FieldKey.fromParts(field.getPropertyURI()), field);
            }
        }
    }

    private MutableColumnInfo addVocabularyDomainColumns(Domain domain, @NotNull String lookupColName)
    {
        var lsidColumn = _rootTable.getColumn(FieldKey.fromParts("lsid"));
        if (lsidColumn == null)
            return null;

        var colProperty = wrapColumn(lookupColName, lsidColumn);
        colProperty.setFk(new PropertyForeignKey(_userSchema, getContainerFilter(), domain));
        colProperty.setUserEditable(false);
        colProperty.setIsUnselectable(true);
        colProperty.setCalculated(true);

        return addColumn(colProperty);
    }

    @Override
    protected boolean acceptColumn(ColumnInfo col)
    {
        return super.acceptColumn(col) && !ignoredColumns.contains(col.getName());
    }

    /**
     * Override to resolve Property URIs for vocabulary columns during update. Consider adding the
     * capability to resolve vocabulary columns by field key or name.
     */
    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo lsidCol = getColumn("LSID", false);
        if (lsidCol != null)
        {
            // Attempt to resolve the column name as a property URI if it looks like a URI
            FieldKey fieldKey = FieldKey.decode(name);
            if (_vocabularyFieldMap.containsKey(fieldKey))
            {
                DomainProperty field = _vocabularyFieldMap.get(fieldKey);

                // mark vocab propURI col as Voc column
                PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(field.getPropertyURI(), getContainer());
                if (pd != null)
                {
                    PropertyColumn pc = new PropertyColumn(pd, lsidCol, getContainer(), getUserSchema().getUser(), false);
                    String label = pc.getLabel();
                    pc.setFieldKey(fieldKey);
                    pc.setLabel(label);

                    return pc;
                }
            }
        }
        return super.resolveColumn(name);
    }

    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo col)
    {
        var columnInfo = super.wrapColumn(col);

        // workaround for sample lookup not resolving correctly
        if (columnInfo.getName().equalsIgnoreCase("SampleId"))
        {
            columnInfo.setFk(QueryForeignKey.from(getUserSchema(), getContainerFilter())
                    .schema("exp", getContainer())
                    .to("Materials", "RowId", "Name"));
        }
        return columnInfo;
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return schemaColumn;
    }

    @Override
    public @Nullable String getObjectURIColumnName()
    {
        return "lsid";
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new WellUpdateService(this, AssayDbSchema.getInstance().getTableInfoWell());
    }

    protected static class WellUpdateService extends DefaultQueryUpdateService
    {
        public WellUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            final TableInfo wellTable = getQueryTable();

            SimpleTranslator lsidRemover = new SimpleTranslator(data.getDataIterator(context), context);
            lsidRemover.selectAll();
            if (lsidRemover.getColumnNameMap().containsKey("lsid"))
            {
                // remove any furnished lsid since we will be computing one
                lsidRemover.removeColumn(lsidRemover.getColumnNameMap().get("lsid"));
            }

            SimpleTranslator lsidGenerator = new SimpleTranslator(lsidRemover, context);
            lsidGenerator.setDebugName("lsidGenerator");
            lsidGenerator.selectAll();
            final Map<String, Integer> nameMap = lsidGenerator.getColumnNameMap();

            // consider enforcing this in the schema
            if (!nameMap.containsKey("row") || !nameMap.containsKey("col"))
            {
                context.getErrors().addRowError(new ValidationException("Row and Col are required fields"));
                return data;
            }

            // generate a value for the lsid
            lsidGenerator.addColumn(wellTable.getColumn("lsid"),
                    (Supplier) () -> {
                        Object row = lsidGenerator.get(nameMap.get("row"));
                        Object col = lsidGenerator.get(nameMap.get("col"));

                        Lsid lsid = PlateManager.get().getLsid(Well.class, container);
                        return String.format("%s-well-%s-%s", lsid.toString(), row, col);
                    });

            DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(wellTable, lsidGenerator, container, user, context);
            dib = new TableInsertDataIteratorBuilder(dib, wellTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet("RowId", "Lsid"))
                    .setVocabularyProperties(PropertyService.get().findVocabularyProperties(container, nameMap.keySet()));
            dib = LoggingDataIterator.wrap(dib);
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption(), user, container);

            return dib;
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            return super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            // enforce no updates if the plate has been imported in an assay run
            if (oldRow.containsKey("plateId"))
            {
                Plate plate = PlateManager.get().getPlate(container, (Integer)oldRow.get("plateId"));
                if (plate != null)
                {
                    int runsInUse = PlateManager.get().getRunCountUsingPlate(container, plate);
                    if (runsInUse > 0)
                        throw new QueryUpdateServiceException(String.format("This %s is used by %d runs and its wells cannot be modified.", plate.isTemplate() ? "Plate template" : "Plate", runsInUse));
                }
            }
            return super.updateRow(user, container, row, oldRow);
        }
    }
}
