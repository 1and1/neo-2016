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





import java.io.File;
import java.io.FileWriter;
import java.time.Duration;
import java.time.Instant;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;

import net.oneandone.neo.datareplicator.utils.InMemoryConsumer;
import net.oneandone.neo.datareplicator.utils.TestServlet;
import net.oneandone.neo.datareplicator.utils.Utils;
import net.oneandone.neo.datareplicator.utils.WebServer;


public class ExpiredFilesCleanupTest {
    
    private static TestServlet servlet = new TestServlet();
    private static WebServer server;

    
    @BeforeClass
    public static void setUp() throws Exception {
        server = WebServer.withServlet(servlet)
                          .start();
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        server.close();
    }
    
    
    @Test
    public void testHttpSource() throws Exception {
        InMemoryConsumer testConsumer = new InMemoryConsumer();
        File cacheDir = Files.createTempDir();
        
        // create a old temp file (simulates former replication crash)
        File tempFile = new File(cacheDir, "97b145f7-c7c6-49f8-9aeb-49bae9bf1373.temp");
        FileWriter fw = new FileWriter(tempFile);
        fw.write("data data data data");  // content does not matter
        fw.close();
        tempFile.setLastModified(Instant.now().minus(Duration.ofDays(9)).toEpochMilli());
        
        System.out.println(cacheDir.getAbsolutePath());

        ReplicationJob job = ReplicationJob.source(server.getBasepath() + "hello.utf8.txt?charset=utf-8")
                                           .withCacheDir(cacheDir)
                                           .withFailOnInitFailure(true)
                                           .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();
        
        
        // restart job to force new fetch -> new cache file
        job = ReplicationJob.source(server.getBasepath() + "hello.utf8.txt?charset=utf-8")
                            .withCacheDir(cacheDir)
                            .withFailOnInitFailure(true)
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();

        
        // check that cache dir contains one cache file only
        Assert.assertEquals(1, cacheDir.listFiles().length);
    }
}