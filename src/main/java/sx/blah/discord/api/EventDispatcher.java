package sx.blah.discord.api;

import net.jodah.typetools.TypeResolver;
import sx.blah.discord.Discord4J;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * Manages event listeners and event logic.
 */
public class EventDispatcher {

	private ConcurrentHashMap<Class<?>, ConcurrentHashMap<Method, CopyOnWriteArrayList<ListenerPair<Object>>>> methodListeners = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<ListenerPair<IListener>>> classListeners = new ConcurrentHashMap<>();
	private final ExecutorService eventExecutor = Executors.newCachedThreadPool(runnable -> { //Ensures all threads are daemons
		Thread thread = Executors.defaultThreadFactory().newThread(runnable);
		thread.setName("Event Dispatch Thread");
		thread.setDaemon(true);
		return thread;
	});
	private IDiscordClient client;

	public EventDispatcher(IDiscordClient client) {
		this.client = client;
	}

	/**
	 * Registers a listener using {@link EventSubscriber} method annotations.
	 *
	 * @param listener The listener.
	 */
	public void registerListener(Object listener) {
		registerListener(listener, false);
	}

	/**
	 * Registers a single event listener.
	 *
	 * @param listener The listener.
	 */
	public void registerListener(IListener listener) {
		registerListener(listener, false);
	}

	private void registerListener(Object listener, boolean isTemporary) {
		for (Method method : listener.getClass().getMethods()) {
			if (method.getParameterCount() == 1
					&& method.isAnnotationPresent(EventSubscriber.class)) {
				method.setAccessible(true);
				Class<?> eventClass = method.getParameterTypes()[0];
				if (Event.class.isAssignableFrom(eventClass)) {
					if (!methodListeners.containsKey(eventClass))
						methodListeners.put(eventClass, new ConcurrentHashMap<>());

					if (!methodListeners.get(eventClass).containsKey(method))
						methodListeners.get(eventClass).put(method, new CopyOnWriteArrayList<>());

					methodListeners.get(eventClass).get(method).add(new ListenerPair<>(isTemporary, listener));
					Discord4J.LOGGER.trace("Registered method listener {}", listener.getClass().getSimpleName(), method.toString());
				}
			}
		}
	}

	private <T extends Event> void registerListener(IListener<T> listener, boolean isTemporary) {
		Class<?> rawType = TypeResolver.resolveRawArgument(IListener.class, listener.getClass());
		if (Event.class.isAssignableFrom(rawType)) {
			if (!classListeners.containsKey(rawType))
				classListeners.put(rawType, new CopyOnWriteArrayList<>());

			Discord4J.LOGGER.trace("Registered IListener {}", listener.getClass().getSimpleName());
			classListeners.get(rawType).add(new ListenerPair<>(isTemporary, listener));
		}
	}

	/**
	 * This registers a temporary event listener using {@link EventSubscriber} method annotations.
	 * Meaning that when it listens to an event, it immediately unregisters itself.
	 *
	 * @param listener The listener.
	 */
	public void registerTemporaryListener(Object listener) {
		registerListener(listener, true);
	}

	/**
	 * This registers a temporary single event listener.
	 * Meaning that when it listens to an event, it immediately unregisters itself.
	 *
	 * @param listener The listener.
	 */
	public <T extends Event> void registerTemporaryListener(IListener<T> listener) {
		registerListener(listener, true);
	}

	/**
	 * This causes the currently executing thread to wait until the specified event is dispatched.
	 *
	 * @param eventClass The class of the event to wait for.
	 * @param <T> The event type to wait for.
	 *
	 * @throws InterruptedException
	 */
	public <T extends Event> void waitFor(Class<T> eventClass) throws InterruptedException {
		waitFor((T event) -> true);
	}

	/**
	 * This causes the currently executing thread to wait until the specified event is dispatched and the provided
	 * {@link Predicate} returns true.
	 *
	 * @param filter This is called to determine whether the thread should be resumed as a result of this event.
	 * @param <T> The event type to wait for.
	 *
	 * @throws InterruptedException
	 */
	public <T extends Event> void waitFor(Predicate<T> filter) throws InterruptedException {
		final Thread currentThread = Thread.currentThread();
		synchronized (currentThread) {
			registerListener(new IListener<T>() {
				@Override
				public void handle(T event) {
					if (filter.test(event)) {
						client.getDispatcher().unregisterListener(this);
						synchronized (currentThread) {
							currentThread.notify();
						}
					}
				}
			});
			currentThread.wait();
		}
	}

	/**
	 * Unregisters a listener using {@link EventSubscriber} method annotations.
	 *
	 * @param listener The listener.
	 */
	public void unregisterListener(Object listener) {
		for (Method method : listener.getClass().getDeclaredMethods()) {
			if (method.getParameterCount() == 1) {
				Class<?> eventClass = method.getParameterTypes()[0];
				if (Event.class.isAssignableFrom(eventClass)) {
					if (methodListeners.containsKey(eventClass))
						if (methodListeners.get(eventClass).containsKey(method)) {
							methodListeners.get(eventClass).get(method).removeIf((ListenerPair pair) -> pair.listener == listener); //Yes, the == is intentional. We want the exact same instance.
							Discord4J.LOGGER.trace("Unregistered method listener {}", listener.getClass().getSimpleName(), method.toString());
						}
				}
			}
		}
	}

	/**
	 * Unregisters a single event listener.
	 *
	 * @param listener The listener.
	 */
	public void unregisterListener(IListener listener) {
		Class<?> rawType = TypeResolver.resolveRawArgument(IListener.class, listener.getClass());
		if (Event.class.isAssignableFrom(rawType)) {
			if (classListeners.containsKey(rawType)) {
				classListeners.get(rawType).removeIf((ListenerPair pair) -> pair.listener == listener); //Yes, the == is intentional. We want the exact same instance.
				Discord4J.LOGGER.trace("Unregistered IListener {}", listener.getClass().getSimpleName());
			}
		}
	}

	/**
	 * Dispatches an event.
	 *
	 * @param event The event.
	 */
	public void dispatch(Event event) {
		if (client.isReady()) {
			eventExecutor.submit(() -> {
				Discord4J.LOGGER.trace("Dispatching event of type {}", event.getClass().getSimpleName());
				event.client = client;

				methodListeners.entrySet().stream()
						.filter(e -> e.getKey().isAssignableFrom(event.getClass()))
						.map(e -> e.getValue())
						.forEach(m ->
								m.forEach((k, v) ->
										v.forEach(o -> {
											try {
												k.invoke(o.listener, event);
												if (o.isTemporary)
													unregisterListener(o.listener);
											} catch (IllegalAccessException | InvocationTargetException e) {
												Discord4J.LOGGER.error("Error dispatching event "+event.getClass().getSimpleName(), e);
											} catch (Exception e) {
												Discord4J.LOGGER.error("Unhandled exception caught dispatching event "+event.getClass().getSimpleName(), e);
											}
										})));

				classListeners.entrySet().stream()
						.filter(e -> e.getKey().isAssignableFrom(event.getClass()))
						.map(e -> e.getValue())
						.forEach(s -> s.forEach(l -> {
							try {
								l.listener.handle(event);

								if (l.isTemporary)
									unregisterListener(l.listener);
							} catch (ClassCastException e) {
								//FIXME: This occurs when a lambda expression is used to create an IListener leading it to be registered under the type 'Event'. This is due to a bug in TypeTools: https://github.com/jhalterman/typetools/issues/14
						 	} catch (Exception e) {
								Discord4J.LOGGER.error("Unhandled exception caught dispatching event "+event.getClass().getSimpleName(), e);
							}
						}));
			});
		}
	}

	/**
	 * This is used to differentiate temporary event listeners from permanent ones.
	 *
	 * @param <V> The type of listener, either {@link Object} or {@link IListener}
	 */
	private static class ListenerPair<V> {

		/**
		 * Whether the listener is temporary.
		 * True if a temporary listener, false if otherwise.
		 */
		final boolean isTemporary;
		/**
		 * The actual listener object instance.
		 */
		final V listener;

		private ListenerPair(boolean isTemporary, V listener) {
			this.isTemporary = isTemporary;
			this.listener = listener;
		}
	}
}