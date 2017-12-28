package jitstatic.remote;

import java.io.IOException;

/*-
 * #%L
 * jitstatic
 * %%
 * Copyright (C) 2017 H.Hegardt
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

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Constants;

import jitstatic.CorruptedSourceException;
import jitstatic.source.Source;
import jitstatic.source.SourceEventListener;

class RemoteManager implements Source {

	private static final int _5 = 5;
	private final ScheduledExecutorService poller;
	private final RemoteRepositoryManager remoteRepoManager;
	private final long duration;
	private final TimeUnit unit;

	private volatile ScheduledFuture<?> job;
	private final String defaultRef;

	public RemoteManager(final URI remoteRepoManager, final String userName, final String password, final long duration,
			final TimeUnit unit, Path baseDirectory, final String defaultRef) throws CorruptedSourceException, IOException {
		this(new RemoteRepositoryManager(remoteRepoManager, userName, password, baseDirectory, defaultRef),
				new ScheduledThreadPoolExecutor(1), duration, unit, defaultRef);
	}

	RemoteManager(final RemoteRepositoryManager remoteRepoManager, final ScheduledExecutorService scheduler,
			final long duration, final TimeUnit unit, final String defaultRef) {
		this.remoteRepoManager = Objects.requireNonNull(remoteRepoManager);
		this.poller = Objects.requireNonNull(scheduler);
		this.unit = Objects.requireNonNull(unit);
		this.duration = (duration <= 0 ? _5 : duration);
		this.defaultRef = defaultRef == null ? Constants.R_HEADS + Constants.MASTER : defaultRef;
	}

	@Override
	public void close() {
		final ScheduledFuture<?> j = this.job;
		if (j != null) {
			j.cancel(false);
		}
		this.poller.shutdown();
	}

	@Override
	public void addListener(final SourceEventListener listener) {
		this.remoteRepoManager.addListeners(listener);
	}

	@Override
	public void start() {
		this.job = this.poller.scheduleWithFixedDelay(this.remoteRepoManager.checkRemote(), 0, duration, unit);
	}

	@Override
	public void checkHealth() {
		final Exception fault = remoteRepoManager.getFault();
		if (fault != null) {
			throw new RuntimeException(fault);
		}
	}

	@Override
	public InputStream getSourceStream(final String key, String ref) {
		try {
			if (ref == null) {
				ref = defaultRef;
			}
			return remoteRepoManager.getStorageInputStream(key, ref);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

}
