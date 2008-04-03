package org.labkey.demo.model;

import org.labkey.api.data.Entity;
import org.apache.commons.lang.StringUtils;

/**
 * User: brittp
 * Date: Jan 23, 2006
 * Time: 1:18:25 PM
 */
public class Person extends Entity
{
    private String _firstName;
    private String _lastName;
    private Integer _age;
    private Integer _rowId;

    public Person()
    {
    }

    public Person(String firstName, String lastName, Integer age)
    {
        _firstName = StringUtils.trimToEmpty(firstName);
        _lastName = StringUtils.trimToEmpty(lastName);
        _age = age;
    }

    public Integer getAge()
    {
        return _age;
    }

    public void setAge(Integer age)
    {
        _age = age;
    }

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

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }


    public boolean equals(Object obj)
    {
        if (!(obj instanceof Person))
            return false;
        Person p = (Person)obj;
        
        return StringUtils.equals(_firstName, p.getFirstName()) &&
                StringUtils.equals(_lastName, p.getLastName()) &&
                _age == p.getAge() || _age != null && _age.equals(p.getAge());
    }
}
