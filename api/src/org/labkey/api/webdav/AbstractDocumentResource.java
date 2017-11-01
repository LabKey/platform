/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.webdav;

import org.labkey.api.util.Path;

import java.util.Collection;
import java.util.Collections;

/**
 * Base class for WebDav entities, typically backed by rows in the database, that are exposed
 * as if they are files. An example is a custom SQL query created through the schema browser, stored
 * in the query.queryDef table, that is exposed via WebDav as if it were a .sql file in a virtual file system.
 * User: matthewb
 * Date: Oct 22, 2008
 */
public abstract class AbstractDocumentResource extends AbstractWebdavResource
{
    public AbstractDocumentResource(Path path)
    {
        super(path);
    }

    protected AbstractDocumentResource(Path parent, String name)
    {
        super(parent, name);
    }

	public boolean isFile()
	{
		return exists();
	}

	public WebdavResource find(String name)
	{
		return null;
	}

    public Collection<String> listNames()
    {
        return Collections.emptyList();
    }
    
    public boolean isCollection()
    {
        return false;
    }
}
