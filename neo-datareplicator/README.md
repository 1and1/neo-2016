Neo DataReplicator
==============

The DataReplicator is a ***pull-based client*** lib to replicated uri-addressed resources in a ***resilient*** way. Typically the replicator is used to replicate central managed resources such as configurations, template files, schema files, ACL lists or IP lists. 

The replicator uses a uri to address the resource to replicate. Currently the [uri schemes](https://tools.ietf.org/html/rfc3986) `http`, `https`, `file` and `classpath` are supported. 
For instance `http://myserver/defintions/schemas.zip`, `file:/C:/image/defintions/schemas.zip` or `classpath:defintions/schemas.zip` are valid resource identifier to address a `schemas.zip` resource.  

Modifications of the resource to replicate will be detected by running periodically checks. If the resource is modified, the replicator will process the modified resource. This pull-based approach causes that ***modifications become visible after a small delay*** of some seconds or few minutes depending on the concrete check period. As higher the check frequency as lower the modification delay. On the other side as higher the check frequency, as higher the load of the resource server. By default the replication period is 1 minute.

The replicator provides resiliency by ***caching the replicated resource on the local node***. The means the resource will also be available, if the resource server is down by falling back to the cached resource. Each time a resource is replicated it will be stored on the local node. If the replication job will be started, first the replicator tries to replicate the resource. If the resource is not available, the replicator will try to read the local stored resource of a former replication. By default the cached resource will be valid until 30 days. After this time the cached resource will be removed. 

In the example code below a new replicator job will be started within the constructor of the `HostnameValidator` class. Each time the resource is modified the `updateWhilelist` method is called. The `updateWhilelist` method will throw a runtime exception, if the data is invalid. In this case the replicator will not cache the resource. If the example `HostnameValidator` class is closed, the replicator job will be stopped by performing the `close` method. The replicator job uses an thread pool internally and should be stopped in an explicit way.     


```
public class HostnameValidator implements Closeable {   
    private final ReplicationJob whitelistReplicationJob;
    private volatile ImmutableList<String> whitelist;
    
    public HostnameValidator(final URI hostnameWhitelistUri) {
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .startConsumingText(this::updateWhilelist);
    }
    
    // replicator-related parsing code
    private void updateWhilelist(final String whitelistData) throws IllegalArgumentException {
        final ImmutableList<String> newWhitelist = ImmutableList.copyOf(Splitter.on("\n").trimResults().split(whitelistData));
        if (newWhitelist.isEmpty()) {
            throw new IllegalArgumentException("list must not be empty");
        }
        
        this.whitelist = newWhitelist;
    }
    
    // component lify cycle code
    @Override
    public void close() {
        whitelistReplicationJob.close();
    }

    // business code
    public boolean validate(final String hostname) {
        if (whitelist.contains(hostname)) {
            return true;    
        }

        // ...  
        return false;
    }
} 

```


## Customizing the replication properties ##
To use a custom check frequency the replicator provides the `withRefreshPeriod` method  
```
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMinutes(5))
                                                     .startConsumingText(this::updateWhilelist);
```

The max cache time can be modified by using `withMaxCacheTime` method  
```
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMinutes(5))
                                                     .withMaxCacheTime(Duration.ofDays(7))
                                                     .startConsumingText(this::updateWhilelist);
```

If the replicator should fail on start the `withFailOnInitFailure` method will be used. If true, the replicator will throw a runtime exception, if the resource is not available within the initialization phase.  
```
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMinutes(5))
                                                     .withMaxCacheTime(Duration.ofDays(7))
                                                     .withFailOnInitFailure(true)
                                                     .startConsumingText(this::updateWhilelist);
```

The cache directory can be customized by using the `withCacheDir` method. By default the cache directory is a subdirectory of the working directory.  
```
        final File cacheDir = ...
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMinutes(5))
                                                     .withMaxCacheTime(Duration.ofDays(7))
                                                     .withFailOnInitFailure(true)
													 .withCacheDir(cacheDir)
                                                     .startConsumingText(this::updateWhilelist);
```

To retrieve http(s) addressed resource the replicator uses a [JAX-RS client](https://docs.oracle.com/javaee/7/api/javax/ws/rs/client/Client.html) internally. By using the `withClient` method the user-specific client instance will be used instead.
```
        final File cacheDir = ...
		final Client client = ...
        this.whitelistReplicationJob = ReplicationJob.source(hostnameWhitelistUri)
                                                     .withRefreshPeriod(Duration.ofMinutes(5))
                                                     .withMaxCacheTime(Duration.ofDays(7))
                                                     .withFailOnInitFailure(true)
													 .withCacheDir(cacheDir)
													 .withClient(client)
                                                     .startConsumingText(this::updateWhilelist);
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
  
    // replicator-related parsing code
    private void unpackTemplatesZipfile(final ImmutableList<byte[]> zipped) throws RuntimeException {
        // perform unzip and some validations. May throw a runtime exception
        // ...
        
        this.templates = newTemplates;
    }

    // component lify cycle code
    @Override
    public void close() {
        whitelistReplicationJob.close();
    }
  
    // business code
    public String render(final String templatename, final Object... params) {
        // ...	
    }
} 
```
