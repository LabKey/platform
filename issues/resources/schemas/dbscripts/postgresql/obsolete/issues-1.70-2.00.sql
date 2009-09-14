/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

ALTER TABLE issues.IssueKeywords
    ADD COLUMN "default" BOOLEAN NOT NULL DEFAULT '0';

/*
    Old, hard-coded defaults were Priority="3", ResolutionType="Fixed"
    Set these in the keywords table so existing issues lists continue to work as before
*/
UPDATE issues.IssueKeywords SET "default"='1' WHERE Type=6 AND Keyword='3';
UPDATE issues.IssueKeywords SET "default"='1' WHERE Type=7 AND Keyword='Fixed';