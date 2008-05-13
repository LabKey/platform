/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import antlr.RecognitionException;

import java.io.StringReader;
import java.util.List;
import java.util.ArrayList;

import org.labkey.query.sql.antlr.SqlBaseLexer;
import org.labkey.query.sql.antlr.SqlBaseParser;

public class SqlParser extends SqlBaseParser
{
    List<RecognitionException> _errors;
    public SqlParser(String str)
    {
        super(new SqlBaseLexer(new StringReader(str)));
        _errors = new ArrayList();
    }

    public void reportError(RecognitionException ex)
    {
        _errors.add(ex);
    }

    public List<RecognitionException> getErrors()
    {
        return _errors;
    }
}
