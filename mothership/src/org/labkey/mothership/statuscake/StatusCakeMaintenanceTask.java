/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.mothership.statuscake;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.UnexpectedException;
import org.labkey.mothership.MothershipManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.labkey.api.query.QueryUpdateService.ConfigParameters.BulkLoad;

/**
 * Pulls info from StatusCake via their API to populate three lists. Calculates monthly percent uptimes for each server.
 */
public class StatusCakeMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Pull data from StatusCake API to populate lists that track server uptime";
    }

    @Override
    public String getName()
    {
        return "StatusCake";
    }

    @Override
    public boolean isEnabledByDefault()
    {
        return false;
    }

    static final DateTimeFormatter yearMonthParser = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).toFormatter();

    static final DateTimeFormatter rfc3339Parser = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 2, 9, true) //2nd parameter: 2 for JRE (8, 11 LTS), 1 for JRE (17 LTS)
            .optionalEnd()
            .appendOffset("+HH:MM","Z")
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT)
            .withChronology(IsoChronology.INSTANCE);

    @Override
    public void run(Logger log)
    {
        String statusCakeAPIKey = MothershipManager.get().getStatusCakeApiKey();
        if (statusCakeAPIKey == null)
        {
            log.error("No StatusCake API key set");
            return;
        }

        String containerPath = MothershipManager.get().getUptimeContainer();

        if (containerPath == null)
        {
            log.error("No target container path set for StatusCake maintenance task");
            return;
        }

        Container targetContainer = ContainerManager.getForPath(containerPath);
        if (targetContainer == null)
        {
            log.error("No such target container {} found for StatusCake maintenance task", containerPath);
            return;
        }

        User user = User.getAdminServiceUser();
        UserSchema listSchema = QueryService.get().getUserSchema(user, targetContainer, "lists");
        TableInfo serversTable = listSchema.getTable("StatusCakeServers");
        if (serversTable == null)
        {
            log.error("No StatusCakeServers table");
            return;
        }
        TableInfo downtimeTable = listSchema.getTable("StatusCakeDowntime");
        if (downtimeTable == null)
        {
            log.error("No StatusCakeDowntime table");
            return;
        }
        TableInfo yearMonthsTable = listSchema.getTable("StatusCakeYearMonths");
        if (yearMonthsTable == null)
        {
            log.error("No StatusCakeYearMonths table");
            return;
        }

        List<Map<String, Object>> serverRows = new ArrayList<>();
        List<Map<String, Object>> historyRows = new ArrayList<>();

        // Limit to data from 2022 and later
        LocalDateTime since = LocalDateTime.of(2022, 1, 1, 0, 0, 0);

        int limit = 100;
        AtomicInteger rowsReturned = new AtomicInteger();
        int page = 1;

        do
        {
            try (CloseableHttpClient client = HttpClients.createDefault())
            {
                ClassicHttpRequest httpGet = ClassicRequestBuilder.get("https://api.statuscake.com/v1/uptime?limit=" + limit + "&page=" + page)
                        .build();
                httpGet.setHeader("Authorization", "Bearer " + statusCakeAPIKey);

                page++;

                client.execute(httpGet, response -> {
                    final HttpEntity entity1 = response.getEntity();

                    try (Reader reader = new InputStreamReader(entity1.getContent(), StandardCharsets.UTF_8))
                    {
                        JSONObject o = new JSONObject(new JSONTokener(reader));
                        JSONArray data = o.getJSONArray("data");
                        rowsReturned.set(data.length());
                        for (int i = 0; i < data.length(); i++)
                        {
                            JSONObject check = data.getJSONObject(i);
                            String id = check.getString("id");
                            String name = check.getString("name");
                            List tags = check.getJSONArray("tags").toList();
                            Server s = new Server(id, name, tags);

                            List<History> history = getUptime(s, client, since, statusCakeAPIKey, log);
                            log.info("Fetched {} with {} history rows", s, history.size());

                            serverRows.add(s.toMap());
                            historyRows.addAll(history.stream().map(History::toMap).toList());
                        }
                    }
                    EntityUtils.consume(entity1);
                    return null;
                });
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        while (limit == rowsReturned.get());

        try
        {
            log.info("Truncating lists");
            serversTable.getUpdateService().truncateRows(user, targetContainer, null, null);
            downtimeTable.getUpdateService().truncateRows(user, targetContainer, null, null);
            yearMonthsTable.getUpdateService().truncateRows(user, targetContainer, null, null);

            insertRows(log, serversTable, user, targetContainer, serverRows);
            insertRows(log, downtimeTable, user, targetContainer, historyRows);
            insertRows(log, yearMonthsTable, user, targetContainer, createYearMonthRows(since));
        }
        catch (BatchValidationException | QueryUpdateServiceException | SQLException | DuplicateKeyException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private static @NotNull List<Map<String, Object>> createYearMonthRows(LocalDateTime since)
    {
        List<Map<String, Object>> yearMonthRows = new ArrayList<>();
        LocalDateTime month = since;
        int monthIndex = 1;
        while (month.isBefore(LocalDateTime.now()))
        {
            yearMonthRows.add(Map.of("YearMonth", yearMonthParser.format(month), "MonthIndex", monthIndex++));
            month = month.plusMonths(1);
        }
        return yearMonthRows;
    }

    private void insertRows(Logger log, TableInfo table, User user, Container targetContainer, List<Map<String, Object>> rows) throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        log.info("Populating {} with {} rows", table.getName(), rows.size());
        BatchValidationException errors = new BatchValidationException();
        // Skip detailed audit via BulkLoad
        table.getUpdateService().insertRows(user, targetContainer, rows, errors, Map.of(BulkLoad, true), null);
        if (errors.hasErrors())
        {
            throw errors;
        }
    }

    private List<History> getUptime(Server server,
                                           CloseableHttpClient client,
                                           LocalDateTime since,
                                           String statusCakeAPIKey,
                                           Logger log) throws IOException
    {
        Instant instant = since.toInstant(ZoneOffset.UTC);

        int limit = 100;

        ClassicHttpRequest httpGet = ClassicRequestBuilder.get("https://api.statuscake.com/v1/uptime/" + server.getId() + "/periods?limit=" + limit + "&after=" + (instant.toEpochMilli() / 1000))
                .build();
        httpGet.setHeader("Authorization", "Bearer " + statusCakeAPIKey);

        List<History> result = new ArrayList<>();

        // StatusCake occasionally gives a 500 error on these requests. It doesn't seem to be rate-limiting, which
        // should return a 429. Regardless, give it a few retries before failing
        int attemptsRemaining = 3;
        boolean success = false;
        while (!success)
        {
            try
            {
                requestUptime(server, client, httpGet, result);
                success = true;
            }
            catch (FailedRequestException e)
            {
                attemptsRemaining--;

                if (attemptsRemaining == 0)
                {
                    throw e;
                }
                log.warn(e.getMessage());
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException ignored) {}
            }
        }

        if (result.size() == limit)
        {
            History h = result.getLast();
            result.addAll(getUptime(server, client, h.end(), statusCakeAPIKey ,log));
        }

        return result;
    }

    private static class FailedRequestException extends RuntimeException
    {
        public FailedRequestException(String message)
        {
            super(message);
        }

        public FailedRequestException(String message, Exception cause)
        {
            super(message, cause);
        }
    }

    private void requestUptime(Server server, CloseableHttpClient client, ClassicHttpRequest httpGet, List<History> result) throws IOException
    {
        client.execute(httpGet, response -> {
            final HttpEntity entity1 = response.getEntity();
            if (response.getCode() != 200)
            {
                throw new FailedRequestException("Bad response " + response.getCode() + " for " + httpGet.getPath());
            }

            // Responses are small so they fit comfortably in memory. Capture as a string so that we can report on
            // JSON parsing problems which happen intermittently
            String jsonString = PageFlowUtil.getStreamContentsAsString(entity1.getContent());
            try
            {
                JSONObject o = new JSONObject(new JSONTokener(jsonString));
                JSONArray data = o.getJSONArray("data");
                for (int i = 0; i < data.length(); i++)
                {
                    JSONObject check = data.getJSONObject(i);
                    String status = check.getString("status");
                    LocalDateTime start = LocalDateTime.parse(check.getString("created_at"), rfc3339Parser);
                    LocalDateTime end = check.has("ended_at") ? LocalDateTime.parse(check.getString("ended_at"), rfc3339Parser) : null;
                    server.checkFirstUse(start);
                    if ("down".equalsIgnoreCase(status))
                    {
                        History history = new History(server, start, end);
                        result.add(history);
                    }
                }
            }
            catch (JSONException e)
            {
                throw new FailedRequestException("Bad JSON for " + httpGet.getPath() + ", truncated content: " + StringUtils.truncate(jsonString, 500), e);
            }
            return null;
        });
    }
}
