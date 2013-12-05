/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.demo.model;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.demo.DemoSchema;
import org.springframework.validation.Errors;

import java.sql.SQLException;


public class DemoManager
{
    private static final DemoManager _instance = new DemoManager();

    private DemoManager()
    {
        // prevent external construction with a private default constructor
    }

    public static DemoManager getInstance()
    {
        return _instance;
    }

    public void deleteAllData(Container c) throws SQLException
    {
        // delete all people when the container is deleted:
        Filter containerFilter = SimpleFilter.createContainerFilter(c);
        Table.delete(DemoSchema.getInstance().getTableInfoPerson(), containerFilter);
    }

    public Person[] getPeople(Container c)
    {
        Filter containerFilter = SimpleFilter.createContainerFilter(c);
        return getPeople(containerFilter);
    }

    public void deletePerson(Container c, int rowId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);
        Table.delete(DemoSchema.getInstance().getTableInfoPerson(), filter);
    }

    public Person getPerson(Container c, int rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);
        Person[] people = getPeople(filter);
        if (people != null && people.length > 0)
            return people[0];
        else
            return null;
    }

    private Person[] getPeople(Filter filter)
    {
        return new TableSelector(DemoSchema.getInstance().getTableInfoPerson(), filter, new Sort("RowId")).getArray(Person.class);
    }

    public Person insertPerson(Container c, User user, Person person) throws SQLException
    {
        person.setContainer(c.getId());
        return Table.insert(user, DemoSchema.getInstance().getTableInfoPerson(), person);
    }

    public Person updatePerson(Container c, User user, Person person, Object ts) throws SQLException
    {
        if (person.getRowId() == null)
            throw new IllegalStateException("Can't update a row with a null rowId");
        if (person.getContainerId() == null)
            person.setContainerId(c.getId());
        if (!person.getContainerId().equals(c.getId()))
            throw new IllegalStateException("Can't update a row with a null rowId");

        return Table.update(user, DemoSchema.getInstance().getTableInfoPerson(), person, person.getRowId());
    }


    public static void validate(Person person, Errors errors)
    {
        if (StringUtils.trimToNull(person.getLastName()) == null)
            errors.rejectValue("lastName", null, null, "Last Name is required.");
    //        errors.addError(new ObjectError("lastname", null, null, "Last Name is required."));
        if (StringUtils.trimToNull(person.getFirstName()) == null)
            errors.rejectValue("firstName", null, null, "First Name is required.");
        if (person.getAge() != null && (person.getAge() < 0 || person.getAge() > 120))
            errors.rejectValue("age", null, null, "Age should be between 0 and 120.");
    }
}