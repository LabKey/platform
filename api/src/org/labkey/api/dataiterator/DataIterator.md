#DataIterator Overview and Inventory

## Overview

**DataIterator** is an interface somewhat like jdbc ResultSet for iterating over 'rectangular' datasets (rows/cols).  
It provides ```next()``` to advance rows, and ```get()``` to retrieve column values.  Column metadata is provided 
using ColumnInfo instead of jdbc ResultSetMetaData.  Like ResultSet data iterator columns are 1-based.  So if there are
5 columns then they are 1,2,3,4,5.  Column 0, is used as a row number integer column.  That is especially useful for error reporting.

**DataIteratorBuilder** is simply a factory interface for DataIterator.  The idea is that a 'logical' data
processing step may be implemented by a combination of DataIterator objects, so it is very useful to separate
the classes that define the intended data processing from the classes that implement the process.  (see StandardDataIteratorBuilder)

**DataIteratorContext** is used to provide shared 'out of band' information across the DataIterators that implement a data 
processing pipeline.  In particular, the context holds the shared error collection.  It indicates whether we are implementing an "append" or "merge" operation.
It also provides a configuration map to pass parameters to particular data iterators. (see 
QueryUpdateService.ConfigParameters, 
HashDataIterator.Option,
DetailedAuditLogDataIterator.AuditConfigs,
SampleTypeUpdateServiceDI.Options,
DatasetUpdateService.Config)

**Pump** Once you've constructed a chain of DataIterator that you want to run, you can iterate through the rows yourself.  
However, often you won't really need to see the values being run through.  Pump is a helper class that will 'run' your 
DataIterator pipeline for you.

**QueryUpdateService**  
QueryUpdateService !== DataIterator!  Most implementations of insert/update/mergeRows() are implemented
using DataIterators, but not all.  No implementations of update/deleteRows() use DataIterators as far as I know.  Think
of QUS as an extended part of the TableInfo's implementation.  QUS is ultimately responsible for making sure that the
expected semantics of its table are correctly implemented.

## Inventory

### General

**AbstractDataIterator**  
This is a thin abstract base class for implementing your own DataIterator.  It provides some useful helper methods such as ```addRowError()```.  
It leaves the 'real work' entirely up to the subclass.  If you need a new DataIterator class, keep reading.  
There is likely a subclass that will get you there with less work.   

**AttachmentDataIterator**  
This class helps implement import from some of our archive formats.  It will handle resolving filenames provided as column
values and creating attachments using ```AttachmentService.get().addAttachment()```.

**BeanDataIterator**  
This shims a list of POJOs to look like a dataset.  Conceptually the same as translating all the objects to Maps using 
ObjectFactory.toMap() then using ```ListOfMapsDataIterator```.
 
**CachingDataIterator**    
CachingDataIterator is used to take a non-scrollable DataIterator and make it scrollable.  By default
this will cache all rows in memory.  The caller can control caching only a portion of the data by using the ```mark()``` and ```reset()``` methods.

_dev note: use mark() before advancing to the row you want to cache, use reset() to set the cursor _before_ the first cached row.
If it seems confusing, think of reset() as being analagous to the beforeFirst() method.  They do not put you on a row, but rather _before_ the first cached row._

**CoerceDataIterator**  
This class is used in conjunction with 'before' triggers.  We decide to try to present triggers with properly
converted data if possible e.g. integer:1 rather than string:"1".  However, we do not want to fail an import prematurely if 
a conversion fails.  After all, the purpose of the Trigger might be to implement its own translation logic.  This 
DataIterator therefore performs "best effort" conversion of the incoming data.

**DataIteratorUtil**  
Not a DataIterator obviously.  This class contains lots of useful stuff like ```wrapMap()``` and ```wrapScrollable()```.
It also can construct various column maps, which can be handy compared the DataIterator.getColumnInfo(int).  It also 
handles mapping DataIterator columns to columns in a target TableInfo.

**DetailedAuditLogDataIterator**  
DetailedAuditLogDataIterator handles generating per-row auditing of record modifications.  Overkill, maybe.  Awesome definitely.
Creating a detailed audit log record requires being able to see the "before" record and the "after" record.  To accomplish this
DetailedAuditLogDataIterator is paired with ExistingRecordDataIterator.  ExistingRecordDataIterator prefetches the before records
before they are updated and passes it as a name/value map down to the DetailedAuditLogDataIterator which creates the audit
log entry after the record is updated.

**DiskCachingDataIterator**  
This is a subclass of CachingDataIterator that will spill rows to disk after some in memory limit is reached.

**EmbargoDataIterator**  
EmbargoDataIterator is an upside down and backward version of CachingDataIterator.  Whereas CachingDataIterator caches
rows as you read them, EmbargoDataIterator caches rows before you read them.  The rows are held back (embargoed) until some
event happens.  In particular this solves an occasional problem caused by batching updates to the SQL database.  We may want
to ensure that the database insert/update for a row has happened before continuing.  StatementDataIterator and EmbargoDataIterator
work together to batch updates to the database, but hold on to rows until the batch has actually been submitted.

**ErrorIterator**  
This iterator always errors!  This class helps implements some particular error handling semantics.  For instance, an error
condition may be encountered in setup that should not be reported unless the input has one or more rows.  Moving on.

**ExistingRecordDataIterator**  
see DetailedAuditLogDataIterator.  We may find other uses for this in the future, but for now it is used to help
implement functionality required by DetailedAuditLogDataIterator.

**FilterDataIterator**  
Filters rows it does.  Should not renumber rows.  

**HashDataIterator**  
This DataIterator supports DataIntegrationService.createReimportBuilder().  This computes a hash of all the data values
in the source DataIterator.  This can be used by the reimport functionality to detect source data rows that have changed since they
were last imported.  This can be very useful improving import performance for large datasets that are imported on a regular schedule, but often have
many rows that do not change.

**ListOfMapsDataIterator**  
This creates a DataIterator from a List<Map>.  Used by APIs and for testing.

**MapDataIterator**  
This is a marker interface for a DataIterator that implements ```getMap()```.  The developer should use ```DataIteratorUtil.wrapMap()``` 
if this functionality is required.

**QueryDataIteratorBuilder**  
Executes LabKey SQL or saved query and returns the result as a DataIterator.  see ResultSetDataIterator.

**ResultSetDataIterator**  
This simply wraps a jdbc ResultSet or a org.labkey.api.data.Results with a DataIterator.  see QueryDataIteratorBuilder.

**ScrollableDataIterator**  
This is a marker interface for DataIterators that (might) support ```beforeFirst()```.  The scrollability of some
classes depend on their inputs, so to test for scrollability the develeper must check
```(di instanceof ScrollableDataIterator && ((ScrollableDataIterator)di).isScrollable()))```. 
If a scrollable data iterator is required, one can be constructed using ```ScrollableDataIterator wrap()```.
As much as possible we try to 'stream' data as we import it, that is we try to _not_ require that the
entire data set is resident in memory at one time.  Please be thoughtful about caching imported data.

**SimpleTranslator**  
A dubiously named DataIterator base class.  This is an opinionated base class where each column is implemented
by a ```java.util.function.Supplier```.  This class is particularly useful if you want to append several calculated
columns to your data stream.  

_dev note: using ```SimpleTranslator.selectAll(Set<String>)``` makes this class almost usable. It selects all the
columns from your input except those indicated in the Set<>.  Then you can add any additional columns.  One useful 
feature is that this class fetches and caches each column value in order.  This means you can reuse previously computed columns.
E.g. if Column 4 is participantId, and Column 5 is a calculated SequenceNum, the Column 6 can compute ParticipantSequenceNum
using the cached values for Column 4 and 5._

**StandardDataIteratorBuilder**  
This is an import DIB.  It implements a lot of the behavior that are just expected for LabKey imports.  The main features are:

* Align source columns with columns in the target TableInfo
* Convert columns to the target database type.  E.g. String->Integer.  Conversion is aware of missing value indicators so that those
can be handled correctly without generating conversion errors.
* Validate data constraints such as required, range, length, etc.

In addition, this is where the special type concept lookup type is handled.  It probably seems a little out of place, but this
step really wanted to live between "convert" and "validate".  We don't have a general purpose way to inject a step in the middle
so that's why that is hooked up here.

**StatementDataIterator**  
In principal, this data iterator has a pretty simple job.  Given a jdbc Statment (a ParameterMapStatement actually), bind
the statement parameter using values from the source data iterator and execute.  Repeat.  There is a little additional
complexity in order to handle the case where we need to reselect auto-generated id's (e.g. RowId or exp.objects.ObjectId).
Then there's statement batching to minimize round trips to the server.  Moreover, StatementDataIterator can be configured to actually submit Statements to a background thread that actually 
calls Statement.executeBatch().  This allows the foreground thread to populate the next batch while the previous batch
is being sent to the SQL database for processing.

Usually this is used as part of QueryUpdateService insert/merge/loadRows().  However, anytime you want to execute the 
same statement repeatedly this can be used.  It can and is used outside of QUS for various custom import scenarios.

see also **ParameterMapStatement**

**TableInsertDataIterator**  
This is a subclass of StatementDataIterator.  Given a target table this class generates the insert/merge statement
using ```StatementUtils.createStatement()```, then does the StatementDataIterator thing.

**TriggerDataIteratorHelper**  
This constructs the iterators that call TableInfo._target.fireRowTrigger().  It handles generation one iterator for the
"before" triggers and one for the "after" triggers.

_dev note: today QUS and triggers don't really understand merge.  For insert/import/loadRows(), the QUS will fire "insert"
triggers.  For updateRows() it will fire the "update" triggers._

**ValidatorIterator**  
ValidatorIterator passes data through untouched, however, it runs validated.  It has built-in support for a lot of 
common validations.  You can add your own using the interface ColumnValidator.  ValidatorIterator is typically constructed 
by StandardDataIteratorBuilder.

**WrapperDataIterator**  
This is an abstract base class that passes through every method to its input DataIterator.  This is a handy base class
for DataIterators that just fire some event on each row, or append exactly one column to an existing DataIterator.  
There are a lot of subclasses based on this e.g. FilterDataIterator.

### Test/Debug

**LoggingDataIterator**  
LoggingDataIterator.wrap() wraps a DataIterator with a verbose logger.  This is very, very handy when debugging
your new DataIterator.  You can see where data gets converted, where new columns get added etc.  Use this class liberally.
It is a noop if logging is not enabled for LoggingDataIterator.class, it will also avoid double wrapping the same DataIterator.
```LoggingDatasIterator.wrap(LoggingDataiterator.wrap(di))``` is harmless.  Its more confusing to have too few of these than
too many.

**StringTestIterator**  
Useful for creating a very simple DataIterator for tests.  ListOfMapsDataIterator is also useful for test data.

### Experiment/Sample/Dataclass etc.

**NameExpressionDataIterator**

**ExpDataIterators**

### More
There are lots of DataIterators hiding various modules.  If an implementation might generalized or be of interest to other developers please document here.   

### Unused / Archived

**AsyncDataIterator**  
This can be used to separate a data iterator pipeline into parts that are processed by separate threads.  This could be useful
if there is an expensive processing step.  This has not gotten much use because the implementation of StatementDataIterator
has lessened the need to parallelize other parts of the data pipeline.

**RemoveDuplicatesDataIterator**  
_Not used?_


## Logging Example
Here is an example of an TSV being imported into a List.  Not all the steps are shown, but this is just an example of
how to look at the output.


The first DataIterator shown in this chain is the TabLoader.  Notice that column 0 is the _rowNumber in the
source file and that all the column values are type String.  Because DataLoader supports MapDataIterator, LoggingDataIterator
also shows the current row printed as a JSON object.
````
DEBUG LoggingDataIterator      2021-04-02T11:38:48,223     http-nio-8080-exec-4 : TabLoader : org.labkey.api.reader.DataLoader$_DataIterator
                    _rowNumber    Integer| 1
                          ptid     String| 999320016
                       boolean     String| 0
                           dbl     String| 0
                          date     String| 12/8/2005 12:35
                       integer     String| -1
                        string     String| Phasellus scelerisque enim ut odio. Pellentesque ac augue sit amet risus mattis varius. Praesent at odio. In vel 
{"date":"12/8/2005 12:35","boolean":"0","string":"Phasellus scelerisque enim ut odio. Pellentesque ac augue sit amet risus mattis varius. Praesent at odio. In vel ","ptid":"999320016","integer":"-1","dbl":"0"}
````
This SimpleTranslator is constructed by StandardDataIteratorBuilder.  It handles type conversion and adds the builtin columns
 CreatedBy, ModifiedBy, Created, Modified, and EntityId.
````
DEBUG LoggingDataIterator      2021-04-02T11:38:48,224     http-nio-8080-exec-4 : StandardDIB convert : org.labkey.api.dataiterator.SimpleTranslator
                    _rowNumber    Integer| 1
                          ptid    Integer| 999320016
                       boolean    Boolean| false
                           dbl     Double| 0.0
                          date       Date| Thu Dec 08 12:35:00 PST 2005
                       integer    Integer| -1
                        string     String| Phasellus scelerisque enim ut odio. Pellentesque ac augue sit amet risus mattis varius. Praesent at odio. In vel 
                     container     String| d0b5141a-ad00-1038-afda-057652905698
                     CreatedBy    Integer| 1005
                    ModifiedBy    Integer| 1005
                       Created NowTimestamp| 2021-04-02 11:38:48.223
                      Modified NowTimestamp| 2021-04-02 11:38:48.223
                      EntityId     String| a6925574-7608-1039-a8ba-f0549910ac1b
````
And this is the ValidatorIterator, it probably doesn't need its own logger, because as you can see the data is unmodified.
This iterator only reports errors.
````
DEBUG LoggingDataIterator      2021-04-02T11:38:48,224     http-nio-8080-exec-4 : StandardDIB validate : org.labkey.api.dataiterator.ValidatorIterator
                    _rowNumber    Integer| 1
                          ptid    Integer| 999320016
                       boolean    Boolean| false
                           dbl     Double| 0.0
                          date       Date| Thu Dec 08 12:35:00 PST 2005
                       integer    Integer| -1
                        string     String| Phasellus scelerisque enim ut odio. Pellentesque ac augue sit amet risus mattis varius. Praesent at odio. In vel 
                     container     String| d0b5141a-ad00-1038-afda-057652905698
                     CreatedBy    Integer| 1005
                    ModifiedBy    Integer| 1005
                       Created NowTimestamp| 2021-04-02 11:38:48.223
                      Modified NowTimestamp| 2021-04-02 11:38:48.223
                      EntityId     String| a6925574-7608-1039-a8ba-f0549910ac1b
````