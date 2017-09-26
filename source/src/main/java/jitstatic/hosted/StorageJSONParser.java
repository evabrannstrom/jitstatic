package jitstatic.hosted;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser.Feature;

class StorageJSONParser {

	private static final JsonFactory mapper = new JsonFactory().enable(Feature.ALLOW_COMMENTS)
			.enable(Feature.STRICT_DUPLICATE_DETECTION);

	public void parse(InputStream bc) throws IOException {
		try (final JsonParser parser = mapper.createParser(bc);) {
			if (parser.nextToken() != JsonToken.START_OBJECT) {
				errorParsingJSONFormat(parser);
			}

			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				if (parser.nextToken() != JsonToken.START_OBJECT) {
					errorParsingJSONFormat(parser);
				}
				int m = 0;
				while (parser.nextToken() == JsonToken.FIELD_NAME) {
					m ^= checkStorageData(parser);
				}
				if ((m & 0xFF) != 0xFF) {
					errorParsingStorageFormat(parser);
				}
			}
		}
	}

	private int checkStorageData(final JsonParser parser) throws IOException {
		switch (parser.getText()) {
		case "users":
			parseUsers(parser);
			return 0xF0;
		case "data":
			parseData(parser);
			return 0xF;
		default:
			errorParsingStorageFormat(parser);
		}
		return 0;
	}

	private void parseData(final JsonParser parser) throws IOException {
		final ArrayDeque<JsonToken> stack = new ArrayDeque<>();
		parserData(parser, stack);
		if (!stack.isEmpty())
			errorParsingJSONFormat(parser);
	}

	private void parserData(final JsonParser parser, final ArrayDeque<JsonToken> stack) throws IOException {
		do {
			final JsonToken nextToken = parser.nextToken();
			if (nextToken == null) {
				break;
			}
			if (nextToken == JsonToken.START_OBJECT || nextToken == JsonToken.START_ARRAY) {
				stack.push(nextToken);
			} else if (nextToken == JsonToken.END_OBJECT || nextToken == JsonToken.END_ARRAY) {
				final JsonToken pop = stack.pop();
				if (JsonToken.END_ARRAY.equals(nextToken)) {
					if (pop != JsonToken.START_ARRAY) {
						errorParsingJSONFormat(parser);
					}
				} else {
					if (pop != JsonToken.START_OBJECT) {
						errorParsingJSONFormat(parser);
					}
				}
			}
		} while (!stack.isEmpty());
	}

	private void parseUsers(final JsonParser parser) throws IOException {
		if (parser.nextToken() != JsonToken.START_ARRAY) {
			errorParsingStorageFormat(parser);
		}
		JsonToken nextToken;
		while ((nextToken = parser.nextToken()) == JsonToken.START_OBJECT) {
			int m = 0;
			while ((parser.nextToken()) == JsonToken.FIELD_NAME) {
				final String field = parser.getText();
				if (parser.nextToken() != JsonToken.VALUE_STRING)
					errorParsingStorageFormat(parser);
				m ^= checkUser(parser, field);
			}
			if ((m & 0xFF) != 0xFF) {
				errorParsingStorageFormat(parser);
			}
		}
		if (nextToken != JsonToken.END_ARRAY) {
			errorParsingJSONFormat(parser);
		}
	}

	private int checkUser(final JsonParser parser, final String field) throws IOException {
		switch (field) {
		case "user":
			return 0xF;
		case "password":
			return 0xF0;
		default:
			errorParsingStorageFormat(parser);
		}
		return 0;
	}

	private void errorParsingStorageFormat(final JsonParser parser) throws IOException {
		final JsonLocation cl = parser.getCurrentLocation();
		throw new IOException(String.format("File does not have valid store file format at line: %s, column: %s ",
				cl.getLineNr(), cl.getColumnNr()));
	}

	private void errorParsingJSONFormat(final JsonParser parser) throws IOException {
		final JsonLocation cl = parser.getCurrentLocation();
		throw new IOException(
				String.format("File is not valid JSON at line: %s, column: %s", cl.getLineNr(), cl.getColumnNr()));
	}
}
