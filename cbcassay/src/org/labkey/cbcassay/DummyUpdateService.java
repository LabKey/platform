package org.labkey.cbcassay;

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.RunDataTable;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: Dec 18, 2009 2:54:53 PM
 */
public class DummyUpdateService implements QueryUpdateService
{

    public DummyUpdateService()
    {
    }

    public Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("NYI");
    }

    public Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("NYI");
    }

    public Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("NYI");
    }

    public Map<String, Object> deleteRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("NYI");
    }
}
