package it.digisfera.llm.ollama;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.digisfera.llm.api.Chat;
import it.digisfera.llm.api.Connection;
import it.digisfera.llm.api.Message;
import it.digisfera.llm.api.Role;

public class OllamaConnection implements Connection {

	private class OllamaChat implements Chat {

		private final Consumer<String> contentConsumer;
		private final CloseableHttpClient httpClient;
		private final Consumer<Message> messageConsumer;
		private final List<Message> messages;
		private final String model;

		private OllamaChat(CloseableHttpClient httpClient, String model, List<Message> history, Consumer<String> contentConsumer,
				Consumer<Message> messageConsumer) {
			this.httpClient = httpClient;
			this.model = model;
			if (history == null) {
				this.messages = new LinkedList<>();
			} else {
				this.messages = new LinkedList<>(history);
			}
			this.contentConsumer = contentConsumer;
			this.messageConsumer = messageConsumer;
		}

		private void addMessage(Message message) {
			messages.add(message);
			if (messageConsumer != null) {
				messageConsumer.accept(message);
			}
		}

		@Override
		protected void finalize() throws Throwable {
			httpClient.close();
		}

		@Override
		public List<Message> getMessages() {
			return Collections.unmodifiableList(messages);
		}

		@Override
		public String getModel() {
			return model;
		}

		@Override
		public synchronized void sendMessage(String message) throws IOException {
			Message reply = null;
			StringBuilder contentBuilder = new StringBuilder();
			try {
				addMessage(new Message(Role.USER, message));
				HttpPost request = new HttpPost(uri);
				if (authenticator != null) {
					authenticator.accept(request);
				}
				request.setHeader("Content-Type", "application/json");
				request.setEntity(new StringEntity(objectMapper.writeValueAsString(this), StandardCharsets.UTF_8));
				HttpResponse response = httpClient.execute(request);
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						JsonNode jsonNode = objectMapper.readTree(line);
						if (reply == null) {
							reply = new Message(toRole(jsonNode.get("message").get("role").asText()), "");
						}
						String content = jsonNode.get("message").get("content").asText();
						contentBuilder.append(content);
						if (contentConsumer != null) {
							contentConsumer.accept(content);
						}
					}
				}
			} finally {
				if (contentConsumer != null) {
					contentConsumer.accept(null);
				}
				if (reply != null) {
					reply.setContent(contentBuilder.toString());
					addMessage(reply);
				}
			}
		}
	}

	private static String encodeBasicAuth(String username, String password, Charset charset) {
		if (username == null || username.isEmpty()) {
			throw new IllegalArgumentException("Username must not be empty");
		}
		if (username.contains(":")) {
			throw new IllegalArgumentException("Username must not contain a colon");
		}
		if (password == null || password.isEmpty()) {
			throw new IllegalArgumentException("Password must not be empty");
		}
		if (charset == null) {
			charset = StandardCharsets.ISO_8859_1;
		}
		CharsetEncoder encoder = charset.newEncoder();
		if (!encoder.canEncode(username) || !encoder.canEncode(password)) {
			throw new IllegalArgumentException(
					"Username or password contains characters that cannot be encoded to " + charset.displayName());
		}
		String credentialsString = username + ":" + password;
		byte[] encodedBytes = Base64.getEncoder().encode(credentialsString.getBytes(charset));
		return new String(encodedBytes, charset);
	}

	private static Consumer<HttpRequestBase> getBasicAuthenticator(String username, String password) {
		String basicAuth;
		try {
			basicAuth = encodeBasicAuth(username, password, null);
		} catch (Exception ex) {
			return null;
		}
		return request -> {
			request.setHeader("Authorization", "Basic " + basicAuth);
		};
	}

	private static Role toRole(String role) {
		return Role.valueOf(role.toUpperCase());
	}

	private final Consumer<HttpRequestBase> authenticator;
	private final ObjectMapper objectMapper;
	private final URI uri;

	private OllamaConnection(ObjectMapper objectMapper, URI uri, Consumer<HttpRequestBase> authenticator) {
		this.objectMapper = objectMapper;
		this.uri = uri;
		this.authenticator = authenticator;
	}

	public OllamaConnection(ObjectMapper objectMapper, URI uri, String username, String password) {
		this(objectMapper, uri, getBasicAuthenticator(username, password));
	}

	@Override
	public Chat chat(String model, List<Message> history, Consumer<String> contentConsumer, Consumer<Message> messageConsumer) {
		return new OllamaChat(HttpClients.createDefault(), model, history, contentConsumer, messageConsumer);
	}

}
