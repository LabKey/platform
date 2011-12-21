/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

-- Create an index to make the upgrade go faster
CREATE INDEX idx_exceptionstacktrace ON
  mothership.exceptionstacktrace(container, stacktracehash, exceptionstacktraceid);

-- Consolidate duplicate exceptions rows that have the same hash in the same container
UPDATE mothership.ExceptionReport SET ExceptionStackTraceId =
  (SELECT MIN(est.ExceptionStackTraceId) FROM mothership.ExceptionStackTrace est
    WHERE Container = (SELECT Container FROM mothership.ExceptionStackTrace est2 WHERE est2.ExceptionStackTraceId = mothership.ExceptionReport.ExceptionStackTraceId)
    AND StackTraceHash = (SELECT StackTraceHash FROM mothership.ExceptionStackTrace est2 WHERE est2.ExceptionStackTraceId = mothership.ExceptionReport.ExceptionStackTraceId));

-- Delete all but the first report for rows with the same hash and container
DELETE FROM mothership.ExceptionStackTrace WHERE ExceptionStackTraceId NOT IN
  (SELECT MIN(est2.ExceptionStackTraceId) FROM mothership.ExceptionStackTrace est2 GROUP BY Container, StackTraceHash);

-- We don't need the index anymore, since we're going to be adding a UNIQUE constraint
-- now that we've de-deduped the rows
DROP INDEX mothership.idx_exceptionstacktrace;

-- Add a constraint to prevent us from getting duplicate rows in the future
ALTER TABLE mothership.ExceptionStackTrace
  ADD CONSTRAINT uq_exceptionstacktrace_container_hash UNIQUE (Container, StackTraceHash);
