package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.TestContext;

import java.io.StringWriter;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.hasItem;

public class ExpProvisionedTableTestHelper
{
    public static final SchemaKey expDataSchemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString());

    final User user = TestContext.get().getUser();
    public static final String agePropertyName = "Age";
    public static final String colorPropertyName = "Color";
    public static final String typePropertyName = "Type";

    public Domain createVocabularyTestDomain(User user, Container c) throws ValidationException
    {
        String domainName = "TestVocabularyDomain";
        String domainDescription = "This is a test vocabulary";

        GWTPropertyDescriptor prop1 = new GWTPropertyDescriptor();
        prop1.setRangeURI("int");
        prop1.setName(agePropertyName);

        GWTPropertyDescriptor prop2 = new GWTPropertyDescriptor();
        prop2.setRangeURI("string");
        prop2.setName(typePropertyName);

        GWTPropertyDescriptor prop3 = new GWTPropertyDescriptor();
        prop3.setRangeURI("string");
        prop3.setName(colorPropertyName);

        GWTDomain domain = new GWTDomain();
        domain.setName(domainName);
        domain.setDescription(domainDescription);
        domain.setFields(List.of(prop1, prop2, prop3));

        return DomainUtil.createDomain("Vocabulary", domain, null, c, user, domainName, null);
    }

    public Map<String, String> getVocabularyPropertyURIS(Domain domain)
    {
        Map<String, String> propertyURIs = new HashMap<>();
        domain.getProperties().forEach(dp -> propertyURIs.put(dp.getName(), dp.getPropertyURI()));
        return propertyURIs;
    }

    public List<Map<String, Object>> buildRows(ArrayListMap row)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);
        return rows;
    }

    private QueryUpdateService getQueryUpdateService(UserSchema schema, String tableName)
    {
        TableInfo table = schema.getTable(tableName, null);
        Assert.assertNotNull(table);

        QueryUpdateService qus = table.getUpdateService();
        Assert.assertNotNull(qus);
        return qus;
    }

    public List<Map<String, Object>> insertRows(Container c, List<Map<String, Object>> rows, String tableName, @Nullable UserSchema schema)
            throws Exception
    {
        BatchValidationException errors = new BatchValidationException();
        UserSchema userSchema = null == schema ? QueryService.get().getUserSchema(user, c, expDataSchemaKey) : schema;
        List<Map<String, Object>> ret = getQueryUpdateService(userSchema, tableName).insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        return ret;
    }

    public List<Map<String, Object>> insertRows(Container c, List<Map<String, Object>> rows, String tableName)
            throws Exception
    {
        return  this.insertRows(c, rows, tableName, null);
    }

    public List<Map<String, Object>> updateRows(Container c, List<Map<String, Object>> rowsToUpdate,  List<Map<String, Object>> oldKeys, String tableName, @Nullable UserSchema schema) throws Exception
    {
        UserSchema userSchema = null == schema ? QueryService.get().getUserSchema(user, c, expDataSchemaKey) : schema;
        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> ret = getQueryUpdateService(userSchema, tableName).updateRows(user, c, rowsToUpdate, oldKeys, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        return ret;
    }

    public List<Map<String, Object>> updateRows(Container c, List<Map<String, Object>> rowsToUpdate,  List<Map<String, Object>> oldKeys, String tableName) throws Exception
    {
        return this.updateRows(c, rowsToUpdate, oldKeys, tableName, null);
    }

    static void assertMultiValue(Collection<Object> values, Collection<String> expected) throws Exception
    {
        Assert.assertNotNull(values);
        for (var expect : expected)
            Assert.assertThat(values, hasItem(expect));
    }

    static void assertMultiValue(Object value, String... expected) throws Exception
    {
        Assert.assertNotNull(value);
        String s;
        if (value instanceof Clob)
        {
            StringWriter sw = new StringWriter();
            org.apache.commons.io.IOUtils.copy(((Clob)value).getCharacterStream(),sw);
            s = sw.toString();
        }
        else
        {
            s = String.valueOf(value);
        }

        for (String e : expected)
            Assert.assertTrue("Failed to find '" + e + "' in multivalue '" + s + "'", s.contains(e));
    }

}
