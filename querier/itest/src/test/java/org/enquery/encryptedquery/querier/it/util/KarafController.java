package org.enquery.encryptedquery.querier.it.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.karaf.shell.api.console.Command;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.apache.sshd.common.util.io.NullInputStream;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KarafController {

	private static final Logger log = LoggerFactory.getLogger(KarafController.class);
	private static final ExecutorService executor = Executors.newCachedThreadPool();
	private static final long SERVICE_TIMEOUT = 120_000L;
	private static final Long COMMAND_TIMEOUT = 300_000L;

	private final SessionFactory sessionFactory;

	public KarafController(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	public String executeCommand(final String command) throws TimeoutException {
		waitForCommandService(command);
		log.info("Will execute command {}", command);
		try {
			Future<String> f = executor.submit(() -> {
				try {
					final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					final PrintStream printStream = new PrintStream(byteArrayOutputStream);
					Session session = sessionFactory.create(new NullInputStream(), printStream, printStream);
					session.execute(command);
					printStream.flush();
					String response = byteArrayOutputStream.toString();
					log.info("Command response:\n{}", response);
					return response;
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(e.getMessage(), e);
				}
			});

			return f.get(COMMAND_TIMEOUT, TimeUnit.MILLISECONDS);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause().getCause();
			throw new RuntimeException(cause.getMessage(), cause);
		} catch (InterruptedException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private Command waitForCommandService(String command) {
		// the commands are represented by services. Due to the asynchronous nature of services they
		// may not be
		// immediately available. This code waits the services to be available, in their secured
		// form. It
		// means that the code waits for the command service to appear with the roles defined.


		if (command == null || command.length() == 0) {
			return null;
		}

		log.info("command: " + command);

		int spaceIdx = command.indexOf(' ');
		if (spaceIdx > 0) {
			command = command.substring(0, spaceIdx);
		}
		int colonIndx = command.indexOf(':');
		String scope = (colonIndx > 0) ? command.substring(0, colonIndx) : "*";
		String name = (colonIndx > 0) ? command.substring(colonIndx + 1) : command;
		try {
			long start = System.currentTimeMillis();
			long cur = start;
			while (cur - start < SERVICE_TIMEOUT) {
				Command command2 = sessionFactory.getRegistry().getCommand(scope, name);
				if (command2 != null) {
					log.info("found command " + command);
					return command2;
				}
				Thread.sleep(100);
				cur = System.currentTimeMillis();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		throw new RuntimeException("command service not loaded " + scope + ":" + name);
	}

	public <T> T getOsgiService(Class<T> type, long timeout, BundleContext bundleContext) {
		return getOsgiService(type, null, timeout, bundleContext);
	}

	public <T> T getOsgiService(Class<T> type, String filter, BundleContext bundleContext) {
		return getOsgiService(type, null, SERVICE_TIMEOUT, bundleContext);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private <T> T getOsgiService(Class<T> type, String filter, long timeout, BundleContext bundleContext) {
		ServiceTracker tracker = null;
		try {
			String flt;
			if (filter != null) {
				if (filter.startsWith("(")) {
					flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
				} else {
					flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
				}
			} else {
				flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
			}
			org.osgi.framework.Filter osgiFilter = FrameworkUtil.createFilter(flt);
			tracker = new ServiceTracker(bundleContext, osgiFilter, null);
			tracker.open(true);
			// Note that the tracker is not closed to keep the reference
			// This is buggy, as the service reference may change i think
			Object svc = type.cast(tracker.waitForService(timeout));
			if (svc == null) {
				Dictionary dic = bundleContext.getBundle().getHeaders();
				log.info("Test bundle headers: " + explode(dic));

				for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
					log.info("ServiceReference: " + ref);
				}

				for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
					log.info("Filtered ServiceReference: " + ref);
				}

				throw new RuntimeException("Gave up waiting for service " + flt);
			}
			return type.cast(svc);
		} catch (InvalidSyntaxException e) {
			throw new IllegalArgumentException("Invalid filter", e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("rawtypes")
	private Collection<ServiceReference> asCollection(ServiceReference[] references) {
		return references != null ? Arrays.asList(references) : Collections.<ServiceReference>emptyList();
	}

	@SuppressWarnings("rawtypes")
	private static String explode(Dictionary dictionary) {
		Enumeration keys = dictionary.keys();
		StringBuffer result = new StringBuffer();
		while (keys.hasMoreElements()) {
			Object key = keys.nextElement();
			result.append(String.format("%s=%s", key, dictionary.get(key)));
			if (keys.hasMoreElements()) {
				result.append(", ");
			}
		}
		return result.toString();
	}
}
