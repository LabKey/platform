/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 22, 2008
 * Time: 3:00:19 PM
 */
public abstract class AbstractDocumentResource extends AbstractResource
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

	public Resource find(String name)
	{
		return null;
	}

	public List<Resource> list()
    {
        return Collections.emptyList();
    }

    public List<String> listNames()
    {
        return Collections.emptyList();
    }
    
    public boolean isCollection()
    {
        return false;
    }
}
