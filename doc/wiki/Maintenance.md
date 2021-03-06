# Maintenance

## Description

Integration platform generates application logs and writes records to database.

## Application logs

This chapter is specific for CleverBus admin GUI use.

Application logs are generated via [logback library](http://logback.qos.ch) and are configured in *logback.xml*. There is Maven property *log.folder* that specifies target folder for application logs.

Log names are in format *logFile\_RRRR-MM-DD\_N.log*, where *N* is order number unique for each day. If the log file is greater than 10 MB then new log file with next order number is created. It's best practice to backup these application logs, at least for 2 months.

Logback offers lot of possibilities where and how log items save to - to file system, to database, send it by emails etc. 

CleverBus provides simple tool for application log searching (see <a href='Admin-GUI'>Admin GUI - Search in log by date</a>). This tool must be correctly configured (<i>log.folder.path</i> - path to application logs, <i>log.file.pattern</i> - format of log file names) and then allows to go through all application logs and find searched information.

When we use standard installation with Apache Tomcat server then we have all logs in */srv/cbssesb/logs/* folder with the the following sub-folders:

-   *apache*: logs generated by Apache HTTP server
-   *j2ee*: logs from integration platform
-   *tomcat*: logs generated by Apache Tomcat

### Database

CleverBus stores lot of records to database, namely to these tables (see [Data model](Data-model) for more information):

-   *message*
-   *external\_call*
-   *request* (it depends on configuration)
-   *response* (it depends on configuration)

Also database records should be backuped. Remove old records to keep good performance - we remove records older then 2 months usually on our production installations.


There is functionality for <a href='Data-model'>archiving</a> database tables.

### Stop ESB

CleverBus allows to switch to stopping mode where no new requests will be processed, only current asynchronous messages will be finished (messages in states *PROCESSING* and *WAITING\_FOR\_RES*).

You will find more information in [admin GUI](Admin-GUI) documentation.

