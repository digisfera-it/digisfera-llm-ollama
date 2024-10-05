package it.digisfera.llm.ollama;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OllamaDriverTest {
	
	@Test
	public void test() {
		OllamaDriver driver = new OllamaDriver();
		assertFalse(driver.acceptsURL(null));
		assertFalse(driver.acceptsURL("http://localhost"));
		assertTrue(driver.acceptsURL(OllamaDriver.URL_PREFIX + "http://localhost"));
	}

}
