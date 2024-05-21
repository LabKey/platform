package org.labkey.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.importer.EventVialRollup;
import org.labkey.specimen.importer.ImportTypes;
import org.labkey.api.specimen.importer.RollupHelper;
import org.labkey.api.specimen.importer.RollupHelper.RollupMap;
import org.labkey.specimen.importer.SpecimenColumn;
import org.labkey.specimen.importer.TargetTable;
import org.labkey.api.specimen.model.SpecimenTablesProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Shared code used by both specimen writers and importers. Combine with SpecimenTablesProvider?
public class SpecimenTableManager
{
    // Event -> Vial Rollup map. Note: This is initialized at construction time in determineSpecimenColumns().
    private final RollupMap<EventVialRollup> _eventToVialRollups = new RollupMap<>();
    private final Container _container;
    private final User _user;

    // Provisioned specimen tables
    private final TableInfo _tableInfoSpecimen;
    private final TableInfo _tableInfoVial;
    private final TableInfo _tableInfoSpecimenEvent;
    private final TableInfo _tableInfoLocation;
    private final TableInfo _tableInfoPrimaryType;
    private final TableInfo _tableInfoDerivative;
    private final TableInfo _tableInfoAdditive;
    private final SqlDialect _dialect;

    public SpecimenTableManager(Container container, User user)
    {
        _container = container;
        _user = user;

        _tableInfoSpecimenEvent = SpecimenSchema.get().getTableInfoSpecimenEvent(_container);
        _tableInfoVial = SpecimenSchema.get().getTableInfoVial(_container);
        _tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(_container);
        _tableInfoPrimaryType = SpecimenSchema.get().getTableInfoSpecimenPrimaryType(_container);
        _tableInfoLocation = SpecimenSchema.get().getTableInfoLocation(_container);
        _tableInfoDerivative = SpecimenSchema.get().getTableInfoSpecimenDerivative(_container);
        _tableInfoAdditive = SpecimenSchema.get().getTableInfoSpecimenAdditive(_container);
        _dialect = _tableInfoSpecimen.getSqlDialect();

        _specimenColumns = determineSpecimenColumns();
    }

    protected SqlDialect getSqlDialect()
    {
        return _dialect;
    }

    protected TableInfo getTableInfoSpecimen()
    {
        return _tableInfoSpecimen;
    }

    protected TableInfo getTableInfoVial()
    {
        return _tableInfoVial;
    }

    protected TableInfo getTableInfoSpecimenEvent()
    {
        return _tableInfoSpecimenEvent;
    }

    protected TableInfo getTableInfoPrimaryType()
    {
        return _tableInfoPrimaryType;
    }
    protected TableInfo getTableInfoLocation()
    {
        return _tableInfoLocation;
    }

    protected TableInfo getTableInfoDerivative()
    {
        return _tableInfoDerivative;
    }

    protected TableInfo getTableInfoAdditive()
    {
        return _tableInfoAdditive;
    }

    public TableInfo getTableInfoFromFkTableName(String fkTableName)
    {
        if ("Site".equalsIgnoreCase(fkTableName))
            return getTableInfoLocation();
        if ("SpecimenPrimaryType".equalsIgnoreCase(fkTableName))
            return getTableInfoPrimaryType();
        if ("SpecimenDerivative".equalsIgnoreCase(fkTableName))
            return getTableInfoDerivative();
        if ("SpecimenAdditive".equalsIgnoreCase(fkTableName))
            return getTableInfoAdditive();
        throw new IllegalStateException("Unexpected table name.");
    }

    protected Container getContainer()
    {
        return _container;
    }

    protected User getUser()
    {
        return _user;
    }

    protected final Collection<SpecimenColumn> _specimenColumns;

    public Collection<SpecimenColumn> getSpecimenColumns()
    {
        return _specimenColumns;
    }

    protected RollupMap<EventVialRollup> getEventToVialRollups()
    {
        return _eventToVialRollups;
    }

    private Collection<SpecimenColumn> determineSpecimenColumns()
    {
        Collection<SpecimenColumn> specimenColumns = new ArrayList<>(SpecimenColumns.BASE_SPECIMEN_COLUMNS);

        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(_container, _user, null);

        Domain vialDomain = specimenTablesProvider.getDomain("Vial", true);
        if (null == vialDomain)
            throw new IllegalStateException("Expected Vial domain to already be created.");

        List<PropertyDescriptor> vialProperties = new ArrayList<>();
        for (DomainProperty domainProperty : vialDomain.getNonBaseProperties())
            vialProperties.add(domainProperty.getPropertyDescriptor());

        Domain specimenEventDomain = specimenTablesProvider.getDomain("SpecimenEvent", true);
        if (null == specimenEventDomain)
            throw new IllegalStateException("Expected SpecimenEvent domain to already be created.");

        SqlDialect dialect = getTableInfoSpecimen().getSqlDialect();

        Set<DomainProperty> eventBaseProperties = specimenEventDomain.getBaseProperties();
        for (DomainProperty domainProperty : specimenEventDomain.getProperties())
        {
            PropertyDescriptor property = domainProperty.getPropertyDescriptor();
            if (!eventBaseProperties.contains(domainProperty))
            {
                SpecimenColumn specimenColumn = new SpecimenColumn(property.getName().toLowerCase(), property.getImportAliasSet(), property.getStorageColumnName(), getTypeName(property, dialect), TargetTable.SPECIMEN_EVENTS);
                specimenColumns.add(specimenColumn);
            }
            RollupHelper.findRollups(_eventToVialRollups, property, vialProperties, RollupHelper.getEventVialRollups(), false);
        }

        return specimenColumns;
    }

    private static final Map<JdbcType, String> JDBCtoIMPORTER_TYPE = new HashMap<>();
    static
    {
        JDBCtoIMPORTER_TYPE.put(JdbcType.DATE, ImportTypes.DATETIME_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.TIMESTAMP, ImportTypes.DATETIME_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.TIME, ImportTypes.DURATION_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.DECIMAL, ImportTypes.NUMERIC_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.BOOLEAN, ImportTypes.BOOLEAN_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.BINARY, ImportTypes.BINARY_TYPE);
        JDBCtoIMPORTER_TYPE.put(JdbcType.BIGINT, "BIGINT");
        JDBCtoIMPORTER_TYPE.put(JdbcType.INTEGER, "INT");
        JDBCtoIMPORTER_TYPE.put(JdbcType.REAL, "FLOAT");
        JDBCtoIMPORTER_TYPE.put(JdbcType.DOUBLE, null);
        JDBCtoIMPORTER_TYPE.put(JdbcType.VARCHAR, "VARCHAR");
    }

    private String getTypeName(PropertyDescriptor property, SqlDialect dialect)
    {
        String typeName = JDBCtoIMPORTER_TYPE.get(property.getJdbcType());
        if (null == typeName)
            typeName = dialect.getSqlTypeName(property.getJdbcType());
        if (null == typeName)
            throw new UnsupportedOperationException("Unsupported JdbcType: " + property.getJdbcType().toString());
        if ("VARCHAR".equals(typeName))
            typeName = String.format("VARCHAR(%d)", property.getScale());
        return typeName;
    }
}
