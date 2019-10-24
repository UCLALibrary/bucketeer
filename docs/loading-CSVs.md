## Bucketeer's Batch Loader 

Bucketeer has a web interface through which CSV files can be loaded and their items processed in batch. This page aims to document the requirements of the batch loader.

### Initial vs. Subsequent Loads

There is a checkbox on the batch loader form that says "Process previous failures only". This is unchecked by default. By default, a CSV upload is considered to be the first run of a batch. There probably won't be a `Bucketeer State` or `IIIF Access URL` column in the CSV because these are added by Bucketeer. If you choose to reload a CSV that was output from Bucketeer, and that does have these two columns, the values of the `Bucketeer State` column will be removed before the new batch job is run.

If you do check the checkbox on the form, it means that you'd like to reload a CSV that has been output by Bucketeer in a previous run. You might need to do this because not all of the images were successfully processed in the first run. In this case, the following state changes will be made to the `Bucketeer State` column's values when it's loaded into Bucketeer:

* `empty` to `empty` (Empty is the state that means the row is ready for processing; this isn't really a change)
* `failed` to `empty` (Previous failures are emptied so that they will be retried in the new run)
* `missing` to `empty` (Rows without files in the first batch will be retried to see if the file now exists)
* `ingested` to `ingested` (Something that is ingested is something that has been through two batch runs already)
* `succeeded` to `ingested` (These are things that were converted in the first run and don't need to be tried again)

Both initial and subsequent jobs will output a CSV file with the `Bucketeer State` and `IIIF Access URL` columns. Any row that is not there for structural purposes only should have a `failed`, `missing`, `ingested`, or `succeeded` state value.

### What Are Structural Rows?

Structural rows are rows in the CSV file that aren't intended to have a source file that needs to be processed. An example of this may be a row that represents a Collection. A CSV file may also represent a complex object, something that might have a Collection, a Work, and Pages. In this case, neither a Collection nor a Work might have source files. So, how does Bucketeer distinguish between a simple Work that's in a Collection and a Work that's in a collection but that also has pages?

There is an optional `Object Type` column that may coexist with another optional column: `viewingHint`. If a row has an object type of `Work` and a value in the `viewingHint` column it's considered a part of a complex object that will not have a source file. If a row has an object type of `Work` but no `viewingHint` column, then it's considered a part of a simple object. Those types of works will have source files associated with them. This is the distinction that Bucketeer currently uses, but it may use more sophisticated detection methods in the future. The purpose of the distinction is so that Buckeeteer will not mark a Work or Collection row as having a missing item when it was not intended to have one (so to reduce false failures and save the user time spent looking at these potential issues).