package org.labkey.demo.view;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

import org.labkey.demo.model.Person;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 10, 2007
 * Time: 10:20:38 AM
 */
public class BulkUpdatePage
{
    private List<Person> list = Collections.emptyList();

    public BulkUpdatePage()
    {
    }
    
    public BulkUpdatePage(Person[] a)
    {
        setList(a);
    }

    public BulkUpdatePage(List<Person> list)
    {
        setList(list);
    }

    public List<Person> getList()
    {
        return list;
    }

    public void setList(Person[] a)
    {
        this.list = Arrays.asList(a);
    }

    public void setList(List<Person> list)
    {
        this.list = list;
    }
}
