/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Assume;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.TestContext;

import java.io.StringWriter;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.hasItem;

public class ExpProvisionedTableTestHelper
{
    public static final SchemaKey expDataSchemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString());
    public static final String agePropertyName = "Age";
    public static final String colorPropertyName = "Color";
    public static final String typePropertyName = "Type";

    final User user = TestContext.get().getUser();

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

        GWTDomain<GWTPropertyDescriptor> domain = new GWTDomain<>();
        domain.setName(domainName);
        domain.setDescription(domainDescription);
        domain.setFields(List.of(prop1, prop2, prop3));

        return DomainUtil.createDomain("Vocabulary", domain, null, c, user, domainName, null, false);
    }

    public Map<String, String> getVocabularyPropertyURIS(Domain domain)
    {
        Map<String, String> propertyURIs = new HashMap<>();
        domain.getProperties().forEach(dp -> propertyURIs.put(dp.getName(), dp.getPropertyURI()));
        return propertyURIs;
    }

    public List<Map<String, Object>> buildRows(ArrayListMap<String, Object> row)
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

    public static void requireSimpleTestModule(Container c)
    {
        if (!AppProps.getInstance().isDevMode()) // Skip test in production mode if necessary modules are not available
        {
            Assume.assumeTrue("List module is required to test domain templates", ModuleLoader.getInstance().getModule("list") != null);
            Assume.assumeTrue("simpletest module is required to test domain templates", ModuleLoader.getInstance().getModule("simpletest") != null);
        }

        Module m = ModuleLoader.getInstance().getModule("simpletest");
        Assert.assertNotNull("This test requires 'simpletest' module to be deployed", m);
        Set<Module> activeModules = new HashSet<>(c.getActiveModules());
        activeModules.add(m);
        c.setActiveModules(activeModules);
    }

    static void assertMultiValue(Collection<Object> values, Collection<String> expected)
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

    public void verifyReservedColumnNames(Container c, User user, @NotNull Domain domain)
    {
        DomainKind<?> kind = domain.getDomainKind();
        Assert.assertNotNull(kind);

        // Verify <reservedColumnNames> from the template are honored by the DomainKind
        Set<String> reservedNames = kind.getReservedPropertyNames(domain, user);
        Assert.assertTrue("Expected template reserved name 'reservedOne' in: " + reservedNames,
                reservedNames.stream().anyMatch(n -> n.equalsIgnoreCase("reservedOne")));
        Assert.assertTrue("Expected template reserved name 'ReservedTwo' in: " + reservedNames,
                reservedNames.stream().anyMatch(n -> n.equalsIgnoreCase("ReservedTwo")));

        // Attempt to add a field whose name collides with a template-reserved name (mixed case)
        GWTDomain<GWTPropertyDescriptor> origGwt = DomainUtil.getDomainDescriptor(user, domain);
        GWTDomain<GWTPropertyDescriptor> updateGwt = new GWTDomain<>(origGwt);
        List<GWTPropertyDescriptor> updatedFields = new ArrayList<>(updateGwt.getFields());
        updatedFields.add(new GWTPropertyDescriptor("RESERVEDONE", "http://www.w3.org/2001/XMLSchema#string"));
        updateGwt.setFields(updatedFields);
        ValidationException ve = DomainUtil.updateDomainDescriptor(origGwt, updateGwt, c, user);
        Assert.assertTrue("Expected a validation error when adding a reserved-name field", ve.hasErrors());
        Assert.assertTrue("Expected error message to flag the reserved name: " + ve.getMessage(),
                ve.getMessage().toLowerCase().contains("reserved"));

        // A non-reserved field name should validate cleanly
        GWTDomain<GWTPropertyDescriptor> okUpdate = new GWTDomain<>(origGwt);
        List<GWTPropertyDescriptor> okFields = new ArrayList<>(okUpdate.getFields());
        okFields.add(new GWTPropertyDescriptor("nonReservedField", "http://www.w3.org/2001/XMLSchema#string"));
        okUpdate.setFields(okFields);
        ValidationException okVe = DomainUtil.validateProperties(domain, okUpdate, kind, origGwt, user);
        Assert.assertFalse("Did not expect validation errors for a non-reserved field: " + okVe.getMessage(), okVe.hasErrors());
    }
}
