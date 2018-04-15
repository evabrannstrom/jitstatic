package io.jitstatic.api;

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

import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;

import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.testing.junit.ResourceTestRule;
import io.jitstatic.StorageData;
import io.jitstatic.auth.ConfiguratedAuthenticator;
import io.jitstatic.auth.User;
import io.jitstatic.storage.Storage;
import io.jitstatic.storage.StoreInfo;

public class MetaKeyResourceTest {

    private static final String UTF_8 = "UTF-8";
    private static final String PUSER = "puser";
    private static final String PSECRET = "psecret";

    private static final String BASIC_AUTH_CRED = createCreds(PUSER, PSECRET);
    private static final String BASIC_AUTH_CRED_2 = createCreds("not", "right");

    private Storage storage = mock(Storage.class);

    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Rule
    public ResourceTestRule RESOURCES = ResourceTestRule.builder()
            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addProvider(new AuthDynamicFeature(
                    new BasicCredentialAuthFilter.Builder<User>().setAuthenticator(new ConfiguratedAuthenticator())
                            .setRealm("jitstatic").setAuthorizer((User u, String r) -> true).buildAuthFilter()))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(User.class))
            .addResource(new MetaKeyResource(storage, (user) -> new User(PUSER, PSECRET).equals(user))).build();

    @After
    public void tearDown() {
        Mockito.reset(storage);
    }

    @Test
    public void testUserKeyWithoutUser() {
        ex.expect(NotAuthorizedException.class);
        ex.expectMessage("HTTP 401 Unauthorized");
        RESOURCES.target("/metakey/dog").request().get(JsonNode.class);
    }

    @Test
    public void testUserKeyWithUser() {
        ex.expect(NotAuthorizedException.class);
        ex.expectMessage("HTTP 401 Unauthorized");
        RESOURCES.target("/metakey/dog").request().header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_CRED_2)
                .get(JsonNode.class);
    }

    @Test
    public void testGetUserKeyWithWrongRef() {
        ex.expect(NotFoundException.class);
        ex.expectMessage("HTTP 404 Not Found");
        RESOURCES.target("/metakey/dog").queryParam("ref", "master").request()
                .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_CRED).get(JsonNode.class);
    }

    @Test
    public void testGetAKey() {
        StorageData storageData = new StorageData(new HashSet<>(), null, false, false, List.of());
        StoreInfo sd = new StoreInfo(new byte[] { 1 }, storageData, "version", "metadataversion");
        Mockito.when(storage.get("dog", null)).thenReturn(CompletableFuture.completedFuture(Optional.of(sd)));
        Response response = RESOURCES.target("/metakey/dog").request()
                .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_CRED).get();
        assertThat("metadataversion", Matchers.is(response.getEntityTag().getValue()));
        assertThat(HttpStatus.SC_OK, Matchers.is(response.getStatus()));
        assertThat(MediaType.APPLICATION_JSON_TYPE, Matchers.is(response.getMediaType()));
        assertThat(storageData, Matchers.is(response.readEntity(StorageData.class)));
        response.close();
    }

    @Test
    public void testModifyAKeyWithoutuser() {
        StorageData storageData = new StorageData(new HashSet<>(), null, false, false, List.of());
        ModifyMetaKeyData mukd = new ModifyMetaKeyData();
        mukd.setMessage("message");
        mukd.setUserInfo("userinfo");
        mukd.setUserMail("usermail");
        mukd.setMetaData(storageData);
        Response put = RESOURCES.target("/metakey/dog").request().put(Entity.json(mukd));
        assertThat(put.getStatus(), Matchers.is(HttpStatus.SC_UNAUTHORIZED));
        put.close();
    }

    @Test
    public void testModifyAKeyWithWrongUser() {
        StorageData storageData = new StorageData(new HashSet<>(), null, false, false, List.of());
        ModifyMetaKeyData mukd = new ModifyMetaKeyData();
        mukd.setMessage("message");
        mukd.setUserInfo("userinfo");
        mukd.setUserMail("usermail");
        mukd.setMetaData(storageData);
        Response put = RESOURCES.target("/metakey/dog").request().header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_CRED_2)
                .put(Entity.json(mukd));
        assertThat(put.getStatus(), Matchers.is(HttpStatus.SC_UNAUTHORIZED));
        put.close();
    }

    @Test
    public void testModifyAKeyWithWrongVersion() {
        StorageData storageData = new StorageData(new HashSet<>(), null, false, false, List.of());
        ModifyMetaKeyData mukd = new ModifyMetaKeyData();
        mukd.setMessage("message");
        mukd.setUserInfo("userinfo");
        mukd.setUserMail("usermail");
        mukd.setMetaData(storageData);
        StoreInfo sd = new StoreInfo(new byte[] { 1 }, storageData, "version", "metadataversion");
        Mockito.when(storage.get("dog", null)).thenReturn(CompletableFuture.completedFuture(Optional.of(sd)));
        Response put = RESOURCES.target("/metakey/dog").request().header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_CRED)
                .header(HttpHeaders.IF_MATCH, "\"2\"").put(Entity.json(mukd));
        assertThat(put.getStatus(), Matchers.is(HttpStatus.SC_PRECONDITION_FAILED));
        put.close();
    }

    @Test
    public void testModifyAKey() {
        StorageData storageData = new StorageData(new HashSet<>(), null, false, false, List.of());
        ModifyMetaKeyData mukd = new ModifyMetaKeyData();
        mukd.setMessage("message");
        mukd.setUserInfo("userinfo");
        mukd.setUserMail("usermail");
        mukd.setMetaData(storageData);
        StoreInfo sd = new StoreInfo(new byte[] { 1 }, storageData, "version", "2");
        Mockito.when(storage.get("dog", null)).thenReturn(CompletableFuture.completedFuture(Optional.of(sd)));
        Mockito.when(storage.putMetaData(Mockito.eq("dog"), Mockito.isNull(), Mockito.isA(StorageData.class),
                Mockito.eq("2"), Mockito.eq("message"), Mockito.eq("userinfo"), Mockito.eq("usermail")))
                .thenReturn(CompletableFuture.completedFuture("3"));
        Response put = RESOURCES.target("/metakey/dog").request().header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_CRED)
                .header(HttpHeaders.IF_MATCH, "\"2\"").put(Entity.json(mukd));
        assertThat(put.getStatus(), Matchers.is(HttpStatus.SC_OK));
        assertThat(put.getEntityTag().getValue(), Matchers.equalTo("3"));
        put.close();
    }

    @Test
    public void testModifyAKeyWithMalformedKeyData() {
        StorageData storageData = new StorageData(new HashSet<>(), null, false, false, List.of());
        ModifyMetaKeyData mukd = new ModifyMetaKeyData();
        mukd.setUserInfo("userinfo");
        mukd.setUserMail("usermail");
        mukd.setMetaData(storageData);
        StoreInfo sd = new StoreInfo(new byte[] { 1 }, storageData, "version", "metadataversion");
        Mockito.when(storage.get("dog", null)).thenReturn(CompletableFuture.completedFuture(Optional.of(sd)));
        Response put = RESOURCES.target("/metakey/dog").request().header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_CRED)
                .header(HttpHeaders.IF_MATCH, "\"2\"").put(Entity.json(mukd));
        assertThat(put.getStatus(), Matchers.is(422));
        put.close();
    }

    private static String createCreds(String user, String secret) {
        try {
            return "Basic " + Base64.getEncoder().encodeToString((user + ":" + secret).getBytes(UTF_8));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}