/*
 * Copyright 2012-2016 the original author or authors.
 *
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
 */

package org.springframework.boot.logging.logback;

import java.io.File;
import java.io.FileReader;
import java.util.logging.Handler;
import java.util.logging.LogManager;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.SLF4JLogFactory;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.impl.StaticLoggerBinder;

import org.springframework.boot.logging.AbstractLoggingSystemTests;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.testutil.InternalOutputCapture;
import org.springframework.boot.testutil.Matched;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Tests for {@link LogbackLoggingSystem}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author David Harrigan
 */
public class LogbackLoggingSystemTests extends AbstractLoggingSystemTests {

	@Rule
	public InternalOutputCapture output = new InternalOutputCapture();

	private final LogbackLoggingSystem loggingSystem = new LogbackLoggingSystem(
			getClass().getClassLoader());

	private Log logger;

	private LoggingInitializationContext initializationContext;

	private MockEnvironment environment;

	@Before
	public void setup() {
		this.logger = new SLF4JLogFactory().getInstance(getClass().getName());
		this.environment = new MockEnvironment();
		this.initializationContext = new LoggingInitializationContext(this.environment);
	}

	@Override
	@After
	public void clear() {
		this.loggingSystem.cleanUp();
	}

	@Test
	public void noFile() throws Exception {
		this.loggingSystem.beforeInitialize();
		this.logger.info("Hidden");
		this.loggingSystem.initialize(this.initializationContext, null, null);
		this.logger.info("Hello world");
		String output = this.output.toString().trim();
		assertThat(output).contains("Hello world").doesNotContain("Hidden");
		assertThat(getLineWithText(output, "Hello world")).contains("INFO");
		assertThat(new File(tmpDir() + "/spring.log").exists()).isFalse();
	}

	@Test
	public void withFile() throws Exception {
		this.loggingSystem.beforeInitialize();
		this.logger.info("Hidden");
		this.loggingSystem.initialize(this.initializationContext, null,
				getLogFile(null, tmpDir()));
		this.logger.info("Hello world");
		String output = this.output.toString().trim();
		File file = new File(tmpDir() + "/spring.log");
		assertThat(output).contains("Hello world").doesNotContain("Hidden");
		assertThat(getLineWithText(output, "Hello world")).contains("INFO");
		assertThat(file.exists()).isTrue();
		assertThat(getLineWithText(file, "Hello world")).contains("INFO");
	}

	@Test
	public void testBasicConfigLocation() throws Exception {
		this.loggingSystem.beforeInitialize();
		ILoggerFactory factory = StaticLoggerBinder.getSingleton().getLoggerFactory();
		LoggerContext context = (LoggerContext) factory;
		Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		assertThat(root.getAppender("CONSOLE")).isNotNull();
	}

	@Test
	public void testNonDefaultConfigLocation() throws Exception {
		this.loggingSystem.beforeInitialize();
		this.loggingSystem.initialize(this.initializationContext,
				"classpath:logback-nondefault.xml",
				getLogFile(tmpDir() + "/tmp.log", null));
		this.logger.info("Hello world");
		String output = this.output.toString().trim();
		assertThat(output).contains("Hello world").contains(tmpDir() + "/tmp.log");
		assertThat(output).endsWith("BOOTBOOT");
		assertThat(new File(tmpDir() + "/tmp.log").exists()).isFalse();
	}

	@Test
	public void testWithResources() throws Exception {
		this.loggingSystem.beforeInitialize();
		this.loggingSystem.initialize(this.initializationContext,
				"classpath:logback-with-resources.xml",
				getLogFile(tmpDir() + "/tmp.log", null));
		this.logger.info("Hello world");
		String output = this.output.toString().trim();
		assertThat(output).contains("Hello world").contains(tmpDir() + "/tmp.log");
		assertThat(output).endsWith("BOOTBOOT");
		assertThat(new File(tmpDir() + "/tmp.log").exists()).isFalse();
	}

	@Test
	public void testLogbackSpecificSystemProperty() throws Exception {
		System.setProperty("logback.configurationFile", "/foo/my-file.xml");
		try {
			this.loggingSystem.beforeInitialize();
			this.loggingSystem.initialize(this.initializationContext, null, null);
			String output = this.output.toString().trim();
			assertThat(output).contains("Ignoring 'logback.configurationFile' "
					+ "system property. Please use 'logging.config' instead.");
		}
		finally {
			System.clearProperty("logback.configurationFile");
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testNonexistentConfigLocation() throws Exception {
		this.loggingSystem.beforeInitialize();
		this.loggingSystem.initialize(this.initializationContext,
				"classpath:logback-nonexistent.xml", null);
	}

	@Test
	public void setLevel() throws Exception {
		this.loggingSystem.beforeInitialize();
		this.loggingSystem.initialize(this.initializationContext, null, null);
		this.logger.debug("Hello");
		this.loggingSystem.setLogLevel("org.springframework.boot", LogLevel.DEBUG);
		this.logger.debug("Hello");
		assertThat(StringUtils.countOccurrencesOf(this.output.toString(), "Hello"))
				.isEqualTo(1);
	}

	@Test
	public void loggingThatUsesJulIsCaptured() {
		this.loggingSystem.beforeInitialize();
		this.loggingSystem.initialize(this.initializationContext, null, null);
		java.util.logging.Logger julLogger = java.util.logging.Logger
				.getLogger(getClass().getName());
		julLogger.info("Hello world");
		String output = this.output.toString().trim();
		assertThat(output).contains("Hello world");
	}

	@Test
	public void loggingLevelIsPropagatedToJul() {
		this.loggingSystem.beforeInitialize();
		this.loggingSystem.initialize(this.initializationContext, null, null);
		this.loggingSystem.setLogLevel(getClass().getName(), LogLevel.DEBUG);
		java.util.logging.Logger julLogger = java.util.logging.Logger
				.getLogger(getClass().getName());
		julLogger.fine("Hello debug world");
		String output = this.output.toString().trim();
		assertThat(output).contains("Hello debug world");
	}

	@Test
	public void jbossLoggingIsConfiguredToUseSlf4j() {
		this.loggingSystem.beforeInitialize();
		assertThat(System.getProperty("org.jboss.logging.provider")).isEqualTo("slf4j");
	}

	@Test
	public void bridgeHandlerLifecycle() {
		assertThat(bridgeHandlerInstalled()).isFalse();
		this.loggingSystem.beforeInitialize();
		assertThat(bridgeHandlerInstalled()).isTrue();
		this.loggingSystem.cleanUp();
		assertThat(bridgeHandlerInstalled()).isFalse();
	}

	@Test
	public void standardConfigLocations() throws Exception {
		String[] locations = this.loggingSystem.getStandardConfigLocations();
		assertThat(locations).containsExactly("logback-test.groovy", "logback-test.xml",
				"logback.groovy", "logback.xml");
	}

	@Test
	public void springConfigLocations() throws Exception {
		String[] locations = getSpringConfigLocations(this.loggingSystem);
		assertThat(locations).containsExactly("logback-test-spring.groovy",
				"logback-test-spring.xml", "logback-spring.groovy", "logback-spring.xml");
	}

	private boolean bridgeHandlerInstalled() {
		java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
		Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) {
			if (handler instanceof SLF4JBridgeHandler) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void testConsolePatternProperty() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("logging.pattern.console", "%logger %msg");
		LoggingInitializationContext loggingInitializationContext = new LoggingInitializationContext(
				environment);
		this.loggingSystem.initialize(loggingInitializationContext, null, null);
		this.logger.info("Hello world");
		String output = this.output.toString().trim();
		assertThat(getLineWithText(output, "Hello world")).doesNotContain("INFO");
	}

	@Test
	public void testLevelPatternProperty() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("logging.pattern.level", "X%clr(%p)X");
		LoggingInitializationContext loggingInitializationContext = new LoggingInitializationContext(
				environment);
		this.loggingSystem.initialize(loggingInitializationContext, null, null);
		this.logger.info("Hello world");
		String output = this.output.toString().trim();
		assertThat(getLineWithText(output, "Hello world")).contains("XINFOX");
	}

	@Test
	public void testFilePatternProperty() throws Exception {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("logging.pattern.file", "%logger %msg");
		LoggingInitializationContext loggingInitializationContext = new LoggingInitializationContext(
				environment);
		File file = new File(tmpDir(), "logback-test.log");
		LogFile logFile = getLogFile(file.getPath(), null);
		this.loggingSystem.initialize(loggingInitializationContext, null, logFile);
		this.logger.info("Hello world");
		String output = this.output.toString().trim();
		assertThat(getLineWithText(output, "Hello world")).contains("INFO");
		assertThat(getLineWithText(file, "Hello world")).doesNotContain("INFO");
	}

	@Test
	public void exceptionsIncludeClassPackaging() throws Exception {
		this.loggingSystem.beforeInitialize();
		this.loggingSystem.initialize(this.initializationContext, null,
				getLogFile(null, tmpDir()));
		Matcher<String> expectedOutput = containsString("[junit-");
		this.output.expect(expectedOutput);
		this.logger.warn("Expected exception", new RuntimeException("Expected"));
		String fileContents = FileCopyUtils
				.copyToString(new FileReader(new File(tmpDir() + "/spring.log")));
		assertThat(fileContents).is(Matched.by(expectedOutput));
	}

	@Test
	public void customExceptionConversionWord() throws Exception {
		System.setProperty("LOG_EXCEPTION_CONVERSION_WORD", "%ex");
		try {
			this.loggingSystem.beforeInitialize();
			this.logger.info("Hidden");
			this.loggingSystem.initialize(this.initializationContext, null,
					getLogFile(null, tmpDir()));
			Matcher<String> expectedOutput = Matchers.allOf(
					containsString("java.lang.RuntimeException: Expected"),
					not(containsString("Wrapped by:")));
			this.output.expect(expectedOutput);
			this.logger.warn("Expected exception",
					new RuntimeException("Expected", new RuntimeException("Cause")));
			String fileContents = FileCopyUtils
					.copyToString(new FileReader(new File(tmpDir() + "/spring.log")));
			assertThat(fileContents).is(Matched.by(expectedOutput));
		}
		finally {
			System.clearProperty("LOG_EXCEPTION_CONVERSION_WORD");
		}
	}

	@Test
	public void reinitializeShouldSetSystemProperty() throws Exception {
		// gh-5491
		this.loggingSystem.beforeInitialize();
		this.logger.info("Hidden");
		this.loggingSystem.initialize(this.initializationContext, null, null);
		LogFile logFile = getLogFile(tmpDir() + "/example.log", null, false);
		this.loggingSystem.initialize(this.initializationContext,
				"classpath:logback-nondefault.xml", logFile);
		assertThat(System.getProperty("LOG_FILE")).endsWith("example.log");
	}

	private String getLineWithText(File file, String outputSearch) throws Exception {
		return getLineWithText(FileCopyUtils.copyToString(new FileReader(file)),
				outputSearch);
	}

	private String getLineWithText(String output, String outputSearch) {
		String[] lines = output.split("\\r?\\n");
		for (String line : lines) {
			if (line.contains(outputSearch)) {
				return line;
			}
		}
		return null;
	}

}
