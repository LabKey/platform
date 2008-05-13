/*
 * Copyright (c) 2007 LabKey Corporation
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

import java.util.List;
import java.util.ArrayList;

public class Browse_data
{
    String session_id;
    String mod;
    String op;
    String ent;
    Integer id;
    String parent_ent;
    Integer parent_id;
    List<Entityinfo> content = new ArrayList();

    public String getSession_id()
    {
        return session_id;
    }

    public void setSession_id(String session_id)
    {
        this.session_id = session_id;
    }

    public String getMod()
    {
        return mod;
    }

    public void setMod(String mod)
    {
        this.mod = mod;
    }

    public String getOp()
    {
        return op;
    }

    public void setOp(String op)
    {
        this.op = op;
    }

    public String getEnt()
    {
        return ent;
    }

    public void setEnt(String ent)
    {
        this.ent = ent;
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getParent_ent()
    {
        return parent_ent;
    }

    public void setParent_ent(String parent_ent)
    {
        this.parent_ent = parent_ent;
    }

    public Integer getParent_id()
    {
        return parent_id;
    }

    public void setParent_id(Integer parent_id)
    {
        this.parent_id = parent_id;
    }

    public List<Entityinfo> getAllContent()
    {
        return this.content;
    }

    public void setContent(Entityinfo content)
    {
        this.content.add(content);
    }
}
