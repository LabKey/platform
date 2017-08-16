/*
 * Copyright (c) 2017 LabKey Corporation
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
CREATE NONCLUSTERED INDEX IX_ExceptionReport_Created ON mothership.exceptionreport (created DESC);
ALTER TABLE mothership.serverinstallation ADD IgnoreExceptions BIT;

ALTER TABLE mothership.exceptionstacktrace
    ADD LastReport DATETIME,
    FirstReport DATETIME,
    Instances INT;
GO

UPDATE mothership.ExceptionStackTrace
SET instances = x.Instances, LastReport = x.LastReport, FirstReport= x.FirstReport
FROM mothership.ExceptionStackTrace est JOIN
(SELECT exceptionStackTraceId, count(exceptionReportId) Instances, max(created) LastReport, min(created) FirstReport FROM mothership.ExceptionReport GROUP BY ExceptionStackTraceId) x
ON est.ExceptionStackTraceId = x.ExceptionStackTraceId;