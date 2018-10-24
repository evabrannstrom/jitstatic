package io.jitstatic.hosted;

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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.jitstatic.CommitMetaData;
import io.jitstatic.RepositoryUpdater;
import io.jitstatic.auth.UserData;
import io.jitstatic.utils.Pair;

public class UserUpdater {

    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    private static final Logger LOGGER = LoggerFactory.getLogger(UserUpdater.class);
    private final RepositoryUpdater repositoryUpdater;

    public UserUpdater(RepositoryUpdater repositoryUpdater) {
        this.repositoryUpdater = repositoryUpdater;
    }

    public List<Pair<String, String>> updateUser(final List<Pair<String, UserData>> userData, final Ref ref, final CommitMetaData commitMetaData)
            throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, UnmergedPathException, IOException {
        final List<Pair<String, byte[]>> convertedData = userData.stream().parallel().map(p -> {
            try {
                return Pair.of(p.getLeft(), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(p.getRight()));
            } catch (JsonProcessingException e) {
                LOGGER.error("Error deserializing user {} ", p.getKey(), e);
                return Pair.of(p.getKey(), (byte[]) null);
            }
        }).collect(Collectors.toList());
        return repositoryUpdater.commit(ref, commitMetaData, "update user", convertedData);
    }

    public List<Pair<String, String>> updateUser(final String userName, final Ref ref, final UserData userData, final CommitMetaData commitMetaData)
            throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, UnmergedPathException, JsonProcessingException, IOException {
        return repositoryUpdater.commit(ref, commitMetaData, "update user", List.of(Pair.of(userName, MAPPER.writeValueAsBytes(userData))));
    }

}