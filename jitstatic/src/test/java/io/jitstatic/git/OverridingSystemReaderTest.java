package io.jitstatic.git;

/*-
 * #%L
 * jitstatic
 * %%
 * Copyright (C) 2017 - 2019 H.Hegardt
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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OverridingSystemReaderTest {

    @Test
    void testHostName() {
        OverridingSystemReader reader = new OverridingSystemReader();
        assertNotNull(reader.getHostname());
    }

    @Test
    void testLoadFileNoSystemConfig() throws IOException, ConfigInvalidException {
        OverridingSystemReader reader = new OverridingSystemReader();
        Config config = Mockito.mock(Config.class);
        FS fs = Mockito.mock(FS.class);
        FileBasedConfig openSystemConfig = reader.openSystemConfig(config, fs);
        assertNotNull(openSystemConfig);
        Mockito.verify(fs).getGitSystemConfig();
        openSystemConfig.load();
        assertFalse(openSystemConfig.isOutdated());
    }

}
