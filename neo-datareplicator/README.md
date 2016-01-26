DataReplicator
==============

The DataReplicator is a ***pull-based client*** lib to replicated uri addressed resources in a ***resilient*** way. Typically the replicator is used to replicate central managed resources such as configurations, template files, ACL lists or IP lists. 

The replicator uses uri to adress the resource to replicate. Currently the [uri schemes](https://tools.ietf.org/html/rfc3986) `http`, `https`, `file` and `classpath` are supported. 
For instance `http://myserver/defintions/schemas.zip`, `file:/C:/image/defintions/schemas.zip` or `classpath:defintions/schemas.zip` are valid resource identifier to address a `schemas.zip` resource.  

Modifications of the resource to replicate will be detected by performing periodical checks. If the resource is modified, the replicator will process the modified resource. This pull approach causes that ***modifications become visible after a small delay*** of some seconds or few minutes depending on the concrete check period. As higher the check frequency as lower the modification delay. On the other side as higher the check frequency, as higher the load of the resource server. By default the replication period is 1 minute.

The replicator provides resiliency by ***caching the replicated resource on the local node***. The means the resource will also be available, if the resource Server is down by falling back to the cached resource. Each time a resource is replicated it will be stored on the local node. If the replication job will be started, first the replicator tries to replicate the resource. If the resource is not available the replicator tries to read the local stored resource of a former replication. By default the cached resource will be valid until 30 days. After this time the cached resource will be removed. 

In the example code below a new replicator job will be started within the constructor. Each time the resource is modified the updateWhilelist is called. The updateWhilelist will throw an exception, if the data is invalid. In this case the replicator will not cache the resource. If the example HostnameValidator class is closed, the replicator job will be stopped by performing the close method. The replicator job uses an thread pool internally and should be stopped in an explicit way.     


```
public class HostnameValidator implements Closeable {
    
    private final ReplicationJob whitelistReplicationJob;
    private volatile ImmutableList<String> whitelist;
    
    public HostnameValidator(final URI hostnameWhitelistUri) {
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .startConsumingTextList(this::updateWhilelist);
    }
       
    @Override
    public void close() {
        whitelistReplicationJob.close();
    }

    private void updateWhilelist(final ImmutableList<String> whitelist) throws IllegalArgumentException {
        // perform some validations. May throw a runtime exception
        // ...
        
        this.whitelist = whitelist;
    }
    
    public boolean vaildate(final String hostname) {
        if (whitelist.contains(hostname)) {
            return gtrue;	
        }

        // ...	
    }
} 
```


## Customizing the replication properties ##
To use a custom check frequency the replicator provides the `withRefreshPeriod` method  
```
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMillis(5))
                                                     .startConsumingTextList(this::updateWhilelist);

```

The max cache time can be modified by using `withMaxCacheTime` method  
```
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMillis(5))
                                                     .withMaxCacheTime(Duration.ofDays(7))
                                                     .startConsumingTextList(this::updateWhilelist);

```

If the replicator should fail on start the `withFailOnInitFailure` method will be used. If true, the replicator will throw a runtime exception, if the resource is not available within the initialization phase.  
```
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMillis(5))
                                                     .withMaxCacheTime(Duration.ofDays(7))
                                                     .withFailOnInitFailure(true)
                                                     .startConsumingTextList(this::updateWhilelist);

```


## Metadata support ##
To implement a custom health check the `ReplicationJob` instance supports getting meta data.
```
    public Health healthCheck() {   // springboot health-based implementation 
        return whitelistReplicationJob.getExpiredTimeSinceRefreshSuccess()
                                      .map(elapsed -> Duration.ofDays(2).minus(elapsed).isNegative())  // expired?
                                      .map(expired -> expired ? Health.down().build() 
                                                              : Health.up().build())
                                      .orElseGet(Health.down().build());
    }
```
 

## Consumer support ##
By starting the replicator a consumer has to be passed such as the `updateWhilelist` method in the example above. The replicator supports binary data consumer as well as text-based consumer. To support text-based consumer the content type charset setting in context of the `http`, `https` scheme is considered as well as the charset in context of a `file`, `classpath` scheme. In this case BOM detection and heuristics methods (as fallback) are used


```
public class RenderingService implements Closeable {
    
    private final ReplicationJob whitelistReplicationJob;
    private volatile ImmutableMap<String, String> templates;
    
    public RenderingService(final URI templatesZipUri) {
        this.whitelistReplicationJob = ReplicationJob.source(templatesZipUri)
                                                     .startConsumingBinary(this::unpackTemplatesZipfile);
    }
  
    @Override
    public void close() {
        whitelistReplicationJob.close();
    }
  
    private void unpackTemplatesZipfile(final ImmutableList<byte[]> zipped) throws RuntimeException {
        // perform unzip and some validations. May throw a runtime exception
        // ...
        
        this.templates = templates;
    }
  
    public String render(final String templatename, final Object... params) {
        // ...	
    }
} 
```
