/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.demo.view;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

import org.labkey.demo.model.Person;

/**
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
