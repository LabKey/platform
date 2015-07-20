/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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


CREATE TABLE test.TestTable2
(
    _ts TIMESTAMP DEFAULT now(),
    EntityId ENTITYID NOT NULL,
    RowId SERIAL,
    CreatedBy USERID,
    Created TIMESTAMP,

    Container ENTITYID,        --container/path
    Text VARCHAR(195),        --filename

    IntNull INT NULL,
    IntNotNull INT NOT NULL,
    DatetimeNull TIMESTAMP NULL,
    DatetimeNotNull TIMESTAMP NOT NULL,
    RealNull REAL NULL,
    BitNull BOOLEAN NULL,
    BitNotNull BOOLEAN NOT NULL,

    CONSTRAINT PK_TestTable2 PRIMARY KEY (Container,Text)
);
