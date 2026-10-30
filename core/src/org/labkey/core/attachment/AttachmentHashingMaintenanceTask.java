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
package org.labkey.core.attachment;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

// Temporary: measures how long the post-26.11 de-duplicating attachment migration will take. Remove once that migration is in place.
public class AttachmentHashingMaintenanceTask implements MaintenanceTask
{
    private static final String NAME = "AttachmentHashing";
    private static final String STATISTICS_CATEGORY = "AttachmentHashingStatistics";
    // Matches GlobalAttachmentManager's property; labbook may not be deployed, so read it directly
    private static final String ELN_PROPERTIES_KEY = "ELN";
    private static final String ATTACHMENT_PARENT_KEY = "attachmentParentId";
    private static final long PROGRESS_INTERVAL = TimeUnit.SECONDS.toMillis(30);

    private record DocumentInfo(int rowId, String name, long size) {}

    @Override
    public String getDescription()
    {
        return "Hash all attachments once to measure hashing performance";
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public void run(Logger log)
    {
        if (!PropertyManager.getProperties(STATISTICS_CATEGORY).isEmpty())
        {
            log.debug("Attachment hashing statistics already collected; skipping");
            return;
        }

        DbScope scope = CoreSchema.getInstance().getScope();
        TableInfo documents = CoreSchema.getInstance().getTableInfoDocuments();
        String globalParentId = PropertyManager.getProperties(ELN_PROPERTIES_KEY).get(ATTACHMENT_PARENT_KEY);

        // FileSystemSnapshot attachments live on the file system, not in core.Documents
        SQLFragment listSql = SQLFragment.unsafe("SELECT RowId, DocumentName, DocumentSize FROM ").append(documents)
            .unsafeAppend(" WHERE (ParentType IS NULL OR ParentType <> 'FileSystemSnapshot')");
        if (globalParentId != null)
            listSql.unsafeAppend(" AND (Parent IS NULL OR Parent <> ?)", globalParentId);
        listSql.unsafeAppend(" ORDER BY RowId");

        long start = System.currentTimeMillis();
        List<DocumentInfo> docs = new ArrayList<>();
        new SqlSelector(scope, listSql).forEach(rs -> docs.add(new DocumentInfo(rs.getInt("RowId"), rs.getString("DocumentName"), rs.getLong("DocumentSize"))));
        long totalBytes = docs.stream().mapToLong(DocumentInfo::size).sum();
        long enumerationMillis = System.currentTimeMillis() - start;
        log.info("Attachment hashing: enumerated {} documents ({} bytes) in {}", Formats.commaf0.format(docs.size()), Formats.commaf0.format(totalBytes), DateUtil.formatDuration(enumerationMillis));

        long hashStart = System.currentTimeMillis();
        long lastProgress = hashStart;
        int hashed = 0;
        int failed = 0;
        long hashedBytes = 0;

        for (DocumentInfo doc : docs)
        {
            try
            {
                String[] hash = new String[1];
                new SqlSelector(scope, SQLFragment.unsafe("SELECT Document FROM ").append(documents).unsafeAppend(" WHERE RowId = ?", doc.rowId()))
                    .forEach(rs -> {
                        try (InputStream is = rs.getBinaryStream("Document"))
                        {
                            hash[0] = is == null ? null : DigestUtils.sha256Hex(is);
                        }
                        catch (IOException e)
                        {
                            throw new UncheckedIOException(e);
                        }
                    });

                if (hash[0] == null)
                {
                    failed++;
                    log.warn("Attachment hashing: no content for document {} (\"{}\")", doc.rowId(), doc.name());
                }
                else
                {
                    hashed++;
                    hashedBytes += doc.size();
                }
            }
            catch (Exception e)
            {
                failed++;
                log.error("Attachment hashing: failed to hash document {} (\"{}\")", doc.rowId(), doc.name(), e);
            }

            long now = System.currentTimeMillis();
            if (now - lastProgress >= PROGRESS_INTERVAL)
            {
                lastProgress = now;
                log.info("Attachment hashing: {} of {} documents hashed ({} of {} bytes), {} failed, elapsed {}, {} attachments per second", Formats.commaf0.format(hashed), Formats.commaf0.format(docs.size()), Formats.commaf0.format(hashedBytes), Formats.commaf0.format(totalBytes), Formats.commaf0.format(failed), DateUtil.formatDuration(now - hashStart), Formats.commaf0.format(perSecond(hashed, now - hashStart)));
            }
        }

        long hashingMillis = System.currentTimeMillis() - hashStart;
        long attachmentsPerSecond = perSecond(hashed, hashingMillis);
        log.info("Attachment hashing: complete. Hashed {} documents ({} bytes), {} failed. Hashing took {}, {} attachments per second", Formats.commaf0.format(hashed), Formats.commaf0.format(hashedBytes), Formats.commaf0.format(failed), DateUtil.formatDuration(hashingMillis), Formats.commaf0.format(attachmentsPerSecond));

        WritablePropertyMap statistics = PropertyManager.getWritableProperties(STATISTICS_CATEGORY, true);
        statistics.put("documentCount", String.valueOf(docs.size()));
        statistics.put("totalBytes", String.valueOf(totalBytes));
        statistics.put("hashedCount", String.valueOf(hashed));
        statistics.put("hashedBytes", String.valueOf(hashedBytes));
        statistics.put("failedCount", String.valueOf(failed));
        statistics.put("enumerationMillis", String.valueOf(enumerationMillis));
        statistics.put("hashingMillis", String.valueOf(hashingMillis));
        statistics.put("attachmentsPerSecond", String.valueOf(attachmentsPerSecond));
        statistics.save();
    }

    private static long perSecond(int count, long elapsedMillis)
    {
        return elapsedMillis > 0 ? Math.round(count * 1000.0 / elapsedMillis) : 0;
    }

    public static UsageMetricsProvider getMetricsProvider()
    {
        return metrics -> {
            Map<String, String> statistics = PropertyManager.getProperties(STATISTICS_CATEGORY);
            metrics.reportedIf(!statistics.isEmpty())
                .group("attachmentHashing", "One-time SHA-256 hashing of all non-global, database-stored attachments: document and byte counts, failures, enumeration and hashing times (ms), and attachments hashed per second")
                .putAll(() -> {
                    Map<String, Long> values = new TreeMap<>();
                    statistics.forEach((key, value) -> values.put(key, Long.valueOf(value)));
                    return values;
                });
            return metrics;
        };
    }
}
