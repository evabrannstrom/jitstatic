package jitstatic;

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

import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.GenericType;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import jitstatic.storage.StorageFactory;

public class RemoteHttpHostedGitRepositoryTest {

	private static final String ACCEPT_STORAGE2 = "accept/storage2";
	private static final TemporaryFolder tmpFolder = new TemporaryFolder();
	private static final DropwizardAppRule<JitstaticConfiguration> remote;
	private static final DropwizardAppRule<JitstaticConfiguration> local;
	private static final GenericType<Map<String, Object>> type = new GenericType<Map<String, Object>>() {
	};

	@ClassRule
	public static RuleChain chain = RuleChain.outerRule(tmpFolder)
			.around((remote = new DropwizardAppRule<>(JitstaticApplication.class,
					ResourceHelpers.resourceFilePath("simpleserver.yaml"),
					ConfigOverride.config("storage.baseDirectory", getFolder()),
					ConfigOverride.config("storage.localFilePath", ACCEPT_STORAGE2),
					ConfigOverride.config("hosted.basePath", getFolder()))))
			.around(new EraseSystemProperties("hosted.basePath"))
			.around(new TestClientRepositoryRule(getFolder(),
					() -> remote.getConfiguration().getHostedFactory().getUserName(),
					() -> remote.getConfiguration().getHostedFactory().getSecret(), getRepo(), ACCEPT_STORAGE2))
			.around((local = new DropwizardAppRule<>(JitstaticApplication.class,
					ResourceHelpers.resourceFilePath("simpleserver2.yaml"),
					ConfigOverride.config("storage.baseDirectory", getFolder()),
					ConfigOverride.config("storage.localFilePath", ACCEPT_STORAGE2),
					ConfigOverride.config("remote.remoteRepo", getRepo()),
					ConfigOverride.config("remote.userName",
							() -> remote.getConfiguration().getHostedFactory().getUserName()),
					ConfigOverride.config("remote.remotePassword",
							() -> remote.getConfiguration().getHostedFactory().getSecret()))));

	private static String remoteAdress;
	private static String localAdress;

	private static String remoteBasic;
	private static String localBasic;
	private static UsernamePasswordCredentialsProvider provider;

	@Rule
	public final ExpectedException ex = ExpectedException.none();

	private final ObjectMapper mapper = new ObjectMapper();

	@BeforeClass
	public static void setup() {
		remoteAdress = String.format("http://localhost:%d/application", remote.getLocalPort());
		localAdress = String.format("http://localhost:%d/application", local.getLocalPort());

		StorageFactory storage = remote.getConfiguration().getStorageFactory();
		remoteBasic = buildBasicAuth(storage.getUser(), storage.getSecret());
		storage = local.getConfiguration().getStorageFactory();
		localBasic = buildBasicAuth(storage.getUser(), storage.getSecret());
		provider = new UsernamePasswordCredentialsProvider(remote.getConfiguration().getHostedFactory().getUserName(),
				remote.getConfiguration().getHostedFactory().getSecret());
		assertTrue(local.getConfiguration().getHostedFactory() == null);
	}

	@Test
	public void testRemoteHostedGitRepository() {
		Client client = new JerseyClientBuilder(remote.getEnvironment()).build("test5 client");
		try {
			Map<String, Object> response = client.target(String.format("%s/storage/urlkey", remoteAdress)).request()
					.header(HttpHeader.AUTHORIZATION.asString(), remoteBasic).get(type);
			assertEquals("value", response.get("key"));
		} finally {
			client.close();
		}
		client = new JerseyClientBuilder(local.getEnvironment()).build("test6 client");
		try {
			Map<String, Object> response = client.target(String.format("%s/storage/urlkey", localAdress)).request()
					.header(HttpHeader.AUTHORIZATION.asString(), localBasic).get(type);
			assertEquals("value", response.get("key"));
		} finally {
			client.close();
		}
	}

	@Test
	public void testRemoteHostedGitUpdate()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		final String newValue = UUID.randomUUID().toString();
		String originalValue = null;
		Client client = new JerseyClientBuilder(local.getEnvironment()).build("test7 client");
		try {
			Builder clientTarget = client.target(String.format("%s/storage/urlkey", localAdress)).request()
			.header(HttpHeader.AUTHORIZATION.asString(), localBasic);
			
			Map<String, Object> response = clientTarget.get(type);
			assertEquals("value", response.get("key"));
			originalValue = (String) response.get("key");

			Path newFolder = tmpFolder.newFolder().toPath();
			Path storage = newFolder.resolve(ACCEPT_STORAGE2);
			try (Git git = Git.cloneRepository().setDirectory(newFolder.toFile()).setURI(getRepo().get())
					.setCredentialsProvider(provider).call();) {
				
				Map<String, Map<String, Object>> data = readSource(storage);
				Map<String, Object> map = data.get("urlkey");
				map.put("key", newValue);
				writeSource(data, storage);
				
				git.add().addFilepattern(ACCEPT_STORAGE2).call();
				RevCommit commit = git.commit().setMessage("Evolve").call();
				Iterable<PushResult> result = git.push().setCredentialsProvider(provider).call();
				RemoteRefUpdate remoteUpdate = result.iterator().next().getRemoteUpdate("refs/heads/master");
				assertEquals(Status.OK, remoteUpdate.getStatus());

				sleep(10 * 1000);

				response = clientTarget.get(type);
				assertEquals(newValue, response.get("key"));

				git.revert().include(commit).call();
				result = git.push().setCredentialsProvider(provider).call();
				remoteUpdate = result.iterator().next().getRemoteUpdate("refs/heads/master");
				assertEquals(Status.OK, remoteUpdate.getStatus());

				sleep(10 * 1000);

				response = clientTarget.get(type);
				assertEquals(originalValue, response.get("key"));

			}
		} finally {
			client.close();
		}
	}

	@Test
	public void testRemoteHostedJitStaticShouldNotHaveAnyRepoAccess()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		ex.expect(InvalidRemoteException.class);
		ex.expectCause(isA(NoRemoteRepositoryException.class));
		String host = String.format("http://localhost:%d/application/%s/%s", local.getLocalPort(), "selfhosted", "git");
		try (Git git = Git.cloneRepository().setDirectory(tmpFolder.newFolder()).setURI(host)
				.setCredentialsProvider(provider).call()) {
			fail("There shouldn't be any git repo when not hosted");
		}
	}

	private static Supplier<String> getFolder() {
		return () -> {
			try {
				return tmpFolder.newFolder().toString();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
	}

	private static String buildBasicAuth(final String user, final String password) {
		return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
	}

	private static Supplier<String> getRepo() {
		return () -> String.format("http://localhost:%d/application/%s/%s", remote.getLocalPort(),
				remote.getConfiguration().getHostedFactory().getServletName(),
				remote.getConfiguration().getHostedFactory().getHostedEndpoint());
	}

	private Map<String, Map<String, Object>> readSource(final Path storage) throws IOException {
		assertTrue(Files.exists(storage));
		try (InputStream bc = Files.newInputStream(storage);) {
			return mapper.readValue(bc, new TypeReference<Map<String, Map<String, Object>>>() {
			});
		}
	}

	private void writeSource(final Map<String, Map<String, Object>> map, final Path storage)
			throws JsonGenerationException, JsonMappingException, IOException {
		assertTrue(Files.exists(storage));
		mapper.writeValue(storage.toFile(), map);
	}

	private void sleep(final int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignore) {
		}
	}
}
