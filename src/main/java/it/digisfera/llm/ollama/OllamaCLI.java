package it.digisfera.llm.ollama;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import it.digisfera.llm.api.Chat;
import it.digisfera.llm.api.Connection;
import it.digisfera.llm.api.DriverManager;

public class OllamaCLI {

	private static final String MODEL = "llama3.1";
	private static final String URL = "http://localhost:11434/api";

	public static void main(String[] args) throws Exception {
		Class.forName(OllamaDriver.class.getName());
		String url = URL;
		Properties properties = new Properties();
		String model = MODEL;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--url":
				if (i + 1 < args.length) {
					url = args[i + 1];
					i++;
				}
				break;
			case "--username":
				if (i + 1 < args.length) {
					properties.setProperty(Connection.USERNAME, args[i + 1]);
					i++;
				}
				break;
			case "--password":
				if (i + 1 < args.length) {
					properties.setProperty(Connection.PASSWORD, args[i + 1]);
					i++;
				}
				break;
			case "--model":
				if (i + 1 < args.length) {
					model = args[i + 1];
				}
				break;
			default:
				System.out.println("Opzione non riconosciuta: " + args[i]);
				break;
			}
		}
		System.out.println("URL: " + url);
		System.out.println("Model: " + model);
		new OllamaCLI(url, properties, model).run();
	}

	private final String model;
	private final String url;
	private final Properties properties;

	public OllamaCLI(String url, Properties properties, String model) {
		this.url = url;
		this.properties = properties;
		this.model = model;
	}

	public void run() throws IOException {
		Connection conn = DriverManager.connect(OllamaDriver.URL_PREFIX + url, properties);
		Chat chat = conn.chat(model, null, m -> {
			if (m == null) {
				System.out.println();
			} else {
				System.out.print(m);
			}
		}, null);
		System.out.print("> ");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String message = in.readLine();
			if ("/bye".equalsIgnoreCase(message)) {
				return;
			}
			chat.sendMessage(message);
			System.out.print("> ");
		}
	}

}
