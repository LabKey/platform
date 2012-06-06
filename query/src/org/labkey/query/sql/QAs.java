/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

package org.labkey.query.sql;

import java.util.Map;

public class QAs extends QNode implements SupportsAnnotations
{
	public QAs()
	{
		super(QExpr.class);
	}
	
    public QExpr getExpression()
    {
        return (QExpr)getFirstChild();
    }

    public QIdentifier getAlias()
    {
        return (QIdentifier) getLastChild();
    }

    public void appendSource(SourceBuilder builder)
    {
        getFirstChild().appendSource(builder);
        builder.append(" AS ");
        getLastChild().appendSource(builder);
    }


    Map<String,Object> _annotations = null;

    @Override
    public void setAnnotations(Map<String, Object> a)
    {
        _annotations = a;
    }

    @Override
    public Map<String, Object> getAnnotations()
    {
        return _annotations;
    }
}
