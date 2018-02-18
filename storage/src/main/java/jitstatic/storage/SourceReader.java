package jitstatic.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jitstatic.StorageData;

public class SourceReader {
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(Feature.ALLOW_COMMENTS);

	public Object readStorageData(final InputStream is, final String type)
			throws JsonParseException, JsonMappingException, IOException  {
		Objects.requireNonNull(type);
		switch (type) {
		case "application/json":
			return MAPPER.readValue(is, JsonNode.class);
		case "text/plain":
		case "text/html":
		case "text/css":
		case "text/javascript":			
		default:
			return readByteArray(is);
		}
	}

	public StorageData readStorage(final InputStream storageStream) throws IOException {
		try (final JsonParser parser = MAPPER.getFactory().createParser(storageStream);) {
			return parser.readValueAs(StorageData.class);
		}
	}
	
	public byte[] readByteArray(final InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[4096];
		while ((nRead = is.read(data, 0, data.length)) != -1) {
		  buffer.write(data, 0, nRead);
		}
		buffer.flush();
		return buffer.toByteArray();
	}
}
