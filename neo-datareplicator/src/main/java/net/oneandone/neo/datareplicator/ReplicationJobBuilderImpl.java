/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package net.oneandone.neo.datareplicator;



import java.io.Closeable;


import java.io.File;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.oneandone.neo.collect.Immutables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;


final class ReplicationJobBuilderImpl implements ReplicationJobBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ReplicationJobBuilderImpl.class);

    private final URI uri;
    private final boolean failOnInitFailure;
    private final Duration refreshPeriod;
    private final File cacheDir;
    private final Duration maxCacheTime;
    private final Client client;


    ReplicationJobBuilderImpl(final URI uri,
                              final boolean failOnInitFailure,
                              final File cacheDir,
                              final Duration maxCacheTime,
                              final Duration refreshPeriod,
                              final Client client) {
        this.uri = uri;
        this.failOnInitFailure = failOnInitFailure;
        this.refreshPeriod = refreshPeriod;
        this.cacheDir = cacheDir;
        this.maxCacheTime = maxCacheTime;
        this.client = client;
    }

    @Override
    public ReplicationJobBuilderImpl withRefreshPeriod(final Duration refreshPeriod) {
        Preconditions.checkNotNull(refreshPeriod);
        return new ReplicationJobBuilderImpl(this.uri,
                                             this.failOnInitFailure,
                                             this.cacheDir,
                                             this.maxCacheTime,
                                             refreshPeriod,
                                             this.client);
    }

    @Override
    public ReplicationJobBuilderImpl withMaxCacheTime(final Duration maxCacheTime) {
        Preconditions.checkNotNull(maxCacheTime);
        return new ReplicationJobBuilderImpl(this.uri,
                                             this.failOnInitFailure,
                                             this.cacheDir,
                                             maxCacheTime,
                                             this.refreshPeriod,
                                             this.client);
    }

    @Override
    public ReplicationJobBuilderImpl withFailOnInitFailure(final boolean failOnInitFailure) {
        return new ReplicationJobBuilderImpl(this.uri,
                                             failOnInitFailure,
                                             this.cacheDir,
                                             this.maxCacheTime,
                                             this.refreshPeriod,
                                             this.client);
    }

    @Override
    public ReplicationJobBuilderImpl withCacheDir(final File cacheDir) {
        Preconditions.checkNotNull(cacheDir);
        return new ReplicationJobBuilderImpl(this.uri,
                                             this.failOnInitFailure,
                                             cacheDir,
                                             this.maxCacheTime,
                                             this.refreshPeriod,
                                             this.client);
    }

    @Override
    public ReplicationJobBuilderImpl withClient(final Client client) {
        Preconditions.checkNotNull(client);
        return new ReplicationJobBuilderImpl(this.uri,
                                             this.failOnInitFailure, 
                                             this.cacheDir,
                                             this.maxCacheTime,
                                             this.refreshPeriod,
                                             client);
    }

    @Override
    public ReplicationJob startConsumingBinary(final Consumer<byte[]> consumer) {
        return startConsuming(data -> consumer.accept(data.asBinary()));
    }
    
    @Override
    public ReplicationJob startConsumingText(final Consumer<String> consumer) {
        return startConsuming(data -> consumer.accept(data.asText()));
    }

    private ReplicationJob startConsuming(final Consumer<Data> consumer) {
        Preconditions.checkNotNull(consumer);
        return new ReplicatonJobImpl(uri,
                                     failOnInitFailure,
                                     cacheDir,
                                     maxCacheTime,
                                     refreshPeriod,
                                     client,
                                     consumer);  
    }
    
    
    // internal data representation
    private static interface Data {
        
        long getHash();
        
        byte[] asBinary();
        
        String asText();
    }

    
    private static class HeuristicsDecodingData implements Data {
        private final byte[] binary;
        private final long hash;
        
        public HeuristicsDecodingData(final byte[] binary) {
            this.binary = binary;
            this.hash = Hashing.md5().newHasher().putBytes(binary).hash().asLong();
        }
        
        @Override
        public long getHash() {
            return hash;
        }
        
        @Override
        public byte[] asBinary() {
            return binary;
        }
        
        @Override
        public String asText() {
            return new String(binary, getCharset());
        }
        
        protected Charset getCharset() {
            return CharsetDetector.guessEncoding(binary);
        }
    }


    
    
    private static class MimeTypeBasedDecodingData extends HeuristicsDecodingData {
        private final Charset charset;
        
        public MimeTypeBasedDecodingData(final byte[] binary, final MediaType mediaType) {
            this(binary, mediaType.getParameters().get(MediaType.CHARSET_PARAMETER));
        }
        
        private MimeTypeBasedDecodingData(final byte[] binary, final String charsetname) {
            this(binary, (charsetname == null) ? null : Charset.forName(charsetname));
        }
        
        private MimeTypeBasedDecodingData(final byte[] binary, final Charset charset) {
            super((charset == null) ? binary : toUtf8EncodedBinary(binary, charset));  
            this.charset = (charset == null) ? null : Charsets.UTF_8;
        }
        
        private static byte[] toUtf8EncodedBinary(byte[] binary, final Charset charset) {
            return new String(binary, charset).getBytes(Charsets.UTF_8);
        }

        @Override
        protected Charset getCharset() {
            return (charset == null) ? super.getCharset()
                                     : charset;
        }
    }
    
    
    
    private static final class ReplicatonJobImpl implements ReplicationJob {
        private final Datasource datasource;
        private final FileCache fileCache;
        private final Consumer<Data> consumer;
        private final ScheduledExecutorService executor;
        private final Duration maxCacheTime;
        private final Duration refreshPeriod;

        private final AtomicReference<Optional<Instant>> lastRefreshSuccess = new AtomicReference<>(Optional.empty());
        private final AtomicReference<Optional<Instant>> lastRefreshError = new AtomicReference<>(Optional.empty());


        public ReplicatonJobImpl(final URI uri,
                                 final boolean failOnInitFailure,
                                 final File cacheDir,
                                 final Duration maxCacheTime,
                                 final Duration refreshPeriod,
                                 final Client client,
                                 final Consumer<Data> consumer) {

            this.maxCacheTime = maxCacheTime;
            this.refreshPeriod = refreshPeriod;
            this.consumer = new ConsumerAdapter(consumer);
            this.fileCache = new FileCache(cacheDir, uri.toString(), maxCacheTime);


            // create proper data source
            if (uri.getScheme().equalsIgnoreCase("classpath")) {
                this.datasource = new ClasspathDatasource(uri);

            } else if (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https")) {
                this.datasource = new HttpDatasource(uri, client);

            } else if (uri.getScheme().equalsIgnoreCase("file")) {
                this.datasource = new FileDatasource(uri);

            } else {
                throw new RuntimeException("scheme of " + uri + " is not supported (supported: classpath, http, https, file)");
            }


            // load on startup
            try {
                loadAndNotifyConsumer();

            } catch (final RuntimeException rt) {
                if (failOnInitFailure) {
                    throw rt;
                } else {
                    // fallback -> try to load from cache (will throw a runtime exception, if fails)
                    notifyConsumer(fileCache.load());
                }
            }


            // start scheduler for periodically reloadings
            this.executor = Executors.newScheduledThreadPool(0);
            executor.scheduleWithFixedDelay(() -> loadAndNotifyConsumer(),
                                            refreshPeriod.toMillis(),
                                            refreshPeriod.toMillis(),
                                            TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            executor.shutdown();
            datasource.close();
        }

        private void loadAndNotifyConsumer() throws RuntimeException {
            try {
                Data data = datasource.load();
                notifyConsumer(data);

                // data has been accepted by the consumer -> update cache
                fileCache.update(data);
                lastRefreshSuccess.set(Optional.of(Instant.now()));

            } catch (final RuntimeException rt) {
                // loading failed or consumer has not accepted the data
                LOG.warn("error occured by loading " + getEndpoint(), rt);
                lastRefreshError.set(Optional.of(Instant.now()));

                throw rt;
            }
        }

        private void notifyConsumer(final Data data) throws RuntimeException {
            consumer.accept(data);   // let the consumer handle the new data. Consumer may throw a runtime exception 
        }

        @Override
        public URI getEndpoint() {
            return datasource.getEndpoint();
        }

        @Override
        public Duration getMaxCacheTime() {
            return maxCacheTime;
        }

        @Override
        public Duration getRefreshPeriod() {
            return refreshPeriod;
        }

        @Override
        public Optional<Duration> getExpiredTimeSinceRefreshSuccess() {
            return lastRefreshSuccess.get().map(time -> Duration.between(time, Instant.now()));
        }

        @Override
        public Optional<Duration> getExpiredTimeSinceRefreshError() {
            return lastRefreshError.get().map(time -> Duration.between(time, Instant.now()));
        }


        @Override
        public String toString() {
            return new StringBuilder(datasource.toString())
                    .append(", refreshperiod=").append(refreshPeriod)
                    .append(", maxCacheTime=").append(maxCacheTime)
                    .append(" (last reload success: ").append(lastRefreshSuccess.get().map(time -> time.toString()).orElse("none"))
                    .append(", last reload error: ").append(lastRefreshError.get().map(time -> time.toString()).orElse("none")).append(")")
                    .toString();
        }



        private static final class ConsumerAdapter implements Consumer<Data> {
            private final Consumer<Data> consumer;
            private final AtomicReference<Long> lastMd5 = new AtomicReference<>(0l);

            public ConsumerAdapter(final Consumer<Data> consumer) {
                this.consumer = consumer;
            }

            @Override
            public void accept(final Data data) {

                // data changed (new or modified)?
                if (data.getHash() != lastMd5.get()) {
                    // yes
                    consumer.accept(data);
                    lastMd5.set(data.getHash());
                }
            }
        }


        private static abstract class Datasource implements Closeable {
            private final URI uri;

            public Datasource(final URI uri) {
                this.uri = uri;
            }

            @Override
            public void close() { }

            public URI getEndpoint() {
                return uri;
            }

            public abstract Data load() throws ReplicationException;

            @Override
            public String toString() {
                return "[" + this.getClass().getSimpleName() + "] uri=" + uri;
            }
        }


        private static class ClasspathDatasource extends Datasource {

            public ClasspathDatasource(final URI uri) {
                super(uri);
            }

            @Override
            public Data load() throws ReplicationException {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = getClass().getClassLoader();
                }

                final URL classpathUri = classLoader.getResource(getEndpoint().getRawSchemeSpecificPart());
                if (classpathUri == null) {
                    throw new RuntimeException("resource " + getEndpoint().getRawSchemeSpecificPart() + " not found in classpath");

                } else {
                    InputStream is = null;
                    try {
                        return new HeuristicsDecodingData(ByteStreams.toByteArray(classpathUri.openStream()));
                    } catch (final IOException ioe) {
                        throw new ReplicationException(ioe);
                    } finally {
                        Closeables.closeQuietly(is);
                    }
                }
            }
        }




        private static class FileDatasource extends Datasource {

            public FileDatasource(final URI uri) {
                super(uri);
            }

            @Override
            public Data load() {
                final File file = new File(getEndpoint().getPath());
                if (file.exists()) {
                    try {
                        return new HeuristicsDecodingData(Files.toByteArray(file));
                    } catch (IOException ioe) {
                        throw new ReplicationException(ioe);
                    }

                } else {
                    throw new ReplicationException("file " + file.getAbsolutePath() + " not found");
                }
            }
        }



        private static class HttpDatasource extends Datasource {
            private final Client client;
            private final boolean isUserClient;
            private final AtomicReference<CachedResponseData> cacheResponseDate = new AtomicReference<>(CachedResponseData.EMPTY);

            public HttpDatasource(final URI uri, final Client client) {
                super(uri);
                this.isUserClient = client != null;
                this.client = (client != null) ? client : ClientBuilder.newClient();
            }

            @Override
            public void close() {
                super.close();
                if (!isUserClient) {
                    client.close();
                }
            }

            @Override
            public Data load() {
                return load(getEndpoint());
            }

            protected Data load(final URI uri) {
                Response response = null;
                try {

                    CachedResponseData cached = cacheResponseDate.get();
                    if (!cached.getUri().equals(uri)) {
                        cached = null;
                    }


                    Builder builder = client.target(uri).request();

                    // will make request conditional, if a response has already been a received
                    if (cached != null) {
                        builder = builder.header("etag", cached.getEtag());
                    }


                    // perform query
                    response = builder.get();
                    final int status = response.getStatus();


                    // success
                    if ((status / 100) == 2) {
                        final MediaType mediaType = (response.getHeaderString("Content-type") == null) ? MediaType.APPLICATION_OCTET_STREAM_TYPE
                                                                                                       : MediaType.valueOf(response.getHeaderString("Content-type"));
                        final Data data = new MimeTypeBasedDecodingData(response.readEntity(byte[].class), mediaType);
                        
                        final String etag = response.getHeaderString("etag");
                        if (!Strings.isNullOrEmpty(etag)) {
                            // add response to cache
                            cacheResponseDate.set(new CachedResponseData(uri, data, etag));
                        }

                        return data;

                        // not modified
                    } else if (status == 304) {
                        if (cached == null) {
                            throw new ReplicationException("got " + status + " by performing non-conditional request " + getEndpoint());
                        } else {
                            return cached.getData();
                        }

                        // other (client error, ...)
                    } else {
                        throw new ReplicationException("got " + status + " by calling " + getEndpoint());
                    }

                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }


            private static class CachedResponseData {
                final static CachedResponseData EMPTY = new CachedResponseData(URI.create("http://example.org"), new HeuristicsDecodingData(new byte[0]), "");

                private final URI uri;
                private final Data data;
                private final String etag;

                public CachedResponseData(final URI uri, final Data data, final String etag) {
                    this.uri = uri;
                    this.data = data;
                    this.etag = etag;
                }

                public URI getUri() {
                    return uri;
                }

                public String getEtag() {
                    return etag;
                }

                public Data getData() {
                    return data;
                }
            }
        }




        private static final class FileCache extends Datasource {
            private static final String TEMPFILE_SUFFIX = ".temp";
            private static final String CACHEFILE_SUFFIX = ".cache";
            private final File dir;
            private final String genericCacheFileName;
            private final Duration maxCacheTime;


            public FileCache(final File cacheDir, final String name, final Duration maxCacheTime) {
                super(cacheDir.toURI());

                try {
                    this.maxCacheTime = maxCacheTime;
                    this.dir = new File(cacheDir, "datareplicator").getCanonicalFile();
                    dir.mkdirs();
                    
                    // filename is base64 encoded to avoid trouble with special chars
                    this.genericCacheFileName = Base64.getEncoder().encodeToString(name.getBytes(Charsets.UTF_8)) + "_";  
                      
                } catch (final IOException ioe) {
                    throw new ReplicationException(ioe);
                }
            }

            

            public void update(final Data data) {
                // creates a new cache file with timestamp
                final File cacheFile = new File(dir, genericCacheFileName + Instant.now().toEpochMilli() + CACHEFILE_SUFFIX);
                cacheFile.getParentFile().mkdirs();
                final File tempFile = new File(dir, UUID.randomUUID().toString() + TEMPFILE_SUFFIX);
                tempFile.getParentFile().mkdirs();
 

                /////
                // why this "newest cache file" approach?
                // this approach follows the immutable pattern and avoids race conditions by updating existing files. Instead
                // updating the cache file which could cause trouble in the case of concurrent processes, new cache files will
                // be written by using a timestamp as part of the file name.
                ////

                try {
                    FileOutputStream os = null;
                    try {
                        // write the new cache file
                        os = new FileOutputStream(tempFile);
                        os.write(data.asBinary());
                        os.close();

                        // and commit it (this renaming approach avoids "half-written" cache files. A cache file is there or not)
                        java.nio.file.Files.move(tempFile.toPath(), cacheFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        Closeables.close(os, true);  // close os in any case
                    }

                    // perform clean up to remove expired file
                    cleanup();

                } catch (final IOException ioe) {
                    LOG.warn("writing cache file " + cacheFile.getAbsolutePath() + " failed", ioe);
                }
            }


            @Override
            public Data load() {
                final Optional<File> cacheFile = getNewestCacheFile();
                if (cacheFile.isPresent()) {
                    FileInputStream is = null;
                    try {
                        is = new FileInputStream(cacheFile.get());
                        return new HeuristicsDecodingData(ByteStreams.toByteArray(is));
                    } catch (final IOException ioe) {
                        throw new ReplicationException("loading cache file " + cacheFile.get()  + " failed", ioe);
                    } finally {
                        Closeables.closeQuietly(is);
                    }

                } else {
                    throw new ReplicationException("cache file not exists");
                }
            }


            /**
             * @return the (most likely) newest cache file. It could happen that concurrent processes writes an new cache
             *         file in parallel.
             */
            private Optional<File> getNewestCacheFile() {
                long newestTimestamp = 0;
                File newestCacheFile = null;

                // find newest cache file
                for (File file : getCacheFiles()) {
                    try {
                        final long timestamp = parseTimestamp(file);
                        if (timestamp > newestTimestamp) {
                            newestCacheFile = file;
                            newestTimestamp = timestamp;
                        }
                    } catch (NumberFormatException nfe) {
                        LOG.debug(dir.getAbsolutePath() + " contains cache file with invalid name " + file.getName() + " Ignoring it");
                    }
                }


                // check if newest cache file is expired
                if (newestCacheFile != null) {
                    final Duration age = Duration.between(Instant.ofEpochMilli(newestCacheFile.lastModified()), Instant.now());
                    if (maxCacheTime.minus(age).isNegative()) {
                        LOG.warn("cache file is expired. Age is " + age.toDays() + " days. Ignoring it");
                        newestCacheFile = null;
                    }
                }

                return Optional.ofNullable(newestCacheFile);
            }


            private ImmutableList<File> getCacheFiles() {
                return ImmutableList.copyOf(dir.listFiles())
                                               .stream()
                                               .filter(file -> file.getName().endsWith(CACHEFILE_SUFFIX))
                                               .filter(file -> file.getName().startsWith(genericCacheFileName))
                                               .collect(Immutables.toList());
            }


            private long parseTimestamp(File file) {
                final String fileName = file.getName();
                return Long.parseLong(fileName.substring(fileName.lastIndexOf("_") + 1, fileName.length() - CACHEFILE_SUFFIX.length()));
            }


            private void cleanup() {
                removeExpiredTempFiles();
                removeExpiredCacheFiles();
            }


            private void removeExpiredTempFiles() {
                // remove expired temp files. temp file should exists for few millis or seconds only.
                final long minAgeTime = Instant.now().minus(Duration.ofDays(7)).toEpochMilli();
                ImmutableList.copyOf(dir.listFiles())
                             .stream()
                             .filter(file -> file.getName().endsWith(TEMPFILE_SUFFIX))
                             .filter(file -> file.lastModified() < minAgeTime)           // filter old temp file (days!)
                             .collect(Immutables.toList())
                             .forEach(file -> file.delete());                            // and delete it
            }


            private void removeExpiredCacheFiles() {
                // get newest cache file. Concurrently a new cache file could be written by another process. However, this
                // does not matter
                final Optional<File> newest = getNewestCacheFile();
                if (newest.isPresent()) {
                    final long newestTime = parseTimestamp(newest.get());
                    getCacheFiles().stream()
                                   .filter(file -> parseTimestamp(file) < newestTime)   // filter expired cache files
                                   .collect(Immutables.toList())
                                   .forEach(file -> file.delete());                     // and delete it
                }
            }

        }
    }
}