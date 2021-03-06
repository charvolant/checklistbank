# ChecklistBank Solr


## To build the project

It is configured to build a shaded, executable jar which will build a full new Solr index from the postgres database using a configurabale amount of threads. The following Maven command will produce a shaded jar in your target dir named checklistbank-solr.jar.

```
mvn install package
```


## Building the Solr Index

This project provides the checklistbank solr index, it provides two main implementations to build Solr indexes:

### org.gbif.checklistbank.index.NameUsageIndexer 
Builds an index using embedded Solr servers, this class is mainly used for Unit/Integration testing. However, this class can be used to build a fully functional Solr index by executing the jar file and providing a properties file with the configuration settings:
  
  ```bash
    java -Xmx4g -cp checklistbank-solr.jar org.gbif.checklistbank.index.NameUsageIndexer ./checklistbank.properties
  ```
    
The properties file must contain the following settings:
    
   ```
   \#Thread pool configuration
   checklistbank.indexer.threads=16
   checklistbank.indexer.batchSize=10000
   checklistbank.indexer.writers=1
   \# leave blank to use embedded solr server
   checklistbank.indexer.solr.server=
   checklistbank.indexer.solr.delete=false
   checklistbank.indexer.solr.server.type=EMBEDDED
   \# mybatis
   checklistbank.db.dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
   checklistbank.db.dataSource.serverName=postgres server name
   checklistbank.db.dataSource.databaseName=checklistbank db
   checklistbank.db.dataSource.user=
   checklistbank.db.dataSource.password=
   checklistbank.db.enableCache=true
   checklistbank.db.maximumPoolSize=10
   checklistbank.db.connectionTimeout=10000
   checklistbank.db.leakDetectionThreshold=20000
   \# registry
   registry.ws.url=http://api.gbif.org/
   ```

### Oozie [workflow](src/main/resources/oozie/workflow.xml)  
This worflow has the capability of building Solr indexes for both cloud  and non-cloud servers. The non-cloud index is produced in a single shard index stored in HDFS, it will be copied into the Solr server. In general terms the workflow steps are: 
    * Export name usages to Avro files into HDFS
    * Run a map reduce index builder using the Avro files as input
    * If it's a Solr cloud index, then install it into the target cluster using the provided Zookeeper chroot.
    * If it's a single shard index, keep the produced index in the output directory.

#### How to install/run the Oozie workflow
 The simplest way of doing it is using the script [install-workflow](install-workflow.sh), it requires  3 command line parameters:
  * profile/environment name: properties file name as is stored in the GBIF configuration repository at the location [https://github.com/gbif/gbif-configuration/tree/master/checklistbank-index-builder](https://github.com/gbif/gbif-configuration/tree/master/checklistbank-index-builder).
  * Github OAuth token: Github authentication token to access the private repository [https://github.com/gbif/gbif-configuration/](https://github.com/gbif/gbif-configuration/) where the configuration files are stored.
  * Is single shard?: boolean parameter that determines if the produced index will be a cloud of a single shard index (default is set to false, i.e.: the index will be installed in a Solr Cloud cluster).
  The configuration file used by this workflow requires the following settings:
  
    ```
    hive.db=db
    hadoop.jobtracker=jobtracker:8032
    hdfs.namenode=hdfs:\/\/namenode:8020
    oozie.url=http:\/\/oozieserver:11000\/oozie
    solr.home=\/opt\/solr-5.4.1\/
    solr.zk=zk chroot
    solr.http.url= url to one of the Solr servers in the cluster, required to access de Core admin interface
    hdfs.out.dir=HDFS directory where the produced index is stored
    solr.collection= Solr colletion name
    solr.collection.opts=numShards=N&amp;replicationFactor=M&amp;maxShardsPerNode=K
    hadoop.client.opts=-XmxXXXXm
    mapred.opts=-D \'mapreduce.reduce.shuffle.input.buffer.percent=0.2\' -D \'mapreduce.reduce.shuffle.parallelcopies=5\' -D \'mapreduce.map.memory.mb=XXXX\' -D \'mapreduce.map.java.opts=-XmxXXXXm\' -D \'mapreduce.reduce.memory.mb=XXXX\' -D \'mapreduce.reduce.java.opts=-XmxXXXXm\'
    oozie.use.system.libpath=true
    oozie.wf.application.path=hdfs path where the oozie workflow is installed
    oozie.libpath= oozie workflow path\/lib\/
    oozie.launcher.mapreduce.task.classpath.user.precedence=true
    \# using user yarn because the staging dir is created under yarn user's folder
    user.name=yarn
    environment=dev/uat/prod
 
    \# Thread pool configuration
    checklistbank.indexer.threads=16
    checklistbank.indexer.batchSize=10000
    checklistbank.indexer.writers=1
    checklistbank.indexer.nameNode= should be equal to hdfs.namenode
    checklistbank.indexer.targetHdfsDir= path where the Avro files are created
    
    \# leave blank to use embedded solr server
    checklistbank.indexer.solr.server=
    checklistbank.indexer.solr.delete=false
    checklistbank.indexer.solr.server.type=EMBEDDED
    
    \# mybatis
    checklistbank.db.dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
    checklistbank.db.dataSource.serverName=postgre server name
    checklistbank.db.dataSource.databaseName=db
    checklistbank.db.dataSource.user=
    checklistbank.db.dataSource.password=
    checklistbank.db.enableCache=true
    checklistbank.db.maximumPoolSize=10
    checklistbank.db.connectionTimeout=10000
    checklistbank.db.leakDetectionThreshold=20000
    
    \# registry
    registry.ws.url=http://api.gbif(-dev/uat).org/
  ```
#### Examples

To install/copy the Oozie workflow and created a single shard index run:
  
  ```
  ./install-workflow.sh gitOAuthToken true
  ```
  
To install/copy the Oozie workflow and created a cloud shard index run:

  ```
    ./install-workflow.sh gitOAuthToken false
  ```
  
The workflow can also be executed using custom properties file using the Oozie client, this once the workflow was already installed on HDFS:
    
  ```
    oozie job --oozie http://oozieserver:11000/oozie/ -config custom.properties -run
  ```
