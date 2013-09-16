package tigase.component2.eventbus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultEventBus extends EventBus {

	protected final Map<EventType<?>, List<EventHandler>> handlers = new HashMap<EventType<?>, List<EventHandler>>();

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	private boolean throwingExceptionOn = true;

	@Override
	public <H extends EventHandler> void addHandler(EventType<H> type, H handler) {
		synchronized (this.handlers) {
			List<EventHandler> lst = handlers.get(type);
			if (lst == null) {
				lst = new ArrayList<EventHandler>();
				handlers.put(type, lst);
			}
			lst.add(handler);
		}
	}

	@Override
	public <H extends EventHandler> void addListener(EventListener listener) {
		addListener(null, listener);
	}

	@Override
	public <H extends EventHandler> void addListener(EventType<H> type, EventListener listener) {
		synchronized (this.handlers) {
			List<EventHandler> lst = handlers.get(type);
			if (lst == null) {
				lst = new ArrayList<EventHandler>();
				handlers.put(type, lst);
			}
			lst.add(listener);
		}
	}

	protected void doFire(Event<EventHandler> event, Object source) {
		if (event == null) {
			throw new NullPointerException("Cannot fire null event");
		}

		setEventSource(event, source);

		final ArrayList<EventHandler> handlers = new ArrayList<EventHandler>();
		handlers.addAll(getHandlersList(event.getType(), source));
		handlers.addAll(getHandlersList(null, source));
		// if (source != null) {
		// handlers.addAll(getHandlersList(event.getType(), null));
		// handlers.addAll(getHandlersList(null, null));
		// }

		final Set<Throwable> causes = new HashSet<Throwable>();

		for (EventHandler eventHandler : handlers) {
			try {
				if (eventHandler instanceof EventListener) {
					((EventListener) eventHandler).onEvent(event);
				} else {
					event.dispatch(eventHandler);
				}
			} catch (Throwable e) {
				if (log.isLoggable(Level.WARNING))
					log.log(Level.WARNING, "", e);
				causes.add(e);
			}
		}

		if (!causes.isEmpty()) {
			if (throwingExceptionOn)
				throw new EventBusException(causes);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void fire(Event<?> event) {
		doFire((Event<EventHandler>) event, null);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void fire(Event<?> event, Object source) {
		doFire((Event<EventHandler>) event, source);
	}

	protected Collection<EventHandler> getHandlersList(EventType<?> type, Object source) {
		List<EventHandler> list = this.handlers.get(type);
		if (list == null)
			return Collections.emptyList();
		else
			return list;
	}

	public boolean isThrowingExceptionOn() {
		return throwingExceptionOn;
	}

	@Override
	public <H extends EventHandler> void remove(EventType<H> type, H handler) {
		synchronized (this.handlers) {
			List<EventHandler> lst = handlers.get(type);
			if (lst != null) {
				lst.remove(handler);
				if (lst.isEmpty()) {
					handlers.remove(type);
				}
			}
		}
	}

	@Override
	public <H extends EventHandler> void remove(H handler) {
		synchronized (this.handlers) {
			Iterator<Entry<EventType<?>, List<EventHandler>>> iterator = this.handlers.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<EventType<?>, List<EventHandler>> entry = iterator.next();
				if (entry != null) {
					entry.getValue().remove(handler);
					if (entry.getValue().isEmpty())
						iterator.remove();
				}
			}
		}
	}

	public void setThrowingExceptionOn(boolean throwingExceptionOn) {
		this.throwingExceptionOn = throwingExceptionOn;
	}

}
