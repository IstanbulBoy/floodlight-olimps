package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.floodlightcontroller.routing.Path;

public class PathJSONSerializer extends JsonSerializer<Path> {

	@Override
	public void serialize(Path path, JsonGenerator jGen, SerializerProvider arg2) throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeStringField("id", path.getEndPoints().toString());
        jGen.writeStringField("path", path.getLinks().toString());
        jGen.writeEndObject();
	}
	
    /**
     * Tells SimpleModule that we are the serializer for OFSwitchImpl
     */
    @Override
    public Class<Path> handledType() {
        return Path.class;
    }

}
