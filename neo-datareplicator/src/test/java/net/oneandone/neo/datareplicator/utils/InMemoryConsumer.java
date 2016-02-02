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
package net.oneandone.neo.datareplicator.utils;




import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;




public class InMemoryConsumer implements Consumer<String> {
    private final AtomicReference<Optional<String>> textRef = new AtomicReference<>(Optional.empty());
    
    @Override
    public void accept(String text) {
        this.textRef.set(Optional.of(text));
    }
    
    
    public String waitForText() {
        return waitForText(Duration.ofSeconds(3));
    }
    
    public String waitForText(final Duration maxWaitTime) {
        for (int i = 0; i < 100; i++) {
            if (textRef.get().isPresent()) {
                return textRef.get().get();
            }
            
            try {
                Thread.sleep(maxWaitTime.toMillis() / 100);
            } catch (InterruptedException ignore) { }
        }
        
        throw new RuntimeException("no data received");
    }
}
