package io.jitstatic;

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

import io.dropwizard.lifecycle.Managed;

public class AutoCloseableLifeCycleManager<T extends AutoCloseable> implements Managed {

	private T object;

	public AutoCloseableLifeCycleManager(T object) {
		this.object = object;
	}
	
	@Override
	public void start() throws Exception {
	    // NOOP
	}

	@Override
	public void stop() throws Exception {
		this.object.close();
	}

}
