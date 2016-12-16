/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.api.util;

import org.labkey.api.data.Parameter;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.StringExpressionType;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

/**
 * An expression that knows how to evaluate itself in a given context to substitute values for tokens in the expression.
 * Typical implementations include URL generation.
 * User: matthewb
 * Date: Sep 16, 2009
 */
public interface StringExpression extends Cloneable, Parameter.JdbcParameterValue
{
    StringExpression clone();

    String eval(Map ctx);

    String getSource();

    void render(Writer out, Map ctx) throws IOException;

    StringExpression copy(); // clone without the Exception

    /** @return whether the fieldKeys are sufficient to render this expression */
    boolean canRender(Set<FieldKey> fieldKeys);

    StringExpressionType toXML();
}
