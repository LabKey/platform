/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.data;

/**
 * Marker interface for code that is invoked via a SQL upgrade script to perform upgrade operations that are
 * difficult or impossible to perform in straight-SQL. Methods are invoked via reflection based on calls to the
 * core.executeJavaUpgradeCode "stored procedure" from the SQL script.
 * User: adam
 * Date: Nov 21, 2008
 */
public interface UpgradeCode
{
}
