# **WELCOME TO TURBO-LASER**


## Introduction

Turbo-laser is a distributed, falut tolerant batch processing architecture for GATE, using the Spring Batch framework. In the parlance of the batch processing domain language (http://docs.spring.io/spring-batch/reference/html/domain.html), it uses the remote partitioning method to create 'slave step' metadata for a DB table of documents. This metadata is persisted in the Spring database schema, and farmed out via a JMS middleware server. Remote Worker JVM slaves then retrieve metadata descriptions of work units. The outcome of processing is then persisted in the database, allowing robust tracking of failed steps.

## Why is batch processing in NLP difficult?

When processing very large natural corpora (10s - 100s of millions of documents), it is likely that some documents will present certain difficulties for NLP processes that are hard to predict. For example, some documents may have very long sentences, an unusual sequence of characters, or machine only content. Such circumstances can create a range of problems for NLP algorithms, and thus fault tolerant batch frameworks are required to handle such edge cases.

## Example useage

The entire process is configured via a single config file, which, in order for Spring Batch to pick up correctly, is determined by the environment variable TURBO_LASER

An example of a config file is in the test packages

> test_config.properties

Here, you will need to configure input and output database connection details, the activeMQ server and other particulars specific to the job you want to run (for example, the GATE home directory, and the GATE application .xgapp)

Turbo-laser is run with the standard Spring Batch CommandLineJobRunner, specifying the job type and appropriate Spring profiles, and key/value pairs that uniquely identify a job (which can be more or less anything - see Spring Batch documentation for details)

For example
```
java  -Dspring.profiles.active=prod,dBLineFixer -jar turbo-laser-0.1.0.jar uk.ac.kcl.batch.JobConfiguration dBLineFixerJob date=test1
```

Turbo-laser assumes the job repository schema is already in place in the DB implementation of your choice (see spring batch docs for more details)


The following types of job/profiles are currently available

dBLineFixerJob/dBLineFixer = fixes a bizarre but somehow frequent occurance in databases where strings of text from a single document are spread across multiple rows

gateJob/gate = run a generic GATE app. Specify which annotationSets to keep in the config file, or none to keep them all.

Coming soon:

Cognition text de-identification
Tika and OCR




