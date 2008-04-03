package org.labkey.demo.model;

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.demo.DemoSchema;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.Errors;

import java.sql.SQLException;


public class DemoManager
{
    private static DemoManager _instance;

    private DemoManager()
    {
        // prevent external construction with a private default constructor
    }

    public static synchronized DemoManager getInstance()
    {
        if (_instance == null)
            _instance = new DemoManager();
        return _instance;
    }

    public void deleteAllData(Container c) throws SQLException
    {
        // delete all people when the container is deleted:
        Filter containerFilter = new SimpleFilter("Container", c.getId());
        Table.delete(DemoSchema.getInstance().getTableInfoPerson(), containerFilter);
    }

    public Person[] getPeople(Container c) throws SQLException
    {
        Filter containerFilter = new SimpleFilter("Container", c.getId());
        return getPeople(containerFilter);
    }

    public void deletePerson(Container c, int rowId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", c.getId());
        filter.addCondition("RowId", rowId);
        Table.delete(DemoSchema.getInstance().getTableInfoPerson(), filter);
    }

    public Person getPerson(Container c, int rowId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", c.getId());
        filter.addCondition("RowId", rowId);
        Person[] people = getPeople(filter);
        if (people != null && people.length > 0)
            return people[0];
        else
            return null;
    }

    private Person[] getPeople(Filter filter) throws SQLException
    {
        return Table.select(DemoSchema.getInstance().getTableInfoPerson(),
                Table.ALL_COLUMNS, filter, new Sort("RowId"), Person.class);
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
        return Table.update(user, DemoSchema.getInstance().getTableInfoPerson(), person, person.getRowId(), ts);
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