package io.jitstatic.hosted;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Arrays;

import org.eclipse.jgit.transport.ReceivePack;
import org.junit.Test;

import io.jitstatic.hosted.LogoPoster;

public class LogoPosterTest {

	private final ReceivePack rp = mock(ReceivePack.class);
	
	@Test
	public void testLogoPoster() {	
		doAnswer((i) -> {
             System.out.println(Arrays.toString(i.getArguments()));
             return null;	
		}).when(rp).sendMessage(any());		
		LogoPoster poster = new LogoPoster();
		poster.onPreReceive(rp,null);
	}

}
