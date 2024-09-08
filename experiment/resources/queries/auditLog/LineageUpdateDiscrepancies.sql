-- transaction ids where some, but not all, samples had lineage update events prior to the fix for Issue 51210
WITH MixedTransactions AS
         (
             SELECT a1.transactionId FROM
                 (
                     SELECT DISTINCT transactionId
                     FROM auditlog.SampleTimelineEvent
                     -- created before the Issue 51210 fix was introduced
                     WHERE islineageupdate = true AND comment = 'Sample was updated.' and created < '2024-10-01'
                 ) a1
                     JOIN
                 -- but have associated entries that are not lineage updates
                     (
                         SELECT DISTINCT transactionid
                         FROM auditlog.SampleTimelineEvent
                         WHERE islineageupdate = false

                     ) a2
                 ON a1.transactionid = a2.transactionid
         )

SELECT d.container,
       d.sampletype,
       d.sampletypeid,
       d.samplename,
       d.sampleid,
       d.oldrecordmap AS lineageEventOldData,
       d.newrecordmap AS lineageEventNewData,
       d.rowId AS lineageEventRowId,
       b.latestLineageEventDate,
       m.transactionid as problemTransaction,
       m.oldrecordmap AS problemOldRecordMap,
       m.newRecordmap AS problemNewRecordMap,
       m.rowId AS problemEventRowId
FROM (

         SELECT a.sampleId, max(created) as latestLineageEventDate
         FROM auditlog.SampleTimelineEvent a
         WHERE sampleId IN (
             SELECT DISTINCT sampleId
             FROM auditLog.SampleTimelineEvent sa
             WHERE transactionId IN (
                 SELECT transactionId FROM MixedTransactions
             )
         )
           -- samples had an audit log creating lineage
           AND (a.newrecordmap LIKE '%MaterialInputs%' OR a.newrecordmap LIKE '%DataInputs%')
         GROUP BY sampleid
     ) b
         JOIN auditLog.SampleTimelineEvent d on d.sampleid = b.sampleid AND d.created = b.latestLineageEventDate
         JOIN (
    SELECT DISTINCT sampleId, transactionId, oldrecordmap, newrecordmap, rowId
    FROM auditlog.SampleTimelineEvent WHERE transactionID IN (SELECT transactionId FROM MixedTransactions)
) m ON m.sampleId = b.sampleId
ORDER BY problemTransaction, sampleId
