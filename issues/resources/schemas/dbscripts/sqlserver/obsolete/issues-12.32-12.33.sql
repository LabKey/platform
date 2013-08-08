/*
 * Copyright (c) 2013 LabKey Corporation
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

UPDATE issues.issues SET type = NULL WHERE type = '';
UPDATE issues.issues SET area = NULL WHERE area = '';
UPDATE issues.issues SET milestone = NULL WHERE milestone = '';
UPDATE issues.issues SET resolution = NULL WHERE resolution = '';
UPDATE issues.issues SET string1 = NULL WHERE string1 = '';
UPDATE issues.issues SET string2 = NULL WHERE string2 = '';
UPDATE issues.issues SET string3 = NULL WHERE string3 = '';
UPDATE issues.issues SET string4 = NULL WHERE string4 = '';
UPDATE issues.issues SET string5 = NULL WHERE string5 = '';
