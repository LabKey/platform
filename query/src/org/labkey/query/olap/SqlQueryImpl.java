/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.query.olap;

import org.labkey.api.action.SpringActionController;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.MetadataElement;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.labkey.query.olap.QubeQuery.QubeExpr;
import static org.labkey.query.olap.QubeQuery.QubeMembersExpr;

/**
 * Created by matthew on 3/13/14.
 */
public class SqlQueryImpl
{
    QubeQuery qq;
    BindException errors;

    public SqlQueryImpl(QubeQuery qq, BindException errors)
    {
        this.qq = qq;
        this.errors = errors;
    }


    public String generateSQL() throws BindException
    {
        throw new UnsupportedOperationException("Not implemented yet, need more metadata!");
    }
}
