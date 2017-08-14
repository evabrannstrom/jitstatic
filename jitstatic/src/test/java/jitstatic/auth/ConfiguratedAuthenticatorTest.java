package jitstatic.auth;

/*-
 * #%L
 * jitstatic
 * %%
 * Copyright (C) 2017 HHegardt
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

import static org.junit.Assert.*;

import java.util.Optional;

import org.junit.Test;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.basic.BasicCredentials;
import jitstatic.auth.ConfiguratedAuthenticator;
import jitstatic.auth.User;

public class ConfiguratedAuthenticatorTest {

	private static final String user = "user";
	private static final String secret = "secret";

	@Test
	public void testAuthenticate() throws AuthenticationException {
		ConfiguratedAuthenticator ca = new ConfiguratedAuthenticator(user, secret);
		final String u = new String("user");
		final String s = new String("secret");
		assertTrue(user != u);
		assertTrue(secret != s);
		Optional<User> authenticate = ca.authenticate(new BasicCredentials(u, s));
		assertTrue(authenticate.isPresent());
	}
	
	@Test
	public void testFailingAuthentication() throws AuthenticationException {
		ConfiguratedAuthenticator ca = new ConfiguratedAuthenticator(user, secret);
		Optional<User> authenticate = ca.authenticate(new BasicCredentials(user, "wrongpassword"));
		assertFalse(authenticate.isPresent());
	}
	
	

}
