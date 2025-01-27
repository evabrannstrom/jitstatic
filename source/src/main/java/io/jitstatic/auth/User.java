package io.jitstatic.auth;

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

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(justification = "Equals used here is not dodgy code", value = { "EQ_UNUSUAL" })
public final class User implements Principal {

    @NotNull
    @NotEmpty
    private final String user;
    @NotNull
    private final String password;

    @JsonCreator
    public User(@JsonProperty("user") final String user, @JsonProperty("password") final String password) {
        this.user = user;
        this.password = password;
    }

    @Override
    @JsonGetter("user")
    public String getName() {
        return this.user;
    }

    @JsonGetter("password")
    public String getPassword() {
        return password;
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, password);
    }

    @Override
    public boolean equals(final Object other) {
        return Optional.ofNullable(other)
                .filter(that -> that instanceof User)
                .map(that -> (User) that)
                .filter(that -> Objects.equals(this.user, that.user))
                .filter(that -> Objects.equals(this.password, that.password))
                .isPresent();
    }

    @Override
    public String toString() {
        return "User [user=" + user + "]";
    }
}
