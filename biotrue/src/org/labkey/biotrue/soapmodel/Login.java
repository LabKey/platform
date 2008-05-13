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

package org.labkey.biotrue.soapmodel;

public class Login
{
    String user_name;
    String password;
    String initial_mod;
    String initial_op;
    String initial_ent;
    String initial_id;

    public String getUser_name()
    {
        return user_name;
    }

    public void setUser_name(String user_name)
    {
        this.user_name = user_name;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getInitial_mod()
    {
        return initial_mod;
    }

    public void setInitial_mod(String initial_mod)
    {
        this.initial_mod = initial_mod;
    }

    public String getInitial_op()
    {
        return initial_op;
    }

    public void setInitial_op(String initial_op)
    {
        this.initial_op = initial_op;
    }

    public String getInitial_ent()
    {
        return initial_ent;
    }

    public void setInitial_ent(String initial_ent)
    {
        this.initial_ent = initial_ent;
    }

    public String getInitial_id()
    {
        return initial_id;
    }

    public void setInitial_id(String initial_id)
    {
        this.initial_id = initial_id;
    }
}
