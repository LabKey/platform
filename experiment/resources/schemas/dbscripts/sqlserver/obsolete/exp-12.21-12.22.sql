/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

-- Add a column to track the chaining of original and replaced runs
ALTER TABLE exp.ExperimentRun ADD ReplacedByRunId INT;

ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_ReplacedByRunId FOREIGN KEY (ReplacedByRunId)
        REFERENCES exp.ExperimentRun (RowId);

CREATE INDEX IDX_ExperimentRun_ReplacedByRunId ON exp.ExperimentRun(ReplacedByRunId);
