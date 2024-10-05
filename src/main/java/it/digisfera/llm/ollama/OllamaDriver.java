package it.digisfera.llm.ollama;

import java.io.IOException;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.digisfera.llm.api.Driver;
import it.digisfera.llm.api.DriverManager;
import it.digisfera.llm.api.Connection;

public class OllamaDriver implements Driver {

	public static final String URL_PREFIX = "jllmc:ollama:";

	static {
		DriverManager.registerDriver(new OllamaDriver());
	}

	private final ObjectMapper objectMapper;

	public OllamaDriver() {
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public boolean acceptsURL(String url) {
		return url != null && url.startsWith(URL_PREFIX);
	}

	@Override
	public Connection connect(String url, String username, String password) throws IOException {
		if (!acceptsURL(url)) {
			throw new IllegalArgumentException("Invalid URL");
		}
		return new OllamaConnection(objectMapper, URI.create(url.substring(URL_PREFIX.length())), username, password);
	}

}
