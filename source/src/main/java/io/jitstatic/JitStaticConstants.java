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

import static org.eclipse.jgit.lib.Constants.R_HEADS;

import java.util.Set;
import org.eclipse.jgit.lib.Constants;

public class JitStaticConstants {

    public static final String REFS_JITSTATIC = Constants.R_REFS + "jitstatic/";
    public static final String APPLICATION_JSON = "application/json";
    public static final String METADATA = ".metadata";
    public static final String GIT_REALM = "git";
    public static final String JITSTATIC_KEYADMIN_REALM = "keyadmin";
    public static final String JITSTATIC_KEYUSER_REALM = "keyuser";
    public static final String USERS = ".users/";
    public static final String PULL = "pull";
    public static final String PUSH = "push";
    public static final String FORCEPUSH = "forcepush";
    public static final String CREATE = "create";
    public static final String SECRETS = "secrets";
    public static final Set<String> ROLES = Set.of(PULL, PUSH, FORCEPUSH, SECRETS, CREATE);
    public static final String DECLAREDHEADERS = "declaredheaders";
    public static final String DEFERREDHEADERS = "deferredheaders";
    public static final String X_JITSTATIC = "X-jitstatic";
    public static final String X_JITSTATIC_MAIL = X_JITSTATIC + "-mail";
    public static final String X_JITSTATIC_MESSAGE = X_JITSTATIC + "-message";
    public static final String X_JITSTATIC_NAME = X_JITSTATIC + "-name";
    public static final String JITSTATIC_NOWHERE = "jitstatic@nowhere";
    public static final String REFS_HEADS_SECRETS = R_HEADS + SECRETS;

}
