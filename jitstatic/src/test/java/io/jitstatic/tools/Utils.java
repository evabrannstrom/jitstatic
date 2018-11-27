package io.jitstatic.tools;

/*-
 * #%L
 * jitstatic
 * %%
 * Copyright (C) 2017 - 2018 H.Hegardt
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;

import com.codahale.metrics.health.HealthCheck.Result;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.jitstatic.JitstaticConfiguration;
import io.jitstatic.hosted.SourceHandler;
import io.jitstatic.source.ObjectStreamProvider;

public class Utils {

    public static void checkContainerForErrors(DropwizardAppExtension<JitstaticConfiguration> dw) {
        SortedMap<String, Result> healthChecks = dw.getEnvironment().healthChecks().runHealthChecks();
        List<Throwable> errors = healthChecks.entrySet().stream().map(e -> e.getValue().getError()).filter(Objects::nonNull)
                .collect(Collectors.toList());
        assertThat(errors.stream().map(e -> {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return new StringBuilder(sw.toString());
        }).map(sb -> sb.append(",")).map(sb -> sb.toString()).collect(Collectors.joining(",")), errors.isEmpty(), Matchers.is(true));
    }
    
    public static ObjectStreamProvider toProvider(byte[] data) {
        return new ObjectStreamProvider() {
            @Override
            public long getSize() throws IOException {
                return data.length;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(data);
            }
        };
    }

    public static byte[] toByte(ObjectStreamProvider provider) {
        try (InputStream is = provider.getInputStream()) {
            return SourceHandler.readStorageData(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
