-- lineage update events for samples that had lineage and were involved in a transaction that
-- updated lineage on some but not all samples.
-- Includes samples that may have had their lineage intentionally removed before this transaction happened.
WITH MixedTransactions AS
         (SELECT a1.transactionId
          FROM (SELECT DISTINCT transactionId
                FROM auditlog.SampleTimelineEvent
                -- created before the bug fix was introduced
                WHERE islineageupdate = true
                  AND comment = 'Sample was updated.'
                  AND created < '2024-10-01') a1
                   JOIN
               -- but have associated entries that are not lineage updates
                   (SELECT DISTINCT transactionid
                    FROM auditlog.SampleTimelineEvent
                    WHERE islineageupdate = false) a2
               ON a1.transactionid = a2.transactionid
          )
SELECT
    rowid,
    container,
   sampletype,
   sampletypeid,
   samplename,
   d.sampleid,
    latestLineageEventDate,
   oldrecordmap,
   newrecordmap
   --transactionid,  TODO this should be the transactionid of the problematic transaction

FROM (
         SELECT a.sampleId, max(created) as latestLineageEventDate
         FROM auditlog.SampleTimelineEvent a
         WHERE sampleId IN (
             -- samples involved in transactions where some, but not all, samples had lineage update events prior to the fix for Issue 51210
             SELECT DISTINCT sampleId
             FROM auditLog.SampleTimelineEvent sa
             WHERE transactionId IN ( SELECT transactionId FROM MixedTransactions )
         )
         -- samples had an audit log creating lineage
         AND (a.newrecordmap LIKE '%MaterialInputs%' OR a.newrecordmap LIKE '%DataInputs%')
         GROUP BY sampleid
     ) b

         JOIN auditLog.SampleTimelineEvent d on d.sampleid = b.sampleid AND d.created = b.latestLineageEventDate
ORDER BY sampleId
