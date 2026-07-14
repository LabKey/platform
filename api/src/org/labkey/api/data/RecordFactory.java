/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.collections.CaseInsensitiveCollection;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMap;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ResultSetUtil;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An ObjectFactory that handles records. It doesn't care about the record's visibility (e.g., it can be private). Maps
 * are always read in a case-insensitive manner.
 */
public class RecordFactory<K> implements ObjectFactory<K>
{
    private final Constructor<K> _constructor;
    private final Parameter[] _parameters;
    private final List<Field> _fields;

    public RecordFactory(Class<K> clazz)
    {
        if (!clazz.isRecord())
            throw new IllegalStateException(clazz + " is not a record!");
        //noinspection unchecked
        _constructor = (Constructor<K>) clazz.getDeclaredConstructors()[0];
        _constructor.setAccessible(true);
        _parameters = _constructor.getParameters();
        _fields = Arrays.stream(clazz.getDeclaredFields())
            .peek(field -> field.setAccessible(true))
            .toList();
    }

    // Throws IllegalArgumentExceptions for missing primitive parameters and conversion errors
    private <MAP extends Map<String, ?> & CaseInsensitiveCollection> K fromCaseInsensitiveMap(MAP m)
    {
        Object[] params = Arrays.stream(_parameters).map(p -> {
            Object value = m.get(p.getName());
            try
            {
                return value != null ? ConvertUtils.convert(value, p.getType()) : null;
            }
            catch (ConversionException e)
            {
                throw new IllegalArgumentException("Failed to convert property value of type '" + value.getClass().getName() + "' to required type '" + p.getType() + "' for property '" + p.getName() + "'; " + e.getMessage());
            }
        }).toArray();

        try
        {
            return _constructor.newInstance(params);
        }
        catch (IllegalArgumentException e)
        {
            // Try to determine if this failed due to missing primitive parameters to improve the exception message
            List<String> missingPrimitiveParameters = Arrays.stream(_parameters)
                .filter(p -> p.getType().isPrimitive())
                .map(Parameter::getName)
                .filter(name -> m.get(name) == null)
                .toList();
            if (missingPrimitiveParameters.isEmpty())
                throw e; // Unclear what the problem is, so just re-throw
            if (missingPrimitiveParameters.size() == 1)
                throw new IllegalArgumentException("Primitive parameter \"" + missingPrimitiveParameters.getFirst() + "\" is required");
            throw new IllegalArgumentException("One or more primitive parameters are missing. Primitive parameters include: " + missingPrimitiveParameters);
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public K fromMap(Map<String, ?> m)
    {
        return fromCaseInsensitiveMap(CaseInsensitiveHashMap.ensure(m));
    }

    /**
     * Creates a new record from the map, same as above. Ignores the passed-in record since it's immutable.
     */
    @Override
    public K fromMap(K record, Map<String, ?> map)
    {
        return fromMap(map);
    }

    /**
     * Populates the passed-in map (if provided) or returns a CaseInsensitiveHashMap if map is null
     */
    @Override
    public Map<String, Object> toMap(K record, @Nullable Map<String, Object> m)
    {
        final Map<String, Object> map = (null == m ? new CaseInsensitiveHashMap<>() : m);

        _fields.forEach(field -> {
            try
            {
                map.put(field.getName(), field.get(record));
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
        });

        return map;
    }

    @Override
    public K handle(ResultSet rs) throws SQLException
    {
        Map<String, Object> map = ResultSetUtil.mapRow(rs);
        return fromMap(map);
    }

    @Override
    public ArrayList<K> handleArrayList(ResultSet rs)
    {
        Iterable<Map<String, Object>> iterable = () -> new ResultSetIterator(rs);
        return StreamSupport.stream(iterable.spliterator(), false)
            .map(rowMap -> fromCaseInsensitiveMap((RowMap<Object>)rowMap))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testDatabase() throws SQLException
        {
            Map<String, Object> adHocMap = new CaseInsensitiveHashMap<>();
            adHocMap.put("FirstName", "Keyser");
            adHocMap.put("LastName", "Söze");
            adHocMap.put("lastlogin", "2024-09-01");
            adHocMap.put("USERID", 2000);

            // Round-trip map -> record -> map -> record. Exercises fromMap() and toMap()
            RecordFactory<MiniUser> factory = new RecordFactory<>(MiniUser.class);
            MiniUser adHocUser = factory.fromMap(adHocMap);
            assertEquals(adHocUser, factory.fromMap(factory.toMap(adHocUser, null)));

            // Use TableSelector to exercise handleArrayList() and handle()
            TableSelector selector = new TableSelector(CoreSchema.getInstance().getTableInfoUsers()).setMaxRows(10);
            List<MiniUser> users = selector.getArrayList(MiniUser.class);
            try (Stream<MiniUser> stream = selector.uncachedStream(MiniUser.class))
            {
                Assert.assertEquals(users, stream.collect(Collectors.toCollection(ArrayList::new)));
            }
            try (ResultSet rs = selector.getResultSet())
            {
                rs.next();
                Assert.assertEquals(users.getFirst(), factory.handle(rs));
            }
            MiniUser randomUser = users.get((int)(Math.random() * users.size()));
            MiniUser selectedUser = new TableSelector(CoreSchema.getInstance().getTableInfoUsers(), new SimpleFilter(FieldKey.fromString("UserId"), randomUser.UserId), null).getObject(MiniUser.class);
            Assert.assertEquals(randomUser, selectedUser);

            // Test fromMap() variant (should ignore selectedUser)
            assertEquals(adHocUser, factory.fromMap(selectedUser, adHocMap));
        }

        @Test
        public void testBinding()
        {
            Date lastLogin = new Date();

            // Provide all parameters
            Map<String, Object> params = Map.of(
                "firstName", "Fred",
                "lastName", "Flintstone",
                "lastLogin", DateUtil.formatIsoDateLongTime(lastLogin),
                "userId", 1009
            );
            String toString = "MiniUser[FIRSTname=Fred, LASTNAME=Flintstone, lastLogin=" + lastLogin + ", UserId=1009]";
            testRecordBinding(params, toString);
            testFormBinding(params, toString);

            // Provide just the primitive parameter; others are nullable
            params = Map.of(
                "userId", 1009
            );
            toString = "MiniUser[FIRSTname=null, LASTNAME=null, lastLogin=null, UserId=1009]";
            testRecordBinding(params, toString);
            testFormBinding(params, toString);

            // No parameters should fail for record due to "userid" primitive. Ensure a reasonable error message.
            testRecordBinding(Map.of(), "Primitive parameter \"UserId\" is required");
            // No parameters should succeed for form class. UserId simply defaults to 0;
            testFormBinding(Map.of(), "MiniUser[FIRSTname=null, LASTNAME=null, lastLogin=null, UserId=0]");

            // Verify message for conversion error
            params = Map.of("UserId", "abc");
            String errorMessage = "Failed to convert property value of type 'java.lang.String' to required type 'int' for property 'UserId'; Could not convert 'abc' to an integer";
            testRecordBinding(params, errorMessage);
            testFormBinding(params, errorMessage);
        }

        private void testRecordBinding(Map<String, Object> map, String expectedToStringOrError)
        {
            PropertyValues pvs = new MutablePropertyValues(map);
            BindException be = BaseViewAction.bindParametersToRecord(MiniUser.class, pvs, "form");

            if (be.hasErrors())
            {
                validateError(be, expectedToStringOrError);
            }
            else
            {
                validateTarget(be.getTarget(), expectedToStringOrError);
            }
        }

        private void testFormBinding(Map<String, Object> map, String expectedToStringOrError)
        {
            PropertyValues pvs = new MutablePropertyValues(map);
            BindException be = BaseViewAction.defaultBindParameters(new MiniUserForm(), "form", pvs);

            if (be.hasErrors())
            {
                validateError(be, expectedToStringOrError);
            }
            else
            {
                validateTarget(be.getTarget(), expectedToStringOrError);
            }
        }

        private void validateError(BindException be, String expectedErrorMessage)
        {
            ObjectError error = be.getAllErrors().getFirst();
            assertNotNull(error);
            assertEquals(expectedErrorMessage, error.getDefaultMessage());
        }

        private void validateTarget(Object user, String expectedToString)
        {
            assertNotNull(user);
            assertEquals(expectedToString, user.toString());
        }

        // Simple test record. Weird casing is intentional to test case-insensitivity.
        private record MiniUser(String FIRSTname, String LASTNAME, Date lastLogin, int UserId)
        {
        }

        // Simple test form
        @SuppressWarnings("unused")
        private static class MiniUserForm
        {
            String _firstName;
            String _lastName;
            Date _lastLogin;
            int _userId;

            public String getFirstName()
            {
                return _firstName;
            }

            public void setFirstName(String firstName)
            {
                _firstName = firstName;
            }

            public String getLastName()
            {
                return _lastName;
            }

            public void setLastName(String lastName)
            {
                _lastName = lastName;
            }

            public Date getLastLogin()
            {
                return _lastLogin;
            }

            public void setLastLogin(Date lastLogin)
            {
                _lastLogin = lastLogin;
            }

            public int getUserId()
            {
                return _userId;
            }

            public void setUserId(int userId)
            {
                _userId = userId;
            }

            @Override
            public String toString()
            {
                // No useful default toString(), so emulate the standard record toString(). That's good enough to verify
                // that the parameters were bound correctly.
                return "MiniUser[FIRSTname=" + _firstName + ", LASTNAME=" + _lastName + ", lastLogin=" + _lastLogin + ", UserId=" + _userId + "]";
            }
        }
    }
}
