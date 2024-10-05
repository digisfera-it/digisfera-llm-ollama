package it.digisfera.llm.ollama;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

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

		private OllamaChat(CloseableHttpClient httpClient, String model, List<Message> history,
				Consumer<String> contentConsumer, Consumer<Message> messageConsumer) {
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
				request.setHeader("Content-Type", "application/json");
				request.setEntity(new StringEntity(objectMapper.writeValueAsString(this), StandardCharsets.UTF_8));
				HttpResponse response = httpClient.execute(request);
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(response.getEntity().getContent()))) {
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

	private static Role toRole(String role) {
		return Role.valueOf(role.toUpperCase());
	}

	private final CloseableHttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final URI uri;

	public OllamaConnection(ObjectMapper objectMapper, URI uri, Properties properties) {
		this.objectMapper = objectMapper;
		this.uri = uri;
		if (properties == null) {
			properties = new Properties();
		}
		String username = properties.getProperty(USERNAME, "");
		String password = properties.getProperty(PASSWORD, "");
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		if (!username.isEmpty() && !password.isEmpty()) {
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
			httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
		}
		httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom()
				.setConnectTimeout(Integer.parseInt(properties.getProperty(CONNECTION_TIMEOUT, "10000")))
				.setSocketTimeout(Integer.parseInt(properties.getProperty(SOCKET_TIMEOUT, "30000"))).build());
		this.httpClient = httpClientBuilder.build();
	}

	@Override
	public Chat chat(String model, List<Message> history, Consumer<String> contentConsumer,
			Consumer<Message> messageConsumer) {
		return new OllamaChat(httpClient, model, history, contentConsumer, messageConsumer);
	}

	@Override
	protected void finalize() throws Throwable {
		httpClient.close();
	}

}
