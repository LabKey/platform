package org.labkey.elispot.query;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.study.query.PlateBasedAssayRunDataTable;
import org.labkey.elispot.ElispotDataHandler;
import org.labkey.elispot.ElispotSchema;

import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 21, 2008
 */
public class ElispotRunDataTable extends PlateBasedAssayRunDataTable
{
    public ElispotRunDataTable(final QuerySchema schema, String alias, final ExpProtocol protocol)
    {
        super(schema, alias, protocol);
    }

    public PropertyDescriptor[] getExistingDataProperties(ExpProtocol protocol) throws SQLException
    {
        return ElispotSchema.getExistingDataProperties(protocol);
    }

    public String getInputMaterialPropertyName()
    {
        return ElispotDataHandler.ELISPOT_INPUT_MATERIAL_DATA_PROPERTY;
    }

    public String getDataRowLsidPrefix()
    {
        return ElispotDataHandler.ELISPOT_DATA_ROW_LSID_PREFIX;
    }
}
