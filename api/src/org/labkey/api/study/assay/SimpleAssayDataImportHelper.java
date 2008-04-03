package org.labkey.api.study.assay;

import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.ObjectProperty;

import java.util.Map;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Jul 23, 2007
 */
public class SimpleAssayDataImportHelper implements OntologyManager.ImportHelper
{
    private int _id = 0;
    private String _dataLSID;
    public SimpleAssayDataImportHelper(String dataLSID)
    {
        _dataLSID = dataLSID;
    }

    public String beforeImportObject(Map map) throws SQLException
    {
        return _dataLSID + ".DataRow-" + _id++;
    }

    public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
    {

    }
}
