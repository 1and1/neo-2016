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

import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.Files;

import net.oneandone.neo.datareplicator.utils.InMemoryConsumer;
import net.oneandone.neo.datareplicator.utils.TestServlet;
import net.oneandone.neo.datareplicator.utils.Utils;
import net.oneandone.neo.datareplicator.utils.WebServer;


public class BrokenResourceServerTest {
    

    
    @Test
    public void testBrokenHttpSource() throws Exception {
        
        WebServer server = WebServer.withServlet(new TestServlet())
                                    .start();
        String serverResource = server.getBasepath() + "hello.big5.txt?charset=big5"; 
        
        File cacheDir = Files.createTempDir();
        InMemoryConsumer testConsumer = new InMemoryConsumer();

        
        
        // successful replication -> resource server is up
        ReplicationJob job = ReplicationJob.source(serverResource)
                                           .withCacheDir(cacheDir)
                                           .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Chinese");
        job.close();

        
        server.close();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignore) { } 
        
        
        // successful replication -> event though resource server is down
        job = ReplicationJob.source(serverResource)
                            .withCacheDir(cacheDir)
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Chinese");
        job.close();
        
        
        
        // failed replication -> fall back is deactivated
        try {
            ReplicationJob.source(serverResource)
                          .withCacheDir(cacheDir)
                          .withFailOnInitFailure(true)
                          .startConsumingText(testConsumer);
            
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException expected) { }
    }
}