/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.usageMetrics;

public enum ApiMetricArea implements SimpleMetricArea
{
    FILE_SERVLET("FileServlet", "/files/ URLs forwarded to the FileContent module's sendFile action (urlsDispatched)"),
    REPORT_INVOCATION("ReportInvocation", "Script report executions by report and engine: <report class>-<engine class> for external engines, Internal-<engine name> for in-process engines, and the report class for Jupyter notebook reports"),
    EMAIL_SENDS("emailSends", "Email messages handed to the transport provider, by origin (single, bulk, digest), provider, and outcome: <origin>-<provider>-sent or <origin>-<provider>-failed"),
    EMAIL_SEND_FAILURES("emailSendFailures", "Failed email sends by provider and cause: <provider>-timeout, -connection, -authentication, -rejectedAddress, or -other; notConfigured counts sends attempted with no or conflicting transport configuration"),
    EMAIL_SEND_TIMES("emailSendTimes", "Email send durations by provider and bucket: <provider>-LT250ms, -250msTo1s, -1sTo5s, -5sTo30s, -GT30s"),
    EMAIL_BATCH_SIZES("emailBatchSizes", "Messages sent per bulk notification or digest run, by origin and bucket: <origin>-1, -2To10, -11To50, -51To200, -201To1000, -GT1000"),
    EMAIL_RECIPIENTS("emailRecipients", "Recipients (To, Cc, and Bcc) per email message, by origin and bucket: <origin>-0, -1, -2To10, -11To50, -51To200, -GT200");

    private final Definition _definition;

    ApiMetricArea(String areaName, String description)
    {
        _definition = new Definition("API", areaName, description);
    }

    @Override
    public Definition getDefinition()
    {
        return _definition;
    }
}
