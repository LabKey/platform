/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BooleanConverter;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.GUID;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.common.util.CPUTimer;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 3:23:15 PM
 */
@SuppressWarnings({"StringConcatenationInsideStringBufferAppend"})
public class OntologyManager
{
	static HashMap classMap;
    static Logger _log = Logger.getLogger(OntologyManager.class);
	static DatabaseCache<Map<String, ObjectProperty>> mapCache = null;
	static DatabaseCache<Integer> objectIdCache = null;
	static DatabaseCache<PropertyDescriptor> propDescCache = null;
	static DatabaseCache<DomainDescriptor> domainDescCache = null;
    static Container _sharedContainer = ContainerManager.getSharedContainer();

    static
	{
		BeanObjectFactory.Registry.register(ObjectProperty.class, new ObjectProperty.ObjectPropertyObjectFactory());
        initCaches();
    }


	private OntologyManager()
	{
	}


    public static void initCaches()
    {
        mapCache = new DatabaseCache<Map<String, ObjectProperty>>(getExpSchema().getScope(), 1000);
        objectIdCache = new DatabaseCache<Integer>(getExpSchema().getScope(), 1000);
        propDescCache = new DatabaseCache<PropertyDescriptor>(getExpSchema().getScope(), 2000);
        domainDescCache = new DatabaseCache<DomainDescriptor>(getExpSchema().getScope(), 2000);
    }

    
    /** @return map from PropertyURI to value */
    public static Map<String, Object> getProperties(Container container, String parentLSID) throws SQLException
	{
		Map<String, Object> m = new HashMap<String, Object>();
		Map<String, ObjectProperty> propVals = getPropertyObjects(container, parentLSID);
		if (null != propVals)
		{
			for (Map.Entry<String, ObjectProperty> entry : propVals.entrySet())
			{
				m.put(entry.getKey(), entry.getValue().value());
			}
		}

		return m;
	}


    /** This helper returns a map of names -> PropertyDescriptor that is useful for import */
    public static Map<String,PropertyDescriptor> createImportPropertyMap(PropertyDescriptor[] descriptors)
    {
        HashMap<String,PropertyDescriptor> m = new CaseInsensitiveHashMap<PropertyDescriptor>(descriptors.length * 3);
        for (PropertyDescriptor pd : descriptors)
        {
            if (pd.isQcEnabled())
                m.put(pd.getName() + QcColumn.QC_INDICATOR_SUFFIX, pd);
        }
        for (PropertyDescriptor pd : descriptors)
        {
            if (null != pd.getLabel())
                m.put(pd.getLabel(), pd);
            else
                m.put(ColumnInfo.captionFromName(pd.getName()), pd); // If no label, columns will create one for captions
        }
        for (PropertyDescriptor pd : descriptors)
        {
            m.put(pd.getName(), pd);
        }
        for (PropertyDescriptor pd : descriptors)
        {
            m.put(pd.getPropertyURI(), pd);
        }
        return m;
    }
    

    public static final int MAX_PROPS_IN_BATCH = 10000;

    public static String[] insertTabDelimited(Container c, Integer ownerObjectId, ImportHelper helper, PropertyDescriptor[] descriptors, List<Map<String, Object>> rows, boolean ensureObjects) throws SQLException, ValidationException
    {
		CPUTimer total  = new CPUTimer("insertTabDelimited");
		CPUTimer before = new CPUTimer("beforeImport");
		CPUTimer ensure = new CPUTimer("ensureObject");
		CPUTimer insert = new CPUTimer("insertProperties");

		assert total.start();
		assert getExpSchema().getScope().isTransactionActive();
		String[] resultingLsids = new String[rows.size()];
        int resultingLsidsIndex = 0;
        // Make sure we have enough rows to hande the overflow of the current row so we don't have to resize the list
        List<PropertyRow> propsToInsert = new ArrayList<PropertyRow>(MAX_PROPS_IN_BATCH + descriptors.length);

		try
		{
			// UNDONE: add PropertyDescriptor.getPropertyType()
            PropertyType[] propertyTypes = new PropertyType[descriptors.length];
            for (int i = 0; i < descriptors.length; i++)
                propertyTypes[i] = PropertyType.getFromURI(descriptors[i].getConceptURI(), descriptors[i].getRangeURI());

            OntologyObject objInsert = new OntologyObject();
			objInsert.setContainer(c);
			objInsert.setOwnerObjectId(ownerObjectId);

            List<ValidationError> errors = new ArrayList<ValidationError>();
            Map<Integer, IPropertyValidator[]> validatorMap = new HashMap<Integer, IPropertyValidator[]>();

            // cache all the property validators for this upload
            for (PropertyDescriptor pd : descriptors)
            {
                IPropertyValidator[] validators = PropertyService.get().getPropertyValidators(pd);
                if (validators.length > 0)
                    validatorMap.put(pd.getPropertyId(), validators);
            }

			for (Map map : rows)
			{
				assert before.start();
				String lsid = helper.beforeImportObject(map);
				resultingLsids[resultingLsidsIndex++] = lsid;
				assert before.stop();

				assert ensure.start();
				int objectId;
				if (ensureObjects)
					objectId = ensureObject(c, lsid, ownerObjectId);
				else
				{
					objInsert.setObjectURI(lsid);
					Table.insert(null, getTinfoObject(), objInsert);
					objectId = objInsert.getObjectId();
				}
				assert ensure.stop();

				assert insert.start();
				for (int i = 0; i < descriptors.length; i++)
				{
					PropertyDescriptor pd = descriptors[i];
					Object value = map.get(pd.getPropertyURI());
					if (null == value)
                    {
                        if (pd.isRequired())
                            throw new ValidationException("Missing value for required property " + pd.getName());
                        else
                        {
                            continue;
                        }
                    }
                    else
                    {
                        if (validatorMap.containsKey(pd.getPropertyId()))
                            validateProperty(validatorMap.get(pd.getPropertyId()), pd, value, errors);
                    }
                    PropertyRow row = new PropertyRow(objectId, pd, value, propertyTypes[i]);
					propsToInsert.add(row);
				}

                if (propsToInsert.size() > MAX_PROPS_IN_BATCH)
                {
                    assert insert.start();
                    insertPropertiesBulk(c, propsToInsert);
                    assert insert.stop();
                    propsToInsert = new ArrayList<PropertyRow>(MAX_PROPS_IN_BATCH + descriptors.length);
                }
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);

            assert insert.start();
			insertPropertiesBulk(c, propsToInsert);
			assert insert.stop();
		}
		catch (SQLException x)
		{
            SQLException next = x.getNextException();
            if (x instanceof java.sql.BatchUpdateException && null != next)
                x = next;
            _log.debug("Exception uploading: ", x);
			throw x;
		}

		assert total.stop();
		_log.debug("\t" + total.toString());
		_log.debug("\t" + before.toString());
		_log.debug("\t" + ensure.toString());
		_log.debug("\t" + insert.toString());

		return resultingLsids;
	}

    private static boolean validateProperty(IPropertyValidator[] validators, PropertyDescriptor prop, Object value, List<ValidationError> errors)
    {
        boolean ret = true;

        if (validators != null)
        {
            for (IPropertyValidator validator : validators)
                if (!validator.validate(prop.getLabel() != null ? prop.getLabel() : prop.getName(), value, errors)) ret = false;
        }
        return ret;
    }

    public interface ImportHelper
	{
		/** return LSID for new or existing Object */
		String beforeImportObject(Map<String, Object> map) throws SQLException;
		void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException;
	}


	public static class SubstImportHelper implements OntologyManager.ImportHelper
	{
		StringExpressionFactory.StringExpression expr;

		public SubstImportHelper(String expr)
		{
			this.expr = StringExpressionFactory.create(expr);
		}

		public String beforeImportObject(Map<String, Object> map) throws SQLException
		{
			return expr.eval(map);
		}

		public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
		{
		}
	}


	public static class GuidImportHelper implements OntologyManager.ImportHelper
	{
		public String beforeImportObject(Map<String, Object> map) throws SQLException
		{
			return GUID.makeURN();
		}

		public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
		{
		}
	}


	/**
	 * Get the name for a property. First check name attached to the property
	 * then see if we have a descriptor for the property and load that...
     *
     * Canonical propertyURI:   <vocabulary>#<domainName>.<propertyName>
     *
	 */
	public static String getPropertyName(String propertyURI, Container c)
	{
		PropertyDescriptor pd = getPropertyDescriptor(propertyURI, c);
		if (null != pd)
			return pd.getName();
		else
		{
            //TODO:   verify that this default property name scheme is consistent with major ontologies
            int i = Math.max(propertyURI.lastIndexOf(':'), propertyURI.lastIndexOf('#'));
            i = Math.max(propertyURI.lastIndexOf('.'), i);
            if (i != -1)
				return propertyURI.substring(i + 1);
			return propertyURI;
		}
	}

    /**
     * @return map from PropertyURI to ObjectProperty
     */
    public static Map<String, ObjectProperty> getPropertyObjects(Container container, String objectLSID) throws SQLException
	{
		Map<String, ObjectProperty> m = mapCache.get(objectLSID);
		if (null != m)
			return m;
		try
		{
			m = getObjectPropertiesFromDb(container, objectLSID);
			mapCache.put(objectLSID, m);
			return m;
		}
		catch (SQLException x)
		{
			_log.error("Loading property values for: " + objectLSID, x);
			throw x;
		}
	}

    
    private static Map<String, ObjectProperty> getObjectPropertiesFromDb(Container container, String parentURI) throws SQLException
	{
		return getObjectPropertiesFromDb(container, new SimpleFilter("ObjectURI", parentURI));
    }

    private static Map<String, ObjectProperty> getObjectPropertiesFromDb(Container container, int objectId) throws SQLException
    {
        return getObjectPropertiesFromDb(container, new SimpleFilter("ObjectId", objectId));
    }

    private static Map<String, ObjectProperty> getObjectPropertiesFromDb(Container container, SimpleFilter filter) throws SQLException
    {
        if (container != null)
        {
            filter.addCondition("Container", container.getId());
        }
        ObjectProperty[] pvals = Table.select(getTinfoObjectPropertiesView(), Table.ALL_COLUMNS, filter, null, ObjectProperty.class);
		Map<String, ObjectProperty> m = new HashMap<String, ObjectProperty>();
		for (ObjectProperty value : pvals)
		{
			m.put(value.getPropertyURI(), value);
		}

		return Collections.unmodifiableMap(m);
	}


	public static int ensureObject(Container container, String objectURI) throws SQLException
	{
		return ensureObject(container, objectURI, (Integer) null);
	}

	public static int ensureObject(Container container, String objectURI, String ownerURI) throws SQLException
	{
		Integer ownerId = null;
		if (null != ownerURI)
			ownerId = ensureObject(container, ownerURI, (Integer) null);
		return ensureObject(container, objectURI, ownerId);
	}

    public static int ensureObject(Container container, String objectURI, Integer ownerId) throws SQLException
	{
		//TODO: (marki) Transact?
		Integer i = objectIdCache.get(objectURI);
		if (null != i)
			return i.intValue();

		OntologyObject o = getOntologyObject(container, objectURI);
		if (null == o)
		{
			o = new OntologyObject();
			o.setContainer(container);
			o.setObjectURI(objectURI);
			o.setOwnerObjectId(ownerId);
			o = Table.insert(null, getTinfoObject(), o);
		}

		objectIdCache.put(objectURI, o.getObjectId());
		return o.getObjectId();
	}


	private static OntologyObject getOntologyObject(Container container, String uri) throws SQLException
	{
		SimpleFilter filter = new SimpleFilter("ObjectURI", uri);
        if (container != null)
        {
            filter.addCondition("Container", container.getId());
        }
        return Table.selectObject(getTinfoObject(), filter, null, OntologyObject.class);
	}


    // UNDONE: optimize (see deleteOntologyObjects(Integer[])
    public static void deleteOntologyObjects(Container c, String... uris) throws SQLException
    {
        if (uris.length == 0)
            return;

        try
        {
            DbSchema schema = getExpSchema();
            String sql = getSqlDialect().execute(getExpSchema(), "deleteObject", "?, ?");
            Object[] params = new Object[] {c.getId(), null};
            for (String uri : uris)
            {
                params[1] = uri;
                Table.execute(schema, sql, params);
            }
        }
        finally
        {
            mapCache.clear();
            objectIdCache.clear();
        }
    }


    public static void deleteOntologyObjects(DbSchema schema, SQLFragment sub, Container c, boolean deleteOwnedObjects) throws SQLException
    {
        // we have different levels of optimization possible here deleteOwned=true/false, scope=/<>exp

        // let's handle one case
        if (!schema.getScope().equals(getExpSchema().getScope()))
            throw new UnsupportedOperationException("can only use with same DbScope");

        // CONSIDER: use temp table for objectids?

        if (deleteOwnedObjects)
        {
            throw new UnsupportedOperationException("Don't do this yet either");
        }
        else
        {
            SQLFragment sqlDeleteProperties = new SQLFragment();
            sqlDeleteProperties.append("DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN\n" +
                    "(SELECT ObjectId FROM " + getTinfoObject() + "\n" +
                    " WHERE Container = ? AND ObjectURI IN (");
            sqlDeleteProperties.add(c.getId());
            sqlDeleteProperties.append(sub);
            sqlDeleteProperties.append("))");
            Table.execute(getExpSchema(), sqlDeleteProperties);

            SQLFragment sqlDeleteObjects = new SQLFragment();
            sqlDeleteObjects.append("DELETE FROM " + getTinfoObject() + " WHERE Container = ? AND ObjectURI IN (");
            sqlDeleteObjects.add(c.getId());
            sqlDeleteObjects.append(sub);
            sqlDeleteObjects.append(")");
            Table.execute(getExpSchema(), sqlDeleteObjects);
        }

        // fall back implementation
//        SQLFragment selectObjectIds = new SQLFragment();
//        selectObjectIds.append("SELECT ObjectId FROM exp.Object WHERE ObjectURI IN (");
//        selectObjectIds.append(sub);
//        selectObjectIds.append(")");
//        Integer[] objectIds = Table.executeArray(schema, selectObjectIds, Integer.class);
//        deleteOntologyObjects(objectIds, c, deleteOwnedObjects);
    }


    public static void deleteOntologyObjects(Integer[] objectIds, Container c, boolean deleteOwnedObjects) throws SQLException
    {
        if (objectIds.length == 0)
            return;

        try
        {
            // if uris is long, split it up
            if (objectIds.length > 1000)
            {
                int countBatches = objectIds.length/1000;
                int lenBatch = 1+objectIds.length/(countBatches+1);
                ArrayList<Integer> sub = new ArrayList<Integer>(lenBatch);
                for (int s=0 ; s<objectIds.length; s+=lenBatch)
                {
                    int end = Math.min(s+lenBatch,objectIds.length);
                    System.err.println("delete " + s + "-" + end);
                    sub.clear();
                    for (int i=s ; i<end ; i++)
                        sub.add(objectIds[i]);
                    deleteOntologyObjects(sub.toArray(new Integer[sub.size()]), c, deleteOwnedObjects);
                }
                return;
            }

            StringBuilder in = new StringBuilder();
            for (Integer objectId: objectIds)
            {
                in.append(objectId);
                in.append(",");
            }
            in.setLength(in.length()-1);

            if (deleteOwnedObjects)
            {
                // NOTE: owned objects should never be in a different container than the owner, that would be a problem
                StringBuilder sqlDeleteOwnedProperties = new StringBuilder();
                sqlDeleteOwnedProperties.append("DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = '").append(c.getId()).append("' AND OwnerObjectId IN (");
                sqlDeleteOwnedProperties.append(in);
                sqlDeleteOwnedProperties.append("))");
                Table.execute(getExpSchema(), sqlDeleteOwnedProperties.toString(), null);

                StringBuilder sqlDeleteOwnedObjects = new StringBuilder();
                sqlDeleteOwnedObjects.append("DELETE FROM " + getTinfoObject() + " WHERE Container = '").append(c.getId()).append("' AND OwnerObjectId IN (");
                sqlDeleteOwnedObjects.append(in);
                sqlDeleteOwnedObjects.append(")");
                Table.execute(getExpSchema(), sqlDeleteOwnedObjects.toString(), null);
            }

            StringBuilder sqlDeleteProperties = new StringBuilder();
            sqlDeleteProperties.append("DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = '").append(c.getId()).append("' AND ObjectId IN (");
            sqlDeleteProperties.append(in);
            sqlDeleteProperties.append("))");
            Table.execute(getExpSchema(), sqlDeleteProperties.toString(), null);

            StringBuilder sqlDeleteObjects = new StringBuilder();
            sqlDeleteObjects.append("DELETE FROM " + getTinfoObject() + " WHERE Container = '").append(c.getId()).append("' AND ObjectId IN (");
            sqlDeleteObjects.append(in);
            sqlDeleteObjects.append(")");
            Table.execute(getExpSchema(), sqlDeleteObjects.toString(), null);
        }
        finally
        {
            mapCache.clear();
            objectIdCache.clear();
        }
    }


    public static void deleteOntologyObject(String objectURI, Container container, boolean deleteOwnedObjects) throws SQLException
	{

        OntologyObject ontologyObject = getOntologyObject(container, objectURI);
        if (null!=ontologyObject)
        {
            Integer objid = ontologyObject.getObjectId();
            deleteOntologyObjects(new Integer[]{objid}, container, deleteOwnedObjects);
        }
    }


    public static OntologyObject getOntologyObject(int id)
    {
        return Table.selectObject(getTinfoObject(), id, OntologyObject.class);
    }

    //todo:  review this.  this doesn't delete the underlying data objects.  should it?
    public static void deleteObjectsOfType(String domainURI, Container container) throws SQLException
    {
        boolean ownTransaction = !getExpSchema().getScope().isTransactionActive();
        DomainDescriptor dd = null;
        if (null!= domainURI)
            dd = getDomainDescriptor(domainURI, container);
        if (null==dd)
        {
            _log.debug("deleteObjectsOfType called on type not found in database:  " + domainURI );
            return;
        }

        try
        {
            if (ownTransaction)
                getExpSchema().getScope().beginTransaction();

            // until we set a domain on objects themselves, we need to create a list of objects to
            // delete based on existing entries in ObjectProperties before we delete the objectProperties
            // which we need to do before we delete the objects
            String selectObjectsToDelete = "SELECT DISTINCT O.ObjectId " +
                    " FROM " + getTinfoObject() + " O " +
                    " INNER JOIN " + getTinfoObjectProperty() + " OP ON(O.ObjectId = OP.ObjectId) " +
                    " INNER JOIN " + getTinfoPropertyDomain() + " PDM ON (OP.PropertyId = PDM.PropertyId) " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                    " INNER JOIN " + getTinfoPropertyDescriptor() + " PD ON (PD.PropertyId = PDM.PropertyId) " +
                    " WHERE DD.DomainId = " + dd.getDomainId() +
                    " AND PD.Container = DD.Container";
            Integer[] objIdsToDelete = Table.executeArray(getExpSchema(), selectObjectsToDelete, new Object[]{}, Integer.class);

            String sep;
            StringBuilder sqlIN=null;
            Integer[] ownerObjIds=null;

            if (objIdsToDelete.length > 0)
            {
                //also need list of owner objects whose subobjects are going to be deleted
                // Seems cheaper but less correct to delete the subobjects then cleanup any owner objects with no children
                sep = "";
                sqlIN = new StringBuilder();
                for (Integer id : objIdsToDelete)
                {
                    sqlIN.append(sep + id);
                    sep = ", ";
                }

                String selectOwnerObjects = "SELECT O.ObjectId FROM " + getTinfoObject() + " O " +
                        " WHERE ObjectId IN " +
                        " (SELECT DISTINCT SUBO.OwnerObjectId FROM " + getTinfoObject() + " SUBO " +
                        " WHERE SUBO.ObjectId IN ( " + sqlIN.toString() + " ) )";

                ownerObjIds = Table.executeArray(getExpSchema(), selectOwnerObjects, new Object[]{}, Integer.class);
            }

            String deleteTypePropsSql = "DELETE FROM " + getTinfoObjectProperty() +
                    " WHERE PropertyId IN " +
                    " (SELECT PDM.PropertyId FROM " + getTinfoPropertyDomain() + " PDM " +
                    " INNER JOIN " + getTinfoPropertyDescriptor() + " PD ON (PDM.PropertyId = PD.PropertyId) " +
                    " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                    " WHERE DD.DomainId = " + dd.getDomainId() +
                    " AND PD.Container = DD.Container " +
                    " ) ";
            Table.execute(getExpSchema(), deleteTypePropsSql, new Object[] {});

            if (objIdsToDelete.length > 0)
            {
                // now cleanup the object table entries from the list we made, but make sure they don't have
                // other properties attached to them
                String deleteObjSql = "DELETE FROM " + getTinfoObject() +
                        " WHERE ObjectId IN ( " + sqlIN.toString() + " ) " +
                        " AND NOT EXISTS (SELECT * FROM " + getTinfoObjectProperty() + " OP " +
                        " WHERE  OP.ObjectId = " + getTinfoObject() + ".ObjectId)";
                Table.execute(getExpSchema(), deleteObjSql, new Object[]{});

                if (ownerObjIds.length>0)
                {
                    sep="";
                    sqlIN = new StringBuilder();
                    for (Integer id : ownerObjIds)
                    {
                        sqlIN.append(sep + id);
                        sep = ", ";
                    }
                    String deleteOwnerSql = "DELETE FROM " + getTinfoObject() +
                            " WHERE ObjectId IN ( " + sqlIN.toString() + " ) " +
                            " AND NOT EXISTS (SELECT * FROM " + getTinfoObject() + " SUBO " +
                            " WHERE SUBO.OwnerObjectId = " + getTinfoObject() + ".ObjectId)";
                    Table.execute(getExpSchema(), deleteOwnerSql , new Object[]{});
                }
            }
            // whew!
            clearCaches();
            if (ownTransaction)
            {
                getExpSchema().getScope().commitTransaction();
                ownTransaction = false;
            }
        }
        finally
        {
            if (ownTransaction)
                getExpSchema().getScope().closeConnection();
        }
    }

    public static void deleteDomain(String domainURI, Container container) throws DomainNotFoundException
    {
        boolean ownTransaction = !getExpSchema().getScope().isTransactionActive();
        DomainDescriptor dd = getDomainDescriptor(domainURI, container);
        String msg;
        if (null==dd)
            throw new DomainNotFoundException(domainURI);
        
        if (!dd.getContainer().getId().equals(container.getId()))
        {
            // this domain was not created in this folder. Allow if in the project-level root
            if (!dd.getProject().getId().equals(container.getId()))
            {
                msg = "DeleteDomain:  Domain can only be deleted in original container or from the project root "
                        + "\nDomain:  " + domainURI + " project "+ dd.getProject().getName() + " original container " + dd.getContainer().getPath();
                _log.error(msg);
                throw new RuntimeException(msg);
            }
        }
        try
        {
            if (ownTransaction)
                getExpSchema().getScope().beginTransaction();

            String selectPDsToDelete = "SELECT DISTINCT PDM.PropertyId " +
                            " FROM " + getTinfoPropertyDomain() + " PDM " +
                            " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (PDM.DomainId = DD.DomainId) " +
                            " WHERE DD.DomainId = ? ";

            Integer[] pdIdsToDelete = Table.executeArray(getExpSchema(), selectPDsToDelete, new Object[]{dd.getDomainId()}, Integer.class);

            String deletePDMs = "DELETE FROM " + getTinfoPropertyDomain() +
                    " WHERE DomainId =  " +
                    " (SELECT DD.DomainId FROM " + getTinfoDomainDescriptor() + " DD "+
                    " WHERE DD.DomainId = ? )";
            Table.execute(getExpSchema(), deletePDMs, new Object[] {dd.getDomainId()});

            if (pdIdsToDelete.length > 0)
            {
                String sep="";
                StringBuilder sqlIN = new StringBuilder();
                for (Integer id : pdIdsToDelete)
                {
                    PropertyService.get().deleteValidatorsForPropertyDescriptor(id);

                    sqlIN.append(sep);
                    sqlIN.append(id);
                    sep = ", ";
                }

                String deletePDs = "DELETE FROM " + getTinfoPropertyDescriptor() +
                            " WHERE PropertyId IN ( " + sqlIN.toString() + " ) " +
                            " AND Container = ? " +
                            " AND NOT EXISTS (SELECT * FROM " + getTinfoObjectProperty() + " OP " +
                                " WHERE  OP.PropertyId = " + getTinfoPropertyDescriptor() + ".PropertyId) " +
                            " AND NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() + " PDM " +
                                " WHERE  PDM.PropertyId = " + getTinfoPropertyDescriptor() + ".PropertyId) ";

                Table.execute(getExpSchema(), deletePDs, new Object[]{dd.getContainer().getId()});
            }

            String deleteDD = "DELETE FROM " + getTinfoDomainDescriptor() +
                        " WHERE DomainId = ? " +
                        " AND NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() + " PDM " +
                            " WHERE  PDM.DomainId = " + getTinfoDomainDescriptor() + ".DomainId) ";

            Table.execute(getExpSchema(), deleteDD, new Object[]{dd.getDomainId()});

            clearCaches();
            if (ownTransaction)
            {
                getExpSchema().getScope().commitTransaction();
                ownTransaction = false;
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (ownTransaction)
                getExpSchema().getScope().closeConnection();
        }
    }


    public static void deleteAllObjects(Container c) throws SQLException
	{
        String containerid = c.getId();
        Container projectContainer = c.getProject();
        if (null==projectContainer)
                projectContainer = c;

        boolean ownTransaction = !getExpSchema().getScope().isTransactionActive();
        //ownTransaction=false;
        try
		{
			if (ownTransaction)
				getExpSchema().getScope().beginTransaction();
            if (!c.equals(projectContainer))
            {
                copyDescriptors(c, projectContainer);
            }

            // Owned objects should be in same container, so this should work
			String deleteObjPropSql = "DELETE FROM " + getTinfoObjectProperty() + " WHERE  ObjectId IN (SELECT ObjectId FROM " + getTinfoObject() + " WHERE Container = ?)";
			Table.execute(getExpSchema(), deleteObjPropSql, new Object[]{containerid});
			String deleteObjSql = "DELETE FROM " + getTinfoObject() + " WHERE Container = ?";
			Table.execute(getExpSchema(), deleteObjSql, new Object[]{containerid});

            // delete property validator references on property descriptors
            PropertyService.get().deleteValidatorsForContainer(c);

            String deletePropDomSqlPD = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE PropertyId IN (SELECT PropertyId FROM " + getTinfoPropertyDescriptor() + " WHERE Container = ?)";
            Table.execute(getExpSchema(), deletePropDomSqlPD, new Object[]{containerid});
            String deletePropDomSqlDD = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE DomainId IN (SELECT DomainId FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?)";
            Table.execute(getExpSchema(), deletePropDomSqlDD, new Object[]{containerid});
            String deleteDomSql = "DELETE FROM " + getTinfoDomainDescriptor() + " WHERE Container = ?";
            Table.execute(getExpSchema(), deleteDomSql, new Object[]{containerid});
            // now delete the prop descriptors that are referenced in this container only
            String deletePropSql = "DELETE FROM " + getTinfoPropertyDescriptor() + " WHERE Container = ?";
            Table.execute(getExpSchema(), deletePropSql, new Object[]{containerid});

			clearCaches();
			if (ownTransaction)
			{
				getExpSchema().getScope().commitTransaction();
				ownTransaction = false;
			}
        }
		finally
		{
			if (ownTransaction)
				getExpSchema().getScope().closeConnection();
		}
	}

    public static void copyDescriptors (Container c, Container project) throws SQLException
    {
        // if c is (was) a project, then nothing to do
        if (c.getId().equals(project.getId()))
            return;

        // check to see if any Properties defined in this folder are used in other folders.
        // if so we will make a copy of all PDs and DDs to ensure no orphans
        String sql = " SELECT O.ObjectURI, O.Container, PD.PropertyId, PD.PropertyURI  " +
                " FROM " + getTinfoPropertyDescriptor() + " PD " +
                " INNER JOIN " + getTinfoObjectProperty() + " OP ON PD.PropertyId = OP.PropertyId" +
                " INNER JOIN " + getTinfoObject() + " O ON (O.ObjectId = OP.ObjectId) " +
                " WHERE PD.Container = ? " +
                " AND O.Container <> PD.Container ";
//                " GROUP BY O.ObjectURI, O.Container, PD.PropertyId ";
        ResultSet rsObjsUsingMyProps = null;
        ResultSet rsMyProps=null;

        try {
            rsObjsUsingMyProps = Table.executeQuery(getExpSchema(), sql, new Object[]{c.getId()}, 0, true);
            Map<String, ObjectProperty> mObjsUsingMyProps = new HashMap<String, ObjectProperty>();
            String sqlIn="";
            String sep="";
            String objURI;
            String propURI;
            String objContainer;
            Integer propId;
            while (rsObjsUsingMyProps.next())
            {
                objURI = rsObjsUsingMyProps.getString(1);
                objContainer = rsObjsUsingMyProps.getString(2);
                propId = rsObjsUsingMyProps.getInt(3);
                propURI = rsObjsUsingMyProps.getString(4);

                sqlIn += sep + propId ;
                sep = ", ";
                Map<String,ObjectProperty> mtemp = getPropertyObjects(ContainerManager.getForId(objContainer), objURI);
                if (null != mtemp)
                {
                    for (Map.Entry<String, ObjectProperty> entry : mtemp.entrySet())
                    {
                        if (entry.getValue().getPropertyURI().equals(propURI))
                            mObjsUsingMyProps.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            //For each property that is referenced outside its container, get the
            // domains that it belongs to and the other properties in those domains
            // so we can make copies of those domains and properties
            // Restrict it to properties and domains also in the same container

            if (mObjsUsingMyProps.size() > 0)
            {
                sql = "SELECT PD.PropertyURI, DD.DomainURI " +
                        " FROM " + getTinfoPropertyDescriptor() + " PD " +
                        " LEFT JOIN (" + getTinfoPropertyDomain() + " PDM " +
                        " INNER JOIN " + getTinfoPropertyDomain() + " PDM2 ON (PDM.DomainId = PDM2.DomainId) " +
                        " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId)) "+
                        " ON (PD.PropertyId = PDM2.PropertyId) " +
                        " WHERE PDM.PropertyId IN (" + sqlIn + " ) " +
                        " OR PD.PropertyId IN (" + sqlIn + " ) ";

                rsMyProps = Table.executeQuery(getExpSchema(), sql, new Object[]{}, 0, true);
            }
            String propUri;
            String domUri;
            if (null!=rsMyProps)
            {
                clearCaches();
                while (rsMyProps.next())
                {
                    propUri = rsMyProps.getString(1);
                    domUri =  rsMyProps.getString(2);
                    PropertyDescriptor pd = getPropertyDescriptor(propUri,c );
                    if (pd.getContainer().getId().equals(c.getId()))
                    {
                        pd.setContainer(project);
                        pd.setProject(project);
                        pd.setPropertyId(0);
                        ensurePropertyDescriptor(pd);
                    }
                    if (null !=domUri)
                    {
                        DomainDescriptor dd = getDomainDescriptor(domUri, c);
                        if (dd.getContainer().getId().equals(c.getId()))
                        {
                            dd.setContainer(project);
                            dd.setProject(project);
                            dd.setDomainId(0);
                            ensureDomainDescriptor(dd);
                            ensurePropertyDomain(propUri, domUri, project);
                        }
                    }
                }
                // now unhook the objects that refer to my properties and rehook them to the properties in their own project
                for (ObjectProperty op : mObjsUsingMyProps.values())
                {
                    deleteProperty(op.getObjectURI(), op.getPropertyURI(), op.getContainer(), c);
                    insertProperties(op.getContainer(), op.getObjectURI(), op);
                }
            }
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
        finally
        {
            if (null != rsObjsUsingMyProps) rsObjsUsingMyProps.close();
            if (null != rsMyProps) rsMyProps.close();
        }
    }


    public static void moveContainer(Container c, Container oldParent, Container newParent) throws SQLException
    {

        Container oldProject=c;
        Container newProject=c;

        if (null!=oldParent)
        {
            oldProject = oldParent.getProject();
        }
        if (null!=newParent)
        {
            newProject = newParent.getProject();
            if (null==newProject) // if container is promoted to a project
                newProject= c.getProject();
        }


        if ((null!=oldProject) && oldProject.getId().equals(newProject.getId()))
        {
            //the folder is being moved within the same project.  No problems here
            return;
        }

        String objURI;
        Integer propId;
        String propURI;
        String sql;
        ResultSet rsMyObjsThatRefProjProps=null;
        ResultSet rsPropsRefdByMe=null;

        boolean ownTransaction = !getExpSchema().getScope().isTransactionActive();
        try
        {
            if (ownTransaction)
                getExpSchema().getScope().beginTransaction();

            clearCaches();

            // update project of any descriptors in folder just moved
            sql = " UPDATE " + getTinfoPropertyDescriptor() + " SET Project = ? WHERE Container = ? ";
            Table.execute(getExpSchema(), sql , new Object[]{newProject.getId(), c.getId()});
            sql = " UPDATE " + getTinfoDomainDescriptor() + " SET Project = ? WHERE Container = ? ";
            Table.execute(getExpSchema(), sql , new Object[]{newProject.getId(), c.getId()});

            if (null==oldProject) // if container was a project & demoted I'm done
                return;

            // this method makes sure I'm not getting rid of descriptors used by another folder
            // it is shared by ContainerDelete
            copyDescriptors(c, oldProject);

            // if my objects refer to project-scoped properties I need a copy of those properties
            sql = " SELECT O.ObjectURI, PD.PropertyURI, PD.PropertyId  " +
                    " FROM " + getTinfoPropertyDescriptor() + " PD " +
                    " INNER JOIN " + getTinfoObjectProperty() + " OP ON PD.PropertyId = OP.PropertyId" +
                    " INNER JOIN " + getTinfoObject() + " O ON (O.ObjectId = OP.ObjectId) " +
                    " WHERE O.Container = ? " +
                    " AND O.Container <> PD.Container " +
                    " AND PD.Project <> ? ";
            rsMyObjsThatRefProjProps = Table.executeQuery(getExpSchema(), sql, new Object[]{c.getId(), _sharedContainer.getId()}, 0, true);

            Map<String, ObjectProperty> mMyObjsThatRefProjProps  = new HashMap<String, ObjectProperty>();
            String sqlIn="";
            String sep="";

            while (rsMyObjsThatRefProjProps.next())
            {
                objURI = rsMyObjsThatRefProjProps.getString(1);
                propURI = rsMyObjsThatRefProjProps.getString(2);
                    propId = rsMyObjsThatRefProjProps.getInt(3);

                sqlIn += sep + propId;
                sep = ", ";
                Map<String,ObjectProperty> mtemp = getPropertyObjects(c, objURI);
                if (null != mtemp)
                {
                    for (Map.Entry<String, ObjectProperty> entry : mtemp.entrySet())
                    {
                        if (entry.getValue().getPropertyURI().equals(propURI))
                            mMyObjsThatRefProjProps.put(entry.getKey(), entry.getValue());
                    }
                }

            }

            // this sql gets all properties i ref and the domains they belong to and the
            // other properties in those domains
            //todo  what about materialsource ?
            if (mMyObjsThatRefProjProps.size() > 0)
            {
                sql = "SELECT PD.PropertyURI, DD.DomainURI " +
                        " FROM " + getTinfoPropertyDescriptor() + " PD " +
                        " LEFT JOIN (" + getTinfoPropertyDomain() + " PDM " +
                        " INNER JOIN " + getTinfoPropertyDomain() + " PDM2 ON (PDM.DomainId = PDM2.DomainId) " +
                        " INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId)) "+
                        " ON (PD.PropertyId = PDM2.PropertyId) " +
                        " WHERE PDM.PropertyId IN (" + sqlIn + " ) ";

                rsPropsRefdByMe = Table.executeQuery(getExpSchema(), sql, new Object[]{}, 0, true);
            }

            String propUri;
            String domUri;

            if (null !=rsPropsRefdByMe)
            {
                while (rsPropsRefdByMe.next())
                {
                    propUri = rsPropsRefdByMe.getString(1);
                    domUri =  rsPropsRefdByMe.getString(2);
                    PropertyDescriptor pd = getPropertyDescriptor(propUri,oldProject );
                    if (null != pd)
                    {
                        pd.setContainer(c);
                        pd.setProject(newProject);
                        pd.setPropertyId(0);
                        ensurePropertyDescriptor(pd);
                    }
                    if (null != domUri)
                    {
                        DomainDescriptor dd = getDomainDescriptor(domUri, oldProject);
                        dd.setContainer(c);
                        dd.setProject(newProject);
                        dd.setDomainId(0);
                        ensureDomainDescriptor(dd);

                        ensurePropertyDomain(propUri, domUri, c);
                    }
                }

                for (ObjectProperty op : mMyObjsThatRefProjProps.values())
                {
                    deleteProperty(op.getObjectURI(), op.getPropertyURI(), op.getContainer(), oldProject);
                    insertProperties(op.getContainer(), op.getObjectURI(), op);
                }

            }

            if (ownTransaction)
            {
                getExpSchema().getScope().commitTransaction();
                ownTransaction = false;
            }
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
        finally
        {

            if (null != rsMyObjsThatRefProjProps)
                    rsMyObjsThatRefProjProps.close();
            if (null != rsPropsRefdByMe)
                    rsPropsRefdByMe.close();

            if (ownTransaction)
                getExpSchema().getScope().closeConnection();
        }
    }

    private static PropertyDescriptor ensurePropertyDescriptor(String propertyURI, String dataTypeURI, String name, Container container) throws SQLException
	{
        PropertyDescriptor pdNew = new PropertyDescriptor(propertyURI, dataTypeURI, name, container);
        return ensurePropertyDescriptor (pdNew);
    }


    private static PropertyDescriptor ensurePropertyDescriptor(PropertyDescriptor pdIn) throws SQLException
    {
         if (null == pdIn.getContainer())
            pdIn.setContainer(_sharedContainer);

        PropertyDescriptor pd = getPropertyDescriptor(pdIn.getPropertyURI(), pdIn.getContainer());
        if (null == pd)
        {
            assert pdIn.getPropertyId() == 0;
            pd = Table.insert(null, getTinfoPropertyDescriptor(), pdIn);
            propDescCache.put(getCacheKey(pd), pd);
        }
        else if (pd.equals(pdIn))
        {
            return pd;
        }
        else
        {
            List<String> colDiffs = comparePropertyDescriptors(pdIn, pd);

            if (colDiffs.size()==0)
            {
                // if the descriptor differs by container only and the requested descriptor is in the project fldr
                if (!pdIn.getContainer().getId().equals(pd.getContainer().getId()) &&
                        pdIn.getContainer().getId().equals(pdIn.getProject().getId()))
                {
                    pdIn.setPropertyId(pd.getPropertyId());
                     pd = updatePropertyDescriptor(pdIn);
                }
                return pd;
            }

            // you are allowed to update if you are coming from the project root, or if  you are in the container
            // in which the descriptor was created
            boolean fUpdateIfExists = false;
            if (pdIn.getContainer().getId().equals(pd.getContainer().getId())
                    || pdIn.getContainer().getId().equals(pdIn.getProject().getId()))
                    fUpdateIfExists = true;


            boolean fMajorDifference = false;
            if (colDiffs.toString().contains("RangeURI") || colDiffs.toString().contains("PropertyType"))
                fMajorDifference = true;

            String errmsg = "ensurePropertyDescriptor:  descriptor In different from Found for " + colDiffs.toString() +
                                    "\n\t Descriptor In: " + pdIn +
                                    "\n\t Descriptor Found: " + pd;

            if (fUpdateIfExists)
            {
                //todo:  pass list of cols to update
                pdIn.setPropertyId(pd.getPropertyId());
                pd = updatePropertyDescriptor(pdIn);
                if (fMajorDifference)
                    _log.debug(errmsg);
            }
            else
            {
                if (fMajorDifference)
                    _log.error(errmsg);
                else
                    _log.debug(errmsg);
            }
        }
        return pd;
	}


    private static List<String> comparePropertyDescriptors(PropertyDescriptor pdIn, PropertyDescriptor pd) throws SQLException
    {
        List<String> colDiffs = new ArrayList<String>();
        String val;

        // if the returned pd is in a different project, it better be the shared project
        if (!pd.getProject().equals(pdIn.getProject()) && !pd.getProject().equals(_sharedContainer))
            colDiffs.add("Project");

        // check the pd values that can't change
        if (!pd.getRangeURI().equals(pdIn.getRangeURI()))
            colDiffs.add("RangeURI");
        if (!pd.getPropertyType().equals(pdIn.getPropertyType()))
            colDiffs.add("PropertyType");

        if (pdIn.getPropertyId() != 0 && !(pd.getPropertyId() == pdIn.getPropertyId()))
            colDiffs.add("PropertyId");

        val = pdIn.getName();
        if (null != val && (pd.getName() == null || !pd.getName().equals(val)))
            colDiffs.add("Name");

        val = pdIn.getConceptURI();
        if (null != val && (pd.getConceptURI() == null || !pd.getConceptURI().equals(val)))
            colDiffs.add("ConceptURI");

        val = pdIn.getDescription();
        if (null != val && (pd.getDescription() == null || !pd.getDescription().equals(val)))
            colDiffs.add("Description");

        val = pdIn.getFormat();
        if (null != val && (pd.getFormat() == null || !pd.getFormat().equals(val)))
            colDiffs.add("Format");

        val = pdIn.getLabel();
        if (null != val && (pd.getLabel() == null || !pd.getLabel().equals(val)))
            colDiffs.add("Label");

        val = pdIn.getOntologyURI();
        if (null != val && (pd.getOntologyURI() == null || !pd.getOntologyURI().equals(val)))
            colDiffs.add("OntologyURI");

        val = pdIn.getSearchTerms();
        if (null != val && (pd.getSearchTerms() == null || !pd.getSearchTerms().equals(val)))
            colDiffs.add("SearchTerms");

        val = pdIn.getSemanticType();
        if (null != val && (pd.getSemanticType() == null || !pd.getSemanticType().equals(val)))
            colDiffs.add("SemanticType");

        return colDiffs;
    }

    public static DomainDescriptor ensureDomainDescriptor(String domainURI, String name, Container container) throws SQLException
    {
        DomainDescriptor ddIn = new DomainDescriptor(domainURI, container);
        ddIn.setName(name);
        return ensureDomainDescriptor(ddIn);
    }

    private static DomainDescriptor ensureDomainDescriptor(DomainDescriptor ddIn) throws SQLException
     {
        DomainDescriptor dd = getDomainDescriptor(ddIn.getDomainURI(), ddIn.getContainer());
        if (null == dd)
        {
            dd = Table.insert(null, getTinfoDomainDescriptor(), ddIn);
            domainDescCache.put(getCacheKey(dd),dd);
        }
        else
        {
            List<String> colDiffs = compareDomainDescriptors(ddIn, dd);

            if (colDiffs.size()==0)
            {
                // if the descriptor differs by container only and the requested descriptor is in the project fldr
                if (!ddIn.getContainer().getId().equals(dd.getContainer().getId()) &&
                        ddIn.getContainer().getId().equals(ddIn.getProject().getId()))
                {
                    ddIn.setDomainId(dd.getDomainId());
                     dd = updateDomainDescriptor(ddIn);
                }
                return dd;
            }

            // you are allowed to update if you are coming from the project root, or if  you are in the container
            // in which the descriptor was created
            boolean fUpdateIfExists = false;
            if (ddIn.getContainer().getId().equals(dd.getContainer().getId())
                    || ddIn.getContainer().getId().equals(ddIn.getProject().getId()))
                fUpdateIfExists = true;

            boolean fMajorDifference = false;
            if (colDiffs.toString().contains("RangeURI") || colDiffs.toString().contains("PropertyType"))
                fMajorDifference = true;

            String errmsg = "ensureDomainDescriptor:  descriptor In different from Found for " + colDiffs.toString() +
                                    "\n\t Descriptor In: " + ddIn +
                                    "\n\t Descriptor Found: " + dd;


            if (fUpdateIfExists)
            {
                //todo:  pass list of cols to update
                ddIn.setDomainId(dd.getDomainId());
                dd = updateDomainDescriptor(ddIn);
                if (fMajorDifference)
                    _log.debug(errmsg);
            }
            else
            {
                if (fMajorDifference)
                    _log.error(errmsg);
                else
                    _log.debug(errmsg);
            }
        }
        return dd;
    }

    private static List<String>  compareDomainDescriptors(DomainDescriptor ddIn, DomainDescriptor dd) throws SQLException
    {
        List<String> colDiffs = new ArrayList<String>();
        String val;

        if (ddIn.getDomainId() !=0 && !(dd.getDomainId() == ddIn.getDomainId()))
            colDiffs.add("DomainId");

        val=ddIn.getName();
        if (null!= val && !dd.getName().equals(val))
            colDiffs.add("Name");

        return colDiffs;
    }


    private static void ensurePropertyDomain(String propertyURI, String domainURI, Container c) throws SQLException
    {
        ensurePropertyDomain2(propertyURI, domainURI, c, null);
    }

    
    private static void ensurePropertyDomain2(String propertyURI, String domainURI, Container c, Boolean required) throws SQLException
    {
        PropertyDescriptor pd = getPropertyDescriptor(propertyURI, c);
        DomainDescriptor dd = getDomainDescriptor(domainURI, c);
        if (null == pd)
            throw new SQLException("ensurePropertyDomain:  property does not exist for " + propertyURI);
        if (null == dd)
            throw new SQLException("ensurePropertyDomain:  domain does not exist for " + domainURI);
        if (!pd.getContainer().equals(dd.getContainer())
                    &&  !pd.getProject().equals(_sharedContainer))
            throw new SQLException("ensurePropertyDomain:  property " + propertyURI + " not in same container as domain " + domainURI);

        boolean bRequired = null == required ? pd.isRequired() : required.booleanValue();

        SQLFragment sqlInsert = new SQLFragment("INSERT INTO " + getTinfoPropertyDomain() + " ( PropertyId, DomainId, Required ) " +
                    " SELECT ?, ?, ? WHERE NOT EXISTS (SELECT * FROM " + getTinfoPropertyDomain() +
                    " WHERE PropertyId=? AND DomainId=?)");
        sqlInsert.add(pd.getPropertyId());
        sqlInsert.add(dd.getDomainId());
        sqlInsert.add(bRequired);
        sqlInsert.add(pd.getPropertyId());
        sqlInsert.add(dd.getDomainId());
        int count = Table.execute(getExpSchema(), sqlInsert);
        // if 0 rows affected, we should do an update to make sure required is correct
        if (count == 0)
        {
            SQLFragment sqlUpdate = new SQLFragment("UPDATE " + getTinfoPropertyDomain() + " SET Required = ? WHERE PropertyId=? AND DomainId= ?");
            sqlUpdate.add(bRequired);
            sqlUpdate.add(pd.getPropertyId());
            sqlUpdate.add(dd.getDomainId());
            Table.execute(getExpSchema(), sqlUpdate);
        }
    }



	private static void insertProperties(Container c, ObjectProperty[] props) throws SQLException, ValidationException
	{
		HashMap<String,PropertyDescriptor> descriptors = new HashMap<String, PropertyDescriptor>();
		HashMap<String,Integer> objects = new HashMap<String, Integer>();
        List<ValidationError> errors = new ArrayList<ValidationError>();
       // assert !c.equals(ContainerManager.getRoot());

        for (ObjectProperty property : props)
		{
			if (null == property)
				continue;

			if (0 == property.getPropertyId())
			{
				PropertyDescriptor pd = descriptors.get(property.getPropertyURI());
				if (null == pd)
				{
                    PropertyDescriptor pdIn = new PropertyDescriptor(property.getPropertyURI(), property.getRangeURI(), property.getName(), c);
                    pdIn.setFormat(property.getFormat());
                    pd = ensurePropertyDescriptor(pdIn);
					descriptors.put(property.getPropertyURI(),pd);
				}
				property.setPropertyId(pd.getPropertyId());
                validateProperty(PropertyService.get().getPropertyValidators(pd), pd, property.value(), errors);
            }
			if (0 == property.getObjectId())
			{
				Integer objectId = objects.get(property.getObjectURI());
				if (null == objectId)
				{
					// I'm assuming all properties are in the same container
					objectId = ensureObject(property.getContainer(), property.getObjectURI(), property.getObjectOwnerId());
					objects.put(property.getObjectURI(), objectId);
				}
				property.setObjectId(objectId);
			}
        }
        if (!errors.isEmpty())
            throw new ValidationException(errors);
        insertPropertiesBulk(c, Arrays.asList((PropertyRow[])props));
	}


	private static void insertPropertiesBulk(Container container, List<PropertyRow> props) throws SQLException
	{
		ArrayList<Object[]> dates = new ArrayList<Object[]>();
		ArrayList<Object[]> floats = new ArrayList<Object[]>();
		ArrayList<Object[]> strings = new ArrayList<Object[]>();
        ArrayList<Object[]> qcValues = new ArrayList<Object[]>();

		for (PropertyRow property : props)
		{
			if (null == property)
				continue;

            int objectId = property.getObjectId();
            int propertyId = property.getPropertyId();
            String qcValue = property.getQcValue();
            assert qcValue == null || QcUtil.isQcValue(qcValue, container) : "Attempt to insert an invalid QC Value: " + qcValue;

            if (null != property.getFloatValue())
                floats.add(new Object[] {objectId, propertyId, property.getFloatValue(), qcValue});
            else if (null != property.getDateTimeValue())
                dates.add(new Object[] {objectId, propertyId, new java.sql.Timestamp(property.getDateTimeValue().getTime()), qcValue});
            else if (null != property.getStringValue())
            {
                String string = property.getStringValue();
                /* <UNDONE> handle truncation
                if (string.length() > ObjectProperty.STRING_LENGTH)
                    ;
                </UNDONE> */
                strings.add(new Object[] {objectId, propertyId, string, qcValue});
            }
            else if (null != qcValue)
            {
                qcValues.add(new Object[] {objectId, propertyId, property.getTypeTag(), qcValue});
            }
		}

		assert getExpSchema().getScope().isTransactionActive();

		if (dates.size() > 0)
		{
			String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, DateTimeValue, QcValue) VALUES (?,?,'d',?, ?)";
            Table.batchExecute(getExpSchema(), sql, dates);
		}

		if (floats.size() > 0)
		{
			String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, FloatValue, QcValue) VALUES (?,?,'f',?, ?)";
            Table.batchExecute(getExpSchema(), sql, floats);
		}

		if (strings.size() > 0)
		{
			String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, StringValue, QcValue) VALUES (?,?,'s',?, ?)";
            Table.batchExecute(getExpSchema(), sql, strings);
		}

        if (qcValues.size() > 0)
        {
            String sql = "INSERT INTO " + getTinfoObjectProperty().toString() + " (ObjectId, PropertyId, TypeTag, QcValue) VALUES (?,?,?,?)";
            Table.batchExecute(getExpSchema(), sql, qcValues);
        }

        clearPropertyCache();
    }

    public static void deleteProperty(String objectURI, String propertyURI, Container objContainer, Container propContainer)
    {
        try
        {
            OntologyObject o = getOntologyObject(objContainer, objectURI);
            if (o == null)
                return;

            PropertyDescriptor pd = getPropertyDescriptor(propertyURI, propContainer);
            if (pd == null)
                return;
            SimpleFilter filter = new SimpleFilter("ObjectId", o.getObjectId());
            filter.addCondition("PropertyId", pd.getPropertyId());
            Table.delete(getTinfoObjectProperty(), filter);
            clearPropertyCache(objectURI);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static void deletePropertyDescriptor(PropertyDescriptor pd) throws SQLException
    {
        boolean fTransStarted = false;

        int propId= pd.getPropertyId();
        String key = getCacheKey(pd);

        String deleteObjPropSql = "DELETE FROM " + getTinfoObjectProperty() + " WHERE PropertyId = ? ";
        String deletePropDomSql = "DELETE FROM " + getTinfoPropertyDomain() + " WHERE PropertyId = ? ";
        String deletePropSql = "DELETE FROM " + getTinfoPropertyDescriptor() + " WHERE PropertyId = ? ";

        try
        {
            if (!getExpSchema().getScope().isTransactionActive())
            {
                getExpSchema().getScope().beginTransaction();
                fTransStarted = true;
            }

            Table.execute(getExpSchema(), deleteObjPropSql, new Object[]{propId});
            Table.execute(getExpSchema(), deletePropDomSql, new Object[]{propId});
            Table.execute(getExpSchema(), deletePropSql, new Object[]{propId});
            propDescCache.remove(key);
            if (fTransStarted)
            {
                getExpSchema().getScope().commitTransaction();
                fTransStarted = false;
            }
        }
        finally
        {
            if (fTransStarted)
                getExpSchema().getScope().closeConnection();
        }
    }

    public static void insertProperties(Container container, String ownerObjectLsid, ObjectProperty... properties) throws ValidationException
    {
        boolean ownTransaction = !getExpSchema().getScope().isTransactionActive();
        try
        {
            if (ownTransaction)
                getExpSchema().getScope().beginTransaction();

            Integer parentId = ownerObjectLsid == null ? null : ensureObject(container, ownerObjectLsid);
            for (ObjectProperty oprop : properties)
            {
                oprop.setObjectOwnerId(parentId);
            }
            insertProperties(container, properties);

            if (ownTransaction)
            {
                getExpSchema().getScope().commitTransaction();
                ownTransaction = false;
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            if (ownTransaction)
                getExpSchema().getScope().closeConnection();
        }
    }


    public static PropertyDescriptor getPropertyDescriptor(int propertyId)
    {
        return Table.selectObject(getTinfoPropertyDescriptor(), propertyId, PropertyDescriptor.class);
    }

    
    public static PropertyDescriptor getPropertyDescriptor(String propertyURI, Container c)
	{
        try
        {
            // cache lookup by project. if not found at project level, check to see if global
            String key = getCacheKey(propertyURI , c);
            PropertyDescriptor pd = propDescCache.get(key);
            if (null != pd)
                return pd;

            key = getCacheKey(propertyURI, _sharedContainer);
            pd = propDescCache.get(key);
            if (null != pd)
                return pd;

            Container proj=c.getProject();
            if (null==proj)
                    proj=c;

            //TODO: Currently no way to edit these descriptors. But once
            //there is need to invalidate the cache.
		    String sql = " SELECT * FROM " + getTinfoPropertyDescriptor() + " WHERE PropertyURI = ? AND Project IN (?,?)";
            PropertyDescriptor[] pdArray = Table.executeQuery(getExpSchema(),  sql, new Object[]{propertyURI,
                                                                    proj.getId(),
                                                                    _sharedContainer.getId()},
                                                                    PropertyDescriptor.class);
            if (null != pdArray && pdArray.length > 0)
			{
                pd = pdArray[0];

                // if someone has explicitly inserted a descriptor with the same URI as an existing one ,
                // and one of the two is in the shared project, use the project-level descriiptor.
                if (pdArray.length>1)
                {
                    _log.debug("Multiple PropertyDescriptors found for "+ propertyURI);
                    if (pd.getProject().equals(_sharedContainer))
                        pd=pdArray[1];
                }

                key = getCacheKey(pd);
                propDescCache.put(key, pd);
			}

            return pd;

		}
		catch (SQLException x)
		{
			_log.error("Error getting property descriptor: " + propertyURI + " container: " + c, x);
			return null;
		}
//        finally { _log.debug("getPropertyDescriptor for "+ propertyURI + " container= "+ c.getPath() ); }
	}
    
    public static DomainDescriptor getDomainDescriptor(int id)
    {
        return Table.selectObject(getTinfoDomainDescriptor(), id, DomainDescriptor.class);
    }
        
    public static DomainDescriptor getDomainDescriptor(String domainURI, Container c)
	{
        try
        {
            // cache lookup by project. if not found at project level, check to see if global
            String key = getCacheKey(domainURI , c);
            DomainDescriptor dd = domainDescCache.get(key);
            if (null != dd)
                return dd;

            key = getCacheKey(domainURI , _sharedContainer);
            dd = domainDescCache.get(key);
            if (null != dd)
                return dd;

            Container proj=c.getProject();
            if (null==proj)
                proj=c;

            String sql = " SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE DomainURI = ? AND Project IN (?,?) ";
            DomainDescriptor[] ddArray = Table.executeQuery(getExpSchema(),  sql, new Object[]{domainURI,
                                                                    proj.getId(),
                                                                    _sharedContainer.getId()},
                                                                    DomainDescriptor.class);
            if (null != ddArray && ddArray.length > 0)
            {
                dd = ddArray[0];

                // if someone has explicitly inserted a descriptor with the same URI as an existing one ,
                // and one of the two is in the shared project, use the project-level descriiptor.
                if (ddArray.length>1)
                {
                    _log.debug("Multiple DomainDescriptors found for " + domainURI);
                    if (dd.getProject().equals(_sharedContainer))
                        dd = ddArray[1];
                }
                key = getCacheKey(dd);
                domainDescCache.put(key, dd);
            }
            return dd;
        }
        catch (SQLException x)
        {
            _log.error("Error getting domain descriptor: " + domainURI + " container: " + c, x);
            return null;
        }
        finally { _log.debug("getDomainDescriptor for "+ domainURI + " container= "+ c.getPath() ); }
    }

    public static DomainDescriptor[] getDomainDescriptors(Container container) throws SQLException
    {
        Map<String, DomainDescriptor> ret = new LinkedHashMap<String, DomainDescriptor>();
        String sql = "SELECT * FROM " + getTinfoDomainDescriptor() + " WHERE Project = ?";
        Container project = container.getProject();
        DomainDescriptor[] dds = Table.executeQuery(getExpSchema(), sql, new Object[] { project.getId() }, DomainDescriptor.class);
        for (DomainDescriptor dd : dds)
        {
            ret.put(dd.getDomainURI(), dd);
        }
        if (!project.equals(ContainerManager.getSharedContainer()))
        {
            dds = Table.executeQuery(getExpSchema(), sql, new Object[] { ContainerManager.getSharedContainer().getId()}, DomainDescriptor.class);
            for (DomainDescriptor dd : dds)
            {
                if (!ret.containsKey(dd.getDomainURI()))
                {
                    ret.put(dd.getDomainURI(), dd);
                }
            }
        }
        return ret.values().toArray(new DomainDescriptor[ret.size()]);
    }
    
    public static String getCacheKey (DomainDescriptor dd)
    {
        return getCacheKey(dd.getDomainURI(), dd.getContainer());
    }


    public static String getCacheKey (PropertyDescriptor pd)
    {
        return getCacheKey(pd.getPropertyURI(), pd.getContainer());
    }


    public static String getCacheKey (String uri, Container c)
    {
        Container proj = c.getProject();
        String projId;

        if (null==proj)
            projId = c.getId();
        else
            projId = proj.getId();

        return uri + "|" + projId;
    }


    /*
    public static PropertyDescriptor[] getPropertiesForType(String typeURI)
     {
            return getPropertiesForType(typeURI, _sharedContainer);
     }
     */


    //TODO: DbCache semantics. This loads the cache but does not fetch cause need to get them all together
    //
    public static PropertyDescriptor[] getPropertiesForType(String typeURI, Container c)
	{
        try
		{
            String sql = " SELECT PD.*,Required " +
                     " FROM " + getTinfoPropertyDescriptor() + " PD " +
                     "   INNER JOIN " + getTinfoPropertyDomain() + " PDM ON (PD.PropertyId = PDM.PropertyId) " +
                     "   INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId) " +
                     "  WHERE DD.DomainURI = ?  AND DD.Project IN (?,?) ORDER BY PD.PropertyId ASC ";

            Object[] params = new Object[]
            {
                typeURI,
                // If we're in the root, just double-up the shared project's id
                c.getProject() == null ? _sharedContainer.getProject().getId() : c.getProject().getId(),
                _sharedContainer.getProject().getId()
            };
            PropertyDescriptor[] pdArray = Table.executeQuery(getExpSchema(), sql, params, PropertyDescriptor.class);
            //NOTE: cached descriptors may have differing values of isRequired() as that is a per-domain setting
            //Descriptors returned from this method come direct from DB and have correct values.
            for (PropertyDescriptor pd : pdArray) {
				propDescCache.put(getCacheKey(pd), pd);
			}

			return pdArray;
		}
		catch (SQLException x)
		{
			throw new RuntimeSQLException(x);
		}
	}


    public static DomainDescriptor[] getDomainsForProperty(String propertyURI, Container c)
	{
        Container proj = c.getProject();
        if (null==proj)
            proj=c;
        try
		{
            String sql = " SELECT DD.* FROM " + getTinfoPropertyDescriptor() + " PD " +
                         "   INNER JOIN " + getTinfoPropertyDomain() + " PDM ON (PD.PropertyId = PDM.PropertyId) " +
                         "   INNER JOIN " + getTinfoDomainDescriptor() + " DD ON (DD.DomainId = PDM.DomainId) " +
                         "  WHERE PD.PropertyURI = ?  AND PD.Project = ? ORDER BY DD.DomainId ASC ";
            DomainDescriptor[] ddArray = Table.executeQuery(getExpSchema(), sql, new Object[]{propertyURI, proj}, DomainDescriptor.class );
            for (DomainDescriptor dd : ddArray) {
				domainDescCache.put(getCacheKey(dd),dd);
			}

			return ddArray;
		}
		catch (SQLException x)
		{
			_log.error("Error getting domain descriptors for property: " + propertyURI, x);
			return null;
		}
	}


	public static void deleteType(String domainURI, Container c) throws DomainNotFoundException
	{
        if (null==domainURI)
            return;

        boolean ownTransaction = !getExpSchema().getScope().isTransactionActive();
		try
		{
			if (ownTransaction)
				getExpSchema().getScope().beginTransaction();

			deleteObjectsOfType(domainURI, c);
            deleteDomain(domainURI, c);

            if (ownTransaction)
			{
				getExpSchema().getScope().commitTransaction();
				ownTransaction = false;
			}
		}
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
		{
			if (ownTransaction)
				getExpSchema().getScope().closeConnection();
		}
	}


	public static ColumnInfo[] getColumnsForType(String typeURI, TableInfo parentTable, String parentCol, Container c, User user)
	{
		PropertyDescriptor[] pdArray = getPropertiesForType(typeURI, c);
		if (null == pdArray)
			return null;

		List<ColumnInfo> cols = new ArrayList<ColumnInfo>(pdArray.length);
		for (PropertyDescriptor prop : pdArray)
        {
			ColumnInfo col = new PropertyColumn(prop, parentTable, parentCol, c.getId(), user);
            cols.add(col);
            if (prop.isQcEnabled())
            {
                col.setQcColumnName(col.getName() + QcColumn.QC_INDICATOR_SUFFIX);
                ColumnInfo[] qcColumns = QCDisplayColumnFactory.createQcColumns(col, prop, parentTable, parentCol);
                cols.addAll(Arrays.asList(qcColumns));
            }
        }

		return cols.toArray(new ColumnInfo[cols.size()]);
	}


	public static ColumnInfo[] getColumnsForType(String typeURI, TableInfo parentTable, Container c, User user)
	{
		return getColumnsForType(typeURI, parentTable, "LSID", c, user);
	}


	public static PropertyDescriptor insertOrUpdatePropertyDescriptor(PropertyDescriptor pd, DomainDescriptor dd)
			throws SQLException
	{
        DomainDescriptor dexist = null;

        if (null != dd)
            dexist = ensureDomainDescriptor(dd);

        if (null != dexist && !dexist.getContainer().equals(pd.getContainer()))
        {
            // domain is defined in a different container.
            //ToDO  define property in the domains container?  what security?
            throw new SQLException("Attempt to define property for a domain definition that exists in a different folder\n" +
                                    "domain folder = " + dexist.getContainer().getPath() + "\n" +
                                    "property folder = " + pd.getContainer().getPath());
        }

        PropertyDescriptor pexist = ensurePropertyDescriptor(pd);

        if (null != dexist)
            ensurePropertyDomain2(pexist.getPropertyURI(), dexist.getDomainURI(), pexist.getContainer(), pd.isRequired());

        return pexist;
    }


    /** call this method if you do not expect the propertyDescriptor to exist (and the DomainDescriptor has been created) */
    public static PropertyDescriptor insertPropertyDescriptor(PropertyDescriptor pd, DomainDescriptor dd) throws SQLException
    {
        insertPropertyDescriptor(pd);
        ensurePropertyDomain2(pd.getPropertyURI(), dd.getDomainURI(), pd.getContainer(), pd.isRequired());
        return pd;
    }


    public static PropertyDescriptor insertPropertyDescriptor(PropertyDescriptor pd) throws SQLException
	{
		assert pd.getPropertyId() == 0;
		pd = Table.insert(null, getTinfoPropertyDescriptor(), pd);
		propDescCache.put(getCacheKey(pd), pd);
		return pd;
	}


    //todo:  we automatically update a pd to the last  one in?
	public static PropertyDescriptor updatePropertyDescriptor(PropertyDescriptor pd) throws SQLException
	{
		assert pd.getPropertyId() != 0;
		pd = Table.update(null, getTinfoPropertyDescriptor(), pd, pd.getPropertyId(), null);
		propDescCache.put(getCacheKey(pd), pd);
		return pd;
	}


    public static DomainDescriptor insertOrUpdateDomainDescriptor(DomainDescriptor dd)
            throws SQLException
    {
        DomainDescriptor exist = getDomainDescriptor(dd.getDomainURI(),dd.getContainer());
        if (null == exist)
            return insertDomainDescriptor(dd);
        else
        {
            dd.setDomainId(exist.getDomainId());
            return updateDomainDescriptor(dd);
        }
    }


    public static DomainDescriptor insertDomainDescriptor(DomainDescriptor dd) throws SQLException
    {
        assert dd.getDomainId() == 0;
        dd = Table.insert(null, getTinfoDomainDescriptor(), dd);
        domainDescCache.put(getCacheKey(dd),dd);
        return dd;
    }


    public static DomainDescriptor updateDomainDescriptor(DomainDescriptor dd) throws SQLException
    {
        assert dd.getDomainId() != 0;
        dd = Table.update(null, getTinfoDomainDescriptor(), dd, dd.getDomainId(), null);
        domainDescCache.put(getCacheKey(dd),dd);
        return dd;
    }

	public static void clearCaches()
	{
		ExperimentService.get().clearCaches();
        domainDescCache.clear();
        propDescCache.clear();
		mapCache.clear();
		objectIdCache.clear();
	}


	public static void clearPropertyCache(String parentObjectURI)
	{
		mapCache.remove(parentObjectURI);
	}


	public static void clearPropertyCache()
	{
		mapCache.clear();
	}


    public static PropertyDescriptor[] importOneType(String domainURI, List<Map<String, Object>> maps, Collection<String> errors, Container container)
            throws SQLException
    {
        return importTypes(domainURI, null, maps, errors, container, false);
    }


    public static PropertyDescriptor[] importTypes(String domainPrefix, String typeColumn, List<Map<String, Object>> maps, Collection<String> errors, Container container, boolean ignoreDuplicates)
            throws SQLException
    {
        //_log.debug("importTypes(" + vocabulary + "," + typeColumn + "," + maps.length + ")");
        LinkedHashMap<String, PropertyDescriptor> propsWithoutDomains = new LinkedHashMap<String, PropertyDescriptor>();
        LinkedHashMap<String, PropertyDescriptor> allProps = new LinkedHashMap<String, PropertyDescriptor>();
        Map<String, Map<String, PropertyDescriptor>> newPropsByDomain = new  TreeMap<String, Map<String, PropertyDescriptor>>();
        Map<String, PropertyDescriptor> pdNewMap;

        Map<String,DomainDescriptor> domainMap = new HashMap<String,DomainDescriptor>();

        for (Map m : maps)
        {
            String domainURI = domainPrefix;
            String domainName=null;
            if (typeColumn != null)
            {
                domainName = (String) m.get(typeColumn);
                domainURI += domainName;
            }

            String name = StringUtils.trimToEmpty((String) m.get("property"));
            String propertyURI = domainURI + "." + name;
            if (-1 != name.indexOf('#'))
            {
                propertyURI = name;
                name = name.substring(name.indexOf('#') + 1);
            }
            if (name.length() == 0)
            {
                String e = "'property' field is required";
                if (!errors.contains(e))
                    errors.add(e);
                continue;
            }

            String label = StringUtils.trimToNull((String) m.get("label"));
            if (null == label)
                label = name;
            String conceptURI = (String) m.get("conceptURI");
            String rangeURI = (String) m.get("rangeURI");

            BooleanConverter booleanConverter = new BooleanConverter(Boolean.FALSE);

            boolean required = ((Boolean)booleanConverter.convert(Boolean.class, m.get("NotNull"))).booleanValue();
            boolean hidden = ((Boolean)booleanConverter.convert(Boolean.class, m.get("Hidden"))).booleanValue();
            boolean qcEnabled = ((Boolean)booleanConverter.convert(Boolean.class, m.get("AllowsQC"))).booleanValue();

            String description = (String) m.get("description");
            String format = StringUtils.trimToNull((String)m.get("format"));

            PropertyType pt = PropertyType.getFromURI(conceptURI, rangeURI, null);
            if (null == pt)
            {
                String e = "Unrecognized type URI : " + ((null==conceptURI)? rangeURI : conceptURI);
                if (!errors.contains(e))
                    errors.add(e);
                continue;
            }
            rangeURI = pt.getTypeUri();

            if (format != null)
            {
                try
                {
                    switch (pt)
                    {
                    case DOUBLE:
                        format = convertNumberFormatChars(format);
                        (new DecimalFormat(format)).format(1.0);
                        break;
                    case DATE_TIME:
                        format = convertDateFormatChars(format);
                        (new SimpleDateFormat(format)).format(new Date());
                        // UNDONE: don't import date format until we have default format for study
                        // UNDONE: it looks bad to have mixed formats
                        // break;
                    case STRING:
                    case MULTI_LINE:
                    default:
                        format = null;
                    }
                }
                catch (Exception x)
                {
                    format = null;
                }
            }

            DomainDescriptor dd = null;
            if (null != domainURI)
            {
                dd = domainMap.get(domainURI);
                if (null == dd)
                {
                    dd = ensureDomainDescriptor(domainURI, domainName, container);
                    domainMap.put(domainURI,dd);
                }
            }

            PropertyDescriptor pd = new PropertyDescriptor();
            pd.setPropertyURI(propertyURI);
            pd.setName(name);
            pd.setLabel(label);
            pd.setConceptURI(conceptURI);
            pd.setRangeURI(rangeURI);
            pd.setContainer(container);
            pd.setDescription(description);
            pd.setRequired(required);
            pd.setHidden(hidden);
            pd.setFormat(format);
            pd.setQcEnabled(qcEnabled);

            if (null != allProps.put(pd.getPropertyURI(), pd))
            {
                if (!ignoreDuplicates)
                    errors.add("Duplicate definition of property: " + pd.getPropertyURI());
            }

            if (null != dd)
            {
                pdNewMap = newPropsByDomain.get(dd.getDomainURI());
                if (null == pdNewMap)
                {
                    pdNewMap = new LinkedHashMap<String, PropertyDescriptor>();
                    newPropsByDomain.put(dd.getDomainURI(), pdNewMap);
                }
                pdNewMap.put(pd.getPropertyURI(), pd);
            }
            else
            {
                // put only the domain-less allProps in this list
                propsWithoutDomains.put(pd.getPropertyURI(), pd);
            }
        }

        if (!errors.isEmpty())
            return null;
        //
        //  Find any PropertyDescriptors exist already
        //
        ArrayList<PropertyDescriptor> list = new ArrayList<PropertyDescriptor>(allProps.size());

        for (String dURI : newPropsByDomain.keySet())
        {
            pdNewMap = newPropsByDomain.get(dURI);
            PropertyDescriptor[] domainProps = OntologyManager.getPropertiesForType(dURI, container);

            for (PropertyDescriptor pdToInsert : pdNewMap.values())
            {
                DomainDescriptor dd = domainMap.get(dURI); 
                assert (null!= dd);
                PropertyDescriptor pdInserted = null;
                if (domainProps.length == 0)
                {
                    try
                    {
                        // this is much faster than insertOrUpdatePropertyDescriptor()
                        pdInserted = OntologyManager.insertPropertyDescriptor(pdToInsert, dd);
                    }
                    catch (SQLException x)
                    {
                        // it is possible that the property descriptor exists without being part of the domain
                        // fall through
                    }
                }
                if (null == pdInserted)
                    pdInserted = OntologyManager.insertOrUpdatePropertyDescriptor(pdToInsert, dd);
                list.add(pdInserted);
            }
        }
        for (PropertyDescriptor pdToInsert : propsWithoutDomains.values())
        {
            PropertyDescriptor pdInserted = OntologyManager.insertOrUpdatePropertyDescriptor(pdToInsert, null);
            list.add(pdInserted);
        }
        return list.toArray(new PropertyDescriptor[list.size()]);
    }


    private static String convertNumberFormatChars(String format)
    {
        int length = format.length();
        int decimal = format.indexOf('.');
        if (-1 == decimal)
            decimal = length;
        StringBuilder s = new StringBuilder(format);
        for (int i=0 ; i<s.length() ; i++)
        {
            if ('n' == s.charAt(i))
                s.setCharAt(i, i<decimal-1 ? '#' : '0');
        }
        return s.toString();
    }


    private static String convertDateFormatChars(String format)
    {
        if (format.toUpperCase().equals(format))
            return format.replace('Y','y').replace('D','d');
        return format;
    }


    @SuppressWarnings({"ALL"})
    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


		public TestCase(String name)
		{
			super(name);
		}


		public void testSchema()
		{
			assertNotNull(OntologyManager.getExpSchema());
			assertNotNull(getTinfoPropertyDescriptor());
			assertNotNull(ExperimentService.get().getTinfoMaterialSource());

			assertEquals(getTinfoPropertyDescriptor().getColumns("PropertyId,PropertyURI,OntologyURI,RangeURI,Name,Description").size(), 6);
			assertEquals(getTinfoObject().getColumns("ObjectId,ObjectURI,Container,OwnerObjectId").size(), 4);
			assertEquals(getTinfoObjectPropertiesView().getColumns("ObjectId,ObjectURI,Container,OwnerObjectId,Name,PropertyURI,RangeURI,TypeTag,StringValue,DateTimeValue,FloatValue").size(), 11);
			assertEquals(ExperimentService.get().getTinfoMaterialSource().getColumns("RowId,Name,LSID,MaterialLSIDPrefix,Description,Created,CreatedBy,Modified,ModifiedBy,Container").size(), 10);
		}



        public void testBasicPropertiesObject() throws SQLException
		{
            try
            {
                Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
                String parentObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
                String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

                //First delete in case test case failed before
                OntologyManager.deleteOntologyObjects(c, parentObjectLsid);
                assertNull(OntologyManager.getOntologyObject(c, parentObjectLsid));
                assertNull(OntologyManager.getOntologyObject(c, childObjectLsid));
                OntologyManager.ensureObject(c, childObjectLsid, parentObjectLsid);
                OntologyObject oParent = OntologyManager.getOntologyObject(c, parentObjectLsid);
                assertNotNull(oParent);
                OntologyObject oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);
                assertNull(oParent.getOwnerObjectId());
                assertEquals(oChild.getContainer(), c);
                assertEquals(oParent.getContainer(), c);

                String strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                OntologyManager.insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                String intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                OntologyManager.insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.MILLISECOND, 0);
                String dateProp = new Lsid("Junit", "OntologyManager", "dateProp").toString();
                OntologyManager.insertProperties(c, parentObjectLsid, new ObjectProperty(childObjectLsid, c, dateProp, cal.getTime()));

                Map m = OntologyManager.getProperties(c, oChild.getObjectURI());
                assertNotNull(m);
                assertEquals(m.size(), 3);
                assertEquals(m.get(strProp), "The String");
                assertEquals(m.get(intProp), 5);
                assertEquals(m.get(dateProp), cal.getTime());


                OntologyManager.deleteOntologyObjects(c, parentObjectLsid);
                assertNull(OntologyManager.getOntologyObject(c, parentObjectLsid));
                assertNull(OntologyManager.getOntologyObject(c, childObjectLsid));

                m = OntologyManager.getProperties(c, oChild.getObjectURI());
                assertTrue(null == m || m.size() == 0);
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

		public void testContainerDelete() throws SQLException
		{
            try {
                Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
                //Clean up last time's mess
                OntologyManager.deleteAllObjects(c);
                assertEquals(0, OntologyManager.getObjectCount(c));

                String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
                String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

                OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
                OntologyObject oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                assertNotNull(oParent);
                OntologyObject oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);

                String strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                String intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.MILLISECOND, 0);
                String dateProp = new Lsid("Junit", "OntologyManager", "dateProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, dateProp, cal.getTime()));

                OntologyManager.deleteAllObjects(c);
                assertEquals(0, OntologyManager.getObjectCount(c));
                assertTrue(ContainerManager.delete(c, null));
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        private void defineCrossFolderProperties(Container fldr1a, Container fldr1b) throws SQLException
        {
            try {
                String fa = fldr1a.getPath();
                String fb = fldr1b.getPath();

                //object, prop descriptor in folder being moved
                String objP1Fa = new Lsid("OntologyObject", "JUnit", fa.replace('/','.')).toString();
                OntologyManager.ensureObject(fldr1a, objP1Fa);
                String propP1Fa = fa + "PD1";
                OntologyManager.ensurePropertyDescriptor(propP1Fa, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 1"+ fa, fldr1a);
                OntologyManager.insertProperties(fldr1a, null, new ObjectProperty(objP1Fa, fldr1a, propP1Fa, "same fldr"));

                //object in folder not moving, prop desc in folder moving
                String objP2Fb = new Lsid("OntologyObject", "JUnit", fb.replace('/','.')).toString();
                OntologyManager.ensureObject(fldr1b, objP2Fb);
                OntologyManager.insertProperties(fldr1b, null, new ObjectProperty(objP2Fb, fldr1b, propP1Fa, "object in folder not moving, prop desc in folder moving"));

                //object in folder moving, prop desc in folder not moving
                String propP2Fb = fb + "PD1";
                OntologyManager.ensurePropertyDescriptor(propP2Fb, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 1" + fb, fldr1b);
                OntologyManager.insertProperties(fldr1a, null, new ObjectProperty(objP1Fa, fldr1a, propP2Fb, "object in folder moving, prop desc in folder not moving"));

                // third prop desc in folder that is moving;  shares domain with first prop desc
                String propP1Fa3 = fa + "PD3";
                OntologyManager.ensurePropertyDescriptor(propP1Fa3, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 3" + fa, fldr1a);
                String domP1Fa = fa + "DD1";
                DomainDescriptor dd1 = new DomainDescriptor(domP1Fa, fldr1a);
                dd1.setName("DomDesc 1" + fa);
                OntologyManager.ensureDomainDescriptor(dd1);
                OntologyManager.ensurePropertyDomain(propP1Fa, domP1Fa, fldr1a);
                OntologyManager.ensurePropertyDomain(propP1Fa3, domP1Fa, fldr1a);

                //second domain desc in folder that is moving
                // second prop desc in folder moving, belongs to 2nd domain
                String propP1Fa2 = fa + "PD2";
                OntologyManager.ensurePropertyDescriptor(propP1Fa2, PropertyType.STRING.getTypeUri(),"PropertyDescriptor 2" + fa, fldr1a);
                String domP1Fa2 = fa +  "DD2";
                DomainDescriptor dd2 = new DomainDescriptor(domP1Fa2, fldr1a);
                dd2.setName("DomDesc 2" + fa);
                OntologyManager.ensureDomainDescriptor(dd2);
                OntologyManager.ensurePropertyDomain(propP1Fa2, domP1Fa2, fldr1a);
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        public void testContainerMove() throws SQLException
        {
            deleteMoveTestContainers();

            Container proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            Container proj2 = ContainerManager.ensureContainer("/_ontMgrTestP2");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

            proj1 = ContainerManager.ensureContainer("/");
            proj2 = ContainerManager.ensureContainer("/_ontMgrTestP2");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

            proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            proj2 = ContainerManager.ensureContainer("/");
            doMoveTest(proj1, proj2);
            deleteMoveTestContainers();

        }

        private void doMoveTest(Container proj1, Container proj2) throws SQLException
        {
            String p1Path = proj1.getPath() + "/";
            String p2Path = proj2.getPath() + "/";
            if (p1Path.equals("//")) p1Path="/_ontMgrDemotePromote";
            if (p2Path.equals("//")) p2Path="/_ontMgrDemotePromote";

            Container fldr1a = ContainerManager.ensureContainer(p1Path + "Fa");
            Container fldr1b = ContainerManager.ensureContainer(p1Path + "Fb");
            Container fldr2c = ContainerManager.ensureContainer(p2Path + "Fc");
            Container fldr1aa = ContainerManager.ensureContainer(p1Path + "Fa/Faa");
            Container fldr1aaa = ContainerManager.ensureContainer(p1Path + "Fa/Faa/Faaa");

            defineCrossFolderProperties(fldr1a, fldr1b);
            //defineCrossFolderProperties(fldr1a, fldr2c);
            defineCrossFolderProperties(fldr1aa, fldr1b);
            defineCrossFolderProperties(fldr1aaa, fldr1b);

            String p = fldr1a.getProject().getPath();
            String f = fldr1a.getPath();
            String propId = f + "PD1";
            assertNull(OntologyManager.getPropertyDescriptor(propId, proj2));
            ContainerManager.move(fldr1a, proj2);

            // if demoting a folder
            if (proj1.getPath().equals("/") )
            {
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD2";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD3";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                String domId = f + "DD1";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));

                domId = f + "DD2";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));
            }
            // if promoting a folder,
            else if(proj2.getPath().equals("/"))
            {
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));

                propId = f + "PD2";
                assertNull(OntologyManager.getPropertyDescriptor(propId, proj1));

                propId = f + "PD3";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));

                String domId = f + "DD1";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj1));

                domId = f + "DD2";
                assertNull(OntologyManager.getDomainDescriptor(domId, proj1));
            }
            else
            {
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD2";
                assertNull(OntologyManager.getPropertyDescriptor(propId, proj1));
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                propId = f + "PD3";
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj1));
                assertNotNull(OntologyManager.getPropertyDescriptor(propId, proj2));

                String domId = f + "DD1";
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj1));
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));

                domId = f + "DD2";
                assertNull(OntologyManager.getDomainDescriptor(domId, proj1));
                assertNotNull(OntologyManager.getDomainDescriptor(domId, proj2));
            }

        }

        public void testDeleteFoldersWithSharedProps() throws SQLException
        {
            deleteMoveTestContainers();

            Container proj1 = ContainerManager.ensureContainer("/_ontMgrTestP1");
            String p1Path = proj1.getPath() + "/";

            Container fldr1a = ContainerManager.ensureContainer(p1Path + "Fa");
            Container fldr1b = ContainerManager.ensureContainer(p1Path + "Fb");
            Container fldr1aa = ContainerManager.ensureContainer(p1Path + "Fa/Faa");
            Container fldr1aaa = ContainerManager.ensureContainer(p1Path + "Fa/Faa/Faaa");

            defineCrossFolderProperties(fldr1a, fldr1b);
            defineCrossFolderProperties(fldr1aa, fldr1b);
            defineCrossFolderProperties(fldr1aaa, fldr1b);

            Container c;
            c = ContainerManager.getForPath("/_ontMgrTestP1/Fb");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP1");
            if (null!= c)  ContainerManager.delete(c, null);


        }

        private void deleteMoveTestContainers() throws SQLException
        {
            Container c;
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fc");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrTestP1/Fb");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP1/Fa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrTestP2/Fa");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP2/_ontMgrDemotePromoteFa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrTestP2/_ontMgrDemotePromoteFa/Faa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrTestP2/_ontMgrDemotePromoteFa");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrTestP2");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrTestP1");
            if (null!= c)  ContainerManager.delete(c, null);

            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFc");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFb");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFa/Faa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/_ontMgrDemotePromoteFa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/Fa/Faa/Faaa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/Fa/Faa");
            if (null!= c)  ContainerManager.delete(c, null);
            c = ContainerManager.getForPath("/Fa");
            if (null!= c)  ContainerManager.delete(c, null);

        }

        public void testTransactions() throws SQLException
		{
            try {
                Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
                //Clean up last time's mess
                OntologyManager.deleteAllObjects(c);
                assertEquals(0, OntologyManager.getObjectCount(c));

                String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
                String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

                //Create objects in a transaction & make sure they are all gone.
                OntologyManager.getExpSchema().getScope().beginTransaction();
                OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
                OntologyObject oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                assertNotNull(oParent);
                OntologyObject oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);

                String strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                String intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                    OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                OntologyManager.getExpSchema().getScope().rollbackTransaction();

                assertEquals(0, OntologyManager.getObjectCount(c));
                oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                assertNull(oParent);

                OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
                oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
                assertNotNull(oParent);
                oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);

                strProp = new Lsid("Junit", "OntologyManager", "stringProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, strProp, "The String"));

                //Rollback transaction for one new property
                OntologyManager.getExpSchema().getScope().beginTransaction();
                intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                OntologyManager.getExpSchema().getScope().rollbackTransaction();

                oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
                assertNotNull(oChild);
                Map<String, Object> m = OntologyManager.getProperties(c, childObjectLsid);
                assertNotNull(m.get(strProp));
                assertNull(m.get(intProp));

                OntologyManager.getExpSchema().getScope().beginTransaction();
                intProp = new Lsid("Junit", "OntologyManager", "intProp").toString();
                OntologyManager.insertProperties(c, ownerObjectLsid, new ObjectProperty(childObjectLsid, c, intProp, 5));
                OntologyManager.getExpSchema().getScope().commitTransaction();

                m = OntologyManager.getProperties(c, childObjectLsid);
                assertNotNull(m.get(strProp));
                assertNotNull(m.get(intProp));

                OntologyManager.deleteAllObjects(c);
                assertEquals(0, OntologyManager.getObjectCount(c));
                assertTrue(ContainerManager.delete(c, null));
            }
            catch (ValidationException ve)
            {
                throw new SQLException(ve.getMessage());
            }
        }

        public void testDomains () throws Exception
        {
            Container c = ContainerManager.ensureContainer("/_ontologyManagerTest");
            //Clean up last time's mess
            OntologyManager.deleteAllObjects(c);
            assertEquals(0, OntologyManager.getObjectCount(c));
            String ownerObjectLsid = new Lsid("Junit", "OntologyManager", "parent").toString();
            String childObjectLsid = new Lsid("Junit", "OntologyManager", "child").toString();

            OntologyManager.ensureObject(c, childObjectLsid, ownerObjectLsid);
            OntologyObject oParent = OntologyManager.getOntologyObject(c, ownerObjectLsid);
            assertNotNull(oParent);
            OntologyObject oChild = OntologyManager.getOntologyObject(c, childObjectLsid);
            assertNotNull(oChild);

            String domURIa = new Lsid("Junit", "DD", "Domain1").toString();
            String strPropURI = new Lsid("Junit", "PD", "Domain1.stringProp").toString();
            String intPropURI = new Lsid("Junit", "PD", "Domain1.intProp").toString();
            String datePropURI = new Lsid("Junit", "PD", "Domain1.dateProp").toString();

            DomainDescriptor dd = ensureDomainDescriptor(domURIa, "Domain1", c);
            assertNotNull(dd);

            PropertyDescriptor pd = new PropertyDescriptor();
            pd.setPropertyURI(strPropURI);
            pd.setRangeURI(PropertyType.STRING.getTypeUri());
            pd.setContainer(c);
            pd.setName("Domain1.stringProp");

            pd = ensurePropertyDescriptor(pd);
            assertNotNull(pd);

            pd = ensurePropertyDescriptor(intPropURI, PropertyType.INTEGER.getTypeUri(), "Domain1.intProp", c);

            ensurePropertyDomain(strPropURI, domURIa, c);
            ensurePropertyDomain(intPropURI, domURIa, c);

            PropertyDescriptor[] pds = getPropertiesForType(domURIa, c);
            assertEquals(2, pds.length);
            Map<String, PropertyDescriptor>  mPds = new HashMap<String,PropertyDescriptor>();
            for(PropertyDescriptor pd1  : pds)
                mPds.put( pd1.getPropertyURI(), pd1);

            assertTrue(mPds.containsKey(strPropURI));
            assertTrue(mPds.containsKey(intPropURI));

            ObjectProperty strProp = new ObjectProperty(childObjectLsid, c, strPropURI, "String value");
            ObjectProperty intProp = new ObjectProperty(childObjectLsid, c, intPropURI, new Integer(42));
            OntologyManager.insertProperties(c, ownerObjectLsid, strProp);
            OntologyManager.insertProperties(c, ownerObjectLsid, intProp);

            OntologyManager.deleteType(domURIa, c);
            assertEquals(0, OntologyManager.getObjectCount(c));
            assertTrue(ContainerManager.delete(c, null));
        }


		public static Test suite()
		{
			TestSuite suite;
			suite = new TestSuite(OntologyManager.TestCase.class);
			return suite;
		}
	}


	private static long getObjectCount(Container container) throws SQLException
	{
		String sql = "SELECT COUNT(*) FROM " + getTinfoObject() + " WHERE Container = ?";
		return Table.executeSingleton(getExpSchema(), sql, new Object[]{container.getId()}, Long.class).longValue();
	}


    public static class PropertyRow
	{
		protected int objectId;
		protected int propertyId;
		protected char typeTag;
		protected Double floatValue;
		protected String stringValue;
		protected Date dateTimeValue;
        protected String qcValue;

		public PropertyRow()
		{
		}

		public PropertyRow(int objectId, PropertyDescriptor pd, Object value, PropertyType pt)
		{
			this.objectId = objectId;
			this.propertyId = pd.getPropertyId();
			this.typeTag = pt.getStorageType();

            // Handle field-level QC
            if (value instanceof QcFieldWrapper)
            {
                QcFieldWrapper qcWrapper = (QcFieldWrapper) value;
                this.qcValue = qcWrapper.getQcValue();
                value = qcWrapper.getValue();
            }
            else if (pd.isQcEnabled())
            {
                // Not all callers will have wrapped a QC value if there isn't also
                // a real value
                if (QcUtil.isQcValue(value.toString(), pd.getContainer()))
                {
                    this.qcValue = value.toString();
                    value = null;
                }
            }
            
            switch (pt)
            {
                case STRING:
                case MULTI_LINE:
                    if (value instanceof String)
                        this.stringValue = (String) value;
                    else
                        this.stringValue = ConvertUtils.convert(value);
                    break;
                case ATTACHMENT:
                    this.stringValue = (String) value;
                    break;
                case FILE_LINK:
                    if (value instanceof File)
                        this.stringValue = ((File) value).getPath();
                    else
                        this.stringValue = (String) value;
                    break;
                case DATE_TIME:
                    if (value instanceof Date)
                        this.dateTimeValue = (Date) value;
                    else if (null != value)
                        this.dateTimeValue = (Date) ConvertUtils.convert(value.toString(), Date.class);
                    break;
                case INTEGER:
                    if (value instanceof Integer)
                        this.floatValue = ((Integer) value).doubleValue();
                    else if (null != value)
                        this.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
                    break;
                case DOUBLE:
                    if (value instanceof Double)
                        this.floatValue = (Double) value;
                    else if (null != value)
                        this.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
                    break;
                case BOOLEAN:
                {
                    boolean boolValue = false;
                    if (value instanceof Boolean)
                        boolValue = (Boolean)value;
                    else if (null != value && !"".equals(value))
                        boolValue = (Boolean) ConvertUtils.convert(value.toString(), Boolean.class);
                    this.floatValue = boolValue ? 1.0 : 0.0;
                    break;
                }
                case RESOURCE:
                    if (value instanceof Identifiable)
                    {
                        this.stringValue = ((Identifiable) value).getLSID();
                    }
                    else if (null != value)
                        this.stringValue = value.toString();

                    break;
                default:
                    throw new IllegalArgumentException("Unknown property type: " + pt);
            }

		}

		public int getObjectId()
		{
			return objectId;
		}

		public void setObjectId(int objectId)
		{
			this.objectId = objectId;
		}

		public int getPropertyId()
		{
			return propertyId;
		}

		public void setPropertyId(int propertyId)
		{
			this.propertyId = propertyId;
		}

		public char getTypeTag()
		{
			return typeTag;
		}

		public void setTypeTag(char typeTag)
		{
			this.typeTag = typeTag;
		}

		public Double getFloatValue()
		{
			return floatValue;
		}

		public void setFloatValue(Double floatValue)
		{
			this.floatValue = floatValue;
		}

		public String getStringValue()
		{
			return stringValue;
		}

		public void setStringValue(String stringValue)
		{
			this.stringValue = stringValue;
		}

		public Date getDateTimeValue()
		{
			return dateTimeValue;
		}

		public void setDateTimeValue(Date dateTimeValue)
		{
			this.dateTimeValue = dateTimeValue;
		}

        public String getQcValue()
        {
            return qcValue;
        }

        public void setQcValue(String qcValue)
        {
            this.qcValue = qcValue;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("PropertyRow: ");

            sb.append("objectId=" + objectId);
            sb.append(", propertyId=" + propertyId);
            sb.append(", value=");

            if (stringValue != null)
                sb.append(stringValue);
            else if (floatValue != null)
                sb.append(floatValue);
            else if (dateTimeValue != null)
                sb.append(dateTimeValue);
            else
                sb.append("null");

            if (qcValue != null)
                sb.append(", qcValue=" + qcValue);

            return sb.toString();
        }
    }

    public static DbSchema getExpSchema() {
        return DbSchema.get("exp");
    }

    public static SqlDialect getSqlDialect() {
        return getExpSchema().getSqlDialect();
    }

     public static TableInfo getTinfoPropertyDomain() {
         return getExpSchema().getTable("PropertyDomain");
     }

    public static TableInfo getTinfoObject()
    {
        return getExpSchema().getTable("Object");
    }

    public static TableInfo getTinfoObjectProperty()
    {
        return getExpSchema().getTable("ObjectProperty");
    }

    public static TableInfo getTinfoPropertyDescriptor()
    {
        return getExpSchema().getTable("PropertyDescriptor");
    }

    public static TableInfo getTinfoDomainDescriptor()
    {
        return getExpSchema().getTable("DomainDescriptor");
    }

    public static TableInfo getTinfoObjectPropertiesView()
    {
        return getExpSchema().getTable("ObjectPropertiesView");
    }

    public static String doProjectColumnCheck(boolean bFix) throws SQLException
    {
        StringBuilder msgBuffer = new StringBuilder();
        String descriptorTable=getTinfoPropertyDescriptor().toString();
        String uriColumn = "PropertyURI";
        String idColumn = "PropertyID";
        doProjectColumnCheck(descriptorTable, uriColumn, idColumn, msgBuffer, bFix);

        descriptorTable=getTinfoDomainDescriptor().toString();
        uriColumn = "DomainURI";
        idColumn = "DomainID";
        doProjectColumnCheck(descriptorTable, uriColumn, idColumn, msgBuffer, bFix);

        return msgBuffer.toString();
    }

    private static void doProjectColumnCheck(String descriptorTable, String uriColumn, String idColumn, StringBuilder msgBuffer, boolean bFix) throws SQLException
    {
        ResultSet rs =null;
        String projectId;
        String containerId;
        String newProjectId;
        // get all unique combos of Container, project
        try {
            String sql = "SELECT Container, Project FROM " + descriptorTable + " GROUP BY Container, Project";
            rs = Table.executeQuery(getExpSchema(), sql, new Object[]{});
            while (rs.next())
            {
                containerId = rs.getString("Container");
                projectId = rs.getString("Project");
                Container container = ContainerManager.getForId(containerId);
                if (null==container)
                    continue;  // should be handled by container check
                newProjectId = container.getProject().getId();
                if (!projectId.equals(newProjectId))
                {
                   if  (bFix)
                   {
                       try {
                            fixProjectColumn(descriptorTable, uriColumn, idColumn, container, projectId, newProjectId);
                           msgBuffer.append("<br/>&nbsp;&nbsp;&nbsp;Fixed inconsistent project ids found for " + descriptorTable
                                   + " in folder " + ContainerManager.getForId(containerId).getPath());

                       } catch (SQLException se) {
                           msgBuffer.append("<br/>&nbsp;&nbsp;&nbsp;ERROR:  Failed to fix inconsistent project ids found for " + descriptorTable
                                    + " due to " + se.getMessage() );
                       }
                   }
                   else
                        msgBuffer.append("<br/>&nbsp;&nbsp;&nbsp;ERROR:  Inconsistent project ids found for " + descriptorTable + " in folder " +
                               container.getPath());
                }
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

    }
    private static void fixProjectColumn(String descriptorTable, String uriColumn, String idColumn, Container container, String projectId, String newProjId) throws SQLException
    {
        String sql =  "UPDATE " + descriptorTable + " SET Project= ? WHERE Project = ? AND Container=? AND " + uriColumn + " NOT IN " +
                "(SELECT " + uriColumn + " FROM " + descriptorTable + " WHERE Project = ?)" ;
        Table.execute(getExpSchema(), sql, new Object[]{newProjId, projectId, container.getId(), newProjId});

        // now check to see if there is already an existing descriptor in the target (correct) project.
        // this can happen if a folder containning a descriptor is moved to another project
        // and the OntologyManager's containerMoved handler fails to fire for some reason. (note not in transaction)
        //  If this is the case, the descriptor is redundant and it should be deleted, after we move the objects that depend on it.

        sql= " SELECT prev." + idColumn + " AS PrevIdCol, cur." + idColumn + " AS CurIdCol FROM " + descriptorTable + " prev "
                        + " INNER JOIN " + descriptorTable + " cur ON (prev." + uriColumn + "=  cur." + uriColumn + " ) "
                        + " WHERE cur.Project = ? AND prev.Project= ? AND prev.Container = ? ";
        String updsql1 = " UPDATE " + getTinfoObjectProperty() + " SET " + idColumn + " = ? WHERE " + idColumn + " = ? ";
        String updsql2 = " UPDATE " + getTinfoPropertyDomain() + " SET " + idColumn + " = ? WHERE " + idColumn + " = ? ";
        String delSql =   " DELETE FROM " + descriptorTable + " WHERE " + idColumn + " = ? ";
        ResultSet rs = null;
        try {
            rs = Table.executeQuery(getExpSchema(), sql, new Object[]{newProjId, projectId, container.getId()});
            while (rs.next())
            {
                int prevPropId=rs.getInt(1);
                int curPropId=rs.getInt(2);
                Table.execute(getExpSchema(), updsql1, new Object[]{curPropId, prevPropId });
                Table.execute(getExpSchema(), updsql2, new Object[]{curPropId, prevPropId});
                Table.execute(getExpSchema(), delSql, new Object[]{prevPropId});
            }
        } finally
        {
            if (null != rs)
                rs.close();
        }
    }

    static public PropertyDescriptor updatePropertyDescriptor(User user, String domainURI, PropertyDescriptor pdOld, PropertyDescriptor pdNew) throws ChangePropertyDescriptorException
    {
        try
        {
            PropertyType oldType = pdOld.getPropertyType();
            PropertyType newType = pdNew.getPropertyType();
            if (oldType.getStorageType() != newType.getStorageType())
            {
                Integer count = Table.executeSingleton(getExpSchema(), "SELECT COUNT(ObjectId) FROM exp.ObjectProperty WHERE PropertyId = ?", new Object[] { pdOld.getPropertyId() }, Integer.class);
                if (count != 0)
                {
                    throw new ChangePropertyDescriptorException("This property type cannot be changed because there are existing values.");
                }
            }
            PropertyDescriptor update = Table.update(user, getTinfoPropertyDescriptor(), pdNew, pdOld.getPropertyId(), null);

            if (pdOld.isRequired() != pdNew.isRequired())
                ensurePropertyDomain2(pdNew.getPropertyURI(), domainURI, pdNew.getContainer(), pdNew.isRequired());

            return update;
        }
        catch (Exception e)
        {
            throw new ChangePropertyDescriptorException(e);
        }
    }

    static public boolean checkObjectExistence(String lsid)
    {
        ResultSet rs = null;
        try
        {
            rs = Table.select(getTinfoObject(), Table.ALL_COLUMNS, new SimpleFilter("ObjectURI", lsid), null);
            return (rs.next());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
    }
}
